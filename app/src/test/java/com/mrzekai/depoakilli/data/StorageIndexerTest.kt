package com.mrzekai.depoakilli.data

import com.mrzekai.depoakilli.model.IndexedFile
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Shared-storage walk rules (T3.5): symlink loops, the file cap and skipped folders. */
class StorageIndexerTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun indexer(limit: Int = 1_000) = StorageIndexer(
        fileLimit = { limit },
        makeIndexedFile = { root, file ->
            IndexedFile(
                uri = file.toURI().toString(),
                name = file.name,
                sizeBytes = file.length(),
                mimeType = "",
                modifiedAtMillis = 0L,
                relativePath = file.parentFile!!.relativeTo(root).invariantSeparatorsPath,
            )
        },
        clock = { 0L },
    )

    private fun File.write(path: String, bytes: Int = 10): File =
        File(this, path).apply {
            parentFile!!.mkdirs()
            writeBytes(ByteArray(bytes))
        }

    private suspend fun names(root: File, limit: Int = 1_000): List<String> =
        indexer(limit).indexSharedStorage(listOf(root)) { _, _ -> }.map(IndexedFile::name)

    @Test
    fun `symlink loops are walked once and linked files are not indexed twice`() = runTest {
        val root = temp.newFolder("storage")
        root.write("Pictures/a.jpg")
        // A link back to the root and a second path to the same folder.
        Files.createSymbolicLink(File(root, "Pictures/loop").toPath(), root.toPath())
        Files.createSymbolicLink(File(root, "alias").toPath(), File(root, "Pictures").toPath())

        assertEquals(listOf("a.jpg"), names(root))
    }

    @Test
    fun `links that leave the storage root are ignored`() = runTest {
        val root = temp.newFolder("storage")
        val outside = temp.newFolder("outside")
        outside.write("secret.txt")
        Files.createSymbolicLink(File(root, "escape").toPath(), outside.toPath())

        assertTrue(names(root).isEmpty())
    }

    @Test
    fun `the file cap stops the walk`() = runTest {
        val root = temp.newFolder("storage")
        repeat(20) { root.write("Download/f$it.bin") }

        assertEquals(5, names(root, limit = 5).size)
    }

    @Test
    fun `app-private and hidden folders are skipped`() = runTest {
        val root = temp.newFolder("storage")
        root.write("Android/data/com.app/cache.bin")
        root.write("Android/obb/com.app/main.obb")
        root.write(".hidden/photo.jpg")
        root.write("Pictures/.thumbnails/t.jpg")
        root.write("Pictures/visible.jpg")

        val found = names(root)
        assertEquals(setOf("t.jpg", "visible.jpg"), found.toSet())
        assertFalse("cache.bin" in found)
    }

    @Test
    fun `empty files and hidden marker files are not indexed`() = runTest {
        val root = temp.newFolder("storage")
        root.write("Download/empty.bin", bytes = 0)
        root.write("Download/.nomedia")
        root.write("Download/real.bin")

        assertEquals(listOf("real.bin"), names(root))
    }
}
