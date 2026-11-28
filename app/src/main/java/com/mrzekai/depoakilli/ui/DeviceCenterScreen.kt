package com.mrzekai.depoakilli.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.ScanFocus
import com.mrzekai.depoakilli.model.SuggestionTool
import com.mrzekai.depoakilli.model.ToolSuggestion
import com.mrzekai.depoakilli.ui.common.BlackCard
import com.mrzekai.depoakilli.ui.common.IconChip
import com.mrzekai.depoakilli.ui.common.MakeRoomShapes
import com.mrzekai.depoakilli.ui.common.PageFrame
import com.mrzekai.depoakilli.ui.common.PageTitle
import com.mrzekai.depoakilli.ui.common.PillButton
import com.mrzekai.depoakilli.ui.common.PillStyle
import com.mrzekai.depoakilli.ui.theme.AppWeights
import com.mrzekai.depoakilli.ui.theme.Black
import com.mrzekai.depoakilli.ui.theme.BlackChip
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft

internal enum class LegalPage(@StringRes val titleRes: Int) {
    PRIVACY(R.string.privacy_policy),
    TERMS(R.string.terms_of_service),
    ABOUT(R.string.about_app),
}

/** What a Tools row shows on its right (D4). */
private sealed interface ToolValue {
    /** The suggested amount: the only Lime number (C1). */
    data class Suggested(val bytes: Long) : ToolValue

    /** A plain fact in OnBlack ("16 unused", "85% full"). */
    data class Fact(val text: String) : ToolValue

    /** No Smart scan (or data) yet: a thin line, never "0". */
    data object Unknown : ToolValue

    /** Scanned, nothing to suggest: empty. */
    data object Empty : ToolValue
}

/** One full-width row of Tools (Faz 5D, D4). */
private data class ToolRow(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val value: ToolValue,
    /** Longer description for TalkBack. */
    val description: String,
    val onClick: () -> Unit,
)

/**
 * Tools (T5.7, Faz 5C C4, Faz 5D D4) on Lime: the 44 sp title and six
 * full-width black rows that share the height of the page (at least 96 dp
 * each). A row's Lime number is the suggested amount from [ToolSuggestion] —
 * the same call the tool screen makes (C1); Apps and Storage show a plain
 * fact instead. Before the first Smart scan a thin line stands in.
 */
