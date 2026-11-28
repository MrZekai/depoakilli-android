package com.mrzekai.depoakilli.data

/**
 * Every cleanup threshold in one place. Byte values use base 1000 (MB = 1_000_000).
 * Nothing shown on screen is hard-coded from here: the UI always shows values
 * computed from the scan.
 */
object CleaningThresholds {
    private const val MB = 1_000_000L
    private const val GB = 1_000_000_000L

    const val GROUP_MIN_BYTES = 50L * MB
    const val PLAN_MIN_BYTES = 100L * MB
    const val LARGE_BYTES = 100L * MB
    const val LARGE_VIDEO_DAYS = 90L
    const val OLD_VIDEO_BYTES = 20L * MB
    const val OLD_VIDEO_DAYS = 180L
    const val SCREEN_RECORDING_DAYS = 30L
    const val SCREENSHOT_DAYS = 30L
    const val DOWNLOAD_DAYS = 90L
    const val OTHER_APP_MEDIA_BYTES = 5L * MB
    const val OTHER_APP_MEDIA_DAYS = 90L
    const val WHATSAPP_SENT_DAYS = 30L
    const val WHATSAPP_VIDEO_BYTES = 25L * MB
    const val WHATSAPP_VOICE_DAYS = 180L
    const val WHATSAPP_STATUS_DAYS = 2L
    const val WHATSAPP_OLD_VIDEO_DAYS = 90L
    const val WHATSAPP_OLD_IMAGE_DAYS = 180L
    const val WHATSAPP_OLD_DOCUMENT_DAYS = 90L
    const val WHATSAPP_OLD_AUDIO_DAYS = 180L
    const val TEMP_DAYS = 7L
    const val PARTIAL_DOWNLOAD_DAYS = 14L
    const val UNUSED_APP_DAYS = 60L
    const val TRASH_RETENTION_DAYS = 7
    const val SMART_VERIFY_MAX_BYTES = 1L * GB
    const val SMART_VERIFY_MAX_GROUPS = 20
    const val MAX_RESULT_ITEMS = 4000

    /** Files at or below this size are fully hashed during the quick scan. */
    const val QUICK_FULL_HASH_BYTES = 32L * MB

    /** Faz 3A-1 Storage Watch: WhatsApp grew this much in a week. */
    const val WATCH_WHATSAPP_GROWTH_BYTES = 1L * GB

    /** Faz 3A-1: new big-video candidates since the last notice (on with 3A-2). */
    const val WATCH_NEW_VIDEO_BYTES = 1L * GB
    const val WATCH_NEW_VIDEO_ENABLED = true

    /** Faz 3A-1: the weekly summary is not sent below this much growth. */
    const val WATCH_WEEKLY_MIN_BYTES = 500L * MB

    /**
     * Faz 3B-1 similar photos. Measured on the synthetic set (20 bursts, 20
     * blurry, 40 unrelated) with the bundled embedder: unrelated photos never
     * went above 0.57; at 0.82 18 of 20 bursts were grouped whole, none mixed.
     */
    const val SIMILAR_COSINE_MIN = 0.82
    const val DHASH_MAX_DISTANCE = 10

    /** Faz 3B-1 Watch: new deletable similar frames since the last notice. */
    const val WATCH_NEW_SIMILAR_COUNT = 20

    /**
     * Faz 3B-2 bad shots (classic image processing on a 256 px thumbnail).
     * Measured on the synthetic set: Laplacian variance below 40 caught 75 %
     * of the blurry photos with no false alarm among 60 sharp ones; mean
     * brightness below 30 caught 97 % of the dark ones with no false alarm.
     */
    const val BLUR_VARIANCE_MAX = 40.0
    const val DARK_LUMA_MAX = 30.0

    /** Faz 3B-2: a face's eyes count as open from this probability. */
    const val EYES_OPEN_MIN = 0.5f

    /** Minimum size considered by the duplicate finder. */
    const val MIN_DUPLICATE_BYTES = 32L * 1024L
}
