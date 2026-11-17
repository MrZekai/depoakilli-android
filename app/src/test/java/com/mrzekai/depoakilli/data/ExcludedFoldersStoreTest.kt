package com.mrzekai.depoakilli.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcludedFoldersStoreTest {
    private var stored: String? = null
    private val store = ExcludedFoldersStore({ stored }, { stored = it })

    @Test
    fun `folder covers its subfolders but not siblings or other volumes`() {
        val folder = ExcludedFolder("/v", "Pictures/Archive")
        assertTrue(folder.contains("/v", "Pictures/Archive"))
        assertTrue(folder.contains("/v", "Pictures/Archive/2019"))
        assertFalse(folder.contains("/v", "Pictures/Archive2"))
        assertFalse(folder.contains("/v", "Pictures"))
        assertFalse(folder.contains("/sd", "Pictures/Archive"))
    }

    @Test
    fun `a volume root is never stored and never matches`() {
        assertFalse(store.add(ExcludedFolder("/v", "")))
        assertFalse(store.add(ExcludedFolder("/v", "/")))
        assertTrue(store.folders().isEmpty())
        assertFalse(ExcludedFolder("/v", "").contains("/v", "DCIM/Camera"))
        // A damaged record with an empty path is ignored on read.
        stored = "/v\t\n/v\tPictures"
        assertTrue(store.folders() == listOf(ExcludedFolder("/v", "Pictures")))
    }

    @Test
    fun `add remove duplicates and the fifty folder limit`() {
        assertTrue(store.add(ExcludedFolder("/v", "/A/")))
        assertTrue(store.add(ExcludedFolder("/v", "A")))
        assertEquals(listOf(ExcludedFolder("/v", "A")), store.folders())
        store.remove(ExcludedFolder("/v", "A"))
        assertTrue(store.folders().isEmpty())
        repeat(ExcludedFoldersStore.MAX_FOLDERS) { assertTrue(store.add(ExcludedFolder("/v", "F$it"))) }
        assertFalse(store.add(ExcludedFolder("/v", "Extra")))
        assertEquals(ExcludedFoldersStore.MAX_FOLDERS, store.folders().size)
    }
}
