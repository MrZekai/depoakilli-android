package com.mrzekai.depoakilli.ui.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.ads.AdPolicy
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.ScanFocus
import com.mrzekai.depoakilli.model.ScanSummary
import com.mrzekai.depoakilli.ui.common.BlackCard
import com.mrzekai.depoakilli.ui.common.CardDivider
import com.mrzekai.depoakilli.ui.common.DeleteConfirmDialog
import com.mrzekai.depoakilli.ui.common.FileVisuals
import com.mrzekai.depoakilli.ui.common.GiantNumber
import com.mrzekai.depoakilli.ui.common.LinkButton
import com.mrzekai.depoakilli.ui.common.MakeRoomShapes
import com.mrzekai.depoakilli.ui.common.PageFrame
import com.mrzekai.depoakilli.ui.common.PageLabel
import com.mrzekai.depoakilli.ui.common.SelectionBar
import com.mrzekai.depoakilli.ui.common.bidiWrap
import com.mrzekai.depoakilli.ui.common.rememberGiantScale
import com.mrzekai.depoakilli.ui.media.rememberThumbnail
import com.mrzekai.depoakilli.ui.sectionSelectionState
import com.mrzekai.depoakilli.ui.theme.AppWeights
import com.mrzekai.depoakilli.ui.theme.Black
import com.mrzekai.depoakilli.ui.theme.BlackChip
import com.mrzekai.depoakilli.ui.theme.Ink
import com.mrzekai.depoakilli.ui.theme.InkSoft
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft

/** Rows shown per group before "View all" opens the full group. */
internal const val TOOL_ROWS_PER_SECTION = 20

/**
 * Tool results (T5.8) on Lime: the giant total found, sort chips, each group
 * as rows of one black card (60 dp: preview, name, folder and age, Lime
 * size, checkbox), the in-list native after the 4th row (lists of 6 rows or
 * more), and a sticky black bar with the selection and "Move to Trash". No
 * banner here. Bulk selection keeps the isBulkSelectable rules.
 */
