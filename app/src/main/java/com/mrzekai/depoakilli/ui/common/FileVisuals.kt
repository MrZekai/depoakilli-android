package com.mrzekai.depoakilli.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.ui.graphics.vector.ImageVector

/** One icon rule for files, shared by Smart Clean, the tools and WhatsApp. */
internal object FileVisuals {
    private val ARCHIVES = setOf("zip", "rar", "7z", "tar", "gz")
    private val DOCUMENTS = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "odt", "rtf")

    fun icon(mimeType: String, name: String, voiceNote: Boolean = false): ImageVector {
        val mime = mimeType.lowercase()
        val extension = name.substringAfterLast('.', "").lowercase()
        return when {
            mime.startsWith("image/") -> Icons.Outlined.PhotoLibrary
            mime.startsWith("video/") -> Icons.Outlined.VideoFile
            mime.startsWith("audio/") || voiceNote -> Icons.Outlined.MusicNote
            extension == "apk" -> Icons.Outlined.Android
            extension in ARCHIVES -> Icons.Outlined.Folder
            mime.startsWith("text/") || mime.contains("pdf") || extension in DOCUMENTS -> Icons.Outlined.Description
            else -> Icons.Outlined.InsertDriveFile
        }
    }
}
