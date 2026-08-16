package com.mrzekai.depoakilli.data

import android.app.ActivityManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.AiAssessment
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.IndexedFile
import com.mrzekai.depoakilli.model.MemorySnapshot
import com.mrzekai.depoakilli.model.ScanSummary
import com.mrzekai.depoakilli.model.StorageSnapshot
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

    fun ownCacheSize(): Long = directorySize(context.cacheDir) +
        (context.externalCacheDir?.let(::directorySize) ?: 0L)

    suspend fun clearOwnCache(): Long = withContext(Dispatchers.IO) {
        val before = ownCacheSize()
        context.cacheDir.listFiles()?.forEach(File::deleteRecursively)
        context.externalCacheDir?.listFiles()?.forEach(File::deleteRecursively)
        before - ownCacheSize()
    }

    suspend fun scan(limitedAccess: Boolean): ScanSummary = withContext(Dispatchers.IO) {
        val indexed = buildList {
            addAll(queryCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI))
            addAll(queryCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI))
            addAll(queryDownloads())
        }.distinctBy(IndexedFile::uri)

        val assessed = indexed.mapNotNull { file ->
            coroutineContext.ensureActive()
            aiEngine.assess(file)?.let { assessment -> file.toCleanable(assessment) }
        }.toMutableList()

        assessed += findDuplicates(indexed)
        val cacheBytes = ownCacheSize()
        if (cacheBytes > MIN_CACHE_SUGGESTION_BYTES) {
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

        ScanSummary(
            items = assessed
                .sortedWith(compareByDescending<CleanableItem> { it.assessment.safetyScore }.thenByDescending { it.sizeBytes })
                .distinctBy(CleanableItem::uri)
                .take(MAX_SUGGESTIONS),
            scannedFileCount = indexed.size,
            limitedAccess = limitedAccess,
        )
    }

    fun createDeleteRequest(items: List<CleanableItem>): DeletePlan {
        val mediaItems = items
            .filterNot { it.uri == APP_CACHE_URI }
        val mediaUris = mediaItems
            .map { Uri.parse(it.uri) }

        if (mediaUris.isEmpty()) return DeletePlan.NoMediaFiles

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pendingIntent: PendingIntent = MediaStore.createDeleteRequest(resolver, mediaUris)
            return DeletePlan.RequiresConsent(pendingIntent)
        }

        var deletedBytes = 0L
        mediaUris.zip(mediaItems).forEach { (uri, item) ->
            runCatching { resolver.delete(uri, null, null) }
                .onSuccess { if (it > 0) deletedBytes += item.sizeBytes }
        }
        return DeletePlan.Completed(deletedBytes)
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
        return query(collection, projection, pathColumn)
    }

    private fun queryDownloads(): List<IndexedFile> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        return runCatching {
            queryCollection(MediaStore.Downloads.EXTERNAL_CONTENT_URI)
        }.getOrDefault(emptyList())
    }

    private fun query(
        collection: Uri,
        projection: Array<String>,
        pathColumnName: String,
    ): List<IndexedFile> {
        val output = ArrayList<IndexedFile>()
        runCatching {
            resolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val pathColumn = cursor.getColumnIndexOrThrow(pathColumnName)

                var count = 0
                while (cursor.moveToNext() && count < MAX_FILES_PER_COLLECTION) {
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
                    .mapNotNull { file -> fingerprint(file)?.let { it to file } }
                    .groupBy({ it.first }, { it.second })
                    .values
                    .filter { it.size > 1 }
                    .forEach { sameContentFiles ->
                        val keep = sameContentFiles.maxByOrNull(IndexedFile::modifiedAtMillis)
                        sameContentFiles.filterNot { it.uri == keep?.uri }.forEach { duplicate ->
                            duplicates += duplicate.toCleanable(aiEngine.duplicateAssessment(isExact = true))
                        }
                    }
            }
        return duplicates
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
        data class Completed(val deletedBytes: Long) : DeletePlan
        data object NoMediaFiles : DeletePlan
    }

    companion object {
        const val APP_CACHE_URI = "app-cache://internal"
        private const val MAX_FILES_PER_COLLECTION = 4_000
        private const val MAX_SUGGESTIONS = 500
        private const val MAX_DUPLICATE_GROUP = 20
        private const val MAX_HASH_GROUPS = 100
        private const val HASH_BUFFER_BYTES = 128 * 1024
        private const val MIN_DUPLICATE_BYTES = 32L * 1024L
        private const val MAX_HASH_BYTES = 40L * 1024L * 1024L
        private const val MIN_CACHE_SUGGESTION_BYTES = 1L * 1024L * 1024L
    }
}
