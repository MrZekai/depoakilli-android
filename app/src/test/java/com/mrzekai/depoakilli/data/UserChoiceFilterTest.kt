package com.mrzekai.depoakilli.data

import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.CleanupGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class UserChoiceFilterTest {
    private val engine = AiCleaningEngine { 0L }

    private fun item(id: String, group: CleanupGroup, path: String, root: String = "/v"): CleanableItem {
        val assessment = engine.assessment(group, 0)
        return CleanableItem(
            id = id,
            uri = "file://$root/$path/$id",
            name = id,
            sizeBytes = 10,
            mimeType = "video/mp4",
            modifiedAtMillis = 1,
            relativePath = path,
            assessment = assessment,
            selected = assessment.recommended,
            volumeRoot = root,
        )
    }

    @Test
    fun `kept files and excluded folders leave review groups only`() {
        val kept = item("kept", CleanupGroup.OLD_VIDEO, "Movies")
        val items = listOf(
            kept,
            item("other", CleanupGroup.OLD_VIDEO, "Movies"),
            item("archive", CleanupGroup.LARGE_VIDEO, "Pictures/Archive/2019"),
            item("sibling", CleanupGroup.LARGE_VIDEO, "Pictures/Archive2"),
            item("temp", CleanupGroup.TEMP_FILES, "Pictures/Archive"),
        )
        val result = UserChoiceFilter.apply(
            items,
            kept = setOf(KeptItemsStore.keyOf(kept)),
            excluded = listOf(ExcludedFolder("/v", "Pictures/Archive")),
        )
        assertEquals(listOf("other", "sibling", "temp"), result.items.map(CleanableItem::id))
        assertEquals(1, result.hiddenKept)
        assertEquals(1, result.hiddenExcluded)
    }
}
