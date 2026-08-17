package com.mrzekai.depoakilli.data

import com.mrzekai.depoakilli.model.WhatsAppMediaCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class WhatsAppMediaClassifierTest {
    @Test
    fun `classifies the main WhatsApp media groups`() {
        assertCategory(WhatsAppMediaCategory.IMAGES, "IMG.jpg", "image/jpeg", "WhatsApp Images")
        assertCategory(WhatsAppMediaCategory.VIDEOS, "VID.mp4", "video/mp4", "WhatsApp Video")
        assertCategory(WhatsAppMediaCategory.DOCUMENTS, "invoice.pdf", "application/pdf", "WhatsApp Documents")
        assertCategory(WhatsAppMediaCategory.AUDIO, "song.mp3", "audio/mpeg", "WhatsApp Audio")
        assertCategory(WhatsAppMediaCategory.VOICE_NOTES, "PTT-001.opus", "audio/ogg", "WhatsApp Voice Notes")
        assertCategory(WhatsAppMediaCategory.STICKERS_GIFS, "sticker.webp", "image/webp", "WhatsApp Stickers")
    }

    @Test
    fun `file extension safely identifies a document when mime type is missing`() {
        assertCategory(
            WhatsAppMediaCategory.DOCUMENTS,
            "archive.zip",
            "",
            "WhatsApp Documents",
        )
    }

    private fun assertCategory(
        expected: WhatsAppMediaCategory,
        name: String,
        mimeType: String,
        path: String,
    ) {
        assertEquals(expected, WhatsAppMediaClassifier.classify(name, mimeType, path))
    }
}
