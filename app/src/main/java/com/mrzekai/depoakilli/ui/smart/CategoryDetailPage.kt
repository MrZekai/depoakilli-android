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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.ui.*
import com.mrzekai.depoakilli.ui.common.DeleteConfirmDialog
import com.mrzekai.depoakilli.ui.common.FileVisuals
import com.mrzekai.depoakilli.ui.media.rememberThumbnail
import com.mrzekai.depoakilli.ui.theme.Black
import com.mrzekai.depoakilli.ui.theme.Ink
import com.mrzekai.depoakilli.ui.theme.InkSoft
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft

@Composable
internal fun CategoryDetailPage(
    category: CleanCategory,
    items: List<CleanableItem>,
    cleanupInProgress: Boolean,
    onToggleCategory: () -> Unit,
    onToggleItem: (String) -> Unit,
    onPreview: (CleanableItem) -> Unit,
    onCleanSelected: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    gridAd: @Composable () -> Unit = {},
    verifyingPercent: Int? = null,
) {
    var confirmCleanup by remember { mutableStateOf(false) }
    val displayItems = items.sortedByDescending(CleanableItem::sizeBytes)
    val selectedItems = items.filter(CleanableItem::selected)
    val selectedCount = selectedItems.size
    val selectedBytes = selectedItems.sumOf(CleanableItem::sizeBytes)
    // D1: the box counts the files it can act on (not those shown to differ).
    val candidates = com.mrzekai.depoakilli.ui.PlanSelection.selectAllCandidates(items)
    val allSelected = candidates.isNotEmpty() && candidates.all(CleanableItem::selected)
    val selectionState = sectionSelectionState(candidates.count(CleanableItem::selected), candidates.size)
    val accent = categoryAccent(category)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SmartBackground),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(
                                R.string.smart_detail_selected_summary,
                                selectedCount,
                                items.size,
                                ByteFormatter.format(selectedBytes),
                            ),
                            color = OnBlack,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            stringResource(R.string.smart_detail_grid_hint),
                            color = SmartTextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Surface(
                        color = accent.copy(alpha = .16f),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            ByteFormatter.format(items.sumOf(CleanableItem::sizeBytes)),
                            color = accent,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SmartCardAlt)
                        .clickable(enabled = verifyingPercent == null && candidates.isNotEmpty()) { onToggleCategory() }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TriStateCheckbox(
                        state = selectionState,
                        onClick = onToggleCategory,
                        enabled = verifyingPercent == null && candidates.isNotEmpty(),
                        colors = CheckboxDefaults.colors(
                            checkedColor = Lime,
                            uncheckedColor = OnBlackSoft,
                            checkmarkColor = Ink,
                        ),
                    )
                    Spacer(Modifier.width(5.dp))
                    // The tap/checkbox instruction is already shown once in the
                    // header above; it is not repeated here (QA v43 polish).
                    Text(
                        when {
                            verifyingPercent != null -> stringResource(R.string.verify_select_progress, verifyingPercent)
                            allSelected -> stringResource(R.string.smart_detail_unselect_all)
                            else -> stringResource(R.string.smart_detail_select_all)
                        },
                        color = OnBlack,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (displayItems.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.smart_detail_empty), color = InkSoft)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val beforeAd = displayItems.take(LIST_AD_AFTER_ITEMS)
                val afterAd = displayItems.drop(LIST_AD_AFTER_ITEMS)
                gridItems(beforeAd, key = CleanableItem::id) { item ->
                    CategoryGridItem(
                        item = item,
                        accent = accent,
                        onPreview = { onPreview(item) },
                        onToggle = { onToggleItem(item.id) },
                    )
                }
                // One full-width native ad after the 12th file (T1B.4), only in longer grids.
                if (afterAd.isNotEmpty()) {
                    item(key = "category-list-native", span = { GridItemSpan(maxLineSpan) }) { gridAd() }
                }
                gridItems(afterAd, key = CleanableItem::id) { item ->
                    CategoryGridItem(
                        item = item,
                        accent = accent,
                        onPreview = { onPreview(item) },
                        onToggle = { onToggleItem(item.id) },
                    )
                }
            }
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
            items = selectedItems,
            onDismiss = { confirmCleanup = false },
            onConfirm = { ids ->
                confirmCleanup = false
                onCleanSelected(ids)
            },
        )
    }
}

@Composable
internal fun CategoryGridItem(
    item: CleanableItem,
    accent: Color,
    onPreview: () -> Unit,
    onToggle: () -> Unit,
) {
    val bitmap by rememberThumbnail(item.uri, item.mimeType)

    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = 214.dp),
        onClick = onPreview,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SmartCard),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .background(SmartCardAlt),
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
                    Surface(
                        color = accent.copy(alpha = .16f),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(
                            FileVisuals.icon(item.mimeType, item.name),
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.padding(18.dp).size(38.dp),
                        )
                    }
                }

                if (item.mimeType.startsWith("video/")) {
                    Surface(
                        modifier = Modifier.align(Alignment.Center),
                        color = Black.copy(alpha = .8f),
                        shape = CircleShape,
                    ) {
                        Text(
                            "▶",
                            color = OnBlack,
                            fontSize = 20.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
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
                        ByteFormatter.format(item.sizeBytes),
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
                    item.name,
                    color = OnBlack,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                )
                Text(
                    compactRelativePath(item.relativePath),
                    color = OnBlackSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                )
                Text(
                    // D1/D6: look-alikes carry one short label, never a cut sentence.
                    when {
                        item.verifiedDifferent -> stringResource(R.string.verify_not_a_copy)
                        item.assessment.group == com.mrzekai.depoakilli.model.CleanupGroup.DUPLICATE_UNVERIFIED ->
                            stringResource(R.string.verify_not_verified)
                        else -> assessmentReason(item.assessment)
                    },
                    color = if (item.verifiedDifferent) com.mrzekai.depoakilli.ui.theme.AdLabel else OnBlackSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                )
                Text(
                    stringResource(if (item.selected) R.string.smart_included_in_cleanup else R.string.smart_not_included_in_cleanup),
                    color = if (item.selected) Lime else OnBlackSoft,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}
