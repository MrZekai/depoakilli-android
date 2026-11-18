package com.mrzekai.depoakilli.ui.whatsapp

import com.mrzekai.depoakilli.model.WhatsAppMediaCategory
import com.mrzekai.depoakilli.model.WhatsAppMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppUiLogicTest {
    private fun item(id: String, mime: String, path: String, category: WhatsAppMediaCategory) =
        WhatsAppMediaItem(id, "file:///$path/$id", id, 1, mime, 0, path, category)

    @Test
    fun `groups and directions`() {
        val sent = item("a", "image/jpeg", "WhatsApp/Media/WhatsApp Images/Sent", WhatsAppMediaCategory.IMAGES)
        val received = item("b", "image/jpeg", "WhatsApp/Media/WhatsApp Images", WhatsAppMediaCategory.IMAGES)
        val video = item("c", "video/mp4", "WhatsApp/Media/WhatsApp Video", WhatsAppMediaCategory.VIDEOS)
        val doc = item("d", "application/pdf", "WhatsApp/Media/WhatsApp Documents", WhatsAppMediaCategory.DOCUMENTS)
        assertTrue(isSentWhatsAppItem(sent))
        assertFalse(isSentWhatsAppItem(received))
        assertEquals(WhatsAppUiGroup.IMAGES, uiGroupOf(sent))
        assertEquals(WhatsAppUiGroup.VIDEOS, uiGroupOf(video))
        assertEquals(WhatsAppUiGroup.DOCUMENTS_OTHER, uiGroupOf(doc))
        val all = listOf(sent, received, video, doc)
        assertEquals(listOf("a"), filterWhatsAppItems(all, WhatsAppUiGroup.IMAGES, WhatsAppDirectionFilter.SENT).map { it.id })
        assertEquals(listOf("b"), filterWhatsAppItems(all, WhatsAppUiGroup.IMAGES, WhatsAppDirectionFilter.INCOMING).map { it.id })
        assertEquals(listOf("a", "b"), filterWhatsAppItems(all, WhatsAppUiGroup.IMAGES, WhatsAppDirectionFilter.ALL).map { it.id })
    }
}
