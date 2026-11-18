package com.mrzekai.depoakilli.ui.tools

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.ScanFocus
import com.mrzekai.depoakilli.ui.*
import com.mrzekai.depoakilli.ui.common.FileVisuals
import com.mrzekai.depoakilli.ui.common.bidiWrap
import com.mrzekai.depoakilli.ui.media.MediaPreviewContent
import com.mrzekai.depoakilli.ui.media.MediaPreviewFile

@Composable
internal fun PremiumToolDetailPage(
    section: ToolSection,
    focus: ScanFocus,
    sortMode: ToolSortMode,
    cleanupInProgress: Boolean,
    selectedCount: Int,
    selectedBytes: Long,
    onSortMode: (ToolSortMode) -> Unit,
    onBack: () -> Unit,
    onPreview: (CleanableItem) -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleAll: () -> Unit,
    onClean: () -> Unit,
) {
    val sorted = sortItems(section.items, sortMode)
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(section.title, color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(section.subtitle, color = ToolTextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onToggleAll) {
                Text(
                    stringResource(if (section.items.all(CleanableItem::selected)) R.string.smart_detail_unselect_all else R.string.smart_detail_select_all),
                    color = section.accent,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        Box(Modifier.padding(horizontal = 14.dp)) {
            PremiumSortRow(sortMode = sortMode, onChange = onSortMode)
        }
        Spacer(Modifier.height(10.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            gridItems(sorted, key = CleanableItem::id) { item ->
                PremiumToolItemCard(
                    item = item,
                    accent = section.accent,
                    onPreview = { onPreview(item) },
                    onToggle = { onToggleItem(item.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        PremiumToolBottomAction(
            selectedCount = selectedCount,
            selectedBytes = selectedBytes,
            accent = section.accent,
            accent2 = toolConfig(focus).accent2,
            cleanupInProgress = cleanupInProgress,
            onClean = onClean,
        )
    }
}

@Composable
internal fun PremiumToolBottomAction(
    selectedCount: Int,
    selectedBytes: Long,
    accent: Color,
    accent2: Color,
    cleanupInProgress: Boolean,
    onClean: () -> Unit,
) {
    Surface(color = ToolBackground, shadowElevation = 12.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.width(122.dp).fillMaxHeight().heightIn(min = 66.dp),
                color = ToolCard,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = .35f)),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.Center) {
                    Text(stringResource(R.string.premium_tool_selected), color = ToolTextSecondary, fontSize = 12.sp)
                    Text(ByteFormatter.format(selectedBytes), color = accent, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text(
                        if (selectedCount > 0) {
                            pluralStringResource(R.plurals.premium_tool_selected_count, selectedCount, selectedCount)
                        } else {
                            stringResource(R.string.nothing_selected_hint)
                        },
                        color = ToolTextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
            Button(
                onClick = onClean,
                enabled = selectedCount > 0 && !cleanupInProgress,
                modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 66.dp),
                shape = RoundedCornerShape(21.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(accent, accent2))),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (cleanupInProgress) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = ContrastTokens.OnBrightAction, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.CleaningServices, contentDescription = null, tint = ContrastTokens.OnBrightAction)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.whatsapp_review_and_clean), color = ContrastTokens.OnBrightAction, fontWeight = FontWeight.Black, fontSize = 17.sp)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = ContrastTokens.OnBrightAction)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PremiumToolPreviewDialog(
    item: CleanableItem,
    accent: Color,
    onToggleSelection: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = ToolBackground) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.whatsapp_preview_title), color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(item.name, color = Color.White, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 18.sp)
                    }
                }

                Box(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF020713)),
                    contentAlignment = Alignment.Center,
                ) {
                    MediaPreviewContent(
                        MediaPreviewFile(uri = item.uri, name = item.name, mimeType = item.mimeType, sizeBytes = item.sizeBytes),
                        accent = accent,
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    colors = CardDefaults.cardColors(containerColor = ToolCard),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = accent.copy(alpha = .14f), shape = RoundedCornerShape(14.dp)) {
                                Icon(FileVisuals.icon(item.mimeType, item.name), contentDescription = null, tint = accent, modifier = Modifier.padding(10.dp).size(25.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, color = Color.White, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(bidiWrap(compactPath(item.relativePath)), color = ToolTextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(ByteFormatter.format(item.sizeBytes), color = accent, fontWeight = FontWeight.Black)
                                Text(localDate(item.modifiedAtMillis), color = ToolTextSecondary, fontSize = 12.sp)
                            }
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleSelection),
                            color = if (item.selected) Color(0xFF0C4936) else Color(0xFF10284C),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, if (item.selected) ToolGreen.copy(alpha = .70f) else Color(0xFF5D7498)),
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = item.selected,
                                    onCheckedChange = { onToggleSelection() },
                                    colors = CheckboxDefaults.colors(checkedColor = ToolGreen, uncheckedColor = Color(0xFFB9C7DE)),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(if (item.selected) R.string.whatsapp_included else R.string.whatsapp_not_included), color = Color.White, fontWeight = FontWeight.Black)
                                    Text(stringResource(if (item.selected) R.string.whatsapp_remove_from_cleanup else R.string.whatsapp_add_to_cleanup), color = Color(0xFFC1CEE2), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        if (item.protectedDuplicateName != null) {
                            Surface(color = ToolGreen.copy(alpha = .09f), shape = RoundedCornerShape(14.dp)) {
                                Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Security, contentDescription = null, tint = ToolGreen, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.duplicate_original_kept, item.protectedDuplicateName), color = Color.White.copy(alpha = .88f), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
