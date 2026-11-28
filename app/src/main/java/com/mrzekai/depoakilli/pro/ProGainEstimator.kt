package com.mrzekai.depoakilli.pro

/**
 * How much more space Pro would clear on this phone (Faz 2.1). Only measured
 * scan data goes in; a feature the app does not have yet never adds a byte.
 * Pure; unit-tested.
 */
object ProGainEstimator {
    /** Below this, the app does not sell Pro (honest paywall, Y3). Decimal GB, like the app's sizes. */
    const val PRO_MIN_GAIN_BYTES = 2_000_000_000L

    const val COMPRESSION_MIN_VIDEO_BYTES = 100_000_000L

    /**
     * Faz 3A-2: video shrinking is in the app. Its row is the sum of the
     * per-video estimates of the real candidates (headers read, rules applied;
     * a video whose headers could not be read is not a candidate). Off, the row
     * and every text about it disappear.
     */
    const val VIDEO_COMPRESSION_AVAILABLE = true

    /**
     * Faz 3B-1: similar photos. Its row is the measured size of the frames the
     * last in-app look offered for the Trash (the best of each group stays).
     * Off, the row, the screens, the Watch alert and every text disappear.
     */
    const val SIMILAR_PHOTOS_AVAILABLE = true

    /**
     * Faz 3B-2: blurry/dark shots and closed eyes (inside similar groups), and
     * photo types (documents, memes). Offered one by one only; they never add
     * a gain row here (the user may keep most of them).
     */
    const val BAD_SHOTS_AVAILABLE = true
    const val PHOTO_TYPES_AVAILABLE = true

    enum class Kind {
        /** "My pick" cleaned with one tap (after the first, free cleanup). */
        ONE_TAP_PICK,
        /** Big videos shrunk without deleting (Faz 3.4). */
        VIDEO_COMPRESSION,
        /** Similar frames beyond the best of each group (Faz 3B-1). */
        SIMILAR_PHOTOS,
    }

    data class Row(val kind: Kind, val bytes: Long, val count: Int, val measured: Boolean)

    data class Estimate(val rows: List<Row>) {
        val totalBytes: Long get() = rows.sumOf(Row::bytes)
        val qualifies: Boolean get() = totalBytes >= PRO_MIN_GAIN_BYTES
        val hasEstimates: Boolean get() = rows.any { !it.measured }

        /**
         * Gain only Pro can give (e.g. video compression). One-tap "My pick"
         * is not in it: the same files can be cleared by hand without Pro.
         */
        val proOnlyBytes: Long get() = rows.filter { it.kind != Kind.ONE_TAP_PICK }.sumOf(Row::bytes)

        /** The before/after fill bars appear only for a gain Pro alone gives. */
        val showsFillBars: Boolean get() = proOnlyBytes > 0L

        /** Only the one-tap row: the headline says "Clear X with one tap". */
        val oneTapOnly: Boolean get() = rows.isNotEmpty() && rows.all { it.kind == Kind.ONE_TAP_PICK }
    }

    /**
     * @param suggestedBytes the "My pick" layer of the current scan.
     * @param firstCleanupUsed the free first "My pick" cleanup is spent; before
     *   that, one-tap cleanup is not a Pro gain (it is still free).
     * @param videoSavingBytes sum of the lower-bound estimates of the video
     *   shrinking candidates ([com.mrzekai.depoakilli.video.VideoRules]); shown as "about".
     * @param videoCount number of those candidates.
     * @param similarBytes measured size of the similar frames offered (Faz 3B-1).
     */
    fun estimate(
        suggestedBytes: Long,
        suggestedCount: Int,
        firstCleanupUsed: Boolean,
        videoSavingBytes: Long = 0L,
        videoCount: Int = 0,
        videoCompressionAvailable: Boolean = VIDEO_COMPRESSION_AVAILABLE,
        similarBytes: Long = 0L,
        similarCount: Int = 0,
        similarAvailable: Boolean = SIMILAR_PHOTOS_AVAILABLE,
    ): Estimate {
        val rows = mutableListOf<Row>()
        if (videoCompressionAvailable && videoSavingBytes > 0L && videoCount > 0) {
            rows += Row(Kind.VIDEO_COMPRESSION, videoSavingBytes, videoCount, measured = false)
        }
        if (similarAvailable && similarBytes > 0L && similarCount > 0) {
            rows += Row(Kind.SIMILAR_PHOTOS, similarBytes, similarCount, measured = true)
        }
        if (firstCleanupUsed && suggestedBytes > 0L) {
            rows += Row(Kind.ONE_TAP_PICK, suggestedBytes, suggestedCount, measured = true)
        }
        // Biggest measured gain first (paywall order).
        return Estimate(rows.sortedByDescending(Row::bytes))
    }
}
