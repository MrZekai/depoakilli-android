package com.mrzekai.depoakilli.ui

import com.mrzekai.depoakilli.data.AiCleaningEngine
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.CleanupGroup
import com.mrzekai.depoakilli.model.buildPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanerViewModelPlanTest {
    private val engine = AiCleaningEngine { 0L }
    private val mb = 1_000_000L

    @Test
    fun `selecting and clearing a group updates plan totals`() {
        var items = listOf(
            item("t", CleanupGroup.TEMP_FILES, 100 * mb),
            item("v1", CleanupGroup.LARGE_VIDEO, 200 * mb),
            item("v2", CleanupGroup.LARGE_VIDEO, 100 * mb),
        )
        assertEquals(100 * mb, buildPlan(items).selectedBytes)

        items = PlanSelection.toggleGroup(items, CleanupGroup.LARGE_VIDEO)
        assertEquals(400 * mb, buildPlan(items).selectedBytes)
        assertEquals(2, buildPlan(items).selectedGroupCount)

        items = PlanSelection.setGroupSelected(items, CleanupGroup.TEMP_FILES, false)
        assertEquals(300 * mb, buildPlan(items).selectedBytes)

        items = PlanSelection.toggleGroup(items, CleanupGroup.LARGE_VIDEO)
        assertEquals(0L, buildPlan(items).selectedBytes)
    }

    @Test
    fun `groups that are not group selectable cannot be selected as a whole`() {
        val items = listOf(
            item("u", CleanupGroup.DUPLICATE_UNVERIFIED, 100 * mb),
            item("d", CleanupGroup.DOWNLOAD_DOCUMENT, 100 * mb),
        )
        val afterUnverified = PlanSelection.setGroupSelected(items, CleanupGroup.DUPLICATE_UNVERIFIED, true)
        val afterDocuments = PlanSelection.toggleGroup(afterUnverified, CleanupGroup.DOWNLOAD_DOCUMENT)
        assertTrue(afterDocuments.none(CleanableItem::selected))
    }

    @Test
    fun `verification replaces or removes a look alike`() {
        val items = listOf(item("u", CleanupGroup.DUPLICATE_UNVERIFIED, 100 * mb))
        val verified = items.single().copy(assessment = engine.duplicateAssessment(false))
        assertEquals(CleanupGroup.DUPLICATE_PERSONAL, PlanSelection.applyVerification(items, "u", verified).single().assessment.group)
        assertFalse(PlanSelection.applyVerification(items, "u", verified).single().selected)
        assertTrue(PlanSelection.applyVerification(items, "u", null).isEmpty())
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
