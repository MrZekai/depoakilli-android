package com.mrzekai.depoakilli.pro

/**
 * How much more space Pro would clear on this phone (Faz 2.1). Only measured
 * scan data goes in; a feature the app does not have yet never adds a byte.
 * Pure; unit-tested.
 */
object ProGainEstimator {
    /** Below this, the app does not sell Pro (honest paywall, Y3). Decimal GB, like the app's sizes. */
    const val PRO_MIN_GAIN_BYTES = 2_000_000_000L

    /** Lower-bound saving of compressing a big video (Faz 3.4). */
    const val COMPRESSION_FLOOR_RATIO = 0.30
    const val COMPRESSION_MIN_VIDEO_BYTES = 100_000_000L

    /**
     * Video compression arrives in Faz 3.4. Until then its estimate is computed
     * only in tests and never counts (2.2b: no feature that is not in the app).
     */
    const val VIDEO_COMPRESSION_AVAILABLE = false

    enum class Kind {
        /** "My pick" cleaned with one tap (after the first, free cleanup). */
        ONE_TAP_PICK,
        /** Big videos shrunk without deleting (Faz 3.4). */
        VIDEO_COMPRESSION,
    }

    data class Row(val kind: Kind, val bytes: Long, val count: Int, val measured: Boolean)

    data class Estimate(val rows: List<Row>) {
        val totalBytes: Long get() = rows.sumOf(Row::bytes)
        val qualifies: Boolean get() = totalBytes >= PRO_MIN_GAIN_BYTES
        val hasEstimates: Boolean get() = rows.any { !it.measured }
    }

    /**
     * @param suggestedBytes the "My pick" layer of the current scan.
     * @param firstCleanupUsed the free first "My pick" cleanup is spent; before
     *   that, one-tap cleanup is not a Pro gain (it is still free).
     * @param videoSizes sizes of the videos found by the scan.
     */
    fun estimate(
        suggestedBytes: Long,
        suggestedCount: Int,
        firstCleanupUsed: Boolean,
        videoSizes: List<Long> = emptyList(),
        videoCompressionAvailable: Boolean = VIDEO_COMPRESSION_AVAILABLE,
    ): Estimate {
        val rows = mutableListOf<Row>()
        if (firstCleanupUsed && suggestedBytes > 0L) {
            rows += Row(Kind.ONE_TAP_PICK, suggestedBytes, suggestedCount, measured = true)
        }
        if (videoCompressionAvailable) {
            val big = videoSizes.filter { it >= COMPRESSION_MIN_VIDEO_BYTES }
            val floor = (big.sum() * COMPRESSION_FLOOR_RATIO).toLong()
            if (floor > 0L) rows += Row(Kind.VIDEO_COMPRESSION, floor, big.size, measured = false)
        }
        return Estimate(rows)
    }
}
