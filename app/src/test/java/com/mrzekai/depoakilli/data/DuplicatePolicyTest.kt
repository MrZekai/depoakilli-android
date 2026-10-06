package com.mrzekai.depoakilli.data

import com.mrzekai.depoakilli.model.IndexedFile
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.AiAssessment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicatePolicyTest {
    @Test
    fun `protected original is excluded from every other cleanup category`() {
        val original = file("original", "Download/original.jpg", modifiedAt = 100L)
        val copy = file("copy", "Pictures/copy.jpg", modifiedAt = 200L)
        val decision = requireNotNull(DuplicatePolicy.choose(listOf(original, copy)))
        val duplicate = item(copy, CleanCategory.DUPLICATE)
        for (category in listOf(CleanCategory.OLD_DOWNLOAD, CleanCategory.LARGE_FILE, CleanCategory.JUNK)) {
            val unrelated = item(file("unrelated", "Download/other.jpg", 300L), category)
            val result = DuplicatePolicy.ScanResult(listOf(duplicate), setOf(decision.keep.uri))
                .mergeWith(listOf(item(original, category), item(copy, category), unrelated))
                .toList()
            assertEquals(listOf(duplicate, unrelated), result)
            assertFalse(result.any { it.uri == original.uri })
        }
    }

    private fun item(file: IndexedFile, category: CleanCategory) = CleanableItem(
        id = file.uri,
        uri = file.uri,
        name = file.name,
        sizeBytes = file.sizeBytes,
        mimeType = file.mimeType,
        modifiedAtMillis = file.modifiedAtMillis,
        relativePath = file.relativePath,
        assessment = AiAssessment(category, 80, 0, recommended = false),
    )

    @Test
    fun `camera original is kept instead of newer downloaded copy`() {
        val camera = file("camera", "DCIM/Camera/photo.jpg", modifiedAt = 100L)
        val download = file("download", "Download/photo.jpg", modifiedAt = 200L)

        val decision = requireNotNull(DuplicatePolicy.choose(listOf(download, camera)))

        assertEquals(camera, decision.keep)
        assertTrue(decision.automaticSelectionIsSafe)
    }

    @Test
    fun `ambiguous group keeps oldest file but requires manual selection`() {
        val old = file("old", "Pictures/Exports/photo.jpg", modifiedAt = 100L)
        val recent = file("recent", "Download/photo.jpg", modifiedAt = 200L)

        val decision = requireNotNull(DuplicatePolicy.choose(listOf(recent, old)))

        assertEquals(old, decision.keep)
        assertFalse(decision.automaticSelectionIsSafe)
    }

    private fun file(id: String, path: String, modifiedAt: Long) = IndexedFile(
        uri = "content://test/$id",
        name = "$id.jpg",
        sizeBytes = 10_000L,
        mimeType = "image/jpeg",
        modifiedAtMillis = modifiedAt,
        relativePath = path,
    )
}
