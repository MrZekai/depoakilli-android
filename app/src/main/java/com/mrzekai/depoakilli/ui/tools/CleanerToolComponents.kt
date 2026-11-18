package com.mrzekai.depoakilli.ui.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Security
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.ScanFocus
import com.mrzekai.depoakilli.model.ScanSummary
import com.mrzekai.depoakilli.ui.*
import com.mrzekai.depoakilli.ui.common.DeleteConfirmDialog
import com.mrzekai.depoakilli.ui.common.FileVisuals
import com.mrzekai.depoakilli.ui.media.rememberThumbnail

@Composable
internal fun PremiumToolResults(
    focus: ScanFocus,
    summary: ScanSummary,
    cleanupInProgress: Boolean,
    onScan: () -> Unit,
    onToggleItem: (String) -> Unit,
    onSetItemsSelected: (ids: Set<String>, selected: Boolean, bulk: Boolean) -> Unit,
    onClean: (Set<String>?) -> Unit,
) {
    val config = toolConfig(focus)
    var sortName by rememberSaveable(focus.name) { mutableStateOf(ToolSortMode.LARGEST.name) }
    var detailKey by rememberSaveable(focus.name) { mutableStateOf<String?>(null) }
    var previewId by rememberSaveable(focus.name) { mutableStateOf<String?>(null) }
    var showCleanupConfirmation by rememberSaveable(focus.name) { mutableStateOf(false) }
    val sortMode = ToolSortMode.valueOf(sortName)

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
            focus = focus,
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

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { PremiumToolHero(focus, summary, config) }
            item {
                PremiumSortRow(sortMode = sortMode, onChange = { sortName = it.name })
            }

            if (summary.items.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ToolCard),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = ToolGreen, modifier = Modifier.size(46.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(stringResource(R.string.premium_tool_nothing_found), color = Color.White, fontWeight = FontWeight.Black)
                            Text(stringResource(R.string.premium_tool_nothing_found_detail), color = ToolTextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                items(sections, key = ToolSection::key) { section ->
                    PremiumToolSectionCard(
                        section = section.copy(items = sortItems(section.items, sortMode)),
                        focus = focus,
                        onViewAll = { detailKey = section.key },
                        onPreview = { previewId = it.id },
                        onToggleItem = onToggleItem,
                        onToggleAll = { setSectionSelection(section.items, onSetItemsSelected) },
                    )
                }
            }

            if (summary.scanLimitReached) {
                item {
                    Text(stringResource(R.string.scan_limit_note_v050), color = ToolAmber, style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                TextButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.scan_again), color = config.accent)
                }
            }
        }

        PremiumToolBottomAction(
            selectedCount = summary.selectedItems.size,
            selectedBytes = summary.selectedBytes,
            accent = config.accent,
            accent2 = config.accent2,
            cleanupInProgress = cleanupInProgress,
            onClean = { showCleanupConfirmation = true },
        )
    }
}

