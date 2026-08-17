package com.mrzekai.depoakilli.data

import com.mrzekai.depoakilli.model.IndexedFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicatePolicyTest {
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
