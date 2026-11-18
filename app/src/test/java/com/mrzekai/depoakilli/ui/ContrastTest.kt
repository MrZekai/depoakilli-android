package com.mrzekai.depoakilli.ui

import java.io.File
import org.junit.Assert.assertEquals
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

    @Test
    fun `a disabled action stays readable`() {
        val (text, background) = ContrastTokens.DisabledPair
        assertTrue(ContrastTokens.contrastRatio(text, background) >= 4.5)
    }

    @Test
    fun `white on Lime is a forbidden pair because it fails AA`() {
        ContrastTokens.ForbiddenPairs.forEach { (text, background) ->
            assertTrue(ContrastTokens.contrastRatio(text, background) < 4.5)
        }
    }

    /**
     * B3: on the Lime pages a heading placed straight in a list item (not in a
     * black card) must not be white. Every list on a Lime page is checked; the
     * black swipe screen is the one page with a black background.
     */
    @Test
    fun `no white text sits straight on a Lime page`() {
        val root = listOf("src/main/java/com/mrzekai/depoakilli/ui", "app/src/main/java/com/mrzekai/depoakilli/ui")
            .map(::File)
            .first { it.isDirectory }
        val blackPages = setOf("SwipeReviewScreen.kt")
        val itemText = Regex("""\bitem\s*(?:\([^)]*\))?\s*\{\s*Text\(((?:[^()]|\([^()]*\))*)\)""")
        val white = Regex("""color\s*=\s*(OnBlack\b(?!Soft)|Color\.White)""")
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name !in blackPages }
            .flatMap { file ->
                val source = file.readText()
                itemText.findAll(source)
                    .filter { white.containsMatchIn(it.groupValues[1]) }
                    .map { "${file.name}:${source.substring(0, it.range.first).count { c -> c == '\n' } + 1}" }
            }
            .toList()
        assertEquals(emptyList<String>(), offenders)
    }
}
