package com.mrzekai.depoakilli.data

import android.app.ActivityManager
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.os.storage.StorageManager
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.AppCacheSnapshot
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.CleanupGroup
import com.mrzekai.depoakilli.model.DeleteMode
import com.mrzekai.depoakilli.model.DeviceInfoSnapshot
import com.mrzekai.depoakilli.model.FolderSize
import com.mrzekai.depoakilli.model.IndexedFile
import com.mrzekai.depoakilli.model.InstalledAppEntry
import com.mrzekai.depoakilli.model.MemorySnapshot
import com.mrzekai.depoakilli.model.ScanFocus
import com.mrzekai.depoakilli.model.ScanSummary
import com.mrzekai.depoakilli.model.StorageFileType
import com.mrzekai.depoakilli.model.StorageReviewItem
import com.mrzekai.depoakilli.model.StorageReviewSummary
import com.mrzekai.depoakilli.model.StorageSnapshot
import com.mrzekai.depoakilli.model.WhatsAppLibrarySummary
import com.mrzekai.depoakilli.model.WhatsAppMediaCategory
import com.mrzekai.depoakilli.model.WhatsAppMediaItem
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.lang.ref.SoftReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Storage-management repository for Cleaner Engine 0.5.
 *
 * Broad file operations are intentionally limited to shared storage and only
 * run after Android grants MANAGE_EXTERNAL_STORAGE. App-private directories in
 * Android/data and Android/obb remain outside this engine.
 */
