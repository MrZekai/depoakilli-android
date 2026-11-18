package com.mrzekai.depoakilli.data

import com.mrzekai.depoakilli.model.CleanupGroup
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class TrashStoreTest {
    private val base: File = Files.createTempDirectory("trash").toFile()
    private val volume = File(base, "volume").apply { mkdirs() }
    private val indexFile = File(base, "files/trash_index.json")
    private var now = 10_000_000_000L
    private val rescanned = ArrayList<String>()
    private val removed = ArrayList<String>()
    private fun store(allowed: (File) -> Boolean = { true }) = TrashStore(
        indexFile = indexFile,
        trashRoots = { listOf(volume) },
        isAllowed = allowed,
        removeFromMediaStore = { removed += it },
        rescan = { rescanned += it },
        nowMillis = { now },
    )

    @After
    fun cleanUp() {
        base.setWritableRecursively()
        base.deleteRecursively()
    }

    @Test
    fun `move then restore keeps path and bytes`() {
        val file = write("Movies/clip.mp4", "video-bytes")
        val trash = store()
        val entry = assertNotNull(trash.moveToTrash(file, volume, CleanupGroup.LARGE_VIDEO)).let { trash.entries().single() }
        assertFalse(file.exists())
        assertTrue(File(entry.trashPath).isFile)
        assertTrue(File(volume, ".SmartCleanerTrash/.nomedia").exists())
        assertTrue(File(volume, ".SmartCleanerTrash/README.txt").readText().contains("Safe to delete"))
        assertEquals(listOf(file.absolutePath), removed)
        assertEquals(11L, trash.trashBytes())

        val result = trash.restore(listOf(entry.id))
        assertEquals(setOf(entry.id), result.restoredIds)
        assertEquals("video-bytes", file.readText())
        assertEquals(listOf(file.absolutePath), rescanned)
        assertTrue(trash.entries().isEmpty())
    }

    @Test
    fun `restore into an occupied name and a missing folder`() {
        val file = write("Download/a/report.pdf", "old")
        val trash = store()
        trash.moveToTrash(file, volume, CleanupGroup.DOWNLOAD_DOCUMENT)
        val id = trash.entries().single().id
        write("Download/a/report.pdf", "new")
        trash.restore(listOf(id))
        assertEquals("new", file.readText())
        assertEquals("old", File(volume, "Download/a/report (restored).pdf").readText())

        val other = write("Pictures/gone/x.jpg", "x")
        trash.moveToTrash(other, volume, CleanupGroup.LARGE_OTHER)
        File(volume, "Pictures/gone").deleteRecursively()
        trash.restore(listOf(trash.entries().single().id))
        assertEquals("x", other.readText())
    }

    @Test
    fun `expired entries and old orphans are purged`() {
        val trash = store()
        trash.moveToTrash(write("a.bin", "12345"), volume, CleanupGroup.LARGE_OTHER)
        now += TimeUnit.DAYS.toMillis(6)
        trash.moveToTrash(write("b.bin", "123"), volume, CleanupGroup.LARGE_OTHER)
        now += TimeUnit.DAYS.toMillis(1)
        assertEquals(5L, trash.purgeExpired())
        assertEquals(1, trash.entries().size)

        val orphan = File(volume, ".SmartCleanerTrash/orphan").apply { writeText("zz") }
        orphan.setLastModified(now - TimeUnit.DAYS.toMillis(8))
        val fresh = File(volume, ".SmartCleanerTrash/fresh").apply { writeText("zz") }
        fresh.setLastModified(now)
        // First pass adopts unknown files into the index; nothing is deleted blindly.
        trash.purgeExpired()
        assertTrue(orphan.exists())
        assertTrue(trash.entries().any { it.trashPath == orphan.absolutePath })
        trash.purgeExpired()
        assertTrue(orphan.exists())
        now += TimeUnit.DAYS.toMillis(8)
        trash.purgeExpired()
        assertFalse(orphan.exists())
        assertTrue(File(volume, ".SmartCleanerTrash/README.txt").exists())
    }

    @Test
    fun `empty now returns removed bytes`() {
        val trash = store()
        trash.moveToTrash(write("a.bin", "12345"), volume, CleanupGroup.LARGE_OTHER)
        trash.moveToTrash(write("b.bin", "123"), volume, CleanupGroup.LARGE_OTHER)
        val first = trash.entries().first { it.originalPath.endsWith("a.bin") }
        assertEquals(5L, trash.emptyNow(listOf(first.id)))
        assertEquals(3L, trash.emptyNow())
        assertEquals(0L, trash.trashBytes())
    }

    @Test
    fun `corrupt index is kept and rebuilt from sidecars`() {
        val trash = store()
        val file = write("Movies/keep.mp4", "data")
        trash.moveToTrash(file, volume, CleanupGroup.LARGE_VIDEO)
        indexFile.writeText("{not json")
        val rebuilt = trash.entries().single()
        assertEquals(file.absolutePath, rebuilt.originalPath)
        assertTrue(File(indexFile.parentFile, indexFile.name + ".bak").exists())
        trash.restore(listOf(rebuilt.id))
        assertEquals("data", file.readText())
    }

    @Test
    fun `lost index is recovered on the next purge`() {
        val trash = store()
        val file = write("Movies/clip.mp4", "abc")
        trash.moveToTrash(file, volume, CleanupGroup.LARGE_VIDEO)
        indexFile.delete()
        assertTrue(trash.entries().isEmpty())
        trash.purgeExpired()
        assertEquals(file.absolutePath, trash.entries().single().originalPath)
    }

    @Test
    fun `failed index write leaves the file in place`() {
        val file = write("Movies/keep.mp4", "data")
        val indexDir = indexFile.parentFile!!.apply { mkdirs() }
        assumeTrue(indexDir.setWritable(false))
        assumeTrue(!indexDir.canWrite())
        assertNull(store().moveToTrash(file, volume, CleanupGroup.LARGE_VIDEO))
        assertEquals("data", file.readText())
    }

    @Test
    fun `index survives special characters`() {
        val trash = store()
        val file = write("Download/\"quoted\" \\ name ü.txt", "q")
        trash.moveToTrash(file, volume, CleanupGroup.DOWNLOAD_FILE)
        assertEquals(file.absolutePath, trash.entries().single().originalPath)
    }

    @Test
    fun `failed rename leaves the file in place`() {
        val file = write("Movies/keep.mp4", "data")
        val trashDir = File(volume, ".SmartCleanerTrash").apply { mkdirs() }
        assumeTrue(trashDir.setWritable(false))
        assumeTrue(!trashDir.canWrite())
        assertNull(store().moveToTrash(file, volume, CleanupGroup.LARGE_VIDEO))
        assertEquals("data", file.readText())
    }

    @Test
    fun `blocked files are not moved`() {
        val file = write("WhatsApp/Databases/msgstore.db.crypt14", "secret")
        assertNull(store(allowed = { false }).moveToTrash(file, volume, CleanupGroup.LARGE_OTHER))
        assertTrue(file.exists())
    }

    @Test
    fun `trash folder is a hidden folder the indexer skips`() {
        assertFalse(StoragePathRules.isHiddenDirectoryAllowed(TrashStore.TRASH_DIRECTORY))
        assertTrue(TrashStore.TRASH_DIRECTORY.startsWith("."))
    }

    private fun write(relative: String, text: String): File =
        File(volume, relative).apply {
            parentFile?.mkdirs()
            writeText(text)
        }

    private fun File.setWritableRecursively() {
        walkTopDown().forEach { it.setWritable(true) }
    }
}
