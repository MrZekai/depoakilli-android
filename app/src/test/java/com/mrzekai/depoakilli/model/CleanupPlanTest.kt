package com.mrzekai.depoakilli.model

import com.mrzekai.depoakilli.data.AiCleaningEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanupPlanTest {
    private val engine = AiCleaningEngine { 0L }
    private val mb = 1_000_000L
    private var next = 0

    @Test
    fun `groups are sorted by bytes and items inside by size`() {
        val plan = buildPlan(
            listOf(
                item(CleanupGroup.TEMP_FILES, 60 * mb),
                item(CleanupGroup.THUMBNAILS, 40 * mb),
                item(CleanupGroup.THUMBNAILS, 50 * mb),
                item(CleanupGroup.LARGE_VIDEO, 300 * mb),
                item(CleanupGroup.SCREENSHOT, 70 * mb),
            ),
        )
        assertEquals(listOf(CleanupGroup.THUMBNAILS, CleanupGroup.TEMP_FILES), plan.included.map { it.group })
        assertEquals(listOf(50 * mb, 40 * mb), plan.included.first().items.map { it.sizeBytes })
        assertEquals(listOf(CleanupGroup.LARGE_VIDEO, CleanupGroup.SCREENSHOT), plan.review.map { it.group })
        assertEquals(150 * mb, plan.recommendedBytes)
        assertEquals(2, plan.recommendedGroupCount)
    }

    @Test
    fun `small auto groups merge and small review groups merge into their own row`() {
        val plan = buildPlan(
            listOf(
                item(CleanupGroup.TEMP_FILES, 10 * mb),
                item(CleanupGroup.THUMBNAILS, 20 * mb),
                item(CleanupGroup.SCREENSHOT, 10 * mb),
            ),
        )
        assertTrue(plan.included.isEmpty())
        assertTrue(plan.review.isEmpty())
        val small = plan.smallItems!!
        assertNull(small.group)
        assertEquals(30 * mb, small.bytes)
        assertEquals(listOf(20 * mb, 10 * mb), small.items.map { it.sizeBytes })
        val smallReview = plan.smallReview!!
        assertTrue(smallReview.smallReview)
        assertFalse(smallReview.groupSelectable)
        assertEquals(10 * mb, smallReview.bytes)
    }

    @Test
    fun `card totals equal the rows and app cache is not part of them`() {
        val plan = buildPlan(
            listOf(
                item(CleanupGroup.TEMP_FILES, 60 * mb),
                item(CleanupGroup.THUMBNAILS, 10 * mb),
                item(CleanupGroup.LARGE_VIDEO, 300 * mb),
                item(CleanupGroup.SCREENSHOT, 20 * mb),
                item(CleanupGroup.WHATSAPP_OLD_IMAGE, 15 * mb),
            ),
        )
        val rows = plan.included + listOfNotNull(plan.smallItems, plan.smallReview) + plan.review
        assertEquals(rows.sumOf(PlanGroup::bytes), plan.potentialBytes)
        assertEquals(70 * mb, plan.recommendedBytes)
        assertEquals(335 * mb, plan.reviewBytes)
        assertEquals(405 * mb, plan.potentialBytes)
        assertEquals(CleanupGroup.LARGE_VIDEO, plan.firstReviewRow?.group)
    }

    @Test
    fun `a big review part keeps the card even when the auto part is small`() {
        val plan = buildPlan(listOf(item(CleanupGroup.TEMP_FILES, 5 * mb), item(CleanupGroup.OLD_VIDEO, 400 * mb)))
        assertFalse(plan.isAlreadyTidy)
        assertEquals(0L, plan.selectedBytes - 5 * mb)
    }

    @Test
    fun `no new group is preselected`() {
        assertEquals(
            setOf(
                CleanupGroup.APK_INSTALLED,
                CleanupGroup.DUPLICATE_SAFE,
                CleanupGroup.THUMBNAILS,
                CleanupGroup.TEMP_FILES,
                CleanupGroup.PARTIAL_DOWNLOADS,
            ),
            CleanupGroup.entries.filter { it.auto }.toSet(),
        )
    }

    @Test
    fun `already tidy below the plan minimum`() {
        assertTrue(buildPlan(listOf(item(CleanupGroup.TEMP_FILES, 99 * mb))).isAlreadyTidy)
        assertTrue(buildPlan(listOf(item(CleanupGroup.TEMP_FILES, 49 * mb), item(CleanupGroup.SCREENSHOT, 50 * mb))).isAlreadyTidy)
        assertFalse(buildPlan(listOf(item(CleanupGroup.TEMP_FILES, 100 * mb))).isAlreadyTidy)
        assertTrue(buildPlan(emptyList()).isAlreadyTidy)
    }

    @Test
    fun `selected totals follow the selection`() {
        val review = item(CleanupGroup.LARGE_VIDEO, 300 * mb)
        val plan = buildPlan(listOf(item(CleanupGroup.TEMP_FILES, 100 * mb), review))
        assertEquals(100 * mb, plan.selectedBytes)
        assertEquals(1, plan.selectedGroupCount)
        val withReview = buildPlan(listOf(item(CleanupGroup.TEMP_FILES, 100 * mb), review.copy(selected = true)))
        assertEquals(400 * mb, withReview.selectedBytes)
        assertEquals(2, withReview.selectedGroupCount)
    }

    @Test
    fun `truncated flag is kept`() {
        assertTrue(buildPlan(emptyList(), truncated = true).truncated)
        assertTrue(ScanSummary(truncated = true).plan.truncated)
    }

    private fun item(group: CleanupGroup, size: Long): CleanableItem {
        val assessment = engine.assessment(group, 0)
        val id = "item-${next++}"
        return CleanableItem(
            id = id,
            uri = "file:///x/$id",
            name = id,
            sizeBytes = size,
            mimeType = "application/octet-stream",
            modifiedAtMillis = 0L,
            relativePath = "Download",
            assessment = assessment,
            selected = assessment.recommended,
        )
    }
}
