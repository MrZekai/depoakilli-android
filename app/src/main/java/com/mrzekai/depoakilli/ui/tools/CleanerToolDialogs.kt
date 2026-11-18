package com.mrzekai.depoakilli.ui.tools

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.mrzekai.depoakilli.ui.*
import com.mrzekai.depoakilli.ui.common.FileVisuals
import com.mrzekai.depoakilli.ui.common.PageFrame
import com.mrzekai.depoakilli.ui.common.SelectionBar
import com.mrzekai.depoakilli.ui.common.bidiWrap
import com.mrzekai.depoakilli.ui.media.MediaPreviewContent
import com.mrzekai.depoakilli.ui.media.MediaPreviewFile
import com.mrzekai.depoakilli.ui.theme.AppWeights
import com.mrzekai.depoakilli.ui.theme.Black
import com.mrzekai.depoakilli.ui.theme.BlackChip
import com.mrzekai.depoakilli.ui.theme.BlackLine
import com.mrzekai.depoakilli.ui.theme.Ink
import com.mrzekai.depoakilli.ui.theme.InkSoft
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft

/** Every file of one group (opened with "View all"); the same rows as the tool list. */
@Composable
internal fun PremiumToolDetailPage(
    section: ToolSection,
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
    PageFrame {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back), tint = Ink)
                }
                Text(
                    section.title,
                    color = Ink,
                    fontWeight = AppWeights.Giant,
                    fontSize = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                item(key = "detail-sort") { PremiumSortRow(sortMode = sortMode, onChange = onSortMode) }
                item(key = "detail-head") { ToolSectionHeader(section = section, onToggleAll = onToggleAll) }
                sorted.forEachIndexed { index, item ->
                    item(key = "detail-row-" + item.id) {
                        ToolFileRow(
                            item = item,
                            shape = stackedShape(index, sorted.size),
                            showDivider = index < sorted.lastIndex,
                            onPreview = { onPreview(item) },
                            onToggle = { onToggleItem(item.id) },
                        )
                    }
                }
            }
            SelectionBar(
                selectedCount = selectedCount,
                selectedBytes = selectedBytes,
                enabled = selectedCount > 0 && !cleanupInProgress,
                busy = cleanupInProgress,
                onMoveToTrash = onClean,
            )
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
        Surface(Modifier.fillMaxSize(), color = ToolBackground, contentColor = Ink) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back), tint = Ink)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.whatsapp_preview_title), color = InkSoft, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(item.name, color = Ink, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 18.sp)
                    }
                }

                Box(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp).clip(RoundedCornerShape(22.dp)).background(Black),
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
                                Text(item.name, color = OnBlack, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(bidiWrap(compactPath(item.relativePath)), color = ToolTextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(ByteFormatter.format(item.sizeBytes), color = accent, fontWeight = FontWeight.Black)
                                Text(localDate(item.modifiedAtMillis), color = ToolTextSecondary, fontSize = 12.sp)
                            }
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleSelection),
                            color = BlackChip,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, if (item.selected) Lime else BlackLine),
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = item.selected,
                                    onCheckedChange = { onToggleSelection() },
                                    colors = CheckboxDefaults.colors(checkedColor = Lime, uncheckedColor = OnBlackSoft, checkmarkColor = Ink),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(if (item.selected) R.string.whatsapp_included else R.string.whatsapp_not_included), color = OnBlack, fontWeight = FontWeight.Black)
                                    Text(stringResource(if (item.selected) R.string.whatsapp_remove_from_cleanup else R.string.whatsapp_add_to_cleanup), color = OnBlackSoft, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        if (item.protectedDuplicateName != null) {
                            Surface(color = ToolGreen.copy(alpha = .09f), shape = RoundedCornerShape(14.dp)) {
                                Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Security, contentDescription = null, tint = ToolGreen, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.duplicate_original_kept, item.protectedDuplicateName), color = OnBlack.copy(alpha = .88f), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
