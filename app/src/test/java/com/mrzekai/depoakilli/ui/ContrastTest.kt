package com.mrzekai.depoakilli.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    @Test
    fun `every Faz 5 text and background pair meets WCAG AA`() {
        ContrastTokens.DesignPairs.forEach { (text, background) ->
            val ratio = ContrastTokens.contrastRatio(text, background)
            assertTrue("ratio $ratio for $text on $background", ratio >= 4.5)
        }
    }

    @Test
    fun `dark text on every bright action background meets WCAG AA`() {
        ContrastTokens.BrightActionBackgrounds.forEach { background ->
            val ratio = ContrastTokens.contrastRatio(ContrastTokens.OnBrightAction, background)
            assertTrue("ratio $ratio for $background", ratio >= 4.5)
        }
    }

    @Test
    fun `the six pairs of the design are all checked`() {
        assertTrue(ContrastTokens.DesignPairs.size == 6)
    }
}
