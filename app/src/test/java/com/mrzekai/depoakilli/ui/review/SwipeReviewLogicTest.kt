package com.mrzekai.depoakilli.ui.review

import com.mrzekai.depoakilli.data.AiCleaningEngine
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.CleanupGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeReviewLogicTest {
    private val engine = AiCleaningEngine { 0L }

    @Test
    fun `twelve files need twelve decisions and no break card`() {
        var review = SwipeReviewState.start((1..12).map { item("f$it", it * 10L) })
        assertEquals("f12", review.currentId)
        var decisions = 0
        while (!review.finished) {
            // Faz 2: the ad-era break card is gone; every step shows a file.
            assertTrue(review.currentId != null)
            review = review.mark(delete = decisions % 3 == 0)
            decisions++
        }
        assertEquals(12, decisions)
        assertNull(review.currentId)
    }

    @Test
    fun `mark keep and undo update totals`() {
        var review = SwipeReviewState.start(listOf(item("a", 300), item("b", 200), item("c", 100)))
        review = review.mark(true).mark(false).mark(true)
        assertEquals(setOf("a", "c"), review.markedIds)
        assertEquals(400L, review.markedBytes)
        review = review.undo()
        assertEquals(setOf("a"), review.markedIds)
        assertEquals("c", review.currentId)
        assertEquals(300L, review.markedBytes)
    }

    @Test
    fun `undo after the eighth decision shows the eighth file again`() {
        var review = SwipeReviewState.start((1..10).map { item("f$it", it.toLong()) })
        repeat(8) { review = review.mark(false) }
        assertEquals("f2", review.currentId)
        review = review.undo()
        assertEquals("f3", review.currentId)
        assertEquals(7, review.reviewedCount)
    }

    @Test
    fun `empty group is finished with nothing marked`() {
        val review = SwipeReviewState.start(emptyList())
        assertTrue(review.finished)
        assertNull(review.currentId)
        assertEquals(review, review.mark(true))
        assertEquals(0L, review.markedBytes)
    }

    private fun item(id: String, size: Long): CleanableItem {
        val assessment = engine.assessment(CleanupGroup.LARGE_VIDEO, 0)
        return CleanableItem(
            id = id,
            uri = "file:///x/$id",
            name = id,
            sizeBytes = size,
            mimeType = "video/mp4",
            modifiedAtMillis = 0L,
            relativePath = "Movies",
            assessment = assessment,
            selected = false,
        )
    }
}
