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
 * Faz 3B-2: the two ML Kit models. Faces are only measured as an eye-open
 * probability (no recognition, no face data kept); labels as (index,
 * confidence). Everything runs on the phone; call off the main thread.
 * The models come with Google Play services (not inside the app, ~14 MB
 * smaller download). Until Play services has them, a call fails: the photo is
 * left unmeasured and tried again on a later run.
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
    private var facesFailed = false
    private var labelerFailed = false

    /**
     * Lowest eye-open probability over all faces; null without a face;
     * [FAILED] when the model could not run (then not tried again this run).
     */
    fun eyesOpen(bitmap: Bitmap): Float? {
        if (facesFailed) return FAILED
        return runCatching {
            facesUsed = true
            val found = Tasks.await(faces.process(InputImage.fromBitmap(bitmap, 0)))
            PhotoReviewRules.eyesOpen(found.map { it.leftEyeOpenProbability to it.rightEyeOpenProbability })
        }.getOrElse {
            facesFailed = true
            FAILED
        }
    }

    /** (label index, confidence) pairs, or null when the model could not run. */
    fun labels(bitmap: Bitmap): List<Pair<Int, Float>>? {
        if (labelerFailed) return null
        return runCatching {
            labelerUsed = true
            Tasks.await(labeler.process(InputImage.fromBitmap(bitmap, 0))).map { it.index to it.confidence }
        }.getOrElse {
            labelerFailed = true
            null
        }
    }

    companion object {
        /** eyesOpen(): the model could not run. */
        const val FAILED = Float.NaN
    }

    override fun close() {
        if (facesUsed) runCatching { faces.close() }
        if (labelerUsed) runCatching { labeler.close() }
    }
}
