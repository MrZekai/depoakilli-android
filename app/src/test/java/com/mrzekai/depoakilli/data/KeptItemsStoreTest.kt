package com.mrzekai.depoakilli.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeptItemsStoreTest {
    private var stored: String? = null
    private var now = 1L
    private val store = KeptItemsStore({ stored }, { stored = it }, { now++ })

    @Test
    fun `add remove and clear`() {
        store.add("a")
        store.add("b")
        assertEquals(setOf("a", "b"), store.keys())
        store.remove("a")
        assertEquals(setOf("b"), store.keys())
        store.clear()
        assertEquals(0, store.count())
    }

    @Test
    fun `the oldest records drop past the cap`() {
        repeat(KeptItemsStore.MAX_ENTRIES + 2) { store.add("k$it") }
        assertEquals(KeptItemsStore.MAX_ENTRIES, store.count())
        assertFalse("k0" in store.keys())
        assertFalse("k1" in store.keys())
        assertTrue("k${KeptItemsStore.MAX_ENTRIES + 1}" in store.keys())
    }

    @Test
    fun `a changed file no longer matches and missing files are pruned`() {
        val before = KeptItemsStore.keyOf("/v", "DCIM/Camera", "a.mp4", 100, 5)
        val after = KeptItemsStore.keyOf("/v", "DCIM/Camera", "a.mp4", 100, 6)
        store.add(before)
        assertFalse(after in store.keys())
        store.add("gone")
        store.retainOnly(setOf(before))
        assertEquals(setOf(before), store.keys())
    }
}
