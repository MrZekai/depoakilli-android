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
        assertTrue(DuplicatePolicy.autoSelectable(download, camera, "root", "root"))
    }

    @Test
    fun `oldest original is retained while download copy is selectable`() {
        val old = file("old", "Pictures/Exports/photo.jpg", modifiedAt = 100L)
        val recent = file("recent", "Download/photo.jpg", modifiedAt = 200L)

        val decision = requireNotNull(DuplicatePolicy.choose(listOf(recent, old)))

        assertEquals(old, decision.keep)
        assertTrue(DuplicatePolicy.autoSelectable(recent, old, "root", "root"))
    }

    @Test
    fun `only verified copies in safe locations on the same volume are selectable`() {
        val keep = file("keep", "DCIM/Camera", 1)
        fun copy(path: String, name: String = "photo.jpg") =
            file("copy", path, 2).copy(name = name)
        fun safe(candidate: IndexedFile, root: String = "root") =
            DuplicatePolicy.autoSelectable(candidate, keep, root, "root")

        assertFalse(safe(copy("Android/media/com.whatsapp/WhatsApp/Media")))
        assertFalse(safe(copy("WhatsApp/Media")))
        assertFalse(safe(copy("Download"), "other"))
        assertFalse(safe(copy("Pictures/Album")))
        assertFalse(safe(copy("Download"), ""))
        assertTrue(safe(copy("Download")))
        assertTrue(safe(copy("Pictures/Album", "IMG (1).jpg")))
        assertTrue(safe(copy("Pictures/Album", "IMG (99).jpg")))
        assertFalse(safe(copy("Pictures/Album", "IMG (100).jpg")))
        assertTrue(safe(copy("DCIM/Camera")))
    }

    @Test
    fun `camera recognition is anchored to the volume root`() {
        val older = file("older", "Pictures", 1)
        for (path in listOf("Download/camera-uploads", "Backup/DCIM")) {
            val candidate = file("candidate", path, 2)
            assertEquals(older, requireNotNull(DuplicatePolicy.choose(listOf(candidate, older))).keep)
        }
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
