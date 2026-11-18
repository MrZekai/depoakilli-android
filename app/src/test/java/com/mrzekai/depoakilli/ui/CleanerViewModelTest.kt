package com.mrzekai.depoakilli.ui

import com.mrzekai.depoakilli.data.AiCleaningEngine
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.CleanupGroup
import com.mrzekai.depoakilli.model.MemorySnapshot
import com.mrzekai.depoakilli.model.ScanSummary
import com.mrzekai.depoakilli.model.StorageReviewSummary
import com.mrzekai.depoakilli.model.StorageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the ViewModel's state transitions through [CleanerStateReducers] (T3.5). */
class CleanerViewModelTest {
    private val engine = AiCleaningEngine { 0L }
    private val mb = 1_000_000L

    @Test
    fun `cancelling a scan clears every running flag`() {
        val running = CleanerUiState(
            scanning = true,
            dashboardRefreshing = true,
            scanHashing = true,
            message = "x",
        )
        val cancelled = CleanerStateReducers.scanCancelled(running)
        assertFalse(cancelled.scanning)
        assertFalse(cancelled.dashboardRefreshing)
        assertFalse(cancelled.scanHashing)
        assertNull(cancelled.message)
    }

    @Test
    fun `failed storage review stops the spinner and shows the message`() {
        val loading = CleanerUiState(storageReview = StorageReviewSummary(loading = true))
        val failed = CleanerStateReducers.storageReviewFailed(loading, "failed")
        assertFalse(failed.storageReview.loading)
        assertEquals("failed", failed.message)
    }

    @Test
    fun `finishing a cleanup lowers counters by the deleted items only`() {
        val temp = item("t", CleanupGroup.TEMP_FILES, 100 * mb)
        val video = item("v", CleanupGroup.LARGE_VIDEO, 300 * mb)
        val kept = item("k", CleanupGroup.LARGE_VIDEO, 50 * mb)
        val items = listOf(temp, video, kept)
        assertTrue(temp.assessment.recommended)
        assertFalse(video.assessment.recommended)
        val before = CleanerUiState(
            summary = ScanSummary(items = items),
            planSummary = ScanSummary(items = items),
            dashboardCleanableBytes = 150 * mb,
            dashboardReviewBytes = 350 * mb,
            dashboardCategoryBytes = mapOf(
                temp.assessment.category to 100 * mb,
                video.assessment.category to 350 * mb,
            ),
            cleanupInProgress = true,
        )
        val result = CleanupResult(deletedBytes = 400 * mb, deletedCount = 2, failedCount = 0)
        val after = CleanerStateReducers.afterCleanup(
            state = before,
            items = items,
            deletedIds = setOf("t", "v"),
            storageAfter = StorageSnapshot(totalBytes = 1000 * mb, availableBytes = 500 * mb),
            memoryAfter = MemorySnapshot(),
            ownCacheAfter = 7L,
            result = result,
        )
        assertEquals(listOf("k"), after.summary.items.map(CleanableItem::id))
        assertEquals(listOf("k"), after.planSummary!!.items.map(CleanableItem::id))
        assertEquals(50 * mb, after.dashboardCleanableBytes)
        assertEquals(50 * mb, after.dashboardReviewBytes)
        assertEquals(0L, after.dashboardCategoryBytes[temp.assessment.category])
        assertEquals(50 * mb, after.dashboardCategoryBytes[video.assessment.category])
        assertFalse(after.cleanupInProgress)
        assertEquals(result, after.cleanupResult)
        assertEquals(500 * mb, after.storage.availableBytes)
        assertEquals(7L, after.ownCacheBytes)
    }

    @Test
    fun `dashboard counters never go below zero`() {
        val temp = item("t", CleanupGroup.TEMP_FILES, 100 * mb)
        val after = CleanerStateReducers.afterCleanup(
            state = CleanerUiState(dashboardCleanableBytes = 10 * mb),
            items = listOf(temp),
            deletedIds = setOf("t"),
            storageAfter = StorageSnapshot(),
            memoryAfter = MemorySnapshot(),
            ownCacheAfter = 0L,
            result = CleanupResult(deletedBytes = 100 * mb, deletedCount = 1, failedCount = 0),
        )
        assertEquals(0L, after.dashboardCleanableBytes)
    }

    private fun item(id: String, group: CleanupGroup, size: Long): CleanableItem {
        val assessment = engine.assessment(group, 0)
        return CleanableItem(
            id = id,
            uri = "file:///x/$id",
            name = id,
            sizeBytes = size,
            mimeType = "video/mp4",
            modifiedAtMillis = 0L,
            relativePath = "Movies",
            assessment = assessment,
            selected = assessment.recommended,
        )
    }
}
