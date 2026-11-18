package com.mrzekai.depoakilli.ui

import com.mrzekai.depoakilli.data.AiCleaningEngine
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.CleanupGroup
import com.mrzekai.depoakilli.model.DeleteMode
import com.mrzekai.depoakilli.ui.common.ConfirmGroups
import com.mrzekai.depoakilli.ui.common.DeleteSplit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** T5.3 group names come from the selection; T5.6 keeps trashed and deleted bytes apart. */
class ConfirmAndResultTest {
    private val engine = AiCleaningEngine { 0L }

    private fun item(id: String, group: CleanupGroup, size: Long): CleanableItem {
        val assessment = engine.assessment(group, 0)
        return CleanableItem(
            id = id,
            uri = "file:///x/$id",
            name = id,
            sizeBytes = size,
            mimeType = "application/octet-stream",
            modifiedAtMillis = 0L,
            relativePath = "Download",
            assessment = assessment,
            selected = true,
        )
    }

    @Test
    fun `confirmation names exactly the selected groups, biggest first`() {
        val items = listOf(
            item("a", CleanupGroup.TEMP_FILES, 10),
            item("b", CleanupGroup.APK_INSTALLED, 300),
            item("c", CleanupGroup.TEMP_FILES, 15),
            item("d", CleanupGroup.PARTIAL_DOWNLOADS, 100),
        )
        assertEquals(
            listOf(CleanupGroup.APK_INSTALLED, CleanupGroup.PARTIAL_DOWNLOADS, CleanupGroup.TEMP_FILES),
            ConfirmGroups.of(items),
        )
    }

    @Test
    fun `a group that is not selected is never named`() {
        val groups = ConfirmGroups.of(listOf(item("a", CleanupGroup.THUMBNAILS, 1)))
        assertEquals(listOf(CleanupGroup.THUMBNAILS), groups)
        assertFalse(CleanupGroup.LARGE_VIDEO in groups)
    }

    @Test
    fun `moved to Trash is the headline when files went to Trash`() {
        val figures = ResultFigures.of(
            CleanupResult(deletedBytes = 0L, deletedCount = 3, failedCount = 0, trashedBytes = 900L, trashEntryIds = listOf("t")),
        )
        assertEquals(ResultFigures.Headline.TRASHED, figures.headline)
        assertFalse(figures.showFreed)
        assertTrue(figures.canEmptyTrash)
    }

    @Test
    fun `freed is only claimed for bytes really deleted`() {
        val mixed = ResultFigures.of(
            CleanupResult(deletedBytes = 50L, deletedCount = 4, failedCount = 0, trashedBytes = 900L, trashEntryIds = listOf("t")),
        )
        assertEquals(ResultFigures.Headline.TRASHED, mixed.headline)
        assertTrue(mixed.showFreed)

        val permanentOnly = ResultFigures.of(CleanupResult(deletedBytes = 50L, deletedCount = 1, failedCount = 0))
        assertEquals(ResultFigures.Headline.FREED, permanentOnly.headline)
        assertFalse(permanentOnly.canEmptyTrash)

        val nothing = ResultFigures.of(CleanupResult(deletedBytes = 0L, deletedCount = 0, failedCount = 2))
        assertEquals(ResultFigures.Headline.NOTHING, nothing.headline)
    }

    @Test
    fun `installers and exact copies go to Trash, recreated junk is deleted for good`() {
        assertEquals(DeleteMode.TRASH, CleanupGroup.APK_INSTALLED.deleteMode)
        assertEquals(DeleteMode.TRASH, CleanupGroup.DUPLICATE_SAFE.deleteMode)
        assertEquals(DeleteMode.PERMANENT, CleanupGroup.THUMBNAILS.deleteMode)
        assertEquals(DeleteMode.PERMANENT, CleanupGroup.TEMP_FILES.deleteMode)
    }

    @Test
    fun `a Trash-only selection promises restore and shows no permanent line`() {
        val split = DeleteSplit.of(listOf(item("a", CleanupGroup.DUPLICATE_SAFE, 200), item("b", CleanupGroup.APK_INSTALLED, 36)))
        assertEquals(DeleteSplit.Sheet.TRASH, split.sheet)
        assertEquals(236L, split.trashBytes)
        assertEquals(0L, split.permanentBytes)
        assertTrue(split.showsRestoreLine)
        assertFalse(split.showsPermanentLine)
    }

    @Test
    fun `a permanent-only selection never promises restore`() {
        val split = DeleteSplit.of(listOf(item("a", CleanupGroup.THUMBNAILS, 10), item("b", CleanupGroup.TEMP_FILES, 11)))
        assertEquals(DeleteSplit.Sheet.PERMANENT, split.sheet)
        assertFalse(split.showsRestoreLine)
        assertTrue(split.showsPermanentLine)
        assertEquals(21L, split.permanentBytes)
    }

    @Test
    fun `a mixed selection shows the total in the title and each amount once`() {
        val split = DeleteSplit.of(
            listOf(
                item("a", CleanupGroup.DUPLICATE_SAFE, 200),
                item("b", CleanupGroup.APK_INSTALLED, 36),
                item("c", CleanupGroup.THUMBNAILS, 15),
                item("d", CleanupGroup.TEMP_FILES, 6),
            ),
        )
        assertEquals(DeleteSplit.Sheet.MIXED, split.sheet)
        assertEquals(257L, split.totalBytes)
        assertEquals(236L, split.trashBytes)
        assertEquals(21L, split.permanentBytes)
        assertTrue(split.showsRestoreLine && split.showsPermanentLine)
    }
}
