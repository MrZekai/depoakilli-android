package com.mrzekai.depoakilli.photo

import com.mrzekai.depoakilli.photo.PhotoReviewRules.BadKind
import com.mrzekai.depoakilli.photo.PhotoReviewRules.PhotoType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoReviewRulesTest {
    private val day = 86_400_000L
    private val now = 1_000 * day

    @Test
    fun `blurry before dark, fresh shots never judged`() {
        assertEquals(BadKind.BLURRY, PhotoReviewRules.badKind(10.0, 10.0, now - 10 * day, now))
        assertEquals(BadKind.DARK, PhotoReviewRules.badKind(500.0, 12.0, now - 10 * day, now))
        assertNull(PhotoReviewRules.badKind(500.0, 120.0, now - 10 * day, now))
        assertNull(PhotoReviewRules.badKind(1.0, 1.0, now - day, now))
    }

    @Test
    fun `types need confidence and 30 days`() {
        val receipt = listOf(240 to 0.9f)
        assertEquals(PhotoType.DOCUMENT, PhotoReviewRules.type(receipt, now - 40 * day, now))
        assertNull(PhotoReviewRules.type(receipt, now - 10 * day, now))
        assertNull(PhotoReviewRules.type(listOf(240 to 0.5f), now - 40 * day, now))
        assertEquals(PhotoType.MEME, PhotoReviewRules.type(listOf(2 to 0.8f), now - 40 * day, now))
        assertNull(PhotoReviewRules.type(listOf(1 to 0.99f), now - 40 * day, now))
    }

    @Test
    fun `passports and money are documents with a warning`() {
        val passport = listOf(267 to 0.85f)
        assertEquals(PhotoType.DOCUMENT, PhotoReviewRules.type(passport, now - 40 * day, now))
        assertTrue(PhotoReviewRules.important(passport))
        assertFalse(PhotoReviewRules.important(listOf(240 to 0.9f)))
    }

    @Test
    fun `one closed eye in the group is enough, no face is unknown`() {
        assertEquals(0.1f, PhotoReviewRules.eyesOpen(listOf(0.9f to 0.95f, 0.1f to 0.8f)))
        assertNull(PhotoReviewRules.eyesOpen(emptyList()))
        assertNull(PhotoReviewRules.eyesOpen(listOf(null to null)))
        assertTrue(PhotoReviewRules.eyesClosed(0.2f))
        assertFalse(PhotoReviewRules.eyesClosed(null))
    }

    @Test
    fun `type codes round-trip`() {
        listOf(null to false, PhotoType.DOCUMENT to true, PhotoType.MEME to false).forEach { (t, i) ->
            assertEquals(t to i, PhotoReviewRules.decodeType(PhotoReviewRules.encodeType(t, i)))
        }
        assertNull(PhotoReviewRules.decodeType(-1))
    }
}
