package com.mrzekai.depoakilli.data

import com.mrzekai.depoakilli.model.ByteFormatter
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
}
