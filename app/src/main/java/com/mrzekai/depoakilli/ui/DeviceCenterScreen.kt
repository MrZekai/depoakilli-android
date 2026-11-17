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
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
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
import com.mrzekai.depoakilli.model.ScanFocus
import com.mrzekai.depoakilli.model.ToolSuggestion
import com.mrzekai.depoakilli.model.SuggestionTool
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
 * Tools (T5.7, Faz 5C C4) on Lime: the 44 sp title and six black tiles in a
 * 3 × 2 grid. A tile's Lime number is the suggested amount from
 * [ToolSuggestion] — the same call the tool screen makes (C1). Totals that are
 * not suggestions (apps, storage) are not shown as a Lime number.
 */
@Composable
internal fun DeviceCenterScreen(
    state: CleanerUiState,
    onScan: (ScanFocus) -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onOpenWhatsApp: () -> Unit,
    onOpenAppManager: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val smartPlan = state.smartSummary()?.plan
    fun suggested(tool: SuggestionTool): Long? =
        smartPlan?.let { ToolSuggestion.suggestedBytes(tool, it) }?.takeIf { it > 0L }
    val tiles = listOf(
        ToolTile(stringResource(R.string.category_large_files), Icons.Outlined.VideoFile, suggested(SuggestionTool.LARGE_FILES), stringResource(R.string.large_files_tool_subtitle_v050)) {
            onScan(ScanFocus.LARGE_FILES)
        },
        ToolTile(stringResource(R.string.scan_focus_downloads), Icons.Outlined.Download, suggested(SuggestionTool.DOWNLOADS), stringResource(R.string.tools_downloads_subtitle) + " " + stringResource(R.string.tools_apk_subtitle)) {
            onScan(ScanFocus.DOWNLOADS)
        },
        ToolTile(stringResource(R.string.category_whatsapp), Icons.Outlined.Chat, suggested(SuggestionTool.WHATSAPP), stringResource(R.string.whatsapp_cleaner_subtitle_v050), onOpenWhatsApp),
        ToolTile(stringResource(R.string.category_duplicates), Icons.Outlined.ContentCopy, suggested(SuggestionTool.DUPLICATES), stringResource(R.string.duplicates_tool_subtitle_v050)) {
            onScan(ScanFocus.DUPLICATES)
        },
        // Apps and app cache share one screen with two tabs (C4).
        ToolTile(stringResource(R.string.home_hard_apps), Icons.Outlined.Apps, null, stringResource(R.string.app_manager_subtitle) + " " + stringResource(R.string.cache_manager_subtitle), onOpenAppManager),
        // Storage Analyzer and Storage change share one screen with two tabs (C4).
        ToolTile(stringResource(R.string.tool_storage), Icons.Outlined.Storage, null, stringResource(R.string.storage_analyzer_subtitle) + " " + stringResource(R.string.tools_storage_change_subtitle)) {
            onScan(ScanFocus.ANALYZE)
        },
    )
    PageFrame(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // C4: the last row ends above the banner and the menu, never under them.
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 32.dp),
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
