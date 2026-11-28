package com.mrzekai.depoakilli.photo

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions

/**
 * Faz 3B-2: the two bundled ML Kit models. Faces are only measured as an
 * eye-open probability (no recognition, no face data kept); labels as
 * (index, confidence). Everything runs on the phone; call off the main thread.
 */
class PhotoModels : AutoCloseable {
    private val faces: FaceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.12f)
                .build(),
        )
    }
    private val labeler: ImageLabeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.5f).build())
    }
    private var facesUsed = false
    private var labelerUsed = false

    /** Lowest eye-open probability over all faces; null without a face or on failure. */
    fun eyesOpen(bitmap: Bitmap): Float? = runCatching {
        facesUsed = true
        val found = Tasks.await(faces.process(InputImage.fromBitmap(bitmap, 0)))
        PhotoReviewRules.eyesOpen(found.map { it.leftEyeOpenProbability to it.rightEyeOpenProbability })
    }.getOrNull()

    /** (label index, confidence) pairs, or null on failure. */
    fun labels(bitmap: Bitmap): List<Pair<Int, Float>>? = runCatching {
        labelerUsed = true
        Tasks.await(labeler.process(InputImage.fromBitmap(bitmap, 0))).map { it.index to it.confidence }
    }.getOrNull()

    override fun close() {
        if (facesUsed) runCatching { faces.close() }
        if (labelerUsed) runCatching { labeler.close() }
    }
}
