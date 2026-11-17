package com.mrzekai.depoakilli.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.ScanFocus
import com.mrzekai.depoakilli.ui.common.BlackCard
import com.mrzekai.depoakilli.ui.common.IconChip
import com.mrzekai.depoakilli.ui.common.PageFrame
import com.mrzekai.depoakilli.ui.common.PageTitle
import com.mrzekai.depoakilli.ui.common.PillButton
import com.mrzekai.depoakilli.ui.common.PillStyle
import com.mrzekai.depoakilli.ui.theme.AppWeights
import com.mrzekai.depoakilli.ui.theme.Black
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft

internal enum class LegalPage(@StringRes val titleRes: Int) {
    PRIVACY(R.string.privacy_policy),
    TERMS(R.string.terms_of_service),
    ABOUT(R.string.about_app),
}

/** One tile of the Tools grid (T5.7). */
private data class ToolTile(
    val title: String,
    val icon: ImageVector,
    val bytes: Long?,
    /** What the tool does; read by TalkBack (the tile itself shows no subtitle). */
    val description: String,
    val onClick: () -> Unit,
)

/**
 * Tools (T5.7) on Lime: the 44 sp title and a two-column grid of 92 dp black
 * tiles — Lime icon top left, Lime size top right when it is known (never a
 * made-up "0"), the name below. No subtitles or section descriptions.
 */
@Composable
internal fun DeviceCenterScreen(
    state: CleanerUiState,
    onScan: (ScanFocus) -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onOpenWhatsApp: () -> Unit,
    onOpenCache: () -> Unit,
    onOpenAppManager: () -> Unit,
    onOpenStorageChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val known = state.dashboardSnapshotAtMillis > 0L
    fun category(category: CleanCategory): Long? =
        if (known) state.dashboardCategoryBytes[category]?.takeIf { it > 0L } else null
    val usedBytes = (state.storage.totalBytes - state.storage.availableBytes).takeIf { state.storage.totalBytes > 0L }
    val tiles = listOf(
        ToolTile(stringResource(R.string.category_large_files), Icons.Outlined.VideoFile, category(CleanCategory.LARGE_FILE), stringResource(R.string.large_files_tool_subtitle_v050)) {
            onScan(ScanFocus.LARGE_FILES)
        },
        ToolTile(stringResource(R.string.category_whatsapp), Icons.Outlined.Chat, category(CleanCategory.WHATSAPP_MEDIA), stringResource(R.string.whatsapp_cleaner_subtitle_v050), onOpenWhatsApp),
        ToolTile(stringResource(R.string.scan_focus_downloads), Icons.Outlined.Download, category(CleanCategory.OLD_DOWNLOAD), stringResource(R.string.tools_downloads_subtitle)) {
            onScan(ScanFocus.DOWNLOADS)
        },
        ToolTile(
            stringResource(R.string.tool_app_cache),
            Icons.Outlined.CleaningServices,
            state.appCache.totalCacheBytes.takeIf { state.appCache.accessGranted && it > 0L },
            stringResource(R.string.cache_manager_subtitle),
            onOpenCache,
        ),
        ToolTile(stringResource(R.string.category_screenshots), Icons.Outlined.Screenshot, category(CleanCategory.SCREENSHOT), stringResource(R.string.screenshots_tool_subtitle)) {
            onScan(ScanFocus.SCREENSHOTS)
        },
        ToolTile(stringResource(R.string.category_duplicates), Icons.Outlined.ContentCopy, category(CleanCategory.DUPLICATE), stringResource(R.string.duplicates_tool_subtitle_v050)) {
            onScan(ScanFocus.DUPLICATES)
        },
        ToolTile(stringResource(R.string.category_apk_packages), Icons.Outlined.Android, category(CleanCategory.APK_PACKAGE), stringResource(R.string.tools_apk_subtitle)) {
            onScan(ScanFocus.APKS)
        },
        ToolTile(
            stringResource(R.string.home_hard_apps),
            Icons.Outlined.Apps,
            state.installedApps.filterNot { it.systemApp }.sumOf { it.totalBytes }.takeIf { state.hasUsageAccess && it > 0L },
            stringResource(R.string.app_manager_subtitle),
            onOpenAppManager,
        ),
        ToolTile(stringResource(R.string.storage_analyzer_title), Icons.Outlined.Storage, usedBytes, stringResource(R.string.storage_analyzer_subtitle)) {
            onScan(ScanFocus.ANALYZE)
        },
        ToolTile(stringResource(R.string.storage_change_title), Icons.Outlined.Timeline, null, stringResource(R.string.tools_storage_change_subtitle), onOpenStorageChange),
    )
    PageFrame(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "tools-title") {
                PageTitle(stringResource(R.string.tab_tools), modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            }
            if (!state.hasAllFilesAccess) {
                item(key = "tools-access") {
                    PermissionToolCard(
                        title = stringResource(R.string.all_files_access_title),
                        subtitle = stringResource(R.string.all_files_access_description),
                        action = stringResource(R.string.grant_access),
                        onClick = onRequestAllFilesAccess,
                    )
                }
            }
            items(tiles.chunked(2), key = { row -> "tools-" + row.first().title }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { tile -> ToolTileCard(tile, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ToolTileCard(tile: ToolTile, modifier: Modifier = Modifier) {
    Surface(
        onClick = tile.onClick,
        modifier = modifier
            .heightIn(min = 92.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(
                    tile.title,
                    tile.bytes?.let(ByteFormatter::format),
                    tile.description,
                ).joinToString(". ")
            },
        color = Black,
        contentColor = OnBlack,
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(tile.icon, contentDescription = null, tint = Lime, modifier = Modifier.size(26.dp))
                Spacer(Modifier.weight(1f))
                tile.bytes?.let { bytes ->
                    Text(ByteFormatter.format(bytes), color = Lime, fontWeight = AppWeights.Giant, fontSize = 14.sp)
                }
            }
            Text(tile.title, color = OnBlack, fontWeight = AppWeights.Title, fontSize = 15.sp, lineHeight = 19.sp)
        }
    }
}

/** "Allow storage access" card above the tools when access is missing. */
@Composable
internal fun PermissionToolCard(title: String, subtitle: String, action: String, onClick: () -> Unit) {
    BlackCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(Icons.Outlined.Security)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = OnBlack, fontWeight = AppWeights.Title)
                Text(subtitle, color = OnBlackSoft, fontSize = 13.sp, lineHeight = 17.sp)
            }
        }
        PillButton(text = action, onClick = onClick, style = PillStyle.LIME, modifier = Modifier.padding(top = 12.dp))
    }
}
