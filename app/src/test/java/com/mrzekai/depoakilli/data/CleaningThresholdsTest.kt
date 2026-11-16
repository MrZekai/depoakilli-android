package com.mrzekai.depoakilli.data

import org.junit.Assert.assertTrue
import org.junit.Test

class CleaningThresholdsTest {
    @Test
    fun `thresholds are positive and consistent`() {
        with(CleaningThresholds) {
            listOf(
                GROUP_MIN_BYTES, PLAN_MIN_BYTES, LARGE_BYTES, LARGE_VIDEO_DAYS, SCREEN_RECORDING_DAYS,
                SCREENSHOT_DAYS, DOWNLOAD_DAYS, WHATSAPP_SENT_DAYS, WHATSAPP_VIDEO_BYTES, WHATSAPP_VOICE_DAYS,
                WHATSAPP_STATUS_DAYS, TEMP_DAYS, PARTIAL_DOWNLOAD_DAYS, UNUSED_APP_DAYS, SMART_VERIFY_MAX_BYTES,
                QUICK_FULL_HASH_BYTES, MIN_DUPLICATE_BYTES,
            ).forEach { assertTrue(it > 0L) }
            listOf(TRASH_RETENTION_DAYS, SMART_VERIFY_MAX_GROUPS, MAX_RESULT_ITEMS).forEach { assertTrue(it > 0) }
            assertTrue(GROUP_MIN_BYTES < PLAN_MIN_BYTES)
            assertTrue(WHATSAPP_VIDEO_BYTES < LARGE_BYTES)
            assertTrue(QUICK_FULL_HASH_BYTES < SMART_VERIFY_MAX_BYTES)
            assertTrue(MIN_DUPLICATE_BYTES < QUICK_FULL_HASH_BYTES)
            assertTrue(TEMP_DAYS < PARTIAL_DOWNLOAD_DAYS)
            assertTrue(LARGE_BYTES == 100_000_000L)
        }
    }
}
