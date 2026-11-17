package com.mrzekai.depoakilli.ui.whatsapp

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.WhatsAppLibrarySummary
import com.mrzekai.depoakilli.model.WhatsAppMediaCategory
import com.mrzekai.depoakilli.model.WhatsAppMediaItem
import com.mrzekai.depoakilli.ui.*
import com.mrzekai.depoakilli.ui.common.DeleteConfirmDialog
import com.mrzekai.depoakilli.ui.common.DeleteConfirmEntry
import com.mrzekai.depoakilli.ui.media.rememberThumbnail
import com.mrzekai.depoakilli.ui.theme.WhatsAppGreen

@Composable
internal fun WhatsAppResultsScreen(
    summary: WhatsAppLibrarySummary,
    cleanupInProgress: Boolean,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleCategory: (WhatsAppMediaCategory) -> Unit,
    onDeleteSelected: () -> Unit,
) {
    var directionName by rememberSaveable { mutableStateOf(WhatsAppDirectionFilter.ALL.name) }
    var detailGroupName by rememberSaveable { mutableStateOf<String?>(null) }
    var previewItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }

    val direction = runCatching { WhatsAppDirectionFilter.valueOf(directionName) }
        .getOrDefault(WhatsAppDirectionFilter.ALL)
    val detailGroup = detailGroupName?.let { name ->
        runCatching { WhatsAppUiGroup.valueOf(name) }.getOrNull()
    }
    val previewItem = previewItemId?.let { id -> summary.items.firstOrNull { it.id == id } }

    BackHandler(enabled = detailGroup != null) { detailGroupName = null }

    if (showDeleteConfirmation) {
        val selectedItems = summary.selectedItems
        DeleteConfirmDialog(
            entries = selectedItems.map { DeleteConfirmEntry(it.name, it.relativePath) },
            trashBytes = summary.selectedBytes,
            permanentBytes = 0L,
            onDismiss = { showDeleteConfirmation = false },
            onConfirm = {
                showDeleteConfirmation = false
                onDeleteSelected()
            },
            extraContent = { WhatsAppDeleteBreakdown(selectedItems) },
        )
    }

    if (previewItem != null) {
        WhatsAppPreviewDialog(
            item = previewItem,
            selected = previewItem.selected,
            onToggleSelection = { onToggleItem(previewItem.id) },
            onDismiss = { previewItemId = null },
        )
    }

    if (detailGroup != null) {
        WhatsAppGroupDetailPage(
            summary = summary,
            group = detailGroup,
            direction = direction,
            cleanupInProgress = cleanupInProgress,
            onBack = { detailGroupName = null },
            onDirectionChange = { directionName = it.name },
            onToggleItem = onToggleItem,
            onToggleGroup = { toggleUiGroup(summary, detailGroup, direction, onToggleCategory, onToggleItem) },
            onPreview = { previewItemId = it.id },
            onReviewAndClean = { showDeleteConfirmation = true },
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        WhatsAppHeader(onBack = onBack)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { WhatsAppHeroCard(summary) }
            item {
                WhatsAppDirectionTabs(
                    selected = direction,
                    onSelected = { directionName = it.name },
                )
            }

            if (summary.items.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = WaCard),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(Icons.Outlined.Info, contentDescription = null, tint = WhatsAppGreen)
                            Spacer(Modifier.height(10.dp))
                            Text(stringResource(R.string.whatsapp_no_files), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                WhatsAppUiGroup.entries.forEach { group ->
                    val groupItems = filterWhatsAppItems(summary.items, group, direction)
                    if (groupItems.isNotEmpty()) {
                        item(key = "wa-group-${group.name}") {
                            WhatsAppGroupSection(
                                group = group,
                                items = groupItems,
                                onToggleItem = onToggleItem,
                                onToggleGroup = {
                                    toggleUiGroup(summary, group, direction, onToggleCategory, onToggleItem)
                                },
                                onViewAll = { detailGroupName = group.name },
                                onPreview = { previewItemId = it.id },
                            )
                        }
                    }
                }
            }

            item {
                TextButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.scan_again))
                }
            }
        }

        WhatsAppCleanupFooter(
            selectedCount = summary.selectedItems.size,
            selectedBytes = summary.selectedBytes,
            enabled = summary.selectedItems.isNotEmpty() && !cleanupInProgress,
            inProgress = cleanupInProgress,
            onClick = { showDeleteConfirmation = true },
        )
    }
}