@Composable
internal fun DeviceCenterScreen(
    state: CleanerUiState,
    onScan: (ScanFocus) -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onOpenWhatsApp: () -> Unit,
    onOpenAppManager: () -> Unit,
    modifier: Modifier = Modifier,
    /** Faz 3A-2: about how much shrinking saves (null before the first look); the row needs the switch. */
    videoSavingBytes: Long? = null,
    onOpenVideos: (() -> Unit)? = null,
    /** Faz 3B-1: bytes of the similar frames offered (null before the first look); the row needs the switch. */
    similarBytes: Long? = null,
    onOpenSimilar: (() -> Unit)? = null,
) {
    val smartPlan = state.smartSummary()?.plan
    fun suggested(tool: SuggestionTool): ToolValue =
        smartPlan?.let { plan ->
            ToolSuggestion.suggestedBytes(tool, plan).takeIf { it > 0L }?.let { ToolValue.Suggested(it) } ?: ToolValue.Empty
        } ?: ToolValue.Unknown
    val unusedApps = state.findingsUnusedApps()
    val appsValue = unusedApps?.let { apps ->
        ToolValue.Fact(pluralStringResource(R.plurals.tools_apps_unused, apps.size, apps.size))
    } ?: ToolValue.Unknown
    val usedPercent = HomeLogic.usedPercent(state.storage.totalBytes, state.storage.availableBytes)
    val storageValue = usedPercent?.let { percent ->
        val formatted = java.text.NumberFormat.getPercentInstance(LocalConfiguration.current.locales[0]).format(percent / 100.0)
        ToolValue.Fact(stringResource(R.string.tools_storage_full, formatted))
    } ?: ToolValue.Unknown
    val rows = listOf(
        ToolRow(
            stringResource(R.string.category_large_files),
            stringResource(R.string.tools_row_large_sub),
            Icons.Outlined.VideoFile,
            suggested(SuggestionTool.LARGE_FILES),
            stringResource(R.string.large_files_tool_subtitle_v050),
        ) { onScan(ScanFocus.LARGE_FILES) },
    ) + listOfNotNull(
        // Faz 3A-2: shrinking videos without deleting; shown only while the switch is on.
        onOpenVideos?.takeIf { com.mrzekai.depoakilli.pro.ProGainEstimator.VIDEO_COMPRESSION_AVAILABLE }?.let { open ->
            ToolRow(
                stringResource(R.string.video_title),
                stringResource(R.string.video_tools_detail),
                Icons.Outlined.VideoFile,
                when (videoSavingBytes) {
                    null -> ToolValue.Unknown
                    0L -> ToolValue.Empty
                    else -> ToolValue.Suggested(videoSavingBytes)
                },
                stringResource(R.string.video_tools_detail),
                open,
            )
        },
    ) + listOf(
        ToolRow(
            stringResource(R.string.scan_focus_downloads),
            stringResource(R.string.tools_row_downloads_sub),
            Icons.Outlined.Download,
            suggested(SuggestionTool.DOWNLOADS),
            stringResource(R.string.tools_downloads_subtitle) + " " + stringResource(R.string.tools_apk_subtitle),
        ) { onScan(ScanFocus.DOWNLOADS) },
        ToolRow(
            stringResource(R.string.category_whatsapp),
            stringResource(R.string.tools_row_whatsapp_sub),
            Icons.Outlined.Chat,
            suggested(SuggestionTool.WHATSAPP),
            stringResource(R.string.whatsapp_cleaner_subtitle_v050),
            onOpenWhatsApp,
        ),
        ToolRow(
            stringResource(R.string.category_duplicates),
            stringResource(R.string.tools_row_duplicates_sub),
            Icons.Outlined.ContentCopy,
            suggested(SuggestionTool.DUPLICATES),
            stringResource(R.string.duplicates_tool_subtitle_v050),
        ) { onScan(ScanFocus.DUPLICATES) },
    ) + listOfNotNull(
        // Faz 3B-1: similar photos (on-device model); shown only while the switch is on.
        onOpenSimilar?.takeIf { com.mrzekai.depoakilli.pro.ProGainEstimator.SIMILAR_PHOTOS_AVAILABLE }?.let { open ->
            ToolRow(
                stringResource(R.string.similar_title),
                stringResource(R.string.similar_tools_detail),
                Icons.Outlined.PhotoLibrary,
                when (similarBytes) {
                    null -> ToolValue.Unknown
                    0L -> ToolValue.Empty
                    else -> ToolValue.Suggested(similarBytes)
                },
                stringResource(R.string.similar_tools_detail),
                open,
            )
        },
    ) + listOf(
        // Apps and app cache share one screen with two tabs (C4).
        ToolRow(
            stringResource(R.string.home_hard_apps),
            stringResource(R.string.tools_row_apps_sub),
            Icons.Outlined.Apps,
            appsValue,
            stringResource(R.string.app_manager_subtitle) + " " + stringResource(R.string.cache_manager_subtitle),
            onOpenAppManager,
        ),
        // Storage Analyzer and Storage change share one screen with two tabs (C4).
        ToolRow(
            stringResource(R.string.tool_storage),
            stringResource(R.string.tools_row_storage_sub),
            Icons.Outlined.Storage,
            storageValue,
            stringResource(R.string.storage_analyzer_subtitle) + " " + stringResource(R.string.tools_storage_change_subtitle),
        ) { onScan(ScanFocus.ANALYZE) },
    )
    PageFrame(modifier = modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // D4: the rows share the height under the title (and the access card).
            val header = if (state.hasAllFilesAccess) 80.dp else 240.dp
            val spacing = 10.dp
            val rowHeight = ((maxHeight - header - 32.dp - spacing * rows.size) / rows.size).coerceAtLeast(96.dp)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // The last row ends above the banner and the menu, never under them.
                contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(spacing),
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
                items(rows, key = { row -> "tools-" + row.title }) { row ->
                    ToolRowCard(row, Modifier.fillMaxWidth().height(rowHeight))
                }
            }
        }
    }
}

@Composable
private fun ToolRowCard(row: ToolRow, modifier: Modifier = Modifier) {
    val valueText = when (val value = row.value) {
        is ToolValue.Suggested -> ByteFormatter.format(value.bytes)
        is ToolValue.Fact -> value.text
        ToolValue.Unknown, ToolValue.Empty -> null
    }
    Surface(
        onClick = row.onClick,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = listOfNotNull(row.title, valueText, row.description).joinToString(". ")
        },
        color = Black,
        contentColor = OnBlack,
        shape = MakeRoomShapes.Card,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(BlackChip),
                contentAlignment = Alignment.Center,
            ) {
                Icon(row.icon, contentDescription = null, tint = Lime, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                // K4: one line that shrinks to fit ("Uygulamalar" next to "0 kullanılmayan"),
                // never "…" and never a word cut in two.
                BasicText(
                    row.title,
                    style = LocalTextStyle.current.merge(
                        TextStyle(color = OnBlack, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = AppWeights.Title),
                    ),
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 13.sp, maxFontSize = 20.sp, stepSize = 1f.sp),
                )
                Text(
                    row.subtitle,
                    color = OnBlackSoft,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            when (val value = row.value) {
                is ToolValue.Suggested -> Text(
                    ByteFormatter.format(value.bytes),
                    color = Lime,
                    fontSize = 18.sp,
                    fontWeight = AppWeights.Giant,
                    maxLines = 1,
                )
                is ToolValue.Fact -> Text(
                    value.text,
                    color = OnBlack,
                    fontSize = 15.sp,
                    fontWeight = AppWeights.Strong,
                    maxLines = 1,
                )
                // Before the first Smart scan: a thin line, never "0" (D4).
                ToolValue.Unknown -> Box(Modifier.width(18.dp).height(2.dp).background(OnBlackSoft))
                ToolValue.Empty -> Unit
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = OnBlackSoft,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

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