@Composable
internal fun PremiumToolResults(
    focus: ScanFocus,
    summary: ScanSummary,
    cleanupInProgress: Boolean,
    onScan: () -> Unit,
    onToggleItem: (String) -> Unit,
    onSetItemsSelected: (ids: Set<String>, selected: Boolean, bulk: Boolean) -> Unit,
    onClean: (Set<String>?) -> Unit,
    listAd: @Composable () -> Unit = {},
) {
    val config = toolConfig(focus)
    var sortName by rememberSaveable(focus.name) { mutableStateOf(ToolSortMode.LARGEST.name) }
    var detailKey by rememberSaveable(focus.name) { mutableStateOf<String?>(null) }
    var previewId by rememberSaveable(focus.name) { mutableStateOf<String?>(null) }
    var showCleanupConfirmation by rememberSaveable(focus.name) { mutableStateOf(false) }
    val sortMode = ToolSortMode.valueOf(sortName)
    val scale = rememberGiantScale()

    val sections = premiumSections(focus, summary)
    val activeDetail = sections.firstOrNull { it.key == detailKey }
    val previewItem = summary.items.firstOrNull { it.id == previewId }

    // System Back closes an open group first, like the on-screen back arrow,
    // instead of leaving the whole tool.
    BackHandler(enabled = activeDetail != null) { detailKey = null }

    if (previewItem != null) {
        PremiumToolPreviewDialog(
            item = previewItem,
            accent = config.accent,
            onToggleSelection = { onToggleItem(previewItem.id) },
            onDismiss = { previewId = null },
        )
    }

    if (showCleanupConfirmation) {
        DeleteConfirmDialog(
            items = summary.selectedItems,
            inProgress = cleanupInProgress,
            onConfirm = {
                showCleanupConfirmation = false
                onClean(null)
            },
            onDismiss = { showCleanupConfirmation = false },
        )
    }

    if (activeDetail != null) {
        PremiumToolDetailPage(
            section = activeDetail,
            sortMode = sortMode,
            cleanupInProgress = cleanupInProgress,
            selectedCount = summary.selectedItems.size,
            selectedBytes = summary.selectedBytes,
            onSortMode = { sortName = it.name },
            onBack = { detailKey = null },
            onPreview = { previewId = it.id },
            onToggleItem = onToggleItem,
            onToggleAll = { setSectionSelection(activeDetail.items, onSetItemsSelected) },
            onClean = { showCleanupConfirmation = true },
        )
        return
    }

    PageFrame {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 16.dp),
            ) {
                item(key = "tool-head") {
                    Column(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        GiantNumber(ByteFormatter.format(summary.totalSuggestedBytes), scale, modifier = Modifier.fillMaxWidth())
                        PageLabel(stringResource(R.string.premium_tool_found_size), strong = true)
                        if (focus == ScanFocus.DUPLICATES) {
                            PageLabel(stringResource(R.string.premium_duplicates_original_safe))
                        }
                        if (focus == ScanFocus.APKS) {
                            PageLabel(stringResource(R.string.premium_apk_installer_note))
                        }
                    }
                }
                item(key = "tool-sort") {
                    PremiumSortRow(sortMode = sortMode, onChange = { sortName = it.name })
                }

                if (summary.items.isEmpty()) {
                    item(key = "tool-empty") {
                        BlackCard(
                            modifier = Modifier.padding(top = 12.dp),
                            contentPadding = PaddingValues(22.dp),
                        ) {
                            Text(
                                stringResource(R.string.premium_tool_nothing_found),
                                color = OnBlack,
                                fontWeight = AppWeights.Title,
                                fontSize = 18.sp,
                            )
                            Text(
                                stringResource(R.string.premium_tool_nothing_found_detail),
                                color = OnBlackSoft,
                                fontSize = 14.sp,
                            )
                        }
                    }
                } else {
                    val totalRows = sections.sumOf { minOf(it.items.size, TOOL_ROWS_PER_SECTION) }
                    var rowIndex = 0
                    sections.forEach { section ->
                        val rows = sortItems(section.items, sortMode)
                        toolSection(
                            section = section,
                            rows = rows.take(TOOL_ROWS_PER_SECTION),
                            hiddenCount = (rows.size - TOOL_ROWS_PER_SECTION).coerceAtLeast(0),
                            firstRowIndex = rowIndex,
                            adAfterRow = if (totalRows >= AdPolicy.NATIVE_LIST_MIN_ROWS) {
                                AdPolicy.NATIVE_LIST_AFTER_REVIEW_ROW
                            } else {
                                -1
                            },
                            listAd = listAd,
                            onViewAll = { detailKey = section.key },
                            onPreview = { previewId = it.id },
                            onToggleItem = onToggleItem,
                            onToggleAll = { setSectionSelection(section.items, onSetItemsSelected) },
                        )
                        rowIndex += minOf(section.items.size, TOOL_ROWS_PER_SECTION)
                    }
                }

                if (summary.scanLimitReached) {
                    item(key = "tool-limit") {
                        Text(
                            stringResource(R.string.scan_limit_note_v050),
                            color = Ink,
                            fontSize = 14.sp,
                            fontWeight = AppWeights.Strong,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
                item(key = "tool-scan-again") {
                    LinkButton(
                        stringResource(R.string.scan_again),
                        onClick = onScan,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }

            SelectionBar(
                selectedCount = summary.selectedItems.size,
                selectedBytes = summary.selectedBytes,
                enabled = summary.selectedItems.isNotEmpty() && !cleanupInProgress,
                busy = cleanupInProgress,
                onMoveToTrash = { showCleanupConfirmation = true },
            )
        }
    }
}

/** Shape of row [index] of [count] so the rows read as one black card. */
internal fun stackedShape(index: Int, count: Int): Shape = when {
    count <= 1 -> MakeRoomShapes.Card
    index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    index == count - 1 -> RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
    else -> RoundedCornerShape(0.dp)
}

/** One group: a header on Lime, then its rows stacked into one black card. */
internal fun LazyListScope.toolSection(
    section: ToolSection,
    rows: List<CleanableItem>,
    hiddenCount: Int,
    firstRowIndex: Int,
    adAfterRow: Int,
    listAd: @Composable () -> Unit,
    onViewAll: () -> Unit,
    onPreview: (CleanableItem) -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleAll: () -> Unit,
) {
    item(key = "section-head-" + section.key) {
        ToolSectionHeader(section = section, onToggleAll = onToggleAll)
    }
    val cardRows = rows.size + if (hiddenCount > 0) 1 else 0
    rows.forEachIndexed { index, item ->
        item(key = "row-" + section.key + "-" + item.id) {
            ToolFileRow(
                item = item,
                shape = stackedShape(index, cardRows),
                showDivider = index < cardRows - 1,
                onPreview = { onPreview(item) },
                onToggle = { onToggleItem(item.id) },
            )
        }
        // The in-list native follows the 4th row of the whole list (T5.8).
        if (firstRowIndex + index + 1 == adAfterRow) {
            item(key = "tools-list-native") {
                Column(Modifier.padding(vertical = 24.dp)) { listAd() }
            }
        }
    }
    if (hiddenCount > 0) {
        item(key = "section-more-" + section.key) {
            Surface(
                onClick = onViewAll,
                color = Black,
                contentColor = OnBlack,
                shape = stackedShape(cardRows - 1, cardRows),
            ) {
                Text(
                    stringResource(R.string.whatsapp_view_all) + " (" + section.items.size + ")",
                    color = Lime,
                    fontWeight = AppWeights.Title,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(16.dp),
                )
            }
        }
    }
}

@Composable
internal fun ToolSectionHeader(section: ToolSection, onToggleAll: () -> Unit) {
    val selectionState = sectionSelectionState(section.items.count(CleanableItem::selected), section.items.size)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(section.title, color = Ink, fontWeight = AppWeights.Giant, fontSize = 17.sp, lineHeight = 22.sp)
            Text(section.subtitle, color = InkSoft, fontSize = 13.sp, fontWeight = AppWeights.Strong)
        }
        TriStateCheckbox(
            state = selectionState,
            onClick = onToggleAll,
            colors = CheckboxDefaults.colors(checkedColor = Ink, uncheckedColor = Ink, checkmarkColor = Lime),
        )
    }
}

