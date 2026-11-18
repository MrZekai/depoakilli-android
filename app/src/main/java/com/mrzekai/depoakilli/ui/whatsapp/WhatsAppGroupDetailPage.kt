package com.mrzekai.depoakilli.ui.whatsapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
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
import androidx.compose.ui.graphics.Brush
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
import com.mrzekai.depoakilli.ui.common.bidiWrap
import com.mrzekai.depoakilli.ui.media.MediaPreviewContent
import com.mrzekai.depoakilli.ui.media.MediaPreviewFile
import com.mrzekai.depoakilli.ui.theme.WhatsAppGreen

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
                        Text(stringResource(groupTitleRes(group)), color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
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
                            uncheckedColor = Color(0xFFB9C7DE),
                            checkmarkColor = Color.White,
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
                Text(stringResource(R.string.whatsapp_no_files_for_filter), color = WaTextSecondary)
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
        color = if (selected) Color(0xFF0B3150) else Color(0xFF07152D),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) WaCyan.copy(alpha = .55f) else Color.White.copy(alpha = .08f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) WaCyan else WaTextSecondary, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, color = if (selected) Color.White else WaTextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
internal fun WhatsAppCleanupFooter(
    selectedCount: Int,
    selectedBytes: Long,
    enabled: Boolean,
    inProgress: Boolean,
    onClick: () -> Unit,
) {
    Surface(color = Color(0xFF020817), shadowElevation = 18.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Flexible height: the fixed 64 dp card clipped the "0 items"
            // line (QA v43 polish).
            Card(
                modifier = Modifier.weight(.38f).fillMaxHeight().heightIn(min = 64.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF091B39)),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, WhatsAppGreen.copy(alpha = .20f)),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(25.dp))
                    Spacer(Modifier.width(7.dp))
                    Column {
                        Text(stringResource(R.string.whatsapp_selected_size), color = WaTextSecondary, fontSize = 12.sp)
                        Text(ByteFormatter.format(selectedBytes), color = WhatsAppGreen, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text(
                            if (selectedCount > 0) {
                                pluralStringResource(R.plurals.whatsapp_selected_items_short, selectedCount, selectedCount)
                            } else {
                                stringResource(R.string.nothing_selected_hint)
                            },
                            color = WaTextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            Card(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.weight(.62f).fillMaxHeight().heightIn(min = 64.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent, disabledContainerColor = Color(0xFF19304F)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (enabled) {
                                Brush.horizontalGradient(listOf(Color(0xFF0DCB72), WaCyan, Color(0xFF16D86A)))
                            } else {
                                Brush.horizontalGradient(listOf(Color(0xFF19304F), Color(0xFF203957)))
                            },
                        )
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val actionContent = if (enabled) ContrastTokens.OnBrightAction else Color.White
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = actionContent, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(if (inProgress) R.string.whatsapp_cleanup_in_progress else R.string.whatsapp_review_and_clean),
                        color = actionContent,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = actionContent)
                }
            }
        }
    }
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
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.whatsapp_preview_title), color = WhatsAppGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(item.name, color = Color.White, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 18.sp)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF020713)),
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
                        accent = WhatsAppGreen,
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
                                Text(item.name, color = Color.White, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                                .background(if (selected) Color(0xFF0C4936) else Color(0xFF10284C))
                                .border(
                                    1.dp,
                                    if (selected) WhatsAppGreen.copy(alpha = .70f) else Color(0xFF5D7498),
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
                                    checkedColor = WhatsAppGreen,
                                    uncheckedColor = Color(0xFFB9C7DE),
                                    checkmarkColor = Color(0xFF03130D),
                                ),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(if (selected) R.string.whatsapp_included else R.string.whatsapp_not_included),
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                )
                                Text(
                                    stringResource(if (selected) R.string.whatsapp_remove_from_cleanup else R.string.whatsapp_add_to_cleanup),
                                    color = Color(0xFFC1CEE2),
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