class DeviceRepository(
    private val context: Context,
    private val aiEngine: AiCleaningEngine = AiCleaningEngine(
        apkInfoReader = AndroidApkInfoReader(context.packageManager),
        installedVersions = AndroidInstalledVersionLookup(context.packageManager),
    ),
) {
    private val duplicateFinder = DuplicateFinder(aiEngine, ::openUriInput)
    private val keptItems = KeptItemsStore.create(context)
    private val excludedFolders = ExcludedFoldersStore.create(context)
    private val trashStore = TrashStore(
        indexFile = File(context.filesDir, TRASH_INDEX_FILE),
        trashRoots = { sharedStorageRoots() },
        isAllowed = { file -> isDeletableSharedStorageFile(file, sharedStorageRoots()) },
        removeFromMediaStore = { path -> removeDeletedPathFromMediaStore(context.contentResolver, path) },
        rescan = { path -> MediaScannerConnection.scanFile(context, arrayOf(path), null, null) },
    )
    private val resolver = context.contentResolver
    private val remover = SharedStorageRemover(resolver, trashStore, onRemoved = { invalidateStorageIndex() })
    private val deviceInfo = DeviceInfoSource(context, storageRoots = { sharedStorageRoots() })
    private val appStats = AppStatsSource(context, ownCacheSize = { ownCacheSize() })
    private val indexer = StorageIndexer(
        fileLimit = { indexedFileLimit() },
        makeIndexedFile = { storageRoot, file -> indexedFile(storageRoot, file) },
    )
    private var sharedIndexCache: SoftReference<List<IndexedFile>>? = null
    private var sharedIndexCachedAtElapsed = 0L

    fun storageSnapshot(): StorageSnapshot = deviceInfo.storageSnapshot()

    fun memorySnapshot(): MemorySnapshot = deviceInfo.memorySnapshot()

    fun deviceInfoSnapshot(): DeviceInfoSnapshot = deviceInfo.deviceInfoSnapshot()

    fun hasUsageAccess(): Boolean = appStats.hasUsageAccess()

    suspend fun appCacheSnapshot(): AppCacheSnapshot = appStats.appCacheSnapshot()

    suspend fun installedAppsSnapshot(): List<InstalledAppEntry> = appStats.installedAppsSnapshot()

    private suspend fun indexSharedStorage(
        onTraversalProgress: (visitedDirectories: Int, discoveredFiles: Int) -> Unit,
    ): List<IndexedFile> = indexer.indexSharedStorage(sharedStorageRoots(), onTraversalProgress)

    private suspend fun indexWhatsAppFiles(
        onDiscovered: (Int, Int) -> Unit,
    ): List<IndexedFile> = indexer.indexWhatsAppFiles(whatsappRootPairs(), MAX_WHATSAPP_FILES, onDiscovered)

    fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

    fun hasWhatsAppAccess(): Boolean = hasAllFilesAccess() && whatsappRoots().any(File::isDirectory)

    fun ownCacheSize(): Long = directorySize(context.cacheDir) +
        (context.externalCacheDir?.let(::directorySize) ?: 0L)

    suspend fun clearOwnCache(): Long = withContext(Dispatchers.IO) {
        val before = ownCacheSize()
        context.cacheDir.listFiles()?.forEach(File::deleteRecursively)
        context.externalCacheDir?.listFiles()?.forEach(File::deleteRecursively)
        (before - ownCacheSize()).coerceAtLeast(0L)
    }

    suspend fun scan(
        focus: ScanFocus = ScanFocus.SMART,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        onHashProgress: HashProgress = { _, _ -> },
        onClassified: (Map<CleanCategory, Long>) -> Unit = {},
    ): ScanSummary = withContext(Dispatchers.IO) {
        if (!hasAllFilesAccess()) {
            return@withContext ScanSummary(limitedAccess = true)
        }

        // Smart and Deep always re-index; tool scans reuse a fresh (10 min) index
        // that any delete, move to Trash, restore or "Refresh" drops (B4).
        val reuseIndex = focus != ScanFocus.SMART && focus != ScanFocus.DEEP
        val cachedIndex = if (reuseIndex) cachedSharedIndex()?.also { onProgress(0, it.size) } else null
        val indexed = cachedIndex
            ?: indexSharedStorage { visitedDirectories, discoveredFiles ->
                onProgress(visitedDirectories, discoveredFiles)
            }.also(::cacheSharedIndex)
        val assessments = ArrayList<CleanableItem>()
        var duplicateResult = DuplicatePolicy.ScanResult()

        indexed.forEach { file ->
            coroutineContext.ensureActive()
            ScanFocusRules.assessmentForFocus(aiEngine, file, focus)?.let { assessment ->
                assessments += file.toCleanable(assessment)
            }
        }
        val apkAssessed = aiEngine.markOlderApkVersions(assessments)
        // V5: a RAW file with a JPEG/HEIC partner in the same folder replaces
        // any broader classification of that RAW file.
        val rawPairs = if (focus == ScanFocus.SMART || focus == ScanFocus.DEEP || focus == ScanFocus.MEDIA) {
            RawPairFinder.find(indexed)
        } else {
            emptyMap()
        }
        val assessed = if (rawPairs.isEmpty()) {
            apkAssessed
        } else {
            val byUri = indexed.associateBy(IndexedFile::uri)
            apkAssessed.filterNot { it.uri in rawPairs } + rawPairs.mapNotNull { (uri, partner) ->
                byUri[uri]?.let { raw -> raw.toCleanable(aiEngine.rawCopyAssessment(raw, partner)) }
            }
        }

        // Progress report only (Faz 5 Home): what the classified files add up
        // to before exact copies are verified, with the same focus, de-dup and
        // user-choice filters as the result. The result itself is unchanged.
        val planScanForReport = focus == ScanFocus.SMART || focus == ScanFocus.DEEP
        val reportItems = assessed
            .filter { item -> ScanFocusRules.matchesFocus(item, focus) }
            .distinctBy(CleanableItem::uri)
        val reportChosen = if (planScanForReport) {
            UserChoiceFilter.apply(reportItems, keptItems.keys(), excludedFolders.folders()).items
        } else {
            reportItems
        }
        onClassified(
            reportChosen.groupBy { it.assessment.category }
                .mapValues { (_, categoryItems) -> categoryItems.sumOf(CleanableItem::sizeBytes) },
        )

        // Smart Clean promises duplicates too, but must stay fast: it verifies
        // files up to QUICK_FULL_HASH_BYTES byte for byte and lists larger
        // sample matches as review-only. Deep/Duplicate/Media verify up to 1 GB.
        if (
            focus == ScanFocus.SMART ||
            focus == ScanFocus.DEEP ||
            focus == ScanFocus.DUPLICATES ||
            focus == ScanFocus.MEDIA
        ) {
            // Files already in Android's trash or unfinished writes are never a
            // duplicate or a "kept original".
            val visible = indexed.filterNot { StoragePathRules.isSystemMarkerFile(it.name) }
            val duplicateSource = if (focus == ScanFocus.MEDIA) {
                visible.filter {
                    val mime = StoragePathRules.normalizeText(it.mimeType)
                    mime.startsWith("image/") || mime.startsWith("video/")
                }
            } else {
                visible
            }
            duplicateResult = findDuplicates(
                duplicateSource,
                maxFullHashBytes = if (focus == ScanFocus.SMART) {
                    CleaningThresholds.QUICK_FULL_HASH_BYTES
                } else {
                    CleaningThresholds.SMART_VERIFY_MAX_BYTES
                },
                onHashProgress = onHashProgress,
            )
        }

        // A verified extra copy is shown as a duplicate (with its protected
        // original) even when it also matches a broader rule such as "old
        // download". Listing duplicates first lets distinctBy keep that
        // classification, so Deep Clean and Delete Duplicates report the same
        // duplicate set.
        val ranked = duplicateResult.mergeWith(assessed)
            .filter { item -> ScanFocusRules.matchesFocus(item, focus) }
            .distinctBy(CleanableItem::uri)
            .sortedByDescending(CleanableItem::sizeBytes)
            .toList()
        // V10/V11: the user's "keep" decisions and excluded folders apply to
        // review groups only; records of files that are gone are dropped.
        // Tool screens list everything without a "hidden" note, so the filter
        // applies to the Smart/Deep plan only.
        val planScan = focus == ScanFocus.SMART || focus == ScanFocus.DEEP
        val keptKeys = keptItems.keys()
        if (keptKeys.isNotEmpty() && planScan) {
            keptItems.retainOnly(
                existing = indexed.asSequence().map(KeptItemsStore::keyOf).filter(keptKeys::contains).toHashSet(),
                scannedVolumes = indexed.mapTo(hashSetOf(), IndexedFile::volumeRoot),
            )
        }
        val userChoices = if (planScan) {
            UserChoiceFilter.apply(ranked, keptItems.keys(), excludedFolders.folders())
        } else {
            UserChoiceFilter.Result(ranked, 0, 0)
        }
        val chosen = userChoices.items
        val truncated = chosen.size > CleaningThresholds.MAX_RESULT_ITEMS
        val storageListFiles = StoragePathRules.visibleInStorageLists(indexed, IndexedFile::name)
        val focusedItems = chosen.take(CleaningThresholds.MAX_RESULT_ITEMS)

        ScanSummary(
            items = focusedItems,
            scannedFileCount = indexed.size,
            scannedBytes = indexed.sumOf(IndexedFile::sizeBytes),
            limitedAccess = false,
            scanLimitReached = indexed.size >= indexedFileLimit(),
            storageTypes = storageTypeStats(storageListFiles),
            storagePreviews = storageTypePreviews(storageListFiles),
            smartMediaTypes = smartMediaTypeStats(storageListFiles),
            truncated = truncated,
            hiddenKeptCount = userChoices.hiddenKept,
            fromCachedIndex = cachedIndex != null,
            biggestFolders = if (focus == ScanFocus.SMART || focus == ScanFocus.DEEP) {
                FolderSizes.top(indexed.filterNot { StoragePathRules.isSystemMarkerFile(it.name) })
            } else {
                emptyList()
            },
        )
    }

    /**
     * Every file in [folder] and below it (V7), largest first. Nothing is
     * selected; hidden files are left out. The review uses the folder's most
     * common file type only for its layout.
     */
    suspend fun scanFolderReview(
        folder: FolderSize,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): StorageReviewSummary = withContext(Dispatchers.IO) {
        if (!hasAllFilesAccess()) return@withContext StorageReviewSummary(folder = folder)
        val indexed = cachedSharedIndex()?.also { cached -> onProgress(0, cached.size) }
            ?: indexSharedStorage { visitedDirectories, discoveredFiles ->
                onProgress(visitedDirectories, discoveredFiles)
            }.also(::cacheSharedIndex)
        val inFolder = indexed.filter { file ->
            FolderSizes.contains(folder, file) && !StoragePathRules.isSystemMarkerFile(file.name)
        }
        val dominantType = inFolder.groupBy(::storageFileType)
            .maxByOrNull { (_, files) -> files.sumOf(IndexedFile::sizeBytes) }
            ?.key ?: StorageFileType.OTHER
        val items = inFolder.asSequence()
            .sortedByDescending(IndexedFile::sizeBytes)
            .take(storageReviewItemLimit())
            .map { StorageReviewItem(file = it, selected = false) }
            .toList()
        StorageReviewSummary(
            type = dominantType,
            folder = folder,
            items = items,
            scannedFileCount = inFolder.size,
            scanLimitReached = inFolder.size > storageReviewItemLimit(),
            loading = false,
        )
    }

    suspend fun scanStorageReview(
        type: StorageFileType,
        excludeWhatsAppMedia: Boolean = false,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): StorageReviewSummary = withContext(Dispatchers.IO) {
        if (!hasAllFilesAccess()) {
            return@withContext StorageReviewSummary(
                type = type,
                excludeWhatsAppMedia = excludeWhatsAppMedia,
            )
        }

        val indexed = cachedSharedIndex()?.also { cached ->
            onProgress(0, cached.size)
        } ?: indexSharedStorage { visitedDirectories, discoveredFiles ->
            onProgress(visitedDirectories, discoveredFiles)
        }.also(::cacheSharedIndex)

        val typedFiles = StoragePathRules.visibleInStorageLists(indexed, IndexedFile::name)
            .asSequence()
            .filter { storageFileType(it) == type }
            .filter { !excludeWhatsAppMedia || !isWhatsAppSharedMedia(it) }
            .toList()
        val typed = typedFiles
            .asSequence()
            .sortedByDescending(IndexedFile::sizeBytes)
            .take(storageReviewItemLimit())
            .map { StorageReviewItem(file = it, selected = false) }
            .toList()

        StorageReviewSummary(
            type = type,
            excludeWhatsAppMedia = excludeWhatsAppMedia,
            items = typed,
            scannedFileCount = typedFiles.size,
            scanLimitReached =
                indexed.size >= indexedFileLimit() ||
                    typedFiles.size > storageReviewItemLimit(),
            loading = false,
        )
    }

    suspend fun scanWhatsAppLibrary(
        onProgress: (Int) -> Unit = {},
    ): WhatsAppLibrarySummary = withContext(Dispatchers.IO) {
        if (!hasAllFilesAccess()) return@withContext WhatsAppLibrarySummary()
        onProgress(2)
        val indexed = indexWhatsAppFiles { _, discovered ->
            onProgress((5 + discovered / 75).coerceIn(5, 88))
        }
        val count = indexed.size.coerceAtLeast(1)
        val items = indexed.mapIndexed { index, file ->
            coroutineContext.ensureActive()
            if (index % 40 == 0) {
                onProgress((88 + ((index + 1) * 10 / count)).coerceIn(88, 98))
            }
            val normalized = StoragePathRules.normalizePath(file.relativePath)
            WhatsAppMediaItem(
                id = file.uri,
                uri = file.uri,
                name = file.name,
                sizeBytes = file.sizeBytes,
                mimeType = file.mimeType,
                modifiedAtMillis = file.modifiedAtMillis,
                relativePath = file.relativePath,
                category = if (normalized.contains("/.statuses/")) {
                    WhatsAppMediaCategory.STATUSES
                } else {
                    WhatsAppMediaClassifier.classify(file.name, file.mimeType, file.relativePath)
                },
                selected = false,
            )
        }
        onProgress(100)
        WhatsAppLibrarySummary(items = items, scannedFileCount = indexed.size)
    }

    suspend fun deleteWhatsAppItems(
        items: List<WhatsAppMediaItem>,
        forcePermanent: Boolean = false,
    ): WhatsAppDeleteResult =
        withContext(Dispatchers.IO) {
            val outcome = RemovalTally()
            // Volume roots are resolved once per batch, not once per file (T3.5).
            val roots = sharedStorageRoots()
            val mode = if (forcePermanent) DeleteMode.PERMANENT else DeleteMode.TRASH
            items.forEach { item ->
                coroutineContext.ensureActive()
                outcome.record(
                    item.id,
                    item.sizeBytes,
                    remover.removeFile(item.uri, item.sizeBytes, item.modifiedAtMillis, mode, CleanupGroup.WHATSAPP_SENT, roots),
                )
            }
            WhatsAppDeleteResult(
                attemptedCount = items.size,
                deletedIds = outcome.removedIds,
                deletedBytes = outcome.deletedBytes,
                trashedBytes = outcome.trashedBytes,
                trashEntryIds = outcome.trashEntryIds,
                failed = outcome.failed,
            )
        }

    suspend fun deleteStorageReviewItems(
        items: List<StorageReviewItem>,
        forcePermanent: Boolean = false,
    ): DirectDeleteResult =
        withContext(Dispatchers.IO) {
            val outcome = RemovalTally()
            // Volume roots are resolved once per batch, not once per file (T3.5).
            val roots = sharedStorageRoots()
            val mode = if (forcePermanent) DeleteMode.PERMANENT else DeleteMode.TRASH
            items.forEach { item ->
                coroutineContext.ensureActive()
                outcome.record(
                    item.id,
                    item.file.sizeBytes,
                    remover.removeFile(
                        item.file.uri,
                        item.file.sizeBytes,
                        item.file.modifiedAtMillis,
                        mode,
                        CleanupGroup.LARGE_OTHER,
                        roots,
                    ),
                )
            }
            outcome.toDirectResult(items.mapTo(hashSetOf(), StorageReviewItem::id))
        }

    /**
     * Removes the selected items. [DeleteMode.TRASH] groups are moved to the
     * in-app trash, [DeleteMode.PERMANENT] groups are deleted. A copy is only
     * removed while its protected original still exists unchanged.
     */
    suspend fun deleteDirectItems(
        items: List<CleanableItem>,
        forcePermanent: Boolean = false,
    ): DirectDeleteResult = withContext(Dispatchers.IO) {
        val directItems = items.filter { Uri.parse(it.uri).scheme == "file" }
        val outcome = RemovalTally()
        // Volume roots are resolved once per batch, not once per file (T3.5).
        val roots = sharedStorageRoots()
        directItems.forEach { item ->
            coroutineContext.ensureActive()
            val group = item.assessment.group
            val remove = {
                val mode = if (forcePermanent) DeleteMode.PERMANENT else group.deleteMode
                remover.removeFile(item.uri, item.sizeBytes, item.modifiedAtMillis, mode, group, roots)
            }
            val originalUri = item.protectedDuplicateUri
            val removal = if (originalUri == null) {
                remove()
            } else {
                val original = runCatching {
                    val uri = Uri.parse(originalUri)
                    if (uri.scheme != "file") null else uri.path?.let(::File)
                }.getOrNull()
                var inner: Removal = Removal.Failed(FailureReason.DENIED)
                val checked = FileDeleter.deleteIfOriginalUnchanged(original, item.protectedDuplicateSizeBytes) {
                    inner = remove()
                    inner !is Removal.Failed
                }
                if (checked == FileDeleter.Result.ORIGINAL_CHANGED) {
                    Removal.Failed(FailureReason.ORIGINAL_CHANGED)
                } else {
                    inner
                }
            }
            outcome.record(item.id, item.sizeBytes, removal)
        }
        outcome.toDirectResult(directItems.mapTo(hashSetOf(), CleanableItem::id))
    }

    // V10: files kept in the swipe review.
    fun keepItem(item: CleanableItem) = keptItems.add(KeptItemsStore.keyOf(item))

    fun unkeepItem(item: CleanableItem) = keptItems.remove(KeptItemsStore.keyOf(item))

    fun keptItemCount(): Int = keptItems.count()

    fun clearKeptItems() = keptItems.clear()

    // V11: folders the user does not want suggestions from.
    fun excludedFolderList(): List<ExcludedFolder> = excludedFolders.folders()

    fun excludeFolder(folder: ExcludedFolder): Boolean = excludedFolders.add(folder)

    fun includeFolder(folder: ExcludedFolder) = excludedFolders.remove(folder)

    /**
     * Faz 3A-2: the user approved a shrunk copy; the original goes to the
     * app's Trash (7 days, restorable), never deleted here. Null when it could
     * not be moved (the original then stays where it was).
     */
    suspend fun moveShrunkOriginalToTrash(path: String): TrashEntry? = withContext(Dispatchers.IO) {
        val file = File(path)
        val root = sharedStorageRoots().firstOrNull { file.absolutePath.startsWith(it.absolutePath + File.separator) }
            ?: return@withContext null
        trashStore.moveToTrash(file, root, CleanupGroup.LARGE_VIDEO)?.also { invalidateStorageIndex() }
    }

    /**
     * Faz 3B: frames the user chose (similar photos, one by one) go to the
     * app's Trash, 7 days, restorable. Only after the user's tap.
     */
    suspend fun moveToTrash(paths: List<String>, group: CleanupGroup): List<TrashEntry> = withContext(Dispatchers.IO) {
        val roots = sharedStorageRoots()
        val moved = paths.mapNotNull { path ->
            val file = File(path)
            val root = roots.firstOrNull { file.absolutePath.startsWith(it.absolutePath + File.separator) } ?: return@mapNotNull null
            trashStore.moveToTrash(file, root, group)
        }
        if (moved.isNotEmpty()) invalidateStorageIndex()
        moved
    }

    fun trashEntries(): List<TrashEntry> = trashStore.entries()

    fun trashBytes(): Long = trashStore.trashBytes()

    suspend fun restoreFromTrash(ids: Collection<String>): RestoreResult = withContext(Dispatchers.IO) {
        trashStore.restore(ids).also { if (it.restoredIds.isNotEmpty()) invalidateStorageIndex() }
    }

    suspend fun emptyTrash(ids: Collection<String>? = null): Long = withContext(Dispatchers.IO) {
        trashStore.emptyNow(ids)
    }

    suspend fun purgeExpiredTrash(): Long = withContext(Dispatchers.IO) {
        if (!hasAllFilesAccess()) 0L else trashStore.purgeExpired()
    }

    private fun indexedFile(storageRoot: File, file: File): IndexedFile {
        val relative = runCatching { file.relativeTo(storageRoot).path }.getOrDefault(file.absolutePath)
        val parentPath = relative.substringBeforeLast('/', missingDelimiterValue = "")
        return IndexedFile(
            uri = Uri.fromFile(file).toString(),
            name = file.name.ifBlank { context.getString(R.string.unnamed_file) },
            sizeBytes = file.length().coerceAtLeast(0L),
            mimeType = mimeType(file),
            modifiedAtMillis = file.lastModified().coerceAtLeast(0L),
            relativePath = parentPath,
            volumeRoot = canonicalPathOf(storageRoot),
        )
    }

    fun invalidateStorageIndex() {
        sharedIndexCache = null
        sharedIndexCachedAtElapsed = 0L
    }

    private fun cacheSharedIndex(indexed: List<IndexedFile>) {
        sharedIndexCache = SoftReference(indexed)
        sharedIndexCachedAtElapsed = SystemClock.elapsedRealtime()
    }

    private fun cachedSharedIndex(): List<IndexedFile>? {
        val cached = sharedIndexCache?.get() ?: return null
        if (
            sharedIndexCachedAtElapsed <= 0L ||
            SystemClock.elapsedRealtime() - sharedIndexCachedAtElapsed > SHARED_INDEX_CACHE_TTL_MILLIS
        ) {
            invalidateStorageIndex()
            return null
        }
        return cached
    }

    private fun sharedStorageRoots(): List<File> {
        val storageManager = context.getSystemService(StorageManager::class.java)
        return buildList {
            add(Environment.getExternalStorageDirectory())
            storageManager.storageVolumes
                .mapNotNullTo(this) { volume -> volume.directory }
        }
            .filter(File::isDirectory)
            .distinctBy(::canonicalPathOf)
    }

    private fun whatsappRootPairs(): List<Pair<File, File>> = buildList {
        sharedStorageRoots().forEach { root ->
            add(root to File(root, "Android/media/com.whatsapp/WhatsApp/Media"))
            add(root to File(root, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media"))
            add(root to File(root, "Android/media/com.whatsapp.w4b/WhatsApp/Media"))
            add(root to File(root, "WhatsApp/Media"))
            add(root to File(root, "WhatsApp Business/Media"))
        }
    }
        .filter { (_, mediaRoot) -> mediaRoot.isDirectory }
        .distinctBy { (_, mediaRoot) -> canonicalPathOf(mediaRoot) }

    private fun whatsappRoots(): List<File> =
        whatsappRootPairs().map { (_, mediaRoot) -> mediaRoot }

    private suspend fun findDuplicates(
        files: List<IndexedFile>,
        maxFullHashBytes: Long,
        onHashProgress: HashProgress = { _, _ -> },
    ): DuplicatePolicy.ScanResult = duplicateFinder.find(files, maxFullHashBytes, onHashProgress)

    /**
     * Deep duplicate check: fully verifies the largest look-alike groups. Never
     * runs during a scan; only when the user asks for it.
     */
    suspend fun verifyLargeDuplicateGroups(
        items: List<CleanableItem>,
        onHashProgress: HashProgress = { _, _ -> },
    ): Map<String, CleanableItem?> = withContext(Dispatchers.IO) {
        duplicateFinder.verifyLargeGroups(items, onHashProgress)
    }

    fun largeDuplicateGroupSummary(items: List<CleanableItem>): Pair<Int, Long> =
        duplicateFinder.largeGroupSummary(items)

    /** Full check of one look-alike copy. `null` means it is not an exact copy. */
    suspend fun verifyDuplicate(
        item: CleanableItem,
        onHashProgress: HashProgress = { _, _ -> },
    ): CleanableItem? = withContext(Dispatchers.IO) {
        duplicateFinder.verify(item, onHashProgress)
    }

    private fun openUriInput(uriString: String): FileInputStream? = runCatching {
        val uri = Uri.parse(uriString)
        if (uri.scheme == "file") {
            FileInputStream(File(requireNotNull(uri.path)))
        } else {
            resolver.openFileDescriptor(uri, "r")?.let { descriptor ->
                ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            }
        }
    }.getOrNull()

    private fun directorySize(root: File): Long {
        if (!root.exists()) return 0L
        var total = 0L
        val pending = ArrayDeque<File>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val file = pending.removeFirst()
            if (file.isDirectory) {
                file.listFiles()?.forEach(pending::addLast)
            } else {
                total += file.length().coerceAtLeast(0L)
            }
        }
        return total
    }



    enum class FailureReason { CHANGED, MISSING, ORIGINAL_CHANGED, PROTECTED, DENIED, TRASH_UNAVAILABLE, TRASH_FOLDER_UNAVAILABLE }

    /**
     * [deletedIds] are every item that left its original place; [deletedBytes]
     * counts only permanently deleted files, [trashedBytes] the files moved to
     * the in-app trash.
     */
    data class DirectDeleteResult(
        val attemptedIds: Set<String>,
        val deletedIds: Set<String>,
        val deletedBytes: Long,
        val trashedBytes: Long = 0L,
        val trashEntryIds: List<String> = emptyList(),
        val failed: Map<String, FailureReason> = emptyMap(),
    ) {
        val failedCount: Int get() = attemptedIds.size - deletedIds.size
        val trashUnavailableIds: Set<String> get() = failed.trashUnavailableIds()
        val trashFolderMissing: Boolean get() = failed.trashFolderMissing()
        val changedCount: Int get() = failed.values.count {
            it == FailureReason.CHANGED || it == FailureReason.MISSING || it == FailureReason.ORIGINAL_CHANGED
        }
    }

    data class WhatsAppDeleteResult(
        val attemptedCount: Int,
        val deletedIds: Set<String>,
        val deletedBytes: Long,
        val trashedBytes: Long = 0L,
        val trashEntryIds: List<String> = emptyList(),
        val failed: Map<String, FailureReason> = emptyMap(),
    ) {
        val failedCount: Int get() = attemptedCount - deletedIds.size
        val trashUnavailableIds: Set<String> get() = failed.trashUnavailableIds()
        val trashFolderMissing: Boolean get() = failed.trashFolderMissing()
        val changedCount: Int get() = failed.values.count {
            it == FailureReason.CHANGED || it == FailureReason.MISSING || it == FailureReason.ORIGINAL_CHANGED
        }
    }

    companion object {
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val TRASH_INDEX_FILE = "trash_index.json"
        private const val MAX_INDEXED_FILES = 200_000
        private const val LOW_MEMORY_INDEXED_FILES = 50_000
        private const val MEDIUM_MEMORY_INDEXED_FILES = 100_000
        private const val SHARED_INDEX_CACHE_TTL_MILLIS = 10L * 60L * 1000L
        private const val MAX_WHATSAPP_FILES = 100_000
        private const val MAX_STORAGE_REVIEW_ITEMS = 50_000
        // A review list is rebuilt when a checkbox is toggled. Keep the
        // interactive surface bounded even on high-memory devices so a single
        // tap cannot allocate and recompose tens of thousands of rows.
        private const val MAX_SELECTABLE_STORAGE_REVIEW_ITEMS = 10_000
    }

    private fun indexedFileLimit(): Int {
        val memoryClassMb = context.getSystemService(ActivityManager::class.java)?.memoryClass ?: 256
        return when {
            memoryClassMb <= 128 -> LOW_MEMORY_INDEXED_FILES
            memoryClassMb <= 192 -> MEDIUM_MEMORY_INDEXED_FILES
            else -> MAX_INDEXED_FILES
        }
    }

    private fun storageReviewItemLimit(): Int =
        minOf(
            MAX_STORAGE_REVIEW_ITEMS,
            MAX_SELECTABLE_STORAGE_REVIEW_ITEMS,
            (indexedFileLimit() / 4).coerceAtLeast(10_000),
        )
}

private fun Map<String, DeviceRepository.FailureReason>.trashUnavailableIds(): Set<String> =
    filterValues {
        it == DeviceRepository.FailureReason.TRASH_UNAVAILABLE ||
            it == DeviceRepository.FailureReason.TRASH_FOLDER_UNAVAILABLE
    }.keys

private fun Map<String, DeviceRepository.FailureReason>.trashFolderMissing(): Boolean =
    values.any { it == DeviceRepository.FailureReason.TRASH_FOLDER_UNAVAILABLE }
