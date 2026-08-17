package com.mrzekai.depoakilli.data

import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.IndexedFile
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCleaningEngineTest {
    private val now = 2_000_000_000_000L
    private val engine = AiCleaningEngine { now }

    @Test
    fun `old apk is a high confidence recommendation`() {
        val result = engine.assess(file("installer.apk", "application/vnd.android.package-archive", 45))

        assertEquals(CleanCategory.APK_PACKAGE, result?.category)
        assertTrue(requireNotNull(result).recommended)
        assertTrue(result.safetyScore >= 90)
    }

    @Test
    fun `fresh apk is visible but not preselected`() {
        val result = engine.assess(file("installer.apk", "application/vnd.android.package-archive", 1))

        assertEquals(CleanCategory.APK_PACKAGE, result?.category)
        assertFalse(requireNotNull(result).recommended)
    }

    @Test
    fun `old screenshot is review only`() {
        val result = engine.assess(
            file("Screenshot_2026.png", "image/png", 40, "Pictures/Screenshots/"),
        )

        assertEquals(CleanCategory.SCREENSHOT, result?.category)
        assertFalse(requireNotNull(result).recommended)
    }

    @Test
    fun `recent camera photo is protected from suggestions`() {
        val result = engine.assess(file("IMG_1001.jpg", "image/jpeg", 2, "DCIM/Camera/"))

        assertNull(result)
    }

    @Test
    fun `large old video is review only`() {
        val result = engine.assess(
            file("clip.mp4", "video/mp4", 75, "Movies/", 300L * 1024L * 1024L),
        )

        assertEquals(CleanCategory.LARGE_FILE, result?.category)
        assertFalse(requireNotNull(result).recommended)
    }


    @Test
    fun `temporary artifact is high confidence junk`() {
        val result = engine.assess(
            file("partial.crdownload", "application/octet-stream", 5, "Download/"),
        )

        assertEquals(CleanCategory.JUNK, result?.category)
        assertTrue(requireNotNull(result).recommended)
    }

    @Test
    fun `large archive is discovered as a large file`() {
        val result = engine.assess(
            file("backup.zip", "application/zip", 10, "Backups/", 600L * 1024L * 1024L),
        )

        assertEquals(CleanCategory.LARGE_FILE, result?.category)
        assertFalse(requireNotNull(result).recommended)
    }

    @Test
    fun `whatsapp status is a safe temporary recommendation`() {
        val result = engine.assess(
            file(
                "status.jpg",
                "image/jpeg",
                1,
                "WhatsApp/Media/.Statuses/",
            ),
        )

        assertEquals(CleanCategory.WHATSAPP_MEDIA, result?.category)
        assertTrue(requireNotNull(result).recommended)
    }

    @Test
    fun `old whatsapp sent media is visible but not preselected`() {
        val result = engine.assess(
            file(
                "sent-video.mp4",
                "video/mp4",
                45,
                "WhatsApp/Media/WhatsApp Video/Sent/",
            ),
        )

        assertEquals(CleanCategory.WHATSAPP_MEDIA, result?.category)
        assertFalse(requireNotNull(result).recommended)
    }

    private fun file(
        name: String,
        mime: String,
        ageDays: Long,
        path: String = "Download/",
        size: Long = 2L * 1024L * 1024L,
    ) = IndexedFile(
        uri = "content://test/$name",
        name = name,
        sizeBytes = size,
        mimeType = mime,
        modifiedAtMillis = now - TimeUnit.DAYS.toMillis(ageDays),
        relativePath = path,
    )
}
