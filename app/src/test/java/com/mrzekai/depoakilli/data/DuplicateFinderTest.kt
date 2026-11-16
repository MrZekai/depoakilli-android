package com.mrzekai.depoakilli.data

import com.mrzekai.depoakilli.model.CleanupGroup
import com.mrzekai.depoakilli.model.IndexedFile
import java.io.File
import java.nio.file.Files
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DuplicateFinderTest {
    private val root: File = Files.createTempDirectory("dupes").toFile()
    private val finder = DuplicateFinder(AiCleaningEngine { 0L })

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `same size with different content is separated`() = runTest {
        val bytes = Random(1).nextBytes(200_000)
        val changed = bytes.copyOf().also { it[100_000] = (it[100_000] + 1).toByte() }
        val a = write("Pictures/a.jpg", bytes)
        val b = write("Download/b.jpg", changed)
        val result = finder.find(listOf(a, b), maxFullHashBytes = 1_000_000L)
        assertTrue(result.copies.isEmpty())
    }

    @Test
    fun `identical files under the ceiling are verified copies`() = runTest {
        val bytes = Random(2).nextBytes(200_000)
        val keep = write("Pictures/a.jpg", bytes, modified = 1_000L)
        val copy = write("Download/a.jpg", bytes, modified = 2_000L)
        val result = finder.find(listOf(keep, copy), maxFullHashBytes = 1_000_000L)
        val item = result.copies.single()
        assertEquals(copy.uri, item.uri)
        assertEquals(CleanupGroup.DUPLICATE_SAFE, item.assessment.group)
        assertEquals(keep.uri, item.protectedDuplicateUri)
        assertTrue(keep.uri in result.protectedUris)
    }

    @Test
    fun `files above the ceiling stay unverified and can be checked later`() = runTest {
        val bytes = Random(3).nextBytes(300_000)
        val keep = write("Pictures/a.jpg", bytes, modified = 1_000L)
        val copy = write("Download/a.jpg", bytes, modified = 2_000L)
        val item = finder.find(listOf(keep, copy), maxFullHashBytes = 100_000L).copies.single()
        assertEquals(CleanupGroup.DUPLICATE_UNVERIFIED, item.assessment.group)
        val verified = finder.verify(item)
        assertNotNull(verified)
        assertEquals(CleanupGroup.DUPLICATE_SAFE, verified!!.assessment.group)
    }

    @Test
    fun `verify drops a look alike that differs`() = runTest {
        val bytes = Random(4).nextBytes(300_000)
        val changed = bytes.copyOf().also { it[100_000] = (it[100_000] + 1).toByte() }
        val keep = write("Pictures/a.jpg", bytes, modified = 1_000L)
        val copy = write("Download/a.jpg", changed, modified = 2_000L)
        val item = finder.find(listOf(keep, copy), maxFullHashBytes = 100_000L).copies.single()
        assertEquals(CleanupGroup.DUPLICATE_UNVERIFIED, item.assessment.group)
        assertNull(finder.verify(item))
    }

    @Test
    fun `hashing can be cancelled`() = runTest {
        val file = write("Movies/big.bin", Random(5).nextBytes(2_000_000))
        val job = Job()
        try {
            withContext(job) {
                finder.fingerprint(file.uri) { done, _ -> if (done > 300_000) job.cancel() }
            }
            fail("expected cancellation")
        } catch (expected: CancellationException) {
            assertTrue(job.isCancelled)
        }
    }

    private fun write(relative: String, bytes: ByteArray, modified: Long = 1_000L): IndexedFile {
        val file = File(root, relative)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        file.setLastModified(modified)
        return IndexedFile(
            uri = file.toURI().toString(),
            name = file.name,
            sizeBytes = file.length(),
            mimeType = "image/jpeg",
            modifiedAtMillis = modified,
            relativePath = relative.substringBeforeLast('/'),
            volumeRoot = root.canonicalPath,
        )
    }
}
