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
    fun `bulk selection skips documents and look alikes but explicit selection does not`() {
        val items = listOf(
            item("doc", CleanupGroup.DOWNLOAD_DOCUMENT, 10 * mb),
            item("u", CleanupGroup.DUPLICATE_UNVERIFIED, 10 * mb),
            item("f", CleanupGroup.DOWNLOAD_FILE, 10 * mb),
        )
        val ids = setOf("doc", "u", "f")
        val bulk = PlanSelection.setItemsSelected(items, ids, selected = true, bulk = true)
        assertEquals(setOf("f"), bulk.filter(CleanableItem::selected).map(CleanableItem::id).toSet())

        val explicit = PlanSelection.setItemsSelected(items, setOf("doc", "u"), selected = true, bulk = false)
        assertEquals(setOf("doc", "u"), explicit.filter(CleanableItem::selected).map(CleanableItem::id).toSet())

        val cleared = PlanSelection.setItemsSelected(explicit, ids, selected = false, bulk = true)
        assertTrue(cleared.none(CleanableItem::selected))
    }

    @Test
    fun `verification replaces or removes a look alike`() {
        val items = listOf(item("u", CleanupGroup.DUPLICATE_UNVERIFIED, 100 * mb))
        val verified = items.single().copy(assessment = engine.duplicateAssessment(false))
        assertEquals(CleanupGroup.DUPLICATE_PERSONAL, PlanSelection.applyVerification(items, "u", verified).single().assessment.group)
        assertFalse(PlanSelection.applyVerification(items, "u", verified).single().selected)
        assertTrue(PlanSelection.applyVerification(items, "u", null).isEmpty())
    }

    @Test
    fun `deep check candidates and verification results`() {
        val big = 300 * mb
        val a = item("a", CleanupGroup.DUPLICATE_UNVERIFIED, big).copy(protectedDuplicateUri = "file:///keep1")
        val b = item("b", CleanupGroup.DUPLICATE_UNVERIFIED, big).copy(protectedDuplicateUri = "file:///keep1")
        val small = item("s", CleanupGroup.DUPLICATE_UNVERIFIED, 10 * mb).copy(protectedDuplicateUri = "file:///keep2")
        assertEquals(1 to 2 * big, PlanSelection.deepCheckCandidates(listOf(a, b, small)))
        val verified = a.copy(assessment = engine.duplicateAssessment(true))
        val after = PlanSelection.applyVerifications(listOf(a, b, small), mapOf("a" to verified, "b" to null))
        assertEquals(listOf("a", "s"), after.map(CleanableItem::id))
        assertEquals(CleanupGroup.DUPLICATE_SAFE, after.first().assessment.group)
    }

    @Test
    fun `app open starts a quick scan only when needed`() {
        val hour = 3_600_000L
        val now = 100 * hour
        assertFalse(AutoScanPolicy.shouldScan(hasAccess = true, scanning = false, hasPlan = true, planScannedAtElapsed = now - hour, nowElapsed = now))
        assertTrue(AutoScanPolicy.shouldScan(hasAccess = true, scanning = false, hasPlan = true, planScannedAtElapsed = now - 7 * hour, nowElapsed = now))
        assertTrue(AutoScanPolicy.shouldScan(hasAccess = true, scanning = false, hasPlan = false, planScannedAtElapsed = 0L, nowElapsed = now))
        assertFalse(AutoScanPolicy.shouldScan(hasAccess = false, scanning = false, hasPlan = false, planScannedAtElapsed = 0L, nowElapsed = now))
        assertFalse(AutoScanPolicy.shouldScan(hasAccess = true, scanning = true, hasPlan = false, planScannedAtElapsed = 0L, nowElapsed = now))
    }

    @Test
    fun `a saved summary younger than six hours avoids a scan after restart`() {
        val hour = 3_600_000L
        val wall = 1_800_000_000_000L
        fun scan(savedAt: Long) = AutoScanPolicy.shouldScan(
            hasAccess = true,
            scanning = false,
            hasPlan = false,
            planScannedAtElapsed = 0L,
            nowElapsed = 5_000L,
            savedSummaryAtMillis = savedAt,
            nowMillis = wall,
        )
        assertFalse(scan(wall - 2 * hour))
        assertTrue(scan(wall - 7 * hour))
        assertTrue(scan(0L))
        // A clock moved backwards never counts as fresh.
        assertTrue(scan(wall + hour))
    }

    @Test
    fun `trash days left round up and never go negative`() {
        val day = 86_400_000L
        val entry = com.mrzekai.depoakilli.data.TrashEntry("id", "/a", "/t", 1L, 0L, deletedAtMillis = 0L, group = "")
        assertEquals(7, trashDaysLeft(entry, 0L))
        assertEquals(1, trashDaysLeft(entry, 6 * day + 1))
        assertEquals(0, trashDaysLeft(entry, 8 * day))
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
