package com.mrzekai.depoakilli.data

import com.mrzekai.depoakilli.model.AiAssessment
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.ScanSummary
import com.mrzekai.depoakilli.model.IndexedFile
import com.mrzekai.depoakilli.model.StorageFileType
import com.mrzekai.depoakilli.model.StorageReviewItem
import com.mrzekai.depoakilli.model.StorageReviewSummary
import com.mrzekai.depoakilli.model.AppCacheEntry
import com.mrzekai.depoakilli.model.AppCacheSnapshot
import com.mrzekai.depoakilli.model.WhatsAppLibrarySummary
import com.mrzekai.depoakilli.model.WhatsAppMediaCategory
import com.mrzekai.depoakilli.model.WhatsAppMediaItem
import java.util.Locale
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
    fun `byte formatter is stable under Turkish format locale`() {
        val previous = Locale.getDefault(Locale.Category.FORMAT)
        try {
            Locale.setDefault(Locale.Category.FORMAT, Locale.forLanguageTag("tr-TR"))

            assertEquals("1.0 KB", ByteFormatter.format(1_000))
            assertEquals("1.5 MB", ByteFormatter.format(1_500_000))
            assertEquals("2.0 GB", ByteFormatter.format(2_000_000_000))
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, previous)
        }
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



    @Test
    fun `storage review starts manual and totals only selected files`() {
        val review = StorageReviewSummary(
            type = StorageFileType.VIDEOS,
            items = listOf(
                StorageReviewItem(
                    file = IndexedFile(
                        uri = "file:///video/one.mp4",
                        name = "one.mp4",
                        sizeBytes = 10_000L,
                        mimeType = "video/mp4",
                        modifiedAtMillis = 1L,
                        relativePath = "Movies",
                    ),
                    selected = false,
                ),
                StorageReviewItem(
                    file = IndexedFile(
                        uri = "file:///video/two.mp4",
                        name = "two.mp4",
                        sizeBytes = 20_000L,
                        mimeType = "video/mp4",
                        modifiedAtMillis = 1L,
                        relativePath = "Movies",
                    ),
                    selected = true,
                ),
            ),
        )

        assertEquals(30_000L, review.totalBytes)
        assertEquals(20_000L, review.selectedBytes)
        assertEquals(1, review.selectedItems.size)
    }

    @Test
    fun `scan summary keeps safe selection separate from review candidates`() {
        val summary = ScanSummary(
            items = listOf(
                cleanableItem("safe", 32_000_000L, recommended = true, selected = true),
                cleanableItem("review", 5_168_000_000L, recommended = false, selected = false),
            ),
        )

        assertEquals(32_000_000L, summary.selectedBytes)
        assertEquals(32_000_000L, summary.safeSuggestedBytes)
        assertEquals(5_168_000_000L, summary.reviewBytes)
        assertEquals(5_200_000_000L, summary.totalSuggestedBytes)
    }

    private fun cleanableItem(
        id: String,
        size: Long,
        recommended: Boolean,
        selected: Boolean,
    ) = CleanableItem(
        id = id,
        uri = "file:///test/$id",
        name = "$id.bin",
        sizeBytes = size,
        mimeType = "application/octet-stream",
        modifiedAtMillis = 1L,
        relativePath = "Download/",
        assessment = AiAssessment(
            category = CleanCategory.JUNK,
            safetyScore = 90,
            reasonRes = 0,
            recommended = recommended,
        ),
        selected = selected,
    )

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