@Composable
internal fun PremiumToolHero(
    focus: ScanFocus,
    summary: ScanSummary,
    config: ToolVisualConfig,
) {
    val foundBytes = summary.totalSuggestedBytes
    val selectedBytes = summary.selectedBytes
    Card(
        colors = CardDefaults.cardColors(containerColor = ToolCard),
        shape = RoundedCornerShape(30.dp),
        border = BorderStroke(1.dp, config.accent.copy(alpha = .45f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF06132C), config.accent.copy(alpha = .44f), config.accent2.copy(alpha = .36f))))
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color(0xCC051027), shape = CircleShape, border = BorderStroke(1.dp, config.accent.copy(alpha = .65f))) {
                        Icon(config.icon, contentDescription = null, tint = config.accent, modifier = Modifier.padding(18.dp).size(42.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(config.titleRes), color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(config.subtitleRes), color = Color.White.copy(alpha = .82f), style = MaterialTheme.typography.bodySmall)
                    }
                }

                Text(ByteFormatter.format(foundBytes), color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.premium_tool_found_size), color = config.accent, fontWeight = FontWeight.Black)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    PremiumHeroStat(summary.items.size.toString(), stringResource(R.string.premium_tool_candidates), Modifier.weight(1f))
                    PremiumHeroStat(ByteFormatter.format(selectedBytes), stringResource(R.string.premium_tool_selected), Modifier.weight(1f))
                    PremiumHeroStat(summary.scannedFileCount.toString(), stringResource(R.string.premium_tool_scanned), Modifier.weight(1f))
                }

                if (focus == ScanFocus.DUPLICATES) {
                    Surface(color = Color(0x33000000), shape = RoundedCornerShape(15.dp)) {
                        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Security, contentDescription = null, tint = ToolGreen, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.premium_duplicates_original_safe), color = Color.White.copy(alpha = .90f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (focus == ScanFocus.APKS) {
                    Surface(color = Color(0x33000000), shape = RoundedCornerShape(15.dp)) {
                        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Android, contentDescription = null, tint = ToolGreen, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.premium_apk_installer_note), color = Color.White.copy(alpha = .90f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PremiumHeroStat(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Color(0x55020A19), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 8.dp)) {
            Text(value, color = Color.White, fontWeight = FontWeight.Black)
            Text(label, color = Color.White.copy(alpha = .68f), fontSize = 12.sp)
        }
    }
}

@Composable
internal fun PremiumSortRow(sortMode: ToolSortMode, onChange: (ToolSortMode) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(ToolSortMode.entries) { mode ->
            val selected = sortMode == mode
            Surface(
                modifier = Modifier.clickable { onChange(mode) },
                color = if (selected) Color(0xFF123C68) else ToolCard,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, if (selected) ToolCyan.copy(alpha = .65f) else Color.White.copy(alpha = .08f)),
            ) {
                Text(
                    stringResource(
                        when (mode) {
                            ToolSortMode.LARGEST -> R.string.premium_sort_largest
                            ToolSortMode.NEWEST -> R.string.premium_sort_newest
                            ToolSortMode.SELECTED -> R.string.premium_sort_selected
                        },
                    ),
                    color = if (selected) Color.White else ToolTextSecondary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
internal fun PremiumToolSectionCard(
    section: ToolSection,
    focus: ScanFocus,
    onViewAll: () -> Unit,
    onPreview: (CleanableItem) -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleAll: () -> Unit,
) {
    val selectionState = sectionSelectionState(section.items.count(CleanableItem::selected), section.items.size)
    Card(
        colors = CardDefaults.cardColors(containerColor = ToolCard),
        shape = RoundedCornerShape(25.dp),
        border = BorderStroke(1.dp, section.accent.copy(alpha = .22f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = section.accent.copy(alpha = .15f), shape = RoundedCornerShape(13.dp)) {
                    Icon(sectionIcon(focus, section), contentDescription = null, tint = section.accent, modifier = Modifier.padding(9.dp).size(23.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(section.title, color = Color.White, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(section.subtitle, color = ToolTextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onViewAll) {
                    Text(stringResource(R.string.whatsapp_view_all), color = section.accent, fontWeight = FontWeight.Black)
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = section.accent, modifier = Modifier.size(18.dp))
                }
                TriStateCheckbox(
                    state = selectionState,
                    onClick = onToggleAll,
                    colors = CheckboxDefaults.colors(checkedColor = section.accent, uncheckedColor = Color(0xFFB7C5DB)),
                )
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(section.items.take(4), key = CleanableItem::id) { item ->
                    PremiumToolItemCard(
                        item = item,
                        accent = section.accent,
                        onPreview = { onPreview(item) },
                        onToggle = { onToggleItem(item.id) },
                    )
                }
                if (section.items.size > 4) {
                    item {
                        Card(
                            onClick = onViewAll,
                            modifier = Modifier.width(102.dp).height(190.dp),
                            colors = CardDefaults.cardColors(containerColor = ToolCardAlt),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, section.accent.copy(alpha = .25f)),
                        ) {
                            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text("+${section.items.size - 4}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                                Spacer(Modifier.height(5.dp))
                                Text(stringResource(R.string.whatsapp_view_all), color = section.accent, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PremiumToolItemCard(
    item: CleanableItem,
    accent: Color,
    onPreview: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val thumbnail by rememberThumbnail(item.uri, item.mimeType)
    Card(
        onClick = onPreview,
        modifier = modifier.width(150.dp).height(190.dp),
        colors = CardDefaults.cardColors(containerColor = ToolCardAlt),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (item.selected) accent.copy(alpha = .70f) else Color.White.copy(alpha = .08f)),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxWidth().height(112.dp).background(Color(0xFF05132A)),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnail != null) {
                    Image(requireNotNull(thumbnail), contentDescription = item.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(FileVisuals.icon(item.mimeType, item.name), contentDescription = null, tint = accent, modifier = Modifier.size(43.dp))
                }
                if (item.mimeType.startsWith("video/")) {
                    Surface(color = Color(0xB6020713), shape = CircleShape) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.padding(7.dp).size(24.dp))
                    }
                }
                Checkbox(
                    checked = item.selected,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
                    colors = CheckboxDefaults.colors(checkedColor = accent, uncheckedColor = Color.White, checkmarkColor = Color.White),
                )
                Surface(modifier = Modifier.align(Alignment.BottomStart).padding(6.dp), color = Color(0xCC020713), shape = RoundedCornerShape(9.dp)) {
                    Text(ByteFormatter.format(item.sizeBytes), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                Text(localDate(item.modifiedAtMillis), color = ToolTextSecondary, fontSize = 12.sp, maxLines = 1)
                // This card is a removable extra copy. Name the protected
                // original instead of a label that read as if this file were
                // the original (QA v43 #07).
                item.protectedDuplicateName?.let { original ->
                    Text(
                        stringResource(R.string.premium_duplicate_copy_of, original),
                        color = ToolGreen,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
