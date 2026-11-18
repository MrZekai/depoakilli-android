package com.mrzekai.depoakilli.model

import com.mrzekai.depoakilli.data.AiCleaningEngine
import com.mrzekai.depoakilli.data.KeptItemsStore
import com.mrzekai.depoakilli.data.UserChoiceFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Faz 5C: one number everywhere (C1) and the three tiers (C2). */
class PlanFiguresTest {
    private val engine = AiCleaningEngine { 0L }
    private val mb = 1_000_000L

    private fun item(id: String, group: CleanupGroup, size: Long, path: String = "Download"): CleanableItem =
        CleanableItem(
            id = id,
            uri = "file:///$path/$id",
            name = id,
            sizeBytes = size,
            mimeType = if (group == CleanupGroup.OLD_VIDEO || group == CleanupGroup.LARGE_VIDEO) "video/mp4" else "application/octet-stream",
            modifiedAtMillis = 1L,
            relativePath = path,
            assessment = engine.assessment(group, 0),
            selected = group.auto,
        )

    private val items = listOf(
        item("apk", CleanupGroup.APK_INSTALLED, 300 * mb),
        item("thumbs", CleanupGroup.THUMBNAILS, 20 * mb, "Pictures/.thumbnails"),
        item("old-download", CleanupGroup.DOWNLOAD_FILE, 900 * mb),
        item("sent", CleanupGroup.WHATSAPP_SENT, 250 * mb, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video/Sent"),
        item("shot", CleanupGroup.SCREENSHOT, 150 * mb, "Pictures/Screenshots"),
        item("camera-shot", CleanupGroup.SCREENSHOT, 120 * mb, "DCIM/Camera"),
        item("copy", CleanupGroup.DUPLICATE_PERSONAL, 400 * mb, "Documents"),
        item("look-alike", CleanupGroup.DUPLICATE_UNVERIFIED, 466 * mb, "Movies"),
        item("old-video", CleanupGroup.OLD_VIDEO, 2_000 * mb, "DCIM/Camera"),
        item("big-old-video", CleanupGroup.LARGE_VIDEO, 3_000 * mb, "DCIM/Camera"),
    )
    private val plan = buildPlan(items)
    private val apps = listOf(
        InstalledAppEntry(packageName = "a.b", label = "Old game", appBytes = 700 * mb, lastUsedMillis = 1L),
    )

    @Test
    fun `Home, findings and every tile read the same numbers`() {
        val figures = PlanFigures.of(plan, apps)
        // Home "up to X" = tier 1 + the findings list (unused apps in both).
        assertEquals(figures.safeBytes + figures.findingsBytes, figures.homeMaxBytes)
        assertEquals(plan.potentialBytes + 700 * mb, figures.homeMaxBytes)
        // Findings giant = X − tier 1.
        assertEquals(figures.homeMaxBytes - figures.safeBytes, figures.findingsBytes)
        assertEquals(plan.suggestedBytes + plan.askBytes + 700 * mb, figures.findingsBytes)
        // A tile and its tool screen call the same function on the same plan.
        SuggestionTool.entries.forEach { tool ->
            assertEquals(
                ToolSuggestion.suggestedFor(tool, plan).sumOf(CleanableItem::sizeBytes),
                ToolSuggestion.suggestedBytes(tool, plan),
            )
        }
        assertEquals(1_200 * mb, ToolSuggestion.suggestedBytes(SuggestionTool.DOWNLOADS, plan))
        assertEquals(400 * mb, ToolSuggestion.suggestedBytes(SuggestionTool.DUPLICATES, plan))
        assertEquals(250 * mb, ToolSuggestion.suggestedBytes(SuggestionTool.WHATSAPP, plan))
    }

    @Test
    fun `unused apps count in neither place when they are unknown`() {
        val figures = PlanFigures.of(plan, null)
        assertEquals(plan.potentialBytes, figures.homeMaxBytes)
    }

    @Test
    fun `my pick holds the suggested groups and nothing personal`() {
        val picked = plan.suggestedItems.map(CleanableItem::id).toSet()
        assertEquals(setOf("old-download", "sent", "shot", "copy"), picked)
        // A screenshot in the camera folder is asked about, never suggested.
        assertFalse("camera-shot" in picked)
        // Possible duplicates are only sampled: never suggested.
        assertFalse("look-alike" in picked)
        // Everything suggested goes to Trash.
        assertTrue(plan.suggestedItems.all { it.assessment.group.deleteMode == DeleteMode.TRASH })
        CleanupGroup.SUGGESTED.forEach { assertEquals(DeleteMode.TRASH, it.deleteMode) }
    }

    @Test
    fun `a kept file is never suggested`() {
        val kept = items.first { it.id == "old-download" }
        val visible = UserChoiceFilter.apply(items, setOf(KeptItemsStore.keyOf(kept)), emptyList()).items
        assertFalse(buildPlan(visible).suggestedItems.any { it.id == "old-download" })
    }

    @Test
    fun `old videos and large old videos are one row, biggest first`() {
        val videoRows = plan.askRows.filter { it.group == CleanupGroup.OLD_VIDEO }
        assertEquals(1, videoRows.size)
        assertEquals(listOf("big-old-video", "old-video"), videoRows.single().items.map(CleanableItem::id))
        assertTrue(plan.askRows.none { it.group == CleanupGroup.LARGE_VIDEO })
        assertEquals(listOf("big-old-video", "old-video"), swipeItemsFor(CleanupGroup.OLD_VIDEO, items).map(CleanableItem::id))
    }

    @Test
    fun `the tiers add up to the whole plan`() {
        assertEquals(plan.reviewBytes, plan.suggestedBytes + plan.askBytes)
    }
}
