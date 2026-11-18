package com.mrzekai.depoakilli.ui.review

import com.mrzekai.depoakilli.data.AiCleaningEngine
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.CleanupGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeReviewLogicTest {
    private val engine = AiCleaningEngine { 0L }

    @Test
    fun `twelve files need twelve decisions and one break`() {
        var review = SwipeReviewState.start((1..12).map { item("f$it", it * 10L) })
        assertEquals("f12", review.currentId)
        var breaks = 0
        var decisions = 0
        while (!review.finished) {
            if (review.breakPending) {
                breaks++
                assertNull(review.currentId)
                review = review.continueAfterBreak()
            } else {
                review = review.mark(delete = decisions % 3 == 0)
                decisions++
            }
        }
        assertEquals(12, decisions)
        assertEquals(1, breaks)
        assertEquals(listOf(8), SwipeReviewState.breakPositions(12))
        assertEquals(listOf(8, 16), SwipeReviewState.breakPositions(17))
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
    fun `undo at a break removes the break and undo after it does not repeat it`() {
        var review = SwipeReviewState.start((1..10).map { item("f$it", it.toLong()) })
        repeat(8) { review = review.mark(false) }
        assertTrue(review.breakPending)
        val undone = review.undo()
        assertFalse(undone.breakPending)
        review = review.continueAfterBreak().mark(true).undo()
        assertFalse(review.breakPending)
        assertEquals(8, review.reviewedCount)
    }

    @Test
    fun `empty group is finished with nothing marked`() {
        val review = SwipeReviewState.start(emptyList())
        assertTrue(review.finished)
        assertNull(review.currentId)
        assertFalse(review.breakPending)
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
