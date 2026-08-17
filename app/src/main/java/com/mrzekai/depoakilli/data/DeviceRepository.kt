package com.mrzekai.depoakilli.data

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.usage.StorageStatsManager
import android.os.BatteryManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.AiAssessment
import com.mrzekai.depoakilli.model.AppCacheEntry
import com.mrzekai.depoakilli.model.AppCacheSnapshot
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.DeviceInfoSnapshot
import com.mrzekai.depoakilli.model.IndexedFile
import com.mrzekai.depoakilli.model.MemorySnapshot
import com.mrzekai.depoakilli.model.ScanSummary
import com.mrzekai.depoakilli.model.ScanFocus
import com.mrzekai.depoakilli.model.StorageSnapshot
import com.mrzekai.depoakilli.model.WhatsAppLibrarySummary
import com.mrzekai.depoakilli.model.WhatsAppMediaItem
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class DeviceRepository(
    private val context: Context,
    private val aiEngine: AiCleaningEngine = AiCleaningEngine(),
) {
    private val resolver = context.contentResolver
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

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
        )
    }

    @Suppress("DEPRECATION")
    fun deviceInfoSnapshot(): DeviceInfoSnapshot {
        val battery = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
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
            batteryTemperatureCelsius = (battery?.getIntExtra(
                BatteryManager.EXTRA_TEMPERATURE,
                0,
            ) ?: 0) / 10f,
            batteryCharging = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                batteryStatus == BatteryManager.BATTERY_STATUS_FULL,
            appVersion = packageInfo.versionName.orEmpty(),
        )
    }

    fun ownCacheSize(): Long = directorySize(context.cacheDir) +
        (context.externalCacheDir?.let(::directorySize) ?: 0L)

    fun saveWhatsAppTree(uri: Uri): Boolean {
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(uri) }
            .getOrNull()
            ?.lowercase()
            ?: return false
        if (!treeId.contains("whatsapp") && !treeId.contains("com.whatsapp")) return false
        preferences.edit().putString(KEY_WHATSAPP_TREE_URI, uri.toString()).apply()
        return true
    }

    fun hasWhatsAppTreeAccess(): Boolean {
        val stored = preferences.getString(KEY_WHATSAPP_TREE_URI, null) ?: return false
        val uri = runCatching { Uri.parse(stored) }.getOrNull() ?: return false
        return resolver.persistedUriPermissions.any { permission ->
            permission.uri == uri && permission.isReadPermission && permission.isWritePermission
        }
    }

    @Suppress("DEPRECATION")
    fun hasUsageAccess(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return@withContext AppCacheSnapshot(supported = false)
        }
        if (!hasUsageAccess()) {
            return@withContext AppCacheSnapshot(supported = true, accessGranted = false)
        }

        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcherIntent, 0)
        }
        val applications = resolveInfos
            .mapNotNull { it.activityInfo?.applicationInfo }
            .filterNot { it.packageName == context.packageName }
            .distinctBy(ApplicationInfo::packageName)

        val storageStats = context.getSystemService(StorageStatsManager::class.java)
        val user = Process.myUserHandle()
        val reportedOtherAppsCacheBytes = runCatching {
            val allAppsCache = storageStats.queryStatsForUser(
                StorageManager.UUID_DEFAULT,
                user,
            ).cacheBytes
            (allAppsCache - ownCacheSize()).coerceAtLeast(0L)
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
                    label = application.loadLabel(packageManager).toString().ifBlank {
                        application.packageName
                    },
                    cacheBytes = cacheBytes,
                )
            }.getOrNull()
        }

        AppCacheSnapshot(
            supported = true,
            accessGranted = true,
            entries = entries.sortedByDescending(AppCacheEntry::cacheBytes),
            scannedAppCount = applications.size,
            reportedOtherAppsCacheBytes = reportedOtherAppsCacheBytes,
        )
    }

    suspend fun clearOwnCache(): Long = withContext(Dispatchers.IO) {
        val before = ownCacheSize()
        context.cacheDir.listFiles()?.forEach(File::deleteRecursively)
        context.externalCacheDir?.listFiles()?.forEach(File::deleteRecursively)
        before - ownCacheSize()
    }

    suspend fun scanWhatsAppLibrary(
        onProgress: (Int) -> Unit = {},
    ): WhatsAppLibrarySummary = withContext(Dispatchers.IO) {
        onProgress(3)
        val indexed = queryWhatsAppFiles { visitedDirectories, discoveredFiles ->
            val traversalProgress = 5 + (visitedDirectories * 4) + (discoveredFiles / 50)
            onProgress(traversalProgress.coerceIn(5, 72))
        }
        onProgress(75)
        val total = indexed.size.coerceAtLeast(1)
        val items = indexed.mapIndexed { index, file ->
            coroutineContext.ensureActive()
            if (index % 25 == 0) {
                onProgress(75 + (index * 23 / total))
            }
            WhatsAppMediaItem(
                id = file.uri,
                uri = file.uri,
                name = file.name,
                sizeBytes = file.sizeBytes,
                mimeType = file.mimeType,
                modifiedAtMillis = file.modifiedAtMillis,
                relativePath = file.relativePath,
                category = WhatsAppMediaClassifier.classify(
                    name = file.name,
                    mimeType = file.mimeType,
                    relativePath = file.relativePath,
                ),
            )
        }.sortedWith(
            compareBy<WhatsAppMediaItem> { it.category.ordinal }
                .thenByDescending(WhatsAppMediaItem::sizeBytes),
        )
        onProgress(100)
        WhatsAppLibrarySummary(
            items = items,
            scannedFileCount = indexed.size,
        )
    }

    suspend fun deleteWhatsAppItems(items: List<WhatsAppMediaItem>): WhatsAppDeleteResult =
        withContext(Dispatchers.IO) {
            val deletedIds = hashSetOf<String>()
            var deletedBytes = 0L
            items.forEach { item ->
                coroutineContext.ensureActive()
                val deleted = runCatching {
                    DocumentsContract.deleteDocument(resolver, Uri.parse(item.uri))
                }.getOrDefault(false)
                if (deleted) {
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

    suspend fun scan(
        limitedAccess: Boolean,
        focus: ScanFocus = ScanFocus.SMART,
    ): ScanSummary = withContext(Dispatchers.IO) {
        val indexed = buildList {
            when (focus) {
                ScanFocus.SMART, ScanFocus.DUPLICATES -> {
                    addAll(queryCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI))
                    addAll(queryCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI))
                    addAll(queryDownloads())
                    addAll(queryWhatsAppFiles())
                }

                ScanFocus.JUNK -> {
                    addAll(queryDownloads())
                    addAll(queryWhatsAppFiles())
                }

                ScanFocus.LARGE_FILES -> {
                    addAll(queryCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI))
                }

                ScanFocus.WHATSAPP -> addAll(queryWhatsAppFiles())
            }
        }.distinctBy(IndexedFile::uri)

        val assessed = indexed.mapNotNull { file ->
            coroutineContext.ensureActive()
            aiEngine.assess(file)?.let { assessment -> file.toCleanable(assessment) }
        }.toMutableList()

        if (focus == ScanFocus.SMART || focus == ScanFocus.DUPLICATES) {
            assessed += findDuplicates(indexed)
        }
        val cacheBytes = ownCacheSize()
        if (
            cacheBytes > MIN_CACHE_SUGGESTION_BYTES &&
            (focus == ScanFocus.SMART || focus == ScanFocus.JUNK)
        ) {
            assessed += CleanableItem(
                id = "app-cache",
                uri = APP_CACHE_URI,
                name = context.getString(R.string.cache_item_name),
                sizeBytes = cacheBytes,
                mimeType = "application/x-cache",
                modifiedAtMillis = System.currentTimeMillis(),
                relativePath = context.getString(R.string.cache_item_path),
                assessment = AiAssessment(
                    category = CleanCategory.APP_CACHE,
                    safetyScore = 100,
                    reasonRes = R.string.reason_safe_cache,
                    recommended = true,
                ),
            )
        }

        val focusedItems = assessed.filter { item ->
            when (focus) {
                ScanFocus.SMART -> true
                ScanFocus.JUNK -> item.assessment.category in JUNK_CATEGORIES
                ScanFocus.DUPLICATES -> item.assessment.category == CleanCategory.DUPLICATE
                ScanFocus.LARGE_FILES -> item.assessment.category == CleanCategory.LARGE_VIDEO
                ScanFocus.WHATSAPP -> item.assessment.category == CleanCategory.WHATSAPP_MEDIA
            }
        }

        ScanSummary(
            items = focusedItems
                .sortedWith(compareByDescending<CleanableItem> { it.assessment.safetyScore }.thenByDescending { it.sizeBytes })
                .distinctBy(CleanableItem::uri)
                .take(MAX_SUGGESTIONS),
            scannedFileCount = indexed.size,
            limitedAccess = limitedAccess,
            scanLimitReached = indexed.size >= MAX_FILES_PER_PASS,
        )
    }

    fun createDeleteRequest(items: List<CleanableItem>): DeletePlan {
        val contentItems = items.filterNot { it.uri == APP_CACHE_URI }
        val mediaItems = contentItems.filterNot(::isDocumentItem)
        val mediaUris = mediaItems
            .map { Uri.parse(it.uri) }

        if (mediaUris.isEmpty()) return DeletePlan.NoMediaFiles

        val pendingIntent: PendingIntent = MediaStore.createDeleteRequest(resolver, mediaUris)
        return DeletePlan.RequiresConsent(pendingIntent)
    }

    fun isDocumentItem(item: CleanableItem): Boolean = runCatching {
        DocumentsContract.isDocumentUri(context, Uri.parse(item.uri))
    }.getOrDefault(false)

    suspend fun deleteDocumentItems(items: List<CleanableItem>): DocumentDeleteResult =
        withContext(Dispatchers.IO) {
            val documentItems = items.filter(::isDocumentItem)
            val deletedIds = documentItems.mapNotNullTo(hashSetOf()) { item ->
                coroutineContext.ensureActive()
                val deleted = runCatching {
                    DocumentsContract.deleteDocument(resolver, Uri.parse(item.uri))
                }.getOrDefault(false)
                item.id.takeIf { deleted }
            }
            DocumentDeleteResult(
                attemptedIds = documentItems.mapTo(hashSetOf(), CleanableItem::id),
                deletedIds = deletedIds,
            )
        }

    private fun queryCollection(collection: Uri): List<IndexedFile> {
        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.MediaColumns.RELATIVE_PATH
        } else {
            MediaStore.MediaColumns.DATA
        }
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            pathColumn,
        )
        val oldest = query(
            collection = collection,
            projection = projection,
            pathColumnName = pathColumn,
            sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} ASC",
            maxRecords = MAX_FILES_PER_PASS,
        )
        val largest = query(
            collection = collection,
            projection = projection,
            pathColumnName = pathColumn,
            sortOrder = "${MediaStore.MediaColumns.SIZE} DESC",
            maxRecords = MAX_FILES_PER_PASS,
        )
        return (oldest + largest).distinctBy(IndexedFile::uri)
    }

    private fun queryDownloads(): List<IndexedFile> {
        return runCatching {
            queryCollection(MediaStore.Downloads.EXTERNAL_CONTENT_URI)
        }.getOrDefault(emptyList())
    }

    private fun queryWhatsAppFiles(
        onTraversalProgress: ((visitedDirectories: Int, discoveredFiles: Int) -> Unit)? = null,
    ): List<IndexedFile> {
        val stored = preferences.getString(KEY_WHATSAPP_TREE_URI, null) ?: return emptyList()
        val treeUri = runCatching { Uri.parse(stored) }.getOrNull() ?: return emptyList()
        if (!hasWhatsAppTreeAccess()) return emptyList()
        val rootDocumentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }
            .getOrNull()
            ?: return emptyList()
        val output = ArrayList<IndexedFile>()
        val pending = ArrayDeque<WhatsAppDirectory>()
        pending.add(WhatsAppDirectory(rootDocumentId, "WhatsApp", 0))
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )

        var visitedDirectories = 0

        while (pending.isNotEmpty() && output.size < MAX_WHATSAPP_FILES) {
            val directory = pending.removeFirst()
            visitedDirectories++
            onTraversalProgress?.invoke(visitedDirectories, output.size)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                directory.documentId,
            )
            runCatching {
                resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    )
                    val nameColumn = cursor.getColumnIndexOrThrow(
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    )
                    val mimeColumn = cursor.getColumnIndexOrThrow(
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                    )
                    val sizeColumn = cursor.getColumnIndexOrThrow(
                        DocumentsContract.Document.COLUMN_SIZE,
                    )
                    val modifiedColumn = cursor.getColumnIndexOrThrow(
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    )
                    while (cursor.moveToNext() && output.size < MAX_WHATSAPP_FILES) {
                        val documentId = cursor.getString(idColumn)
                        val name = cursor.getString(nameColumn).orEmpty().ifBlank {
                            context.getString(R.string.unnamed_file)
                        }
                        val mimeType = cursor.getString(mimeColumn).orEmpty()
                        val path = "${directory.relativePath}/$name"
                        if (
                            mimeType == DocumentsContract.Document.MIME_TYPE_DIR &&
                            directory.depth < MAX_WHATSAPP_DEPTH
                        ) {
                            pending.add(
                                WhatsAppDirectory(documentId, path, directory.depth + 1),
                            )
                        } else if (mimeType != DocumentsContract.Document.MIME_TYPE_DIR) {
                            val size = cursor.getLong(sizeColumn).coerceAtLeast(0L)
                            if (size > 0L) {
                                output += IndexedFile(
                                    uri = DocumentsContract.buildDocumentUriUsingTree(
                                        treeUri,
                                        documentId,
                                    ).toString(),
                                    name = name,
                                    sizeBytes = size,
                                    mimeType = mimeType,
                                    modifiedAtMillis = cursor.getLong(modifiedColumn).coerceAtLeast(0L),
                                    relativePath = path,
                                )
                                if (output.size % 25 == 0) {
                                    onTraversalProgress?.invoke(visitedDirectories, output.size)
                                }
                            }
                        }
                    }
                }
            }
        }
        return output
    }

    private fun query(
        collection: Uri,
        projection: Array<String>,
        pathColumnName: String,
        sortOrder: String,
        maxRecords: Int,
    ): List<IndexedFile> {
        val output = ArrayList<IndexedFile>()
        runCatching {
            resolver.query(
                collection,
                projection,
                null,
                null,
                sortOrder,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val pathColumn = cursor.getColumnIndexOrThrow(pathColumnName)

                var count = 0
                while (cursor.moveToNext() && count < maxRecords) {
                    val size = cursor.getLong(sizeColumn).coerceAtLeast(0)
                    if (size == 0L) continue
                    val id = cursor.getLong(idColumn)
                    output += IndexedFile(
                        uri = ContentUris.withAppendedId(collection, id).toString(),
                        name = cursor.getString(nameColumn).orEmpty().ifBlank {
                            context.getString(R.string.unnamed_file)
                        },
                        sizeBytes = size,
                        mimeType = cursor.getString(mimeColumn).orEmpty(),
                        modifiedAtMillis = cursor.getLong(dateColumn) * 1000L,
                        relativePath = cursor.getString(pathColumn).orEmpty(),
                    )
                    count++
                }
            }
        }
        return output
    }

    private suspend fun findDuplicates(files: List<IndexedFile>): List<CleanableItem> {
        val duplicates = ArrayList<CleanableItem>()
        files.asSequence()
            .filter { it.sizeBytes in MIN_DUPLICATE_BYTES..MAX_HASH_BYTES }
            .groupBy { it.sizeBytes to it.mimeType }
            .values
            .asSequence()
            .filter { it.size in 2..MAX_DUPLICATE_GROUP }
            .take(MAX_HASH_GROUPS)
            .forEach { sameSizeFiles ->
                coroutineContext.ensureActive()
                sameSizeFiles
                    .mapNotNull { file -> sampleFingerprint(file)?.let { it to file } }
                    .groupBy({ it.first }, { it.second })
                    .values
                    .filter { it.size > 1 }
                    .forEach { sameSampleFiles ->
                        sameSampleFiles
                            .mapNotNull { file -> fingerprint(file)?.let { it to file } }
                            .groupBy({ it.first }, { it.second })
                            .values
                            .filter { it.size > 1 }
                            .forEach { sameContentFiles ->
                                val decision = DuplicatePolicy.choose(sameContentFiles)
                                    ?: error("Duplicate group unexpectedly contained fewer than two files")
                                val assessment = aiEngine.duplicateAssessment(isExact = true).let {
                                    if (decision.automaticSelectionIsSafe) {
                                        it
                                    } else {
                                        it.copy(recommended = false)
                                    }
                                }
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

    private fun sampleFingerprint(file: IndexedFile): String? = runCatching {
        resolver.openFileDescriptor(Uri.parse(file.uri), "r")?.use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(file.sizeBytes).array())
                val channel = input.channel
                updateDigestFromChannel(channel, digest, 0L, HASH_SAMPLE_BYTES)
                if (file.sizeBytes > HASH_SAMPLE_BYTES) {
                    updateDigestFromChannel(
                        channel,
                        digest,
                        (file.sizeBytes - HASH_SAMPLE_BYTES).coerceAtLeast(0L),
                        HASH_SAMPLE_BYTES,
                    )
                }
                digest.digest().joinToString("") { "%02x".format(it) }
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

    private fun fingerprint(file: IndexedFile): String? = runCatching {
        resolver.openFileDescriptor(Uri.parse(file.uri), "r")?.use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val channel = input.channel
                val buffer = ByteBuffer.allocate(HASH_BUFFER_BYTES)
                while (channel.read(buffer) > 0) {
                    buffer.flip()
                    digest.update(buffer)
                    buffer.clear()
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        }
    }.getOrNull()

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
                total += file.length().coerceAtLeast(0)
            }
        }
        return total
    }

    sealed interface DeletePlan {
        data class RequiresConsent(val pendingIntent: PendingIntent) : DeletePlan
        data object NoMediaFiles : DeletePlan
    }

    data class DocumentDeleteResult(
        val attemptedIds: Set<String>,
        val deletedIds: Set<String>,
    )

    data class WhatsAppDeleteResult(
        val attemptedCount: Int,
        val deletedIds: Set<String>,
        val deletedBytes: Long,
    ) {
        val failedCount: Int get() = attemptedCount - deletedIds.size
    }

    companion object {
        const val APP_CACHE_URI = "app-cache://internal"
        private const val PREFERENCES_NAME = "cleaner_access"
        private const val KEY_WHATSAPP_TREE_URI = "whatsapp_tree_uri"
        private const val MAX_FILES_PER_COLLECTION = 4_000
        private const val MAX_FILES_PER_PASS = MAX_FILES_PER_COLLECTION / 2
        private const val MAX_WHATSAPP_FILES = 4_000
        private const val MAX_WHATSAPP_DEPTH = 8
        private const val MAX_SUGGESTIONS = 500
        private const val MAX_DUPLICATE_GROUP = 20
        private const val MAX_HASH_GROUPS = 100
        private const val HASH_BUFFER_BYTES = 128 * 1024
        private const val HASH_SAMPLE_BYTES = 64 * 1024
        private const val MIN_DUPLICATE_BYTES = 32L * 1024L
        private const val MAX_HASH_BYTES = 40L * 1024L * 1024L
        private const val MIN_CACHE_SUGGESTION_BYTES = 1L * 1024L * 1024L
        private val JUNK_CATEGORIES = setOf(
            CleanCategory.OLD_DOWNLOAD,
            CleanCategory.APK_PACKAGE,
            CleanCategory.APP_CACHE,
            CleanCategory.WHATSAPP_MEDIA,
        )
    }

    private data class WhatsAppDirectory(
        val documentId: String,
        val relativePath: String,
        val depth: Int,
    )
}
