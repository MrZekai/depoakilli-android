package com.mrzekai.depoakilli.ui.whatsapp

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.WhatsAppLibrarySummary
import com.mrzekai.depoakilli.model.WhatsAppMediaCategory
import com.mrzekai.depoakilli.model.WhatsAppMediaItem
import com.mrzekai.depoakilli.ui.*
import com.mrzekai.depoakilli.ui.common.SelectionBar
import com.mrzekai.depoakilli.ui.common.bidiWrap
import com.mrzekai.depoakilli.ui.media.MediaPreviewContent
import com.mrzekai.depoakilli.ui.media.MediaPreviewFile
import com.mrzekai.depoakilli.ui.theme.Ink
import com.mrzekai.depoakilli.ui.theme.InkSoft
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack

@Composable
internal fun WhatsAppGroupDetailPage(
    summary: WhatsAppLibrarySummary,
    group: WhatsAppUiGroup,
    direction: WhatsAppDirectionFilter,
    cleanupInProgress: Boolean,
    onBack: () -> Unit,
    onDirectionChange: (WhatsAppDirectionFilter) -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleGroup: () -> Unit,
    onPreview: (WhatsAppMediaItem) -> Unit,
    onReviewAndClean: () -> Unit,
) {
    var sortName by rememberSaveable(group.name) { mutableStateOf(WhatsAppSortMode.LARGEST.name) }
    val sortMode = runCatching { WhatsAppSortMode.valueOf(sortName) }.getOrDefault(WhatsAppSortMode.LARGEST)
    val filtered = filterWhatsAppItems(summary.items, group, direction)
    val displayItems = when (sortMode) {
        WhatsAppSortMode.LARGEST -> filtered.sortedByDescending(WhatsAppMediaItem::sizeBytes)
        WhatsAppSortMode.NEWEST -> filtered.sortedByDescending(WhatsAppMediaItem::modifiedAtMillis)
    }
    val selectionState = sectionSelectionState(filtered.count(WhatsAppMediaItem::selected), filtered.size)

    Column(Modifier.fillMaxSize()) {
        WhatsAppHeader(onBack = onBack, subtitle = stringResource(groupTitleRes(group)))

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = WaCard),
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = groupAccent(group).copy(alpha = .16f), shape = CircleShape) {
                        Icon(groupIcon(group), contentDescription = null, tint = groupAccent(group), modifier = Modifier.padding(11.dp).size(26.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(groupTitleRes(group)), color = OnBlack, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text(
                            pluralStringResource(
                                R.plurals.whatsapp_category_summary,
                                filtered.size,
                                filtered.size,
                                ByteFormatter.format(filtered.sumOf(WhatsAppMediaItem::sizeBytes)),
                            ),
                            color = WaTextSecondary,
                        )
                    }
                    TriStateCheckbox(
                        state = selectionState,
                        onClick = onToggleGroup,
                        colors = CheckboxDefaults.colors(
                            checkedColor = groupAccent(group),
                            uncheckedColor = WaTextSecondary,
                            checkmarkColor = Ink,
                        ),
                    )
                }
                Text(
                    stringResource(R.string.whatsapp_card_hint),
                    color = WaCyan,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        WhatsAppDirectionTabs(
            selected = direction,
            onSelected = onDirectionChange,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SortTab(
                title = stringResource(R.string.whatsapp_sort_largest),
                icon = Icons.Outlined.GridView,
                selected = sortMode == WhatsAppSortMode.LARGEST,
                modifier = Modifier.weight(1f),
                onClick = { sortName = WhatsAppSortMode.LARGEST.name },
            )
            SortTab(
                title = stringResource(R.string.whatsapp_sort_newest),
                icon = Icons.Outlined.AccessTime,
                selected = sortMode == WhatsAppSortMode.NEWEST,
                modifier = Modifier.weight(1f),
                onClick = { sortName = WhatsAppSortMode.NEWEST.name },
            )
        }

        if (displayItems.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.whatsapp_no_files_for_filter), color = InkSoft)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                gridItems(displayItems, key = WhatsAppMediaItem::id) { item ->
                    WhatsAppMediaCard(
                        item = item,
                        group = group,
                        compact = false,
                        onPreview = { onPreview(item) },
                        onToggle = { onToggleItem(item.id) },
                    )
                }
            }
        }

        WhatsAppCleanupFooter(
            selectedCount = summary.selectedItems.size,
            selectedBytes = summary.selectedBytes,
            enabled = summary.selectedItems.isNotEmpty() && !cleanupInProgress,
            inProgress = cleanupInProgress,
            onClick = onReviewAndClean,
        )
    }
}

