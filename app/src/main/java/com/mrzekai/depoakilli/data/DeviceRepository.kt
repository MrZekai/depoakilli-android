package com.mrzekai.depoakilli.data

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.Environment
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
import java.nio.ByteBuffer
import java.security.MessageDigest
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
    private val aiEngine: AiCleaningEngine = AiCleaningEngine(),
) {
    private val resolver = context.contentResolver

    fun storageSnapshot(): StorageSnapshot {
        val stats = StatFs(Environment.getDataDirectory().absolutePath)
        return StorageSnapshot(
            totalBytes = stats.totalBytes,
            availableBytes = stats.availableBytes,
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
    ): ScanSummary = withContext(Dispatchers.IO) {
        if (!hasAllFilesAccess()) {
            return@withContext ScanSummary(limitedAccess = true)
        }

        val indexed = if (focus == ScanFocus.WHATSAPP) {
            indexWhatsAppFiles { files -> onProgress(files, files) }
        } else {
            indexSharedStorage { visitedDirectories, discoveredFiles ->
                onProgress(visitedDirectories, discoveredFiles)
            }
        }
        val assessments = ArrayList<CleanableItem>()

        indexed.forEach { file ->
            coroutineContext.ensureActive()
            assessmentForFocus(file, focus)?.let { assessment ->
                assessments += file.toCleanable(assessment)
            }
        }

        // Smart Clean must stay fast: exact whole-file duplicate hashing belongs
        // to the dedicated Duplicate Cleaner and deeper review tools.
        if (
            focus == ScanFocus.DEEP ||
            focus == ScanFocus.DUPLICATES ||
            focus == ScanFocus.MEDIA
        ) {
            val duplicateSource = if (focus == ScanFocus.MEDIA) {
                indexed.filter { it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/") }
            } else {
                indexed
            }
            assessments += findDuplicates(duplicateSource)
        }

        val focusedItems = assessments.asSequence()
            .filter { item ->
                when (focus) {
                    ScanFocus.SMART,
                    ScanFocus.DEEP -> true
                    ScanFocus.JUNK -> item.assessment.category == CleanCategory.JUNK
                    ScanFocus.DUPLICATES -> item.assessment.category == CleanCategory.DUPLICATE
                    ScanFocus.LARGE_FILES -> item.assessment.category == CleanCategory.LARGE_FILE
                    ScanFocus.WHATSAPP -> item.assessment.category == CleanCategory.WHATSAPP_MEDIA
                    ScanFocus.MEDIA -> item.assessment.category in MEDIA_CATEGORIES
                    ScanFocus.DOWNLOADS -> item.assessment.category in DOWNLOAD_CATEGORIES
                    ScanFocus.APKS -> item.assessment.category == CleanCategory.APK_PACKAGE
                    ScanFocus.ANALYZE -> false
                }
            }
            .distinctBy(CleanableItem::uri)
            .sortedWith(
                compareByDescending<CleanableItem> { it.assessment.safetyScore }
                    .thenByDescending { it.sizeBytes },
            )
            .take(MAX_RESULT_ITEMS)
            .toList()

        ScanSummary(
            items = focusedItems,
            scannedFileCount = indexed.size,
            scannedBytes = indexed.sumOf(IndexedFile::sizeBytes),
            limitedAccess = false,
            scanLimitReached = indexed.size >= MAX_INDEXED_FILES,
            storageTypes = storageTypeStats(indexed),
            storagePreviews = storageTypePreviews(indexed),
            smartMediaTypes = smartMediaTypeStats(indexed),
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
        val indexed = indexSharedStorage { visitedDirectories, discoveredFiles ->
            onProgress(visitedDirectories, discoveredFiles)
        }
        val typed = indexed
            .asSequence()
            .filter { storageFileType(it) == type }
            .filter { !excludeWhatsAppMedia || !isWhatsAppSharedMedia(it) }
            .sortedByDescending(IndexedFile::sizeBytes)
            .take(MAX_STORAGE_REVIEW_ITEMS)
            .map { StorageReviewItem(file = it, selected = false) }
            .toList()
        StorageReviewSummary(
            type = type,
            excludeWhatsAppMedia = excludeWhatsAppMedia,
            items = typed,
            scannedFileCount = indexed.size,
            scanLimitReached = indexed.size >= MAX_INDEXED_FILES || typed.size >= MAX_STORAGE_REVIEW_ITEMS,
            loading = false,
        )
    }

    suspend fun scanWhatsAppLibrary(
        onProgress: (Int) -> Unit = {},
    ): WhatsAppLibrarySummary = withContext(Dispatchers.IO) {
        if (!hasAllFilesAccess()) return@withContext WhatsAppLibrarySummary()
        onProgress(2)
        val indexed = indexWhatsAppFiles { discovered ->
            onProgress((5 + discovered / 75).coerceIn(5, 88))
        }
        val count = indexed.size.coerceAtLeast(1)
        val items = indexed.mapIndexed { index, file ->
            coroutineContext.ensureActive()
            if (index % 40 == 0) {
                onProgress((88 + ((index + 1) * 10 / count)).coerceIn(88, 98))
            }
            val normalized = "/${file.relativePath.replace('\\', '/').trim('/')}/".lowercase()
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

    suspend fun deleteWhatsAppItems(items: List<WhatsAppMediaItem>): WhatsAppDeleteResult =
        withContext(Dispatchers.IO) {
            var deletedBytes = 0L
            val deletedIds = hashSetOf<String>()
            items.forEach { item ->
                coroutineContext.ensureActive()
                if (
                    fileSnapshotStillMatches(
                        item.uri,
                        item.sizeBytes,
                        item.modifiedAtMillis,
                    ) &&
                    deleteUriDirectly(item.uri)
                ) {
                    deletedIds += item.id
                    deletedBytes += item.sizeBytes
                }
            }
            WhatsAppDeleteResult(
                attemptedCount = items.size,
                deletedIds = deletedIds,
                deletedBytes = deletedBytes,
            )
        }

    suspend fun deleteStorageReviewItems(items: List<StorageReviewItem>): DirectDeleteResult =
        withContext(Dispatchers.IO) {
            val attemptedIds = items.mapTo(hashSetOf(), StorageReviewItem::id)
            val deletedIds = hashSetOf<String>()
            var deletedBytes = 0L
            items.forEach { item ->
                coroutineContext.ensureActive()
                if (
                    fileSnapshotStillMatches(
                        item.file.uri,
                        item.file.sizeBytes,
                        item.file.modifiedAtMillis,
                    ) &&
                    deleteUriDirectly(item.file.uri)
                ) {
                    deletedIds += item.id
                    deletedBytes += item.file.sizeBytes
                }
            }
            DirectDeleteResult(
                attemptedIds = attemptedIds,
                deletedIds = deletedIds,
                deletedBytes = deletedBytes,
            )
        }

    suspend fun deleteDirectItems(items: List<CleanableItem>): DirectDeleteResult = withContext(Dispatchers.IO) {
        val directItems = items.filter { Uri.parse(it.uri).scheme == "file" }
        val deletedIds = hashSetOf<String>()
        var deletedBytes = 0L
        directItems.forEach { item ->
            coroutineContext.ensureActive()
            if (
                fileSnapshotStillMatches(
                    item.uri,
                    item.sizeBytes,
                    item.modifiedAtMillis,
                ) &&
                deleteUriDirectly(item.uri)
            ) {
                deletedIds += item.id
                deletedBytes += item.sizeBytes
            }
        }
        DirectDeleteResult(
            attemptedIds = directItems.mapTo(hashSetOf(), CleanableItem::id),
            deletedIds = deletedIds,
            deletedBytes = deletedBytes,
        )
    }

    fun createDeleteRequest(items: List<CleanableItem>): DeletePlan {
        val mediaUris = items
            .filter { Uri.parse(it.uri).scheme == "content" }
            .map { Uri.parse(it.uri) }
        if (mediaUris.isEmpty()) return DeletePlan.NoMediaFiles
        val pendingIntent: PendingIntent = MediaStore.createDeleteRequest(resolver, mediaUris)
        return DeletePlan.RequiresConsent(pendingIntent)
    }

    private fun assessmentForFocus(file: IndexedFile, focus: ScanFocus): AiAssessment? {
        return when (focus) {
            ScanFocus.SMART -> aiEngine.assess(file)
            ScanFocus.DEEP -> aiEngine.assessDeep(file)

            ScanFocus.JUNK -> aiEngine.assess(file)?.takeIf {
                it.category == CleanCategory.JUNK
            }

            ScanFocus.DUPLICATES,
            ScanFocus.ANALYZE -> null

            ScanFocus.APKS -> aiEngine.assess(file)?.takeIf {
                it.category == CleanCategory.APK_PACKAGE
            }

            ScanFocus.LARGE_FILES -> if (file.sizeBytes >= LARGE_FILE_BYTES) {
                AiAssessment(
                    category = CleanCategory.LARGE_FILE,
                    safetyScore = 60,
                    reasonRes = R.string.reason_large_file,
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

            ScanFocus.DOWNLOADS -> {
                val normalized = "/${file.relativePath.replace('\\', '/').trim('/')}/".lowercase()
                if (!normalized.contains("/download/") && !normalized.contains("/downloads/")) {
                    null
                } else {
                    aiEngine.assessDeep(file)?.takeIf {
                        it.category == CleanCategory.APK_PACKAGE ||
                            it.category == CleanCategory.OLD_DOWNLOAD ||
                            it.category == CleanCategory.JUNK
                    }
                }
            }

            ScanFocus.WHATSAPP -> aiEngine.assessDeep(file) ?: AiAssessment(
                category = CleanCategory.WHATSAPP_MEDIA,
                safetyScore = 55,
                reasonRes = R.string.reason_whatsapp_review,
                recommended = false,
            )
        }
    }

    private suspend fun indexSharedStorage(
        onTraversalProgress: (visitedDirectories: Int, discoveredFiles: Int) -> Unit,
    ): List<IndexedFile> {
        val root = Environment.getExternalStorageDirectory()
        if (!root.isDirectory) return emptyList()
        val output = ArrayList<IndexedFile>()
        val pending = ArrayDeque<File>()
        pending.add(root)
        var visitedDirectories = 0
        var lastProgressDirectories = 0
        var lastProgressFiles = 0
        var lastProgressAt = 0L

        while (pending.isNotEmpty() && output.size < MAX_INDEXED_FILES) {
            coroutineContext.ensureActive()
            val directory = pending.removeFirst()
            visitedDirectories++
            val children = runCatching { directory.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                coroutineContext.ensureActive()
                if (child.isDirectory) {
                    if (!shouldSkipDirectory(root, child)) pending.addLast(child)
                } else if (child.isFile) {
                    val size = child.length().coerceAtLeast(0L)
                    if (size <= 0L) continue
                    output += indexedFile(root, child)
                    if (output.size >= MAX_INDEXED_FILES) break
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
        onDiscovered: (Int) -> Unit,
    ): List<IndexedFile> {
        val roots = whatsappRoots().filter(File::isDirectory)
        if (roots.isEmpty()) return emptyList()
        val storageRoot = Environment.getExternalStorageDirectory()
        val output = ArrayList<IndexedFile>()
        val pending = ArrayDeque<File>()
        roots.forEach(pending::addLast)
        val visited = hashSetOf<String>()

        while (pending.isNotEmpty() && output.size < MAX_WHATSAPP_FILES) {
            coroutineContext.ensureActive()
            val directory = pending.removeFirst()
            val canonical = runCatching { directory.canonicalPath }.getOrDefault(directory.absolutePath)
            if (!visited.add(canonical)) continue
            val children = runCatching { directory.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                coroutineContext.ensureActive()
                if (child.isDirectory) {
                    pending.addLast(child)
                } else if (child.isFile && child.length() > 0L) {
                    output += indexedFile(storageRoot, child)
                    if (output.size % 50 == 0) onDiscovered(output.size)
                    if (output.size >= MAX_WHATSAPP_FILES) break
                }
            }
        }
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
        )
    }

    private fun shouldSkipDirectory(root: File, directory: File): Boolean {
        val relative = runCatching { directory.relativeTo(root).invariantSeparatorsPath }
            .getOrDefault(directory.invariantSeparatorsPath)
            .trim('/')
            .lowercase()
        if (relative == "android/data" || relative.startsWith("android/data/")) return true
        if (relative == "android/obb" || relative.startsWith("android/obb/")) return true
        return false
    }

    private fun whatsappRoots(): List<File> {
        val root = Environment.getExternalStorageDirectory()
        return listOf(
            File(root, "Android/media/com.whatsapp/WhatsApp/Media"),
            File(root, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media"),
            File(root, "Android/media/com.whatsapp.w4b/WhatsApp/Media"),
            File(root, "WhatsApp/Media"),
            File(root, "WhatsApp Business/Media"),
        ).distinctBy { it.absolutePath }
    }

    private suspend fun findDuplicates(files: List<IndexedFile>): List<CleanableItem> {
        val duplicates = ArrayList<CleanableItem>()
        val sameSizeGroups = files.asSequence()
            .filter { it.sizeBytes >= MIN_DUPLICATE_BYTES }
            .groupBy(IndexedFile::sizeBytes)
            .values
            .filter { it.size > 1 }
            .sortedByDescending { group -> group.first().sizeBytes * group.size }

        for (sameSizeFiles in sameSizeGroups) {
            coroutineContext.ensureActive()
            val sampleGroups = sameSizeFiles
                .mapNotNull { file -> sampleFingerprint(file)?.let { it to file } }
                .groupBy({ it.first }, { it.second })
                .values
                .filter { it.size > 1 }
            for (sameSampleFiles in sampleGroups) {
                coroutineContext.ensureActive()
                val contentGroups = sameSampleFiles
                    .mapNotNull { file -> fingerprint(file)?.let { it to file } }
                    .groupBy({ it.first }, { it.second })
                    .values
                    .filter { it.size > 1 }
                for (sameContentFiles in contentGroups) {
                    val decision = DuplicatePolicy.choose(sameContentFiles) ?: continue
                    val assessment = aiEngine.duplicateAssessment(decision.automaticSelectionIsSafe)
                    sameContentFiles.filterNot { it.uri == decision.keep.uri }.forEach { duplicate ->
                        duplicates += duplicate.toCleanable(assessment).copy(
                            protectedDuplicateName = decision.keep.name,
                        )
                    }
                }
            }
        }
        return duplicates
    }

    private fun sampleFingerprint(file: IndexedFile): String? = withFileInput(file) { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(file.sizeBytes).array())
        val channel = input.channel
        updateDigestFromChannel(channel, digest, 0L, HASH_SAMPLE_BYTES)
        if (file.sizeBytes > HASH_SAMPLE_BYTES * 2L) {
            updateDigestFromChannel(
                channel,
                digest,
                (file.sizeBytes / 2L - HASH_SAMPLE_BYTES / 2L).coerceAtLeast(0L),
                HASH_SAMPLE_BYTES,
            )
        }
        if (file.sizeBytes > HASH_SAMPLE_BYTES) {
            updateDigestFromChannel(
                channel,
                digest,
                (file.sizeBytes - HASH_SAMPLE_BYTES).coerceAtLeast(0L),
                HASH_SAMPLE_BYTES,
            )
        }
        digest.digest().toHex()
    }

    private fun fingerprint(file: IndexedFile): String? = withFileInput(file) { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val channel = input.channel
        val buffer = ByteBuffer.allocate(HASH_BUFFER_BYTES)
        while (channel.read(buffer) > 0) {
            buffer.flip()
            digest.update(buffer)
            buffer.clear()
        }
        digest.digest().toHex()
    }

    private fun <T> withFileInput(file: IndexedFile, block: (FileInputStream) -> T): T? = runCatching {
        val uri = Uri.parse(file.uri)
        if (uri.scheme == "file") {
            FileInputStream(File(requireNotNull(uri.path))).use(block)
        } else {
            resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use(block)
            }
        }
    }.getOrNull()

    private fun updateDigestFromChannel(
        channel: java.nio.channels.FileChannel,
        digest: MessageDigest,
        position: Long,
        byteCount: Int,
    ) {
        channel.position(position)
        val buffer = ByteBuffer.allocate(byteCount)
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer)
            if (read <= 0) break
        }
        buffer.flip()
        digest.update(buffer)
    }

    private fun smartMediaTypeStats(files: List<IndexedFile>): List<StorageTypeStat> {
        var imageCount = 0
        var imageBytes = 0L
        var videoCount = 0
        var videoBytes = 0L

        files.forEach { file ->
            when {
                file.mimeType.startsWith("image/") -> {
                    if (!isWhatsAppSharedMedia(file)) {
                        imageCount++
                        imageBytes += file.sizeBytes
                    }
                }
                file.mimeType.startsWith("video/") -> {
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

    private fun isWhatsAppSharedMedia(file: IndexedFile): Boolean {
        val path = "/${file.relativePath.replace('\\', '/').trim('/')}/".lowercase()
        return path.contains("/android/media/com.whatsapp/") ||
            path.contains("/android/media/com.whatsapp.w4b/") ||
            path.startsWith("/whatsapp/") ||
            path.startsWith("/whatsapp business/")
    }

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
        val mime = file.mimeType.lowercase()
        val extension = file.name.substringAfterLast('.', "").lowercase()
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
        val extension = file.extension.lowercase()
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

    private fun deleteUriDirectly(uriString: String): Boolean = runCatching {
        val uri = Uri.parse(uriString)
        when (uri.scheme) {
            "file" -> File(requireNotNull(uri.path)).delete()
            "content" -> resolver.delete(uri, null, null) > 0
            else -> false
        }
    }.getOrDefault(false)

    private fun IndexedFile.toCleanable(assessment: AiAssessment) = CleanableItem(
        id = "$uri-${assessment.category}",
        uri = uri,
        name = name,
        sizeBytes = sizeBytes,
        mimeType = mimeType,
        modifiedAtMillis = modifiedAtMillis,
        relativePath = relativePath,
        assessment = assessment,
        selected = assessment.recommended,
    )

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

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    sealed interface DeletePlan {
        data class RequiresConsent(val pendingIntent: PendingIntent) : DeletePlan
        data object NoMediaFiles : DeletePlan
    }

    data class DirectDeleteResult(
        val attemptedIds: Set<String>,
        val deletedIds: Set<String>,
        val deletedBytes: Long,
    ) {
        val failedCount: Int get() = attemptedIds.size - deletedIds.size
    }

    data class WhatsAppDeleteResult(
        val attemptedCount: Int,
        val deletedIds: Set<String>,
        val deletedBytes: Long,
    ) {
        val failedCount: Int get() = attemptedCount - deletedIds.size
    }

    companion object {
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val MAX_INDEXED_FILES = 200_000
        private const val MAX_WHATSAPP_FILES = 100_000
        private const val MAX_STORAGE_PREVIEWS_PER_TYPE = 80
        private const val MAX_STORAGE_REVIEW_ITEMS = 50_000
        private const val MAX_RESULT_ITEMS = 4_000
        private const val SCAN_PROGRESS_THROTTLE_MILLIS = 120L
        private const val HASH_BUFFER_BYTES = 256 * 1024
        private const val HASH_SAMPLE_BYTES = 64 * 1024
        private const val MIN_DUPLICATE_BYTES = 32L * 1024L
        private const val LARGE_FILE_BYTES = 100L * 1024L * 1024L
        private val JUNK_CATEGORIES = setOf(
            CleanCategory.JUNK,
            CleanCategory.OLD_DOWNLOAD,
            CleanCategory.APK_PACKAGE,
        )
        private val MEDIA_CATEGORIES = setOf(
            CleanCategory.SCREENSHOT,
            CleanCategory.LARGE_FILE,
            CleanCategory.DUPLICATE,
        )
        private val DOWNLOAD_CATEGORIES = setOf(
            CleanCategory.JUNK,
            CleanCategory.OLD_DOWNLOAD,
            CleanCategory.APK_PACKAGE,
        )
        private val ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
        private val DOCUMENT_EXTENSIONS = setOf(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "csv", "epub",
        )
    }
}