@Composable
internal fun WhatsAppHeroCard(summary: WhatsAppLibrarySummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = androidx.compose.foundation.BorderStroke(1.dp, WaCyan.copy(alpha = .35f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF063B3A), Color(0xFF0B9E72), Color(0xFF0860A5)),
                    ),
                )
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = Color(0xAA06162F),
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(2.dp, WaCyan.copy(alpha = .65f)),
            ) {
                Box(Modifier.padding(18.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = WhatsAppGreen,
                        modifier = Modifier.size(42.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    stringResource(R.string.whatsapp_total_media),
                    color = Color.White.copy(alpha = .84f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
                Text(
                    ByteFormatter.format(summary.totalBytes),
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    pluralStringResource(R.plurals.whatsapp_scan_stats, summary.scannedFileCount, summary.scannedFileCount),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.whatsapp_selection_safe),
                    color = Color.White.copy(alpha = .78f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun WhatsAppDirectionTabs(
    selected: WhatsAppDirectionFilter,
    onSelected: (WhatsAppDirectionFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DirectionTab(
            title = stringResource(R.string.whatsapp_filter_incoming),
            icon = Icons.Outlined.Download,
            selected = selected == WhatsAppDirectionFilter.INCOMING,
            modifier = Modifier.weight(1f),
            onClick = { onSelected(WhatsAppDirectionFilter.INCOMING) },
        )
        DirectionTab(
            title = stringResource(R.string.whatsapp_filter_sent),
            icon = Icons.Outlined.Upload,
            selected = selected == WhatsAppDirectionFilter.SENT,
            modifier = Modifier.weight(1f),
            onClick = { onSelected(WhatsAppDirectionFilter.SENT) },
        )
        DirectionTab(
            title = stringResource(R.string.whatsapp_filter_all),
            icon = Icons.Outlined.GridView,
            selected = selected == WhatsAppDirectionFilter.ALL,
            modifier = Modifier.weight(1f),
            onClick = { onSelected(WhatsAppDirectionFilter.ALL) },
        )
    }
}

@Composable
internal fun DirectionTab(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val background = if (selected) WhatsAppGreen.copy(alpha = .18f) else Color(0xFF09162F)
    val borderColor = if (selected) WhatsAppGreen.copy(alpha = .72f) else Color.White.copy(alpha = .10f)
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = background,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) WhatsAppGreen else WaTextSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                title,
                color = if (selected) Color.White else WaTextSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun WhatsAppGroupSection(
    group: WhatsAppUiGroup,
    items: List<WhatsAppMediaItem>,
    onToggleItem: (String) -> Unit,
    onToggleGroup: () -> Unit,
    onViewAll: () -> Unit,
    onPreview: (WhatsAppMediaItem) -> Unit,
) {
    val accent = groupAccent(group)
    val selectionState = sectionSelectionState(items.count(WhatsAppMediaItem::selected), items.size)
    Card(
        colors = CardDefaults.cardColors(containerColor = WaCard),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .07f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(color = accent.copy(alpha = .16f), shape = RoundedCornerShape(13.dp)) {
                    Icon(
                        groupIcon(group),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.padding(9.dp).size(22.dp),
                    )
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(groupTitleRes(group)), color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text(
                        pluralStringResource(
                            R.plurals.whatsapp_category_summary,
                            items.size,
                            items.size,
                            ByteFormatter.format(items.sumOf(WhatsAppMediaItem::sizeBytes)),
                        ),
                        color = WaTextSecondary,
                        fontSize = 12.sp,
                    )
                }
                TextButton(onClick = onViewAll) {
                    Text(stringResource(R.string.whatsapp_view_all), color = WhatsAppGreen, fontWeight = FontWeight.Bold)
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(18.dp))
                }
                TriStateCheckbox(
                    state = selectionState,
                    onClick = onToggleGroup,
                    colors = CheckboxDefaults.colors(
                        checkedColor = accent,
                        uncheckedColor = Color(0xFFB9C7DE),
                        checkmarkColor = Color.White,
                    ),
                )
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                val previewItems = items.sortedByDescending(WhatsAppMediaItem::modifiedAtMillis).take(4)
                items(previewItems, key = WhatsAppMediaItem::id) { item ->
                    WhatsAppMediaCard(
                        item = item,
                        group = group,
                        compact = true,
                        onPreview = { onPreview(item) },
                        onToggle = { onToggleItem(item.id) },
                    )
                }
                if (items.size > previewItems.size) {
                    item(key = "more-${group.name}") {
                        MoreItemsCard(
                            remaining = items.size - previewItems.size,
                            accent = accent,
                            onClick = onViewAll,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MoreItemsCard(
    remaining: Int,
    accent: Color,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(100.dp).height(174.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF091A38)),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .25f)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("+$remaining", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Spacer(Modifier.height(5.dp))
            Text(stringResource(R.string.whatsapp_view_all), color = accent, fontSize = 12.sp)
        }
    }
}

@Composable
internal fun WhatsAppMediaCard(
    item: WhatsAppMediaItem,
    group: WhatsAppUiGroup,
    compact: Boolean,
    onPreview: () -> Unit,
    onToggle: () -> Unit,
) {
    val accent = groupAccent(group)
    val bitmap by rememberThumbnail(item.uri, item.mimeType)
    val width = if (compact) 148.dp else 160.dp
    val height = if (compact) 174.dp else 200.dp
    val previewHeight = if (compact) 100.dp else 120.dp

    Card(
        onClick = onPreview,
        modifier = Modifier.width(width).height(height),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = WaCardSecondary),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (item.selected) accent.copy(alpha = .72f) else Color.White.copy(alpha = .08f),
        ),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(previewHeight).background(Color(0xFF06132A)),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = requireNotNull(bitmap),
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        whatsAppFileIcon(item),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(42.dp),
                    )
                }
                if (group == WhatsAppUiGroup.VIDEOS) {
                    Surface(color = Color(0xBB020713), shape = CircleShape) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.padding(7.dp).size(24.dp))
                    }
                }
                Checkbox(
                    checked = item.selected,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
                    colors = CheckboxDefaults.colors(
                        checkedColor = accent,
                        uncheckedColor = Color.White,
                        checkmarkColor = Color.White,
                    ),
                )
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                    color = Color(0xCC020713),
                    shape = RoundedCornerShape(9.dp),
                ) {
                    Text(
                        ByteFormatter.format(item.sizeBytes),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                )
                Text(
                    localDate(item.modifiedAtMillis),
                    color = WaTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
                if (!compact) {
                    Text(
                        stringResource(if (item.selected) R.string.whatsapp_included else R.string.whatsapp_not_included),
                        color = if (item.selected) WhatsAppGreen else WaAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