@Composable
internal fun SortTab(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (selected) WaCard else WaBackground,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 0.dp else 2.dp,
            if (selected) WaCard else Ink,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) Lime else Ink, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, color = if (selected) Lime else Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

/** Sticky black bar: the selection and "Move to Trash" (T5.8). */
@Composable
internal fun WhatsAppCleanupFooter(
    selectedCount: Int,
    selectedBytes: Long,
    enabled: Boolean,
    inProgress: Boolean,
    onClick: () -> Unit,
) {
    SelectionBar(
        selectedCount = selectedCount,
        selectedBytes = selectedBytes,
        enabled = enabled,
        busy = inProgress,
        onMoveToTrash = onClick,
    )
}

@Composable
internal fun WhatsAppPreviewDialog(
    item: WhatsAppMediaItem,
    selected: Boolean,
    onToggleSelection: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = WaBackground) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back), tint = Ink)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.whatsapp_preview_title), color = InkSoft, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(item.name, color = Ink, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 18.sp)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(WaCard),
                    contentAlignment = Alignment.Center,
                ) {
                    MediaPreviewContent(
                        MediaPreviewFile(
                            uri = item.uri,
                            name = item.name,
                            mimeType = item.mimeType,
                            sizeBytes = item.sizeBytes,
                            voiceNote = item.category == WhatsAppMediaCategory.VOICE_NOTES,
                        ),
                        accent = Lime,
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    colors = CardDefaults.cardColors(containerColor = WaCard),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = groupAccent(uiGroupOf(item)).copy(alpha = .15f), shape = RoundedCornerShape(14.dp)) {
                                Icon(whatsAppFileIcon(item), contentDescription = null, tint = groupAccent(uiGroupOf(item)), modifier = Modifier.padding(10.dp).size(25.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, color = OnBlack, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(bidiWrap(compactPath(item.relativePath)), color = WaTextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(ByteFormatter.format(item.sizeBytes), color = groupAccent(uiGroupOf(item)), fontWeight = FontWeight.Black)
                                Text(localDate(item.modifiedAtMillis), color = WaTextSecondary, fontSize = 12.sp)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(WaCardSecondary)
                                .border(
                                    1.dp,
                                    if (selected) Lime else com.mrzekai.depoakilli.ui.theme.BlackLine,
                                    RoundedCornerShape(16.dp),
                                )
                                .clickable(onClick = onToggleSelection)
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = { onToggleSelection() },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Lime,
                                    uncheckedColor = WaTextSecondary,
                                    checkmarkColor = Ink,
                                ),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(if (selected) R.string.whatsapp_included else R.string.whatsapp_not_included),
                                    color = OnBlack,
                                    fontWeight = FontWeight.Black,
                                )
                                Text(
                                    stringResource(if (selected) R.string.whatsapp_remove_from_cleanup else R.string.whatsapp_add_to_cleanup),
                                    color = WaTextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Sent / received split and the chat warning shown inside the delete confirmation (T2.4). */
@Composable
internal fun WhatsAppDeleteBreakdown(items: List<WhatsAppMediaItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val (sent, received) = items.partition(::isSentWhatsAppItem)
        listOf(
            R.string.whatsapp_filter_sent to sent,
            R.string.whatsapp_filter_incoming to received,
        ).filter { (_, list) -> list.isNotEmpty() }.forEach { (label, list) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(label), style = MaterialTheme.typography.bodySmall)
                Text(
                    pluralStringResource(
                        R.plurals.whatsapp_category_summary,
                        list.size,
                        list.size,
                        ByteFormatter.format(list.sumOf(WhatsAppMediaItem::sizeBytes)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(stringResource(R.string.plan_preview_whatsapp), style = MaterialTheme.typography.bodySmall)
    }
}
