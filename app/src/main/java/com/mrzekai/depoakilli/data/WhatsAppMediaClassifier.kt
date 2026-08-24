package com.mrzekai.depoakilli.data

import com.mrzekai.depoakilli.model.WhatsAppMediaCategory

object WhatsAppMediaClassifier {
    fun classify(
        name: String,
        mimeType: String,
        relativePath: String,
    ): WhatsAppMediaCategory {
        val mime = StoragePathRules.normalizeText(mimeType)
        val lowercaseName = StoragePathRules.normalizeText(name)
        val path = StoragePathRules.normalizePath(relativePath)
        return when {
            "voice notes" in path || "ptt-" in lowercaseName ->
                WhatsAppMediaCategory.VOICE_NOTES
            "sticker" in path || lowercaseName.endsWith(".webp") || mime == "image/webp" ->
                WhatsAppMediaCategory.STICKERS_GIFS
            lowercaseName.endsWith(".gif") || mime == "image/gif" ->
                WhatsAppMediaCategory.STICKERS_GIFS
            mime.startsWith("image/") -> WhatsAppMediaCategory.IMAGES
            mime.startsWith("video/") -> WhatsAppMediaCategory.VIDEOS
            mime.startsWith("audio/") -> WhatsAppMediaCategory.AUDIO
            mime.startsWith("text/") || mime.startsWith("application/") ||
                lowercaseName.substringAfterLast('.', "") in DOCUMENT_EXTENSIONS ->
                WhatsAppMediaCategory.DOCUMENTS
            else -> WhatsAppMediaCategory.OTHER
        }
    }

    private val DOCUMENT_EXTENSIONS = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf",
        "csv", "zip", "rar", "7z", "apk", "vcf",
    )
}
