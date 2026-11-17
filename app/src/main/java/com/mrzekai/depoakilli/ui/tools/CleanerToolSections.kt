package com.mrzekai.depoakilli.ui.tools

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.ScanFocus
import com.mrzekai.depoakilli.model.ScanSummary
import com.mrzekai.depoakilli.ui.*
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

@Composable
internal fun premiumSections(focus: ScanFocus, summary: ScanSummary): List<ToolSection> {
    val items = summary.items
    fun section(
        key: String,
        title: String,
        list: List<CleanableItem>,
        accent: Color,
        subtitle: String = "${list.size} • ${ByteFormatter.format(list.sumOf(CleanableItem::sizeBytes))}",
    ): ToolSection = ToolSection(
        key = key,
        title = title,
        subtitle = subtitle,
        accent = accent,
        items = list,
    )

    return when (focus) {
        ScanFocus.DUPLICATES -> {
            // Group by the protected original's URI: two different originals
            // that merely share a file name must not be merged into one group.
            val unknownOriginal = stringResource(R.string.premium_duplicate_unknown_original)
            val grouped = items
                .groupBy { it.protectedDuplicateUri ?: it.protectedDuplicateName.orEmpty() }
                .values
                .sortedByDescending { list -> list.sumOf(CleanableItem::sizeBytes) }
            val visibleGroups = grouped.take(5).mapIndexed { index, list ->
                val kept = list.first().protectedDuplicateName ?: unknownOriginal
                // P3: the original's name on one line (ellipsized by the header),
                // "1 copy · 192 MB" under it.
                section(
                    key = "duplicate-$index-$kept",
                    title = kept,
                    list = list,
                    accent = if (index % 2 == 0) ToolPurple else ToolCyan,
                    subtitle = pluralStringResource(
                        R.plurals.premium_duplicate_copies,
                        list.size,
                        list.size,
                        ByteFormatter.format(list.sumOf(CleanableItem::sizeBytes)),
                    ),
                )
            }
            val remaining = grouped.drop(5).flatten()
            if (remaining.isEmpty()) visibleGroups else visibleGroups + section(
                key = "duplicate-more",
                title = stringResource(R.string.premium_duplicate_more_groups),
                list = remaining,
                accent = ToolGreen,
            )
        }
        ScanFocus.LARGE_FILES -> {
            val videos = items.filter { it.mimeType.startsWith("video/") }
            val images = items.filter { it.mimeType.startsWith("image/") }
            val docs = items.filter { isDocumentOrArchive(it) }
            val other = items - videos.toSet() - images.toSet() - docs.toSet()
            listOfNotNull(
                videos.takeIf { it.isNotEmpty() }?.let { section("large-videos", stringResource(R.string.storage_type_videos), it, ToolPurple) },
                images.takeIf { it.isNotEmpty() }?.let { section("large-images", stringResource(R.string.storage_type_images), it, ToolCyan) },
                docs.takeIf { it.isNotEmpty() }?.let { section("large-docs", stringResource(R.string.premium_documents_archives), it, ToolAmber) },
                other.takeIf { it.isNotEmpty() }?.let { section("large-other", stringResource(R.string.storage_type_other), it, ToolGreen) },
            )
        }
        ScanFocus.APKS -> {
            val now = System.currentTimeMillis()
            val old = items.filter { ageDays(it.modifiedAtMillis, now) >= 30 }
            val recent = items - old.toSet()
            listOfNotNull(
                old.takeIf { it.isNotEmpty() }?.let { section("apk-old", stringResource(R.string.premium_apk_old), it, ToolGreen) },
                recent.takeIf { it.isNotEmpty() }?.let { section("apk-recent", stringResource(R.string.premium_apk_recent), it, ToolCyan) },
            )
        }
        ScanFocus.MEDIA -> {
            val duplicateMedia = items.filter { it.assessment.category == CleanCategory.DUPLICATE }
            val remainingMedia = items.filterNot { it.assessment.category == CleanCategory.DUPLICATE }
            val images = remainingMedia.filter { it.mimeType.startsWith("image/") }
            val videos = remainingMedia.filter { it.mimeType.startsWith("video/") }
            val other = remainingMedia.filterNot { it in images || it in videos }
            listOfNotNull(
                images.takeIf { it.isNotEmpty() }?.let { section("media-images", stringResource(R.string.storage_type_images), it, ToolCyan) },
                videos.takeIf { it.isNotEmpty() }?.let { section("media-videos", stringResource(R.string.storage_type_videos), it, ToolPurple) },
                duplicateMedia.takeIf { it.isNotEmpty() }?.let { section("media-dupes", stringResource(R.string.category_duplicates), it, ToolGreen) },
                other.takeIf { it.isNotEmpty() }?.let { section("media-other", stringResource(R.string.premium_media_review), it, ToolAmber) },
            )
        }
        ScanFocus.JUNK -> listOfNotNull(
            items.takeIf { it.isNotEmpty() }?.let { section("junk-safe", stringResource(R.string.category_junk), it, ToolCyan) },
        )
        ScanFocus.DOWNLOADS -> summary.byCategory.entries.map { (category, list) ->
            section("downloads-${category.name}", stringResource(category.titleRes), list, categoryAccent(category))
        }
        ScanFocus.DEEP -> summary.byCategory.entries.map { (category, list) ->
            section("deep-${category.name}", stringResource(category.titleRes), list, categoryAccent(category))
        }
        else -> summary.byCategory.entries.map { (category, list) ->
            section("generic-${category.name}", stringResource(category.titleRes), list, categoryAccent(category))
        }
    }
}

