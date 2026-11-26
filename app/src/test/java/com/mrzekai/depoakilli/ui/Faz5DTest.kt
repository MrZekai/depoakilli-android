package com.mrzekai.depoakilli.ui

import com.mrzekai.depoakilli.data.AiCleaningEngine
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.CleanupGroup
import com.mrzekai.depoakilli.model.buildPlan
import com.mrzekai.depoakilli.model.isAppSelectable
import com.mrzekai.depoakilli.model.isSuggestedPick
import com.mrzekai.depoakilli.ui.common.ConfirmSelection
import com.mrzekai.depoakilli.ui.common.DeleteSplit
import com.mrzekai.depoakilli.ui.theme.GiantScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Faz 5D: select all (D1), grouped confirmation (D2), stricter pick (D3), Home fit (D7), ad-free offer (D5). */
class Faz5DTest {
    private val engine = AiCleaningEngine { 0L }
    private val day = 24L * 60L * 60L * 1000L
    private val now = 1_800_000_000_000L

    private fun item(
        id: String,
        group: CleanupGroup,
        size: Long = 10L,
        path: String = "Download",
        mime: String = "application/zip",
        ageDays: Long = 400L,
        original: String? = null,
        selected: Boolean = false,
    ) = CleanableItem(
        id = id,
        uri = "file:///$path/$id",
        name = id,
        sizeBytes = size,
        mimeType = mime,
        modifiedAtMillis = now - ageDays * day,
        relativePath = path,
        assessment = engine.assessment(group, ageDays),
        selected = selected,
        protectedDuplicateUri = original,
    )

    // ---- D1 -------------------------------------------------------------

    @Test
    fun `the user's select all takes camera files and look-alikes`() {
        val items = listOf(
            item("cam", CleanupGroup.OLD_VIDEO, path = "DCIM/Camera", mime = "video/mp4"),
            item("look", CleanupGroup.DUPLICATE_UNVERIFIED, path = "Movies"),
        )
        val result = PlanSelection.setItemsSelected(items, setOf("cam", "look"), selected = true, bulk = false)
        assertTrue(result.all(CleanableItem::selected))
    }

    @Test
    fun `the app's own selection skips camera files and look-alikes`() {
        val items = listOf(
            item("cam", CleanupGroup.OLD_VIDEO, path = "DCIM/Camera", mime = "video/mp4"),
            item("look", CleanupGroup.DUPLICATE_UNVERIFIED, path = "Movies"),
            item("zip", CleanupGroup.DOWNLOAD_FILE),
        )
        val result = PlanSelection.setItemsSelected(items, setOf("cam", "look", "zip"), selected = true, bulk = true)
        assertEquals(listOf("zip"), result.filter(CleanableItem::selected).map(CleanableItem::id))
    }

    @Test
    fun `a half-ticked box selects, a full one clears`() {
        val half = listOf(item("a", CleanupGroup.DOWNLOAD_FILE, selected = true), item("b", CleanupGroup.DOWNLOAD_FILE))
        assertTrue(PlanSelection.selectAllTarget(half))
        val full = half.map { it.copy(selected = true) }
        assertFalse(PlanSelection.selectAllTarget(full))
        assertTrue(PlanSelection.selectAllTarget(full.map { it.copy(selected = false) }))
    }

    @Test
    fun `verified select all keeps one original in every set and marks the different ones`() {
        val originalUri = "file:///Movies/original"
        val items = listOf(
            // The original happens to be listed (and ticked) too.
            item("original", CleanupGroup.DUPLICATE_PERSONAL, path = "Movies", selected = true),
            item("copy1", CleanupGroup.DUPLICATE_UNVERIFIED, path = "Movies", original = originalUri),
            item("copy2", CleanupGroup.DUPLICATE_UNVERIFIED, path = "Movies", original = originalUri),
            item("other", CleanupGroup.DUPLICATE_UNVERIFIED, path = "Movies", original = "file:///Movies/elsewhere"),
        )
        val verified = { it: CleanableItem -> it.copy(assessment = engine.assessment(CleanupGroup.DUPLICATE_PERSONAL, 400L)) }
        val results = mapOf(
            "copy1" to verified(items[1]),
            "copy2" to verified(items[2]),
            "other" to null,
        )
        val after = PlanSelection.selectVerifiedCopies(items, results)
        val byId = after.associateBy(CleanableItem::id)
        assertTrue(byId.getValue("copy1").selected)
        assertTrue(byId.getValue("copy2").selected)
        // The original of the set stays unselected.
        assertFalse(byId.getValue("original").selected)
        // A file that turned out different is marked and never selected.
        assertTrue(byId.getValue("other").verifiedDifferent)
        assertFalse(byId.getValue("other").selected)
        assertFalse(PlanSelection.needsVerification(after.filter { it.id == "other" }))
        // K3: the feedback counts the two exact copies only.
        assertEquals(2, PlanSelection.exactCopiesSelected(after, results))
    }

