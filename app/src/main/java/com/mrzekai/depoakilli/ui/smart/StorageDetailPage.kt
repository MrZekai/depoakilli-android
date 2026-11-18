package com.mrzekai.depoakilli.ui.smart

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.StorageFileType
import com.mrzekai.depoakilli.model.StorageReviewItem
import com.mrzekai.depoakilli.model.StorageReviewSummary
import com.mrzekai.depoakilli.ui.*
import com.mrzekai.depoakilli.ui.common.DeleteConfirmDialog
import com.mrzekai.depoakilli.ui.common.DeleteConfirmEntry
import com.mrzekai.depoakilli.ui.common.FileVisuals
import com.mrzekai.depoakilli.ui.common.SelectionBar
import com.mrzekai.depoakilli.ui.media.rememberThumbnail
import com.mrzekai.depoakilli.ui.theme.Black
import com.mrzekai.depoakilli.ui.theme.Ink
import com.mrzekai.depoakilli.ui.theme.InkSoft
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft

@Composable
internal fun StorageDetailPage(
    type: StorageFileType,
    review: StorageReviewSummary,
    progressFiles: Int,
    progressDirectories: Int,
    cleanupInProgress: Boolean,
    onToggleItem: (String) -> Unit,
    onToggleAll: () -> Unit,
    onPreview: (StorageReviewItem) -> Unit,
    onCleanSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmCleanup by remember { mutableStateOf(false) }
    val selectedItems = review.selectedItems
    val selectedCount = selectedItems.size
    val selectedBytes = review.selectedBytes
    val allSelected = review.items.isNotEmpty() && selectedCount == review.items.size
    val selectionState = sectionSelectionState(selectedCount, review.items.size)
    val accent = storageAccent(type)
    val visualType = type == StorageFileType.IMAGES || type == StorageFileType.VIDEOS

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                SmartBackground,
            ),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = SmartCard),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = accent.copy(alpha = .16f),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(
                            storageTypeVisual(type),
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.padding(10.dp).size(28.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (review.loading) {
                                stringResource(R.string.storage_review_scanning)
                            } else {
                                pluralStringResource(
                                    R.plurals.storage_review_summary,
                                    review.items.size,
                                    review.items.size,
                                    ByteFormatter.format(review.totalBytes),
                                )
                            },
                            color = OnBlack,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            stringResource(R.string.storage_review_no_auto_select),
                            color = SmartTextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                if (review.loading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SmartCardAlt)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp,
                            color = SmartCyan,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(
                                R.string.storage_review_scan_progress,
                                progressFiles,
                                progressDirectories,
                            ),
                            color = OnBlack,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else if (review.items.isNotEmpty() && type !in PERSONAL_STORAGE_TYPES && review.folder == null) {
                    // Photos, videos and documents are personal: no "Select all" (T2.4).
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SmartCardAlt)
                            .clickable { onToggleAll() }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TriStateCheckbox(
                            state = selectionState,
                            onClick = onToggleAll,
                            colors = CheckboxDefaults.colors(
                                checkedColor = Lime,
                                uncheckedColor = OnBlackSoft,
                                checkmarkColor = Ink,
                            ),
                        )
                        Spacer(Modifier.width(5.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(if (allSelected) R.string.smart_detail_unselect_all else R.string.smart_detail_select_all),
                                color = OnBlack,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                stringResource(
                                    R.string.storage_review_selected_summary,
                                    selectedCount,
                                    ByteFormatter.format(selectedBytes),
                                ),
                                color = if (selectedCount > 0) SmartGreen else SmartTextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        when {
            review.loading && review.items.isEmpty() -> {
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.storage_review_loading_hint),
                        color = InkSoft,
                    )
                }
            }

            review.items.isEmpty() -> {
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.smart_storage_preview_empty),
                        color = InkSoft,
                    )
                }
            }

            visualType -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    gridItems(review.items, key = StorageReviewItem::id) { item ->
                        StorageReviewGridItem(
                            item = item,
                            accent = accent,
                            onPreview = { onPreview(item) },
                            onToggle = { onToggleItem(item.id) },
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    items(review.items, key = StorageReviewItem::id) { item ->
                        StorageReviewListItem(
                            item = item,
                            accent = accent,
                            onPreview = { onPreview(item) },
                            onToggle = { onToggleItem(item.id) },
                        )
                    }
                }
            }
        }

        if (review.scanLimitReached) {
            Text(
                stringResource(R.string.storage_review_limit_note),
                color = Ink,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
        }

        ReviewCleanupFooter(
            selectedCount = selectedCount,
            selectedBytes = selectedBytes,
            enabled = selectedItems.isNotEmpty() && !cleanupInProgress,
            inProgress = cleanupInProgress,
            onClick = { confirmCleanup = true },
        )
    }

    if (confirmCleanup) {
        DeleteConfirmDialog(
            entries = selectedItems.map { DeleteConfirmEntry(it.file.name, it.file.relativePath) },
            trashBytes = selectedItems.sumOf { it.file.sizeBytes },
            permanentBytes = 0L,
            groupNames = listOf(stringResource(type.titleRes)),
            onDismiss = { confirmCleanup = false },
            onConfirm = {
                confirmCleanup = false
                onCleanSelected()
            },
        )
    }
}

@Composable
internal fun StorageReviewGridItem(
    item: StorageReviewItem,
    accent: Color,
    onPreview: () -> Unit,
    onToggle: () -> Unit,
) {
    val file = item.file
    val bitmap by rememberThumbnail(file.uri, file.mimeType)

    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = 210.dp),
        onClick = onPreview,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SmartCard),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp)
                    .background(SmartCardAlt),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = requireNotNull(bitmap),
                        contentDescription = file.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        FileVisuals.icon(file.mimeType, file.name),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(42.dp),
                    )
                }
                if (file.mimeType.startsWith("video/")) {
                    Surface(color = Black.copy(alpha = .8f), shape = CircleShape) {
                        Text("▶", color = OnBlack, fontSize = 20.sp, modifier = Modifier.padding(8.dp))
                    }
                }
                Checkbox(
                    checked = item.selected,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    colors = CheckboxDefaults.colors(
                        checkedColor = Lime,
                        uncheckedColor = OnBlack,
                        checkmarkColor = Ink,
                    ),
                )
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(7.dp),
                    color = Black.copy(alpha = .85f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        ByteFormatter.format(file.sizeBytes),
                        color = OnBlack,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    file.name,
                    color = OnBlack,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                )
                Text(
                    compactRelativePath(file.relativePath),
                    color = OnBlackSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                )
                Text(
                    stringResource(if (item.selected) R.string.smart_included_in_cleanup else R.string.smart_not_included_in_cleanup),
                    color = if (item.selected) Lime else OnBlackSoft,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
internal fun StorageReviewListItem(
    item: StorageReviewItem,
    accent: Color,
    onPreview: () -> Unit,
    onToggle: () -> Unit,
) {
    val file = item.file
    Card(
        onClick = onPreview,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SmartCard),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = accent.copy(alpha = .16f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(
                    FileVisuals.icon(file.mimeType, file.name),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.padding(10.dp).size(28.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    file.name,
                    color = OnBlack,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    compactRelativePath(file.relativePath),
                    color = SmartTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    ByteFormatter.format(file.sizeBytes),
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Checkbox(
                checked = item.selected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = Lime,
                    uncheckedColor = OnBlackSoft,
                    checkmarkColor = Ink,
                ),
            )
        }
    }
}

@Composable
internal fun ReviewCleanupFooter(
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
