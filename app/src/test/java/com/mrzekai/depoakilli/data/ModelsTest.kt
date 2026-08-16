package com.mrzekai.depoakilli.data

import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.AppCacheEntry
import com.mrzekai.depoakilli.model.AppCacheSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsTest {
    @Test
    fun `byte formatter uses readable binary units`() {
        assertEquals("0 B", ByteFormatter.format(0))
        assertEquals("1.0 KB", ByteFormatter.format(1024))
        assertEquals("1.5 MB", ByteFormatter.format(1_572_864))
        assertEquals("2.0 GB", ByteFormatter.format(2_147_483_648))
    }

    @Test
    fun `app cache snapshot sums real per-app cache bytes`() {
        val snapshot = AppCacheSnapshot(
            accessGranted = true,
            entries = listOf(
                AppCacheEntry("one.example", "One", 1_500L),
                AppCacheEntry("two.example", "Two", 2_500L),
            ),
            scannedAppCount = 2,
            reportedOtherAppsCacheBytes = 4_500L,
        )

        assertEquals(4_500L, snapshot.totalCacheBytes)
    }
}
