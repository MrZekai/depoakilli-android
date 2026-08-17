package com.mrzekai.depoakilli.data

import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.AppCacheEntry
import com.mrzekai.depoakilli.model.AppCacheSnapshot
import com.mrzekai.depoakilli.model.WhatsAppLibrarySummary
import com.mrzekai.depoakilli.model.WhatsAppMediaCategory
import com.mrzekai.depoakilli.model.WhatsAppMediaItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsTest {
    @Test
    fun `byte formatter uses readable binary units`() {
        assertEquals("0 B", ByteFormatter.format(0))
        assertEquals("1.0 KB", ByteFormatter.format(1_000))
        assertEquals("1.5 MB", ByteFormatter.format(1_500_000))
        assertEquals("2.0 GB", ByteFormatter.format(2_000_000_000))
    }

    @Test
    fun `app cache snapshot sums real per-app cache bytes`() {
        val snapshot = AppCacheSnapshot(
            accessGranted = true,
            entries = listOf(
                AppCacheEntry("one.example", "One", 1_500L),
                AppCacheEntry("two.example", "Two", 2_500L),
            ),
            scannedAppCount = 2,
            reportedOtherAppsCacheBytes = 4_500L,
        )

        assertEquals(4_500L, snapshot.totalCacheBytes)
    }

    @Test
    fun `whatsapp total and selected bytes remain separate`() {
        val summary = WhatsAppLibrarySummary(
            items = listOf(
                whatsAppItem("one", 1_000L, selected = true),
                whatsAppItem("two", 4_000L, selected = false),
            ),
            scannedFileCount = 2,
        )

        assertEquals(5_000L, summary.totalBytes)
        assertEquals(1_000L, summary.selectedBytes)
        assertEquals(1, summary.selectedItems.size)
    }

    private fun whatsAppItem(id: String, size: Long, selected: Boolean) = WhatsAppMediaItem(
        id = id,
        uri = "content://test/$id",
        name = "$id.jpg",
        sizeBytes = size,
        mimeType = "image/jpeg",
        modifiedAtMillis = 1L,
        relativePath = "WhatsApp Images/$id.jpg",
        category = WhatsAppMediaCategory.IMAGES,
        selected = selected,
    )
}
