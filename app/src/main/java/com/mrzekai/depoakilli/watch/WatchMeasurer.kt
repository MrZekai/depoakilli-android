package com.mrzekai.depoakilli.watch

import android.content.Context
import android.provider.MediaStore
import com.mrzekai.depoakilli.data.DeviceRepository
import com.mrzekai.depoakilli.data.StoragePathRules
import com.mrzekai.depoakilli.data.TrashStore
import java.time.LocalDate

/**
 * Reads totals only: StatFs for the volume, MediaStore SIZE sums for the
 * kinds. One cursor pass; no file is opened, moved or deleted, and the full
 * file scan is never started from here.
 */
class WatchMeasurer(private val context: Context) {

    fun measure(nowMillis: Long = System.currentTimeMillis()): WatchSample {
        val storage = DeviceRepository(context).storageSnapshot()
        var videos = 0L
        var photos = 0L
        var whatsApp = 0L
        runCatching {
            val projection = arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.RELATIVE_PATH,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
            )
            val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?) OR " +
                "LOWER(${MediaStore.Files.FileColumns.RELATIVE_PATH}) LIKE ?"
            val args = arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                "%whatsapp%",
            )
            context.contentResolver.query(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                projection,
                selection,
                args,
                null,
            )?.use { cursor ->
                val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val size = cursor.getLong(sizeCol).coerceAtLeast(0L)
                    val path = cursor.getString(pathCol).orEmpty()
                    val name = cursor.getString(nameCol).orEmpty()
                    when (WatchKinds.kindOf(path, name, cursor.getInt(typeCol))) {
                        WatchKind.WHATSAPP -> whatsApp += size
                        WatchKind.VIDEOS -> videos += size
                        WatchKind.PHOTOS -> photos += size
                        else -> Unit
                    }
                }
            }
        }
        return WatchSample(
            dayEpoch = LocalDate.now().toEpochDay(),
            measuredAtMillis = nowMillis,
            totalBytes = storage.totalBytes,
            availableBytes = storage.availableBytes,
            videoBytes = videos,
            photoBytes = photos,
            whatsAppBytes = whatsApp,
        )
    }
}

/** Disjoint kinds (pure, tested): WhatsApp by path first, then videos, then photos. */
object WatchKinds {
    const val MEDIA_TYPE_IMAGE = 1
    const val MEDIA_TYPE_VIDEO = 3

    /** Null: not counted in any kind (the app's Trash, shrink outputs waiting for approval). */
    fun kindOf(relativePath: String, name: String, mediaType: Int): WatchKind? {
        val path = "/" + relativePath.trim('/').lowercase() + "/"
        if ("/${TrashStore.TRASH_DIRECTORY.lowercase()}/" in path) return null
        val lower = name.lowercase()
        // 3A-2's shrink work files; its outputs waiting for approval are added there.
        if (lower.endsWith(".sc.tmp")) return null
        return when {
            StoragePathRules.isWhatsAppPath(path) -> WatchKind.WHATSAPP
            mediaType == MEDIA_TYPE_VIDEO -> WatchKind.VIDEOS
            mediaType == MEDIA_TYPE_IMAGE -> WatchKind.PHOTOS
            else -> null
        }
    }
}
