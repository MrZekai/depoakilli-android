package com.mrzekai.depoakilli.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    @Test
    fun `dark text on every bright action background meets WCAG AA`() {
        ContrastTokens.BrightActionBackgrounds.forEach { background ->
            val ratio = ContrastTokens.contrastRatio(ContrastTokens.OnBrightAction, background)
            assertTrue("ratio $ratio for $background", ratio >= 4.5)
        }
    }

    @Test
    fun `white text on the dark app background meets WCAG AA`() {
        val ratio = ContrastTokens.contrastRatio(
            androidx.compose.ui.graphics.Color.White,
            androidx.compose.ui.graphics.Color(0xFF030A1D),
        )
        assertTrue(ratio >= 4.5)
    }
}
