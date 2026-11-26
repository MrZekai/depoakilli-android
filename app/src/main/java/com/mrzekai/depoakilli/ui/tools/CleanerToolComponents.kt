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
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.isAppSelectable
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
import com.mrzekai.depoakilli.ui.common.PillButton
import com.mrzekai.depoakilli.ui.common.PillStyle
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
    onRefresh: () -> Unit = onScan,
    suggested: List<CleanableItem> = emptyList(),
    onVerifyAndSelect: (Set<String>) -> Unit = {},
    verifyPercent: Int? = null,
) {
    val config = toolConfig(focus)
    var sortName by rememberSaveable(focus.name) { mutableStateOf(ToolSortMode.LARGEST.name) }
    var detailKey by rememberSaveable(focus.name) { mutableStateOf<String?>(null) }
    var previewId by rememberSaveable(focus.name) { mutableStateOf<String?>(null) }
    var showCleanupConfirmation by rememberSaveable(focus.name) { mutableStateOf(false) }
    val sortMode = ToolSortMode.valueOf(sortName)
    val scale = rememberGiantScale()

    // C3: "My pick" first — the Smart scan's suggestion for this tool, matched to
    // these results by file — then the other files, closed until opened.
    val suggestedUris = remember(suggested) { suggested.mapTo(HashSet(), CleanableItem::uri) }
    // D1: the pick holds exactly what "Select my picks" selects; anything the app
    // would not select on its own (camera, look-alikes) falls to "Other files".
    val pickItems = summary.items.filter { it.uri in suggestedUris && it.isAppSelectable() }
    val pickIds = pickItems.mapTo(HashSet(), CleanableItem::id)
    val otherItems = summary.items.filterNot { it.id in pickIds }
    val suggestedBytes = suggested.sumOf(CleanableItem::sizeBytes)
    var othersOpen by rememberSaveable(focus.name) { mutableStateOf(false) }
    val showOthers = pickItems.isEmpty() || othersOpen
    val pickSection = pickItems.takeIf { it.isNotEmpty() }?.let { items ->
        ToolSection(
            key = "suggested",
            title = pluralStringResource(
                R.plurals.tool_suggest_header,
                items.size,
                items.size,
                ByteFormatter.format(items.sumOf(CleanableItem::sizeBytes)),
            ),
            subtitle = "",
            accent = config.accent,
            items = items,
        )
    }
    val otherSections = premiumSections(focus, summary.copy(items = otherItems))
    val sections = listOfNotNull(pickSection) + otherSections
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
            onConfirm = { ids ->
                showCleanupConfirmation = false
                onClean(ids)
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
            onToggleAll = { setSectionSelection(activeDetail.items, onSetItemsSelected, onVerifyAndSelect) },
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
                        // C1/C3: the giant figure is the suggested amount (the same number as
                        // the Tools tile); the total of every listed file is small text under it.
                        if (suggestedBytes > 0L) {
                            GiantNumber(ByteFormatter.format(suggestedBytes), scale, modifier = Modifier.fillMaxWidth())
                            PageLabel(
                                stringResource(R.string.tool_total_note, ByteFormatter.format(summary.totalSuggestedBytes)),
                                strong = true,
                            )
                        } else if (summary.items.isNotEmpty()) {
                            Text(
                                stringResource(R.string.tool_no_suggestion),
                                color = Ink,
                                fontSize = 24.sp,
                                lineHeight = 30.sp,
                                fontWeight = AppWeights.Title,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            )
                            PageLabel(
                                pluralStringResource(R.plurals.home_scan_files_count, summary.items.size, summary.items.size) +
                                    " · " + ByteFormatter.format(summary.totalSuggestedBytes),
                            )
                        }
                        if (verifyPercent != null) {
                            // D1: "Select all" on look-alikes is checking them byte for byte.
                            PageLabel(stringResource(R.string.verify_select_progress, verifyPercent), strong = true)
                        }
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
                    if (pickSection != null) {
                        val rows = sortItems(pickSection.items, sortMode)
                        toolSection(
                            section = pickSection,
                            rows = rows.take(TOOL_ROWS_PER_SECTION),
                            hiddenCount = (rows.size - TOOL_ROWS_PER_SECTION).coerceAtLeast(0),
                            onViewAll = { detailKey = pickSection.key },
                            onPreview = { previewId = it.id },
                            onToggleItem = onToggleItem,
                            onToggleAll = { setSectionSelection(pickSection.items, onSetItemsSelected, onVerifyAndSelect) },
                            headerAction = {
                                PillButton(
                                    text = stringResource(R.string.tool_select_suggested),
                                    onClick = {
                                        // The app's own selection (D1): bulk = true, the same rule
                                        // that built the pick, so the count in the header is what gets selected.
                                        onSetItemsSelected(pickSection.items.mapTo(linkedSetOf(), CleanableItem::id), true, true)
                                    },
                                    style = PillStyle.BLACK,
                                    modifier = Modifier.padding(bottom = 10.dp),
                                )
                            },
                        )
                        if (otherItems.isNotEmpty()) {
                            item(key = "tool-others-toggle") {
                                Surface(
                                    onClick = { othersOpen = !othersOpen },
                                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                                    color = Lime,
                                    contentColor = Ink,
                                    shape = MakeRoomShapes.Pill,
                                    border = androidx.compose.foundation.BorderStroke(2.dp, Ink),
                                ) {
                                    Row(
                                        Modifier.heightIn(min = 52.dp).padding(horizontal = 18.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            stringResource(R.string.tool_other_header, otherItems.size),
                                            color = Ink,
                                            fontWeight = AppWeights.Title,
                                            fontSize = 16.sp,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Icon(
                                            if (othersOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                            contentDescription = null,
                                            tint = Ink,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (showOthers) {
                        otherSections.forEach { section ->
                            val rows = sortItems(section.items, sortMode)
                            toolSection(
                                section = section,
                                rows = rows.take(TOOL_ROWS_PER_SECTION),
                                hiddenCount = (rows.size - TOOL_ROWS_PER_SECTION).coerceAtLeast(0),
                                onViewAll = { detailKey = section.key },
                                onPreview = { previewId = it.id },
                                onToggleItem = onToggleItem,
                                onToggleAll = { setSectionSelection(section.items, onSetItemsSelected, onVerifyAndSelect) },
                            )
                        }
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
                        onClick = onRefresh,
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
    onViewAll: () -> Unit,
    onPreview: (CleanableItem) -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleAll: () -> Unit,
    headerAction: (@Composable () -> Unit)? = null,
) {
    item(key = "section-head-" + section.key) {
        ToolSectionHeader(section = section, onToggleAll = onToggleAll)
    }
    if (headerAction != null) {
        item(key = "section-action-" + section.key) { headerAction() }
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
    val candidates = com.mrzekai.depoakilli.ui.PlanSelection.selectAllCandidates(section.items)
    val selectionState = sectionSelectionState(candidates.count(CleanableItem::selected), candidates.size)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                section.title,
                color = Ink,
                fontWeight = AppWeights.Giant,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                // K4: long translated titles take a second line instead of "…".
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (section.subtitle.isNotBlank()) {
                Text(section.subtitle, color = InkSoft, fontSize = 13.sp, fontWeight = AppWeights.Strong, maxLines = 1)
            }
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