internal fun sortItems(items: List<CleanableItem>, sortMode: ToolSortMode): List<CleanableItem> = when (sortMode) {
    ToolSortMode.LARGEST -> items.sortedByDescending(CleanableItem::sizeBytes)
    ToolSortMode.NEWEST -> items.sortedByDescending(CleanableItem::modifiedAtMillis)
    ToolSortMode.SELECTED -> items.sortedWith(compareByDescending<CleanableItem> { it.selected }.thenByDescending { it.sizeBytes })
}

internal fun setSectionSelection(
    items: List<CleanableItem>,
    onSetItemsSelected: (ids: Set<String>, selected: Boolean, bulk: Boolean) -> Unit,
) {
    if (items.isEmpty()) return
    val targetSelected = !items.all(CleanableItem::selected)
    onSetItemsSelected(items.asSequence().map(CleanableItem::id).toSet(), targetSelected, true)
}

internal fun toolConfig(focus: ScanFocus): ToolVisualConfig = when (focus) {
    ScanFocus.DUPLICATES -> ToolVisualConfig(R.string.scan_focus_duplicates, R.string.premium_duplicates_subtitle, Icons.Outlined.ContentCopy, ToolPurple, ToolCyan)
    ScanFocus.LARGE_FILES -> ToolVisualConfig(R.string.scan_focus_large, R.string.premium_large_subtitle, Icons.Outlined.VideoFile, ToolAmber, ToolCyan)
    ScanFocus.APKS -> ToolVisualConfig(R.string.scan_focus_apks, R.string.premium_apk_subtitle, Icons.Outlined.Android, ToolGreen, ToolCyan)
    ScanFocus.MEDIA -> ToolVisualConfig(R.string.scan_focus_media, R.string.premium_media_subtitle, Icons.Outlined.PhotoLibrary, ToolCyan, ToolPurple)
    ScanFocus.SCREENSHOTS -> ToolVisualConfig(R.string.category_screenshots, R.string.screenshots_tool_subtitle, Icons.Outlined.PhotoLibrary, ToolCyan, ToolCyan)
    ScanFocus.JUNK -> ToolVisualConfig(R.string.scan_focus_junk, R.string.premium_junk_subtitle, Icons.Outlined.DeleteSweep, ToolCyan, ToolGreen)
    ScanFocus.DOWNLOADS -> ToolVisualConfig(R.string.scan_focus_downloads, R.string.premium_downloads_subtitle, Icons.Outlined.Download, ToolAmber, ToolCyan)
    ScanFocus.DEEP -> ToolVisualConfig(R.string.scan_focus_deep, R.string.premium_deep_subtitle, Icons.Outlined.AutoAwesome, ToolGreen, ToolPurple)
    else -> ToolVisualConfig(R.string.scan_focus_smart, R.string.safe_ai_cleaning_description_v050, Icons.Outlined.CleaningServices, ToolCyan, ToolGreen)
}

internal fun sectionIcon(focus: ScanFocus, section: ToolSection): ImageVector = when {
    focus == ScanFocus.DUPLICATES -> Icons.Outlined.ContentCopy
    focus == ScanFocus.APKS -> Icons.Outlined.Android
    section.items.any { it.mimeType.startsWith("video/") } -> Icons.Outlined.VideoFile
    section.items.any { it.mimeType.startsWith("image/") } -> Icons.Outlined.PhotoLibrary
    section.items.any { it.mimeType.startsWith("audio/") } -> Icons.Outlined.MusicNote
    section.items.any { isDocumentOrArchive(it) } -> Icons.Outlined.Description
    else -> Icons.Outlined.Folder
}

internal fun categoryAccent(category: CleanCategory): Color = when (category) {
    CleanCategory.JUNK -> ToolCyan
    CleanCategory.DUPLICATE -> ToolPurple
    CleanCategory.SCREENSHOT -> ToolCyan
    CleanCategory.LARGE_FILE -> ToolAmber
    CleanCategory.OLD_DOWNLOAD -> ToolAmber
    CleanCategory.APK_PACKAGE -> ToolGreen
    CleanCategory.APP_CACHE -> ToolCyan
    CleanCategory.WHATSAPP_MEDIA -> ToolGreen
}

internal fun ageDays(modifiedAtMillis: Long, nowMillis: Long = System.currentTimeMillis()): Long {
    if (modifiedAtMillis <= 0L || modifiedAtMillis > nowMillis) return 0L
    return TimeUnit.MILLISECONDS.toDays(nowMillis - modifiedAtMillis)
}

internal fun localDate(millis: Long): String = if (millis <= 0L) "—" else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

internal fun compactPath(path: String): String {
    val parts = path.replace('\\', '/').split('/').filter(String::isNotBlank)
    return when {
        parts.isEmpty() -> "—"
        parts.size <= 3 -> parts.joinToString(" › ")
        else -> parts.takeLast(3).joinToString(" › ")
    }
}

internal fun isDocumentOrArchive(item: CleanableItem): Boolean {
    val ext = item.name.substringAfterLast('.', "").lowercase()
    return item.mimeType.startsWith("text/") ||
        item.mimeType.contains("pdf") ||
        ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "zip", "rar", "7z", "tar", "gz")
}
