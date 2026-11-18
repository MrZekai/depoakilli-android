package com.mrzekai.depoakilli.data

import com.mrzekai.depoakilli.model.IndexedFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderSizesTest {
    private var n = 0
    private fun file(path: String, size: Long, root: String = "/storage/emulated/0") = IndexedFile(
        uri = "file://$root/$path/f${n++}",
        name = "f$n",
        sizeBytes = size,
        mimeType = "image/jpeg",
        modifiedAtMillis = 0L,
        relativePath = path,
        volumeRoot = root,
    )

    @Test
    fun `folders are cut to three levels and sorted by size`() {
        val top = FolderSizes.top(
            listOf(
                file("DCIM/Camera", 500),
                file("DCIM/Camera", 300),
                file("Movies/A/B/C/D", 400),
                file("Download", 100),
                file("", 9_999),
                file("Android/data/com.app/files", 9_000),
                file(".SmartCleanerTrash", 8_000),
                file("Pictures/.hidden", 7_000),
            ),
        )
        assertEquals(listOf("DCIM/Camera", "Movies/A/B", "Download"), top.map { it.relativePath })
        assertEquals(800L, top.first().bytes)
        assertEquals(2, top.first().fileCount)
        assertEquals("Camera", top.first().name)
    }

    @Test
    fun `top five only and volumes stay apart`() {
        val files = (1..7).map { file("F$it", it * 10L) } + file("F7", 1L, "/storage/ABCD")
        val top = FolderSizes.top(files)
        assertEquals(5, top.size)
        assertEquals("F7", top.first().relativePath)
        assertEquals(70L, top.first().bytes)
    }

    @Test
    fun `contains matches the folder and its subfolders only`() {
        val folder = FolderSizes.top(listOf(file("DCIM/Camera", 1))).single()
        assertTrue(FolderSizes.contains(folder, file("DCIM/Camera/2024", 1)))
        assertTrue(FolderSizes.contains(folder, file("DCIM/Camera", 1)))
        assertFalse(FolderSizes.contains(folder, file("DCIM/CameraRoll", 1)))
        assertFalse(FolderSizes.contains(folder, file("DCIM/Camera", 1, "/storage/ABCD")))
    }
}
