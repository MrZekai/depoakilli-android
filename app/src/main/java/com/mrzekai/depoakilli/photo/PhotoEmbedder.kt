package com.mrzekai.depoakilli.photo

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder

/**
 * Faz 3B-1: the on-device image embedder (MediaPipe, bundled
 * mobilenet_v3_small). Turns a thumbnail into an L2-normalised vector; the
 * photo never leaves the phone. Not thread-safe: one per scan.
 */
class PhotoEmbedder private constructor(private val embedder: ImageEmbedder) : AutoCloseable {
    fun embed(bitmap: Bitmap): FloatArray? = runCatching {
        val argb = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val result = embedder.embed(BitmapImageBuilder(argb).build())
        result.embeddingResult().embeddings().firstOrNull()?.floatEmbedding()
    }.getOrNull()

    override fun close() = runCatching { embedder.close() }.let { }

    companion object {
        const val MODEL_ASSET = "models/similar_embedder.tflite"

        /** Null if the model cannot load on this phone; the feature then finds nothing rather than guessing. */
        fun create(context: Context): PhotoEmbedder? = runCatching {
            val options = ImageEmbedder.ImageEmbedderOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
                .setRunningMode(RunningMode.IMAGE)
                .setL2Normalize(true)
                .setQuantize(false)
                .build()
            PhotoEmbedder(ImageEmbedder.createFromOptions(context.applicationContext, options))
        }.getOrNull()
    }
}