/** One 60 dp file row on black: preview, name, folder · age, Lime size, checkbox. */
@Composable
internal fun ToolFileRow(
    item: CleanableItem,
    shape: Shape,
    showDivider: Boolean,
    onPreview: () -> Unit,
    onToggle: () -> Unit,
) {
    val thumbnail by rememberThumbnail(item.uri, item.mimeType)
    Surface(color = Black, contentColor = OnBlack, shape = shape) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .clickable(role = Role.Button, onClick = onPreview)
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BlackChip),
                    contentAlignment = Alignment.Center,
                ) {
                    val bitmap = thumbnail
                    if (bitmap != null) {
                        Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(FileVisuals.icon(item.mimeType, item.name), contentDescription = null, tint = Lime, modifier = Modifier.size(24.dp))
                    }
                    if (item.mimeType.startsWith("video/")) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = OnBlack, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.name,
                        color = OnBlack,
                        fontWeight = AppWeights.Strong,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        bidiWrap(compactPath(item.relativePath)) + " · " + localDate(item.modifiedAtMillis),
                        color = OnBlackSoft,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // This row is a removable extra copy; name the protected
                    // original instead of a label that reads as if this file
                    // were the original (QA v43 #07).
                    item.protectedDuplicateName?.let { original ->
                        Text(
                            stringResource(R.string.premium_duplicate_copy_of, original),
                            color = Lime,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    ByteFormatter.format(item.sizeBytes),
                    color = Lime,
                    fontWeight = AppWeights.Giant,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
                Checkbox(
                    checked = item.selected,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(checkedColor = Lime, uncheckedColor = OnBlackSoft, checkmarkColor = Ink),
                )
            }
            if (showDivider) CardDivider()
        }
    }
}

@Composable
internal fun PremiumSortRow(sortMode: ToolSortMode, onChange: (ToolSortMode) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(ToolSortMode.entries) { mode ->
            val selected = sortMode == mode
            Surface(
                onClick = { onChange(mode) },
                modifier = Modifier.heightIn(min = 40.dp),
                color = if (selected) Black else Lime,
                contentColor = if (selected) Lime else Ink,
                shape = MakeRoomShapes.Pill,
                border = if (selected) null else androidx.compose.foundation.BorderStroke(2.dp, Ink),
            ) {
                Text(
                    stringResource(
                        when (mode) {
                            ToolSortMode.LARGEST -> R.string.premium_sort_largest
                            ToolSortMode.NEWEST -> R.string.premium_sort_newest
                            ToolSortMode.SELECTED -> R.string.premium_sort_selected
                        },
                    ),
                    color = if (selected) Lime else Ink,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    fontWeight = AppWeights.Title,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
