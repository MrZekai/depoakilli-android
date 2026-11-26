package com.mrzekai.depoakilli.pro

import android.content.Context
import android.provider.MediaStore

/** Average size of the photos on this phone, from MediaStore (Faz 2.2b "about N photos"). */
object PhotoStats {
    /** Null when there are no photos or MediaStore cannot be read. Run off the main thread. */
    fun averagePhotoBytes(context: Context): Long? = runCatching {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            val column = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            var total = 0L
            var count = 0L
            while (cursor.moveToNext()) {
                val size = cursor.getLong(column)
                if (size > 0L) {
                    total += size
                    count++
                }
            }
            if (count == 0L) null else total / count
        }
    }.getOrNull()
}
