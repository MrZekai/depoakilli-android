package com.mrzekai.depoakilli.ui.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One thumbnail cache for every screen. The cache is sized in bytes, so a
 * few large bitmaps cannot push memory up the way a count-based cache can,
 * and a thumbnail decoded once is not decoded again while scrolling.
 */
internal object MediaThumbnails {
    private const val THUMB_WIDTH = 420
    private const val THUMB_HEIGHT = 320
    private const val THUMB_MAX_SIDE = 520
    const val DISPLAY_MAX_SIDE = 1600

    private val cacheBytes: Int = (Runtime.getRuntime().maxMemory() / 16)
        .coerceIn(4L * 1024 * 1024, 32L * 1024 * 1024)
        .toInt()

    private val cache = object : LruCache<String, ImageBitmap>(cacheBytes) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = (value.width * value.height * 4).coerceAtLeast(1)
    }

    fun cached(uri: String): ImageBitmap? = synchronized(cache) { cache.get(uri) }

    fun release() {
        synchronized(cache) { cache.evictAll() }
    }

    /** Small, cached thumbnail for grids and lists. */
    suspend fun thumbnail(context: Context, uriString: String, mimeType: String): ImageBitmap? {
        cached(uriString)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriString)
                val mime = mimeType.lowercase()
                val loaded = if (uri.scheme == "content") {
                    context.contentResolver.loadThumbnail(uri, Size(THUMB_WIDTH, THUMB_HEIGHT), null)
                } else {
                    val file = File(uri.path ?: uriString)
                    when {
                        !file.isFile -> null
                        mime.startsWith("video/") -> ThumbnailUtils.createVideoThumbnail(file, Size(THUMB_WIDTH, THUMB_HEIGHT), null)
                        mime.startsWith("image/") -> decodeSampled(file, THUMB_MAX_SIDE)
                        else -> null
                    }
                }
                loaded?.asImageBitmap()
            }.getOrNull()
        }
        if (bitmap != null) synchronized(cache) { cache.put(uriString, bitmap) }
        return bitmap
    }

    /** Large, uncached image for the full-screen preview. */
    suspend fun display(context: Context, uriString: String, maxSide: Int = DISPLAY_MAX_SIDE): ImageBitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriString)
                if (uri.scheme == "content") {
                    context.contentResolver.loadThumbnail(uri, Size(maxSide, maxSide), null).asImageBitmap()
                } else {
                    val file = File(uri.path ?: uriString)
                    if (file.isFile) decodeSampled(file, maxSide)?.asImageBitmap() else null
                }
            }.getOrNull()
        }

    /** Decodes [file] so that its longest side is at most [maxSide] pixels. */
    fun decodeSampled(file: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = thumbnailSampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        val decoded = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val longest = maxOf(decoded.width, decoded.height)
        if (longest <= maxSide) return decoded
        val scale = maxSide.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }
}

/** Largest power-of-two sample that keeps the decoded image at least [maxSide] long. */
internal fun thumbnailSampleSize(width: Int, height: Int, maxSide: Int): Int {
    var sample = 1
    val largest = maxOf(width, height)
    while (largest / (sample * 2) >= maxSide) sample *= 2
    return sample
}

/** Thumbnail state that starts from the cache, so scrolling back shows it at once. */
@Composable
internal fun rememberThumbnail(uri: String, mimeType: String): State<ImageBitmap?> {
    val context = LocalContext.current
    return produceState(initialValue = MediaThumbnails.cached(uri), uri, mimeType) {
        if (value == null) value = MediaThumbnails.thumbnail(context, uri, mimeType)
    }
}
