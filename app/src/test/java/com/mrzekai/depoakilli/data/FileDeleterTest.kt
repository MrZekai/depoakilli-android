package com.mrzekai.depoakilli.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileDeleterTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun `copy is deleted when original exists with expected size`() {
        val original = folder.newFile("original").apply { writeText("123") }
        val copy = folder.newFile("copy")
        assertEquals(FileDeleter.Result.DELETED, FileDeleter.deleteIfOriginalUnchanged(original, 3) { copy.delete() })
        assertFalse(copy.exists())
    }

    @Test fun `copy survives when original is missing or changed`() {
        val original = File(folder.root, "original")
        val copy = folder.newFile("copy")
        assertEquals(FileDeleter.Result.ORIGINAL_CHANGED, FileDeleter.deleteIfOriginalUnchanged(original, 3) { copy.delete() })
        assertTrue(copy.exists())
        original.writeText("changed")
        assertEquals(FileDeleter.Result.ORIGINAL_CHANGED, FileDeleter.deleteIfOriginalUnchanged(original, 3) { copy.delete() })
        assertTrue(copy.exists())
    }
}