    @Test
    fun `select all on look-alikes always has a count to report`() {
        val originalUri = "file:///Movies/a.mp4"
        val items = listOf(
            item("x", CleanupGroup.DUPLICATE_UNVERIFIED, path = "Movies", original = originalUri),
            item("y", CleanupGroup.DUPLICATE_UNVERIFIED, path = "Movies", original = "file:///Movies/b.mp4"),
        )
        val verified = { it: CleanableItem -> it.copy(assessment = engine.assessment(CleanupGroup.DUPLICATE_PERSONAL, 400L)) }
        // all different
        val none = mapOf("x" to null, "y" to null)
        assertEquals(0, PlanSelection.exactCopiesSelected(PlanSelection.selectVerifiedCopies(items, none), none))
        // some copies
        val some = mapOf("x" to verified(items[0]), "y" to null)
        assertEquals(1, PlanSelection.exactCopiesSelected(PlanSelection.selectVerifiedCopies(items, some), some))
        // all copies
        val all = mapOf("x" to verified(items[0]), "y" to verified(items[1]))
        assertEquals(2, PlanSelection.exactCopiesSelected(PlanSelection.selectVerifiedCopies(items, all), all))
    }

    @Test
    fun `my pick header counts exactly what select my picks selects`() {
        val items = listOf(
            item("zip", CleanupGroup.DOWNLOAD_FILE),
            item("cam-shot", CleanupGroup.SCREENSHOT, path = "DCIM/Camera", mime = "image/png"),
        )
        val pick = items.filter { it.isAppSelectable() }
        val selected = PlanSelection.setItemsSelected(items, pick.map(CleanableItem::id).toSet(), true, bulk = true)
        assertEquals(pick.size, selected.count(CleanableItem::selected))
    }

    // ---- D2 -------------------------------------------------------------

    @Test
    fun `unticking a group changes the total and the ids to delete`() {
        val items = listOf(
            item("zip", CleanupGroup.DOWNLOAD_FILE, size = 2_000L),
            item("apk", CleanupGroup.APK_INSTALLED, size = 300L),
            item("thumb", CleanupGroup.THUMBNAILS, size = 20L, path = "Pictures/.thumbnails"),
        )
        val all = ConfirmSelection.selected(items, emptySet(), emptySet())
        assertEquals(2_320L, DeleteSplit.of(all).totalBytes)
        val withoutDownloads = ConfirmSelection.selected(items, setOf(CleanupGroup.DOWNLOAD_FILE), emptySet())
        assertEquals(setOf("apk", "thumb"), withoutDownloads.map(CleanableItem::id).toSet())
        assertEquals(320L, DeleteSplit.of(withoutDownloads).totalBytes)
        val withoutOneFile = ConfirmSelection.selected(items, emptySet(), setOf("apk"))
        assertEquals(setOf("zip", "thumb"), withoutOneFile.map(CleanableItem::id).toSet())
        assertTrue(ConfirmSelection.selected(items, CleanupGroup.entries.toSet(), emptySet()).isEmpty())
    }

    // ---- D3 -------------------------------------------------------------

    @Test
    fun `recent files, downloaded media and young documents are never suggested`() {
        val items = listOf(
            item("old-zip", CleanupGroup.DOWNLOAD_FILE, ageDays = 200L),
            item("new-zip", CleanupGroup.DOWNLOAD_FILE, ageDays = 10L),
            item("my-video", CleanupGroup.DOWNLOAD_FILE, mime = "video/mp4", ageDays = 400L),
            item("my-song", CleanupGroup.DOWNLOAD_FILE, mime = "audio/mpeg", ageDays = 400L),
            item("young-pdf", CleanupGroup.DOWNLOAD_DOCUMENT, mime = "application/pdf", ageDays = 90L),
            item("old-pdf", CleanupGroup.DOWNLOAD_DOCUMENT, mime = "application/pdf", ageDays = 200L),
        )
        val picked = items.filter { it.isSuggestedPick(now) }.map(CleanableItem::id).toSet()
        assertEquals(setOf("old-zip", "old-pdf"), picked)
    }

    @Test
    fun `downloaded videos and photos get their own ask row`() {
        val plan = buildPlan(
            listOf(
                item("my-video", CleanupGroup.DOWNLOAD_FILE, size = 900_000_000L, mime = "video/mp4"),
                item("my-photo", CleanupGroup.DOWNLOAD_FILE, size = 100_000_000L, mime = "image/jpeg"),
            ),
        )
        val row = plan.askRows.single { it.group == CleanupGroup.DOWNLOAD_MEDIA }
        assertEquals(setOf("my-video", "my-photo"), row.items.map(CleanableItem::id).toSet())
        assertTrue(plan.suggestedItems.isEmpty())
    }

    // ---- D7 -------------------------------------------------------------

    @Test
    fun `the trash line makes Home one size smaller and Home always fits`() {
        assertEquals(GiantScale.LARGE, GiantScale.forHome(891, 760f, 1f, trashLine = false))
        assertEquals(GiantScale.MEDIUM, GiantScale.forHome(891, 760f, 1f, trashLine = true))
        val small = GiantScale.forHome(640, 420f, 1f, trashLine = true)
        assertEquals(GiantScale.SMALL, small)
        listOf(360 to 640, 411 to 891).forEach { (_, height) ->
            listOf(1f, 1.3f).forEach { font ->
                val available = height - 24f - 56f - 80f - 48f
                val scale = GiantScale.forHome(height, available, font, trashLine = true)
                assertTrue(scale == GiantScale.SMALL || GiantScale.homeNeedDp(scale, font, true) <= available)
            }
        }
    }
}
