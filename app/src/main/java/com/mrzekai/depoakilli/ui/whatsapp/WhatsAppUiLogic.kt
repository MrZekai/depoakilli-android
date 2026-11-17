package com.mrzekai.depoakilli.ui.whatsapp

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.WhatsAppLibrarySummary
import com.mrzekai.depoakilli.model.WhatsAppMediaCategory
import com.mrzekai.depoakilli.model.WhatsAppMediaItem
import com.mrzekai.depoakilli.ui.*
import com.mrzekai.depoakilli.ui.common.FileVisuals
import com.mrzekai.depoakilli.ui.theme.Purple500
import com.mrzekai.depoakilli.ui.theme.WhatsAppGreen
import java.text.DateFormat
import java.util.Date

internal fun filterWhatsAppItems(
    items: List<WhatsAppMediaItem>,
    group: WhatsAppUiGroup,
    direction: WhatsAppDirectionFilter,
): List<WhatsAppMediaItem> = items.filter { item ->
    uiGroupOf(item) == group && when (direction) {
        WhatsAppDirectionFilter.ALL -> true
        WhatsAppDirectionFilter.INCOMING -> !isSentWhatsAppItem(item)
        WhatsAppDirectionFilter.SENT -> isSentWhatsAppItem(item)
    }
}

internal fun toggleUiGroup(
    summary: WhatsAppLibrarySummary,
    group: WhatsAppUiGroup,
    direction: WhatsAppDirectionFilter,
    onToggleCategory: (WhatsAppMediaCategory) -> Unit,
    onToggleItem: (String) -> Unit,
) {
    val visible = filterWhatsAppItems(summary.items, group, direction)
    if (visible.isEmpty()) return
    val shouldSelect = visible.any { !it.selected }

    // If the visible group exactly covers whole underlying categories, use the ViewModel's category toggle.
    val categories = visible.map(WhatsAppMediaItem::category).toSet()
    val canUseCategoryToggle = categories.all { category ->
        val allCategoryItems = summary.items.filter { it.category == category }
        allCategoryItems.isNotEmpty() && allCategoryItems.all { it in visible }
    }
    if (canUseCategoryToggle) {
        categories.forEach(onToggleCategory)
        return
    }

    visible.filter { it.selected != shouldSelect }.forEach { onToggleItem(it.id) }
}

internal fun uiGroupOf(item: WhatsAppMediaItem): WhatsAppUiGroup = when {
    item.mimeType.startsWith("image/") ||
        item.category == WhatsAppMediaCategory.IMAGES ||
        item.category == WhatsAppMediaCategory.STICKERS_GIFS -> WhatsAppUiGroup.IMAGES

    item.mimeType.startsWith("video/") || item.category == WhatsAppMediaCategory.VIDEOS -> WhatsAppUiGroup.VIDEOS
    else -> WhatsAppUiGroup.DOCUMENTS_OTHER
}

internal fun isSentWhatsAppItem(item: WhatsAppMediaItem): Boolean {
    val segments = item.relativePath.replace('\\', '/').split('/').filter(String::isNotBlank)
    return segments.any { it.equals("Sent", ignoreCase = true) }
}

internal fun groupTitleRes(group: WhatsAppUiGroup): Int = when (group) {
    WhatsAppUiGroup.IMAGES -> R.string.whatsapp_group_images
    WhatsAppUiGroup.VIDEOS -> R.string.whatsapp_group_videos
    WhatsAppUiGroup.DOCUMENTS_OTHER -> R.string.whatsapp_group_documents_more
}

internal fun groupIcon(group: WhatsAppUiGroup): ImageVector = when (group) {
    WhatsAppUiGroup.IMAGES -> Icons.Outlined.PhotoLibrary
    WhatsAppUiGroup.VIDEOS -> Icons.Outlined.VideoFile
    WhatsAppUiGroup.DOCUMENTS_OTHER -> Icons.Outlined.Description
}

internal fun groupAccent(group: WhatsAppUiGroup): Color = when (group) {
    WhatsAppUiGroup.IMAGES -> WhatsAppGreen
    WhatsAppUiGroup.VIDEOS -> Purple500
    WhatsAppUiGroup.DOCUMENTS_OTHER -> WaAmber
}

@Composable
internal fun localDate(millis: Long): String = remember(millis) {
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
}

internal fun compactPath(path: String): String {
    val parts = path.replace('\\', '/').trim('/').split('/').filter(String::isNotBlank)
    return when {
        parts.isEmpty() -> "—"
        parts.size <= 3 -> parts.joinToString(" › ")
        else -> parts.takeLast(3).joinToString(" › ")
    }
}

internal fun whatsAppFileIcon(item: WhatsAppMediaItem): ImageVector =
    FileVisuals.icon(item.mimeType, item.name, voiceNote = item.category == WhatsAppMediaCategory.VOICE_NOTES)
