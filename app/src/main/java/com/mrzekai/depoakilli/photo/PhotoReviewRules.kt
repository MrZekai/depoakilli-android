package com.mrzekai.depoakilli.photo

import com.mrzekai.depoakilli.data.CleaningThresholds

/**
 * Faz 3B-2. Bad shots are measured with classic image processing (no
 * model): sharpness and brightness. Photo types and closed eyes come from
 * bundled on-device models (ML Kit). Every result here is only offered one by
 * one (keep / Trash); nothing joins a one-tap cleanup. Pure; unit-tested.
 */
object PhotoReviewRules {
    enum class BadKind { BLURRY, DARK }

    enum class PhotoType {
        /** Receipts, papers, menus, whiteboards, slides, newspapers, posters. */
        DOCUMENT,

        /** Screenshots outside the Screenshots folder, comics and memes. */
        MEME,
    }

    /** Fresh shots are not judged: the user may still be editing them. */
    const val BAD_SHOT_MIN_AGE_MILLIS = 2L * 24L * 60L * 60L * 1000L

    /** Only photos of 30 days or more get a type (Faz 3B-2). */
    const val TYPE_MIN_AGE_MILLIS = 30L * 24L * 60L * 60L * 1000L

    /** ML Kit default-model label indexes (label map, verified in the PR report). */
    val DOCUMENT_LABELS = setOf(240 /* Receipt */, 273 /* Paper */, 135 /* Menu */, 160 /* Whiteboard */, 190 /* Presentation */, 263 /* Newspaper */, 93 /* Poster */)
    val MEME_LABELS = setOf(121 /* Screenshot */, 2 /* Comics */)

    /** Looks like an important document: a warning line, never a suggestion to delete. */
    val IMPORTANT_LABELS = setOf(267 /* Passport */, 234 /* Money */)

    fun badKind(sharpness: Double, luma: Double, takenMillis: Long, nowMillis: Long): BadKind? {
        if (nowMillis - takenMillis < BAD_SHOT_MIN_AGE_MILLIS) return null
        return when {
            sharpness in 0.0..CleaningThresholds.BLUR_VARIANCE_MAX -> BadKind.BLURRY
            luma in 0.0..CleaningThresholds.DARK_LUMA_MAX -> BadKind.DARK
            else -> null
        }
    }

    /** [labels] are (index, confidence) from the image labeler. */
    fun type(labels: List<Pair<Int, Float>>, takenMillis: Long, nowMillis: Long): PhotoType? {
        if (nowMillis - takenMillis < TYPE_MIN_AGE_MILLIS) return null
        val sure = labels.filter { it.second >= CleaningThresholds.PHOTO_TYPE_MIN_CONFIDENCE }.map { it.first }.toSet()
        return when {
            sure.any { it in DOCUMENT_LABELS } || sure.any { it in IMPORTANT_LABELS } -> PhotoType.DOCUMENT
            sure.any { it in MEME_LABELS } -> PhotoType.MEME
            else -> null
        }
    }

    fun important(labels: List<Pair<Int, Float>>): Boolean =
        labels.any { it.first in IMPORTANT_LABELS && it.second >= CleaningThresholds.PHOTO_TYPE_MIN_CONFIDENCE }

    /**
     * Eyes of a photo from the face model: the lowest eye-open probability of
     * all faces (one closed eye in the group photo is enough), null without a face.
     */
    fun eyesOpen(faces: List<Pair<Float?, Float?>>): Float? {
        val values = faces.flatMap { listOfNotNull(it.first, it.second) }
        return values.minOrNull()
    }

    fun eyesClosed(eyesOpen: Float?): Boolean = eyesOpen != null && eyesOpen < CleaningThresholds.EYES_OPEN_MIN

    /** Encoded in the feature cache: -1 unknown, 0 none, 1 document, 2 meme, +10 important. */
    fun encodeType(type: PhotoType?, important: Boolean): Int =
        (when (type) { null -> 0; PhotoType.DOCUMENT -> 1; PhotoType.MEME -> 2 }) + if (important) 10 else 0

    fun decodeType(code: Int): Pair<PhotoType?, Boolean>? = when {
        code < 0 -> null
        else -> (when (code % 10) { 1 -> PhotoType.DOCUMENT; 2 -> PhotoType.MEME; else -> null }) to (code >= 10)
    }
}
