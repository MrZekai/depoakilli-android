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
    const val SCREEN_RECORDING_DAYS = 30L
    const val SCREENSHOT_DAYS = 30L
    const val DOWNLOAD_DAYS = 90L
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

    /** Minimum size considered by the duplicate finder. */
    const val MIN_DUPLICATE_BYTES = 32L * 1024L
}
