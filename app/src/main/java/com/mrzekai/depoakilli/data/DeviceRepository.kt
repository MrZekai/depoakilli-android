package com.mrzekai.depoakilli.data

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.AiAssessment
import com.mrzekai.depoakilli.model.AppCacheEntry
import com.mrzekai.depoakilli.model.AppCacheSnapshot
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.CleanupGroup
import com.mrzekai.depoakilli.model.DeleteMode
import com.mrzekai.depoakilli.model.DeviceInfoSnapshot
import com.mrzekai.depoakilli.model.IndexedFile
import com.mrzekai.depoakilli.model.InstalledAppEntry
import com.mrzekai.depoakilli.model.MemorySnapshot
import com.mrzekai.depoakilli.model.ScanFocus
import com.mrzekai.depoakilli.model.ScanSummary
import com.mrzekai.depoakilli.model.StorageFileType
import com.mrzekai.depoakilli.model.StorageReviewItem
import com.mrzekai.depoakilli.model.StorageReviewSummary
import com.mrzekai.depoakilli.model.StorageSnapshot
import com.mrzekai.depoakilli.model.StorageTypeStat
import com.mrzekai.depoakilli.model.WhatsAppLibrarySummary
import com.mrzekai.depoakilli.model.WhatsAppMediaCategory
import com.mrzekai.depoakilli.model.WhatsAppMediaItem
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.lang.ref.SoftReference
import java.util.concurrent.TimeUnit
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
    private val trashStore = TrashStore(
        indexFile = File(context.filesDir, TRASH_INDEX_FILE),
        trashRoots = { sharedStorageRoots() },
        isAllowed = ::isDeletableSharedStorageFile,
        removeFromMediaStore = ::removeDeletedPathFromMediaStore,
        rescan = { path -> MediaScannerConnection.scanFile(context, arrayOf(path), null, null) },
    )
    private val resolver = context.contentResolver
    private var sharedIndexCache: SoftReference<List<IndexedFile>>? = null
    private var sharedIndexCachedAtElapsed = 0L

    fun storageSnapshot(): StorageSnapshot {
        val snapshots = sharedStorageRoots().mapNotNull { root ->
            runCatching {
                val stats = StatFs(root.absolutePath)
                StorageSnapshot(
                    totalBytes = stats.totalBytes,
                    availableBytes = stats.availableBytes,
                )
            }.getOrNull()
        }

        if (snapshots.isNotEmpty()) {
            return StorageSnapshot(
                totalBytes = snapshots.sumOf(StorageSnapshot::totalBytes),
                availableBytes = snapshots.sumOf(StorageSnapshot::availableBytes),
            )
        }

        val fallback = StatFs(Environment.getDataDirectory().absolutePath)
        return StorageSnapshot(
            totalBytes = fallback.totalBytes,
            availableBytes = fallback.availableBytes,
        )
    }

    fun memorySnapshot(): MemorySnapshot {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        val processMemoryInfo = Debug.MemoryInfo()
        manager.getMemoryInfo(info)
        Debug.getMemoryInfo(processMemoryInfo)
        return MemorySnapshot(
            totalBytes = info.totalMem,
            availableBytes = info.availMem,
            appUsedBytes = processMemoryInfo.totalPss.toLong() * 1024L,
            lowMemory = info.lowMemory,
            lowMemoryThresholdBytes = info.threshold,
        )
    }

    @Suppress("DEPRECATION")
    fun deviceInfoSnapshot(): DeviceInfoSnapshot {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryLevel = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val batteryScale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPercent = if (batteryLevel >= 0 && batteryScale > 0) {
            (batteryLevel * 100 / batteryScale).coerceIn(0, 100)
        } else {
            0
        }
        val batteryStatus = battery?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN,
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val metrics = context.resources.displayMetrics
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        return DeviceInfoSnapshot(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            androidVersion = Build.VERSION.RELEASE.orEmpty(),
            sdkLevel = Build.VERSION.SDK_INT,
            cpuAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            screenResolution = "${metrics.widthPixels} × ${metrics.heightPixels}",
            batteryPercent = batteryPercent,
            batteryTemperatureCelsius = (battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
            batteryCharging = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                batteryStatus == BatteryManager.BATTERY_STATUS_FULL,
            appVersion = packageInfo.versionName.orEmpty(),
        )
    }

    fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

    fun hasWhatsAppAccess(): Boolean = hasAllFilesAccess() && whatsappRoots().any(File::isDirectory)

    fun ownCacheSize(): Long = directorySize(context.cacheDir) +
        (context.externalCacheDir?.let(::directorySize) ?: 0L)

    @Suppress("DEPRECATION")
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    suspend fun appCacheSnapshot(): AppCacheSnapshot = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) {
            return@withContext AppCacheSnapshot(supported = true, accessGranted = false)
        }
        val packageManager = context.packageManager
        val applications = launcherApplications(packageManager)
        val storageStats = context.getSystemService(StorageStatsManager::class.java)
        val user = Process.myUserHandle()
        val totalReportedCache = runCatching {
            storageStats.queryStatsForUser(StorageManager.UUID_DEFAULT, user).cacheBytes
        }.getOrDefault(0L)
        val entries = applications.mapNotNull { application ->
            coroutineContext.ensureActive()
            runCatching {
                val volume = application.storageUuid ?: StorageManager.UUID_DEFAULT
                val cacheBytes = storageStats.queryStatsForPackage(
                    volume,
                    application.packageName,
                    user,
                ).cacheBytes.coerceAtLeast(0L)
                if (cacheBytes == 0L) return@runCatching null
                AppCacheEntry(
                    packageName = application.packageName,
                    label = application.loadLabel(packageManager).toString().ifBlank { application.packageName },
                    cacheBytes = cacheBytes,
                )
            }.getOrNull()
        }
        AppCacheSnapshot(
            supported = true,
            accessGranted = true,
            entries = entries.sortedByDescending(AppCacheEntry::cacheBytes),
            scannedAppCount = applications.size,
            reportedOtherAppsCacheBytes = (totalReportedCache - ownCacheSize()).coerceAtLeast(0L),
        )
    }

    suspend fun installedAppsSnapshot(): List<InstalledAppEntry> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val applications = launcherApplications(packageManager)
        val usageAccessGranted = hasUsageAccess()
        val usageMap = if (usageAccessGranted) {
            val now = System.currentTimeMillis()
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            manager.queryUsageStats(
                UsageStatsManager.INTERVAL_YEARLY,
                now - TimeUnit.DAYS.toMillis(365),
                now,
            ).associateBy { it.packageName }
        } else {
            emptyMap()
        }
        val storageStats = context.getSystemService(StorageStatsManager::class.java)
        val user = Process.myUserHandle()
        applications.map { application ->
            coroutineContext.ensureActive()
            val stats = if (usageAccessGranted) {
                runCatching {
                    storageStats.queryStatsForPackage(
                        application.storageUuid ?: StorageManager.UUID_DEFAULT,
                        application.packageName,
                        user,
                    )
                }.getOrNull()
            } else {
                null
            }
            InstalledAppEntry(
                packageName = application.packageName,
                label = application.loadLabel(packageManager).toString().ifBlank { application.packageName },
                appBytes = stats?.appBytes ?: 0L,
                dataBytes = stats?.dataBytes ?: 0L,
                cacheBytes = stats?.cacheBytes ?: 0L,
                lastUsedMillis = usageMap[application.packageName]?.lastTimeUsed ?: 0L,
                systemApp = (
                    application.flags and
                        (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)
                    ) != 0,
            )
        }.sortedByDescending(InstalledAppEntry::totalBytes)
    }

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
    ): ScanSummary = withContext(Dispatchers.IO) {
        if (!hasAllFilesAccess()) {
            return@withContext ScanSummary(limitedAccess = true)
        }

        // Smart and Deep always re-index; tool scans reuse a fresh (90 s) index.
        val reuseIndex = focus != ScanFocus.SMART && focus != ScanFocus.DEEP
        val indexed = (if (reuseIndex) cachedSharedIndex()?.also { onProgress(0, it.size) } else null)
            ?: indexSharedStorage { visitedDirectories, discoveredFiles ->
                onProgress(visitedDirectories, discoveredFiles)
            }.also(::cacheSharedIndex)
        val assessments = ArrayList<CleanableItem>()
        var duplicateResult = DuplicatePolicy.ScanResult()

        indexed.forEach { file ->
            coroutineContext.ensureActive()
            assessmentForFocus(file, focus)?.let { assessment ->
                assessments += file.toCleanable(assessment)
            }
        }
        val assessed = aiEngine.markOlderApkVersions(assessments)

        // Smart Clean promises duplicates too, but must stay fast: it verifies
        // files up to QUICK_FULL_HASH_BYTES byte for byte and lists larger
        // sample matches as review-only. Deep/Duplicate/Media verify up to 1 GB.
        if (
            focus == ScanFocus.SMART ||
            focus == ScanFocus.DEEP ||
            focus == ScanFocus.DUPLICATES ||
            focus == ScanFocus.MEDIA
        ) {
            val duplicateSource = if (focus == ScanFocus.MEDIA) {
                indexed.filter {
                    val mime = StoragePathRules.normalizeText(it.mimeType)
                    mime.startsWith("image/") || mime.startsWith("video/")
                }
            } else {
                indexed
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
            .filter { item ->
                when (focus) {
                    ScanFocus.SMART,
                    ScanFocus.DEEP -> true
                    ScanFocus.JUNK -> item.assessment.category == CleanCategory.JUNK
                    ScanFocus.DUPLICATES -> item.assessment.category == CleanCategory.DUPLICATE
                    ScanFocus.LARGE_FILES -> item.assessment.category == CleanCategory.LARGE_FILE
                    ScanFocus.MEDIA -> item.assessment.category in MEDIA_CATEGORIES
                    ScanFocus.SCREENSHOTS -> item.assessment.category == CleanCategory.SCREENSHOT
                    ScanFocus.DOWNLOADS -> item.assessment.group in DOWNLOAD_TOOL_GROUPS
                    ScanFocus.APKS -> item.assessment.category == CleanCategory.APK_PACKAGE
                    ScanFocus.ANALYZE -> false
                }
            }
            .distinctBy(CleanableItem::uri)
            .sortedByDescending(CleanableItem::sizeBytes)
            .toList()
        val truncated = ranked.size > CleaningThresholds.MAX_RESULT_ITEMS
        val focusedItems = ranked.take(CleaningThresholds.MAX_RESULT_ITEMS)

        ScanSummary(
            items = focusedItems,
            scannedFileCount = indexed.size,
            scannedBytes = indexed.sumOf(IndexedFile::sizeBytes),
            limitedAccess = false,
            scanLimitReached = indexed.size >= indexedFileLimit(),
            storageTypes = storageTypeStats(indexed),
            storagePreviews = storageTypePreviews(indexed),
            smartMediaTypes = smartMediaTypeStats(indexed),
            truncated = truncated,
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

        val typedFiles = indexed
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
            val mode = if (forcePermanent) DeleteMode.PERMANENT else DeleteMode.TRASH
            items.forEach { item ->
                coroutineContext.ensureActive()
                outcome.record(
                    item.id,
                    item.sizeBytes,
                    removeFile(item.uri, item.sizeBytes, item.modifiedAtMillis, mode, CleanupGroup.WHATSAPP_SENT),
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
            val mode = if (forcePermanent) DeleteMode.PERMANENT else DeleteMode.TRASH
            items.forEach { item ->
                coroutineContext.ensureActive()
                outcome.record(
                    item.id,
                    item.file.sizeBytes,
                    removeFile(
                        item.file.uri,
                        item.file.sizeBytes,
                        item.file.modifiedAtMillis,
                        mode,
                        CleanupGroup.LARGE_OTHER,
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
        directItems.forEach { item ->
            coroutineContext.ensureActive()
            val group = item.assessment.group
            val remove = {
                val mode = if (forcePermanent) DeleteMode.PERMANENT else group.deleteMode
                removeFile(item.uri, item.sizeBytes, item.modifiedAtMillis, mode, group)
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

    private fun removeFile(
        uriString: String,
        expectedSizeBytes: Long,
        expectedModifiedAtMillis: Long,
        mode: DeleteMode,
        group: CleanupGroup,
    ): Removal = runCatching {
        val uri = Uri.parse(uriString)
        if (uri.scheme != "file") return@runCatching Removal.Failed(FailureReason.DENIED)
        val file = File(uri.path ?: return@runCatching Removal.Failed(FailureReason.DENIED))
        if (!file.exists()) return@runCatching Removal.Failed(FailureReason.MISSING)
        if (!fileSnapshotStillMatches(uriString, expectedSizeBytes, expectedModifiedAtMillis)) {
            return@runCatching Removal.Failed(FailureReason.CHANGED)
        }
        // Path rules expect a path relative to the storage volume root
        // (e.g. "WhatsApp/Databases"), not the absolute path.
        val relativeDir = volumeRootOf(file)?.let { root ->
            runCatching { file.parentFile?.relativeTo(root)?.invariantSeparatorsPath }.getOrNull()
        } ?: return@runCatching Removal.Failed(FailureReason.PROTECTED)
        if (
            !isDeletableSharedStorageFile(file) ||
            StoragePathRules.isSystemMarkerFile(file.name) ||
            StoragePathRules.isProtectedUserDataPath(relativeDir, file.name)
        ) {
            return@runCatching Removal.Failed(FailureReason.PROTECTED)
        }
        val removal = when (mode) {
            DeleteMode.TRASH -> {
                val root = volumeRootOf(file)
                val entry = root?.let { trashStore.moveToTrash(file, it, group) }
                when {
                    entry != null -> Removal.Trashed(entry.id)
                    // The Trash folder itself cannot be created on this volume
                    // (some manufacturers block it): reported separately.
                    root == null || !trashStore.canUseTrash(root) -> Removal.Failed(FailureReason.TRASH_FOLDER_UNAVAILABLE)
                    else -> Removal.Failed(FailureReason.TRASH_UNAVAILABLE)
                }
            }
            DeleteMode.PERMANENT -> {
                if (FileDeleter.deleteUnprotected(file, relativeDir)) {
                    removeDeletedPathFromMediaStore(file.absolutePath)
                    Removal.Deleted
                } else {
                    Removal.Failed(FailureReason.DENIED)
                }
            }
        }
        if (removal !is Removal.Failed) invalidateStorageIndex()
        removal
    }.getOrDefault(Removal.Failed(FailureReason.DENIED))

    private fun volumeRootOf(file: File): File? = sharedStorageRoots().firstOrNull { root ->
        canonicalPathWithinRoot(canonicalPathOf(root).trimEnd(File.separatorChar), file) != null
    }

    private fun assessmentForFocus(file: IndexedFile, focus: ScanFocus): AiAssessment? {
        return when (focus) {
            ScanFocus.SMART -> aiEngine.assess(file)
            ScanFocus.DEEP -> aiEngine.assessDeep(file)

            ScanFocus.JUNK -> {
                if (StoragePathRules.isDownloadPath(file.relativePath)) {
                    null
                } else {
                    aiEngine.assess(file)?.takeIf {
                        it.category == CleanCategory.JUNK
                    }
                }
            }

            ScanFocus.DUPLICATES,
            ScanFocus.ANALYZE -> null

            ScanFocus.APKS -> aiEngine.assess(file)?.takeIf {
                it.category == CleanCategory.APK_PACKAGE
            }

            ScanFocus.LARGE_FILES -> if (file.sizeBytes >= CleaningThresholds.LARGE_BYTES) {
                AiAssessment(
                    category = CleanCategory.LARGE_FILE,
                    safetyScore = 60,
                    reasonRes = 0,
                    reasonPluralRes = R.plurals.reason_large_file,
                    reasonQuantity = ageDays(file.modifiedAtMillis).coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
                    reasonArgs = listOf(ageDays(file.modifiedAtMillis)),
                    recommended = false,
                )
            } else {
                null
            }

            ScanFocus.MEDIA -> {
                if (!file.mimeType.startsWith("image/") && !file.mimeType.startsWith("video/")) {
                    null
                } else {
                    aiEngine.assessDeep(file)?.takeIf {
                        it.category == CleanCategory.SCREENSHOT || it.category == CleanCategory.LARGE_FILE
                    }
                }
            }

            ScanFocus.SCREENSHOTS -> aiEngine.assessFocusedScreenshot(file)

            ScanFocus.DOWNLOADS -> {
                if (!StoragePathRules.isDownloadPath(file.relativePath)) {
                    null
                } else {
                    aiEngine.assessDeep(file)?.takeIf { it.group in DOWNLOAD_TOOL_GROUPS }
                }
            }
        }
    }

    private suspend fun indexSharedStorage(
        onTraversalProgress: (visitedDirectories: Int, discoveredFiles: Int) -> Unit,
    ): List<IndexedFile> {
        val roots = sharedStorageRoots().mapNotNull(::scanRoot)
        if (roots.isEmpty()) return emptyList()
        // An unreadable primary volume must surface as a failed scan, never as
        // an empty "nothing to clean" result or a fake storage-change snapshot.
        if (runCatching { roots.first().file.listFiles() }.getOrNull() == null) {
            throw java.io.IOException("Shared storage root is not readable")
        }

        val output = ArrayList<IndexedFile>()
        val pending = ArrayDeque<Pair<ScanRoot, File>>()
        roots.forEach { root -> pending.addLast(root to root.file) }
        // Shared storage can contain symlinks and bind mounts (vendor "internal
        // storage" aliases, user-created links). Without a canonical-path guard
        // the traversal can loop indefinitely and, worse, index one physical file
        // several times under different paths - which the duplicate finder would
        // then offer to "clean", deleting a real and unique file.
        val visitedDirectoryPaths = hashSetOf<String>()
        val visitedFilePaths = hashSetOf<String>()
        var visitedDirectories = 0
        var lastProgressDirectories = 0
        var lastProgressFiles = 0
        var lastProgressAt = 0L

        val indexedFileLimit = indexedFileLimit()
        while (pending.isNotEmpty() && output.size < indexedFileLimit) {
            coroutineContext.ensureActive()
            val (root, directory) = pending.removeFirst()
            val directoryPath = canonicalPathWithinRoot(root.canonicalPath, directory) ?: continue
            if (!visitedDirectoryPaths.add(directoryPath)) continue
            visitedDirectories++
            val children = runCatching { directory.listFiles() }.getOrNull() ?: continue

            for (child in children) {
                coroutineContext.ensureActive()
                if (child.isDirectory) {
                    if (
                        canonicalPathWithinRoot(root.canonicalPath, child) != null &&
                        !shouldSkipDirectory(root.file, child)
                    ) {
                        pending.addLast(root to child)
                    }
                } else if (child.isFile) {
                    if (StoragePathRules.isSystemMarkerFile(child.name)) continue
                    val relative = runCatching { child.parentFile?.relativeTo(root.file)?.invariantSeparatorsPath }.getOrNull() ?: continue
                    if (StoragePathRules.isProtectedUserDataPath(relative, child.name)) continue
                    val filePath = canonicalPathWithinRoot(root.canonicalPath, child) ?: continue
                    if (!visitedFilePaths.add(filePath)) continue
                    val size = child.length().coerceAtLeast(0L)
                    if (size <= 0L) continue
                    output += indexedFile(root.file, child)
                    if (output.size >= indexedFileLimit) break
                }
            }

            val now = SystemClock.elapsedRealtime()
            val enoughProgress =
                visitedDirectories - lastProgressDirectories >= 12 ||
                    output.size - lastProgressFiles >= 300
            if (enoughProgress && now - lastProgressAt >= SCAN_PROGRESS_THROTTLE_MILLIS) {
                onTraversalProgress(visitedDirectories, output.size)
                lastProgressDirectories = visitedDirectories
                lastProgressFiles = output.size
                lastProgressAt = now
            }
        }

        if (visitedDirectories != lastProgressDirectories || output.size != lastProgressFiles) {
            onTraversalProgress(visitedDirectories, output.size)
        }
        return output
    }

    private suspend fun indexWhatsAppFiles(
        onDiscovered: (Int, Int) -> Unit,
    ): List<IndexedFile> {
        val rootPairs = whatsappRootPairs().mapNotNull { (storageRoot, mediaRoot) ->
            scanRoot(storageRoot)?.let { it to mediaRoot }
        }
        if (rootPairs.isEmpty()) return emptyList()

        val output = ArrayList<IndexedFile>()
        val pending = ArrayDeque<Pair<ScanRoot, File>>()
        rootPairs.forEach(pending::addLast)
        val visitedDirectories = hashSetOf<String>()
        val visitedFiles = hashSetOf<String>()

        while (pending.isNotEmpty() && output.size < MAX_WHATSAPP_FILES) {
            coroutineContext.ensureActive()
            val (storageRoot, directory) = pending.removeFirst()
            val directoryPath = canonicalPathWithinRoot(storageRoot.canonicalPath, directory) ?: continue
            if (!visitedDirectories.add(directoryPath)) continue
            val children = runCatching { directory.listFiles() }.getOrNull() ?: continue

            for (child in children) {
                coroutineContext.ensureActive()
                if (child.isDirectory) {
                    if (canonicalPathWithinRoot(storageRoot.canonicalPath, child) != null &&
                        !shouldSkipDirectory(storageRoot.file, child)) {
                        pending.addLast(storageRoot to child)
                    }
                } else if (child.isFile && child.length() > 0L) {
                    if (StoragePathRules.isSystemMarkerFile(child.name)) continue
                    val relative = runCatching { child.parentFile?.relativeTo(storageRoot.file)?.invariantSeparatorsPath }.getOrNull() ?: continue
                    if (StoragePathRules.isProtectedUserDataPath(relative, child.name)) continue
                    val filePath = canonicalPathWithinRoot(storageRoot.canonicalPath, child) ?: continue
                    if (!visitedFiles.add(filePath)) continue
                    output += indexedFile(storageRoot.file, child)
                    if (output.size % 50 == 0) onDiscovered(visitedDirectories.size, output.size)
                    if (output.size >= MAX_WHATSAPP_FILES) break
                }
            }
        }
        onDiscovered(visitedDirectories.size, output.size)
        return output.distinctBy(IndexedFile::uri)
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

    private fun canonicalPathOf(file: File): String =
        runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)

    private fun canonicalPathWithinRoot(rootPath: String, file: File): String? {
        val filePath = canonicalPathOf(file)
        if (rootPath.isEmpty()) return null
        return filePath.takeIf {
            it == rootPath || it.startsWith("$rootPath${File.separator}")
        }
    }

    private fun scanRoot(file: File): ScanRoot? =
        canonicalPathOf(file)
            .trimEnd(File.separatorChar)
            .takeIf(String::isNotEmpty)
            ?.let { ScanRoot(file = file, canonicalPath = it) }

    /**
     * Defence in depth for every destructive path.
     *
     * Scan results already originate from [sharedStorageRoots], but deletion is
     * irreversible, so the final gate re-resolves the target through its
     * canonical path and refuses anything that is not inside a shared-storage
     * volume, or that lives in the app-private Android/data and Android/obb
     * trees this engine deliberately never manages. This also neutralises
     * symlinks that point outside shared storage.
     */
    private fun isDeletableSharedStorageFile(file: File): Boolean {
        val canonical = canonicalPathOf(file)
        val root = sharedStorageRoots().firstOrNull { root ->
            canonicalPathWithinRoot(canonicalPathOf(root).trimEnd(File.separatorChar), file) != null
        } ?: return false
        val rootPath = canonicalPathOf(root).trimEnd('/')
        val relative = StoragePathRules.normalizePath(canonical.removePrefix(rootPath))
        return !StoragePathRules.isProtectedAppPrivatePath(relative) &&
            !StoragePathRules.isProtectedUserDataPath(relative.substringBeforeLast('/', ""), file.name)
    }

    @Suppress("DEPRECATION")
    private fun removeDeletedPathFromMediaStore(absolutePath: String) {
        runCatching {
            resolver.delete(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                "${MediaStore.MediaColumns.DATA}=?",
                arrayOf(absolutePath),
            )
        }
    }

    private fun shouldSkipDirectory(root: File, directory: File): Boolean {
        val relative = StoragePathRules.normalizePath(
            runCatching { directory.relativeTo(root).invariantSeparatorsPath }
                .getOrDefault(directory.invariantSeparatorsPath),
        )
        return StoragePathRules.isProtectedAppPrivatePath(relative) ||
            (directory.name.startsWith(".") && !StoragePathRules.isHiddenDirectoryAllowed(directory.name))
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

    private fun smartMediaTypeStats(files: List<IndexedFile>): List<StorageTypeStat> {
        var imageCount = 0
        var imageBytes = 0L
        var videoCount = 0
        var videoBytes = 0L

        files.forEach { file ->
            val mime = StoragePathRules.normalizeText(file.mimeType)
            when {
                mime.startsWith("image/") -> {
                    if (!isWhatsAppSharedMedia(file)) {
                        imageCount++
                        imageBytes += file.sizeBytes
                    }
                }
                mime.startsWith("video/") -> {
                    if (!isWhatsAppSharedMedia(file)) {
                        videoCount++
                        videoBytes += file.sizeBytes
                    }
                }
            }
        }

        return buildList {
            if (videoCount > 0) add(StorageTypeStat(StorageFileType.VIDEOS, videoCount, videoBytes))
            if (imageCount > 0) add(StorageTypeStat(StorageFileType.IMAGES, imageCount, imageBytes))
        }
    }

    private fun isWhatsAppSharedMedia(file: IndexedFile): Boolean =
        StoragePathRules.isWhatsAppPath(file.relativePath)

    private fun storageTypeStats(files: List<IndexedFile>): List<StorageTypeStat> {
        return files.groupBy(::storageFileType)
            .map { (type, typedFiles) ->
                StorageTypeStat(
                    type = type,
                    fileCount = typedFiles.size,
                    totalBytes = typedFiles.sumOf(IndexedFile::sizeBytes),
                )
            }
            .sortedByDescending(StorageTypeStat::totalBytes)
    }

    private fun storageTypePreviews(files: List<IndexedFile>): Map<StorageFileType, List<IndexedFile>> {
        return files.groupBy(::storageFileType)
            .mapValues { (_, typedFiles) ->
                typedFiles
                    .sortedByDescending(IndexedFile::sizeBytes)
                    .take(MAX_STORAGE_PREVIEWS_PER_TYPE)
            }
    }

    private fun storageFileType(file: IndexedFile): StorageFileType {
        val mime = StoragePathRules.normalizeText(file.mimeType)
        val extension = StoragePathRules.normalizeText(file.name.substringAfterLast('.', ""))
        return when {
            mime.startsWith("image/") -> StorageFileType.IMAGES
            mime.startsWith("video/") -> StorageFileType.VIDEOS
            mime.startsWith("audio/") -> StorageFileType.AUDIO
            extension == "apk" || mime == APK_MIME -> StorageFileType.APK
            extension in ARCHIVE_EXTENSIONS -> StorageFileType.ARCHIVES
            mime.startsWith("text/") || extension in DOCUMENT_EXTENSIONS -> StorageFileType.DOCUMENTS
            else -> StorageFileType.OTHER
        }
    }

    private fun mimeType(file: File): String {
        val extension = StoragePathRules.normalizeText(file.extension)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension).orEmpty()
    }

    private fun launcherApplications(packageManager: PackageManager): List<ApplicationInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
        return resolveInfos
            .mapNotNull { it.activityInfo?.applicationInfo }
            .filterNot { it.packageName == context.packageName }
            .distinctBy(ApplicationInfo::packageName)
    }

    private fun fileSnapshotStillMatches(
        uriString: String,
        expectedSizeBytes: Long,
        expectedModifiedAtMillis: Long,
    ): Boolean = runCatching {
        val uri = Uri.parse(uriString)
        if (uri.scheme != "file") return@runCatching true
        val file = File(requireNotNull(uri.path))
        file.isFile &&
            file.length() == expectedSizeBytes &&
            (expectedModifiedAtMillis <= 0L || file.lastModified() == expectedModifiedAtMillis)
    }.getOrDefault(false)

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

    private fun ageDays(modifiedAtMillis: Long): Long {
        if (modifiedAtMillis <= 0L) return 0L
        return TimeUnit.MILLISECONDS.toDays(
            (System.currentTimeMillis() - modifiedAtMillis).coerceAtLeast(0L),
        )
    }



    enum class FailureReason { CHANGED, MISSING, ORIGINAL_CHANGED, PROTECTED, DENIED, TRASH_UNAVAILABLE, TRASH_FOLDER_UNAVAILABLE }

    private sealed interface Removal {
        data object Deleted : Removal
        data class Trashed(val entryId: String) : Removal
        data class Failed(val reason: FailureReason) : Removal
    }

    private class RemovalTally {
        val removedIds = hashSetOf<String>()
        val failed = LinkedHashMap<String, FailureReason>()
        val trashEntryIds = ArrayList<String>()
        var deletedBytes = 0L
        var trashedBytes = 0L

        fun record(id: String, sizeBytes: Long, removal: Removal) {
            when (removal) {
                Removal.Deleted -> {
                    removedIds += id
                    deletedBytes += sizeBytes
                }
                is Removal.Trashed -> {
                    removedIds += id
                    trashedBytes += sizeBytes
                    trashEntryIds += removal.entryId
                }
                is Removal.Failed -> failed[id] = removal.reason
            }
        }

        fun toDirectResult(attemptedIds: Set<String>) = DirectDeleteResult(
            attemptedIds = attemptedIds,
            deletedIds = removedIds,
            deletedBytes = deletedBytes,
            trashedBytes = trashedBytes,
            trashEntryIds = trashEntryIds,
            failed = failed,
        )
    }

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
        private const val SHARED_INDEX_CACHE_TTL_MILLIS = 90L * 1000L
        private const val MAX_WHATSAPP_FILES = 100_000
        private const val MAX_STORAGE_PREVIEWS_PER_TYPE = 80
        private const val MAX_STORAGE_REVIEW_ITEMS = 50_000
        // A review list is rebuilt when a checkbox is toggled. Keep the
        // interactive surface bounded even on high-memory devices so a single
        // tap cannot allocate and recompose tens of thousands of rows.
        private const val MAX_SELECTABLE_STORAGE_REVIEW_ITEMS = 10_000
        private const val SCAN_PROGRESS_THROTTLE_MILLIS = 120L
        private val MEDIA_CATEGORIES = setOf(
            CleanCategory.SCREENSHOT,
            CleanCategory.LARGE_FILE,
            CleanCategory.DUPLICATE,
        )
        private val DOWNLOAD_TOOL_GROUPS = setOf(
            CleanupGroup.DOWNLOAD_FILE,
            CleanupGroup.DOWNLOAD_DOCUMENT,
            CleanupGroup.PARTIAL_DOWNLOADS,
            CleanupGroup.TEMP_FILES,
            CleanupGroup.LARGE_VIDEO,
            CleanupGroup.OLD_VIDEO,
            CleanupGroup.LARGE_OTHER,
        )
        private val ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
        private val DOCUMENT_EXTENSIONS = setOf(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "csv", "epub",
        )
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

    private data class ScanRoot(
        val file: File,
        val canonicalPath: String,
    )
}

private fun Map<String, DeviceRepository.FailureReason>.trashUnavailableIds(): Set<String> =
    filterValues {
        it == DeviceRepository.FailureReason.TRASH_UNAVAILABLE ||
            it == DeviceRepository.FailureReason.TRASH_FOLDER_UNAVAILABLE
    }.keys

private fun Map<String, DeviceRepository.FailureReason>.trashFolderMissing(): Boolean =
    values.any { it == DeviceRepository.FailureReason.TRASH_FOLDER_UNAVAILABLE }
