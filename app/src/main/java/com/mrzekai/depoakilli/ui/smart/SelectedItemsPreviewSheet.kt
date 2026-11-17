package com.mrzekai.depoakilli.ui.smart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.data.CleaningThresholds
import com.mrzekai.depoakilli.data.StoragePathRules
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.DeleteMode
import com.mrzekai.depoakilli.ui.common.PillButton
import com.mrzekai.depoakilli.ui.common.PillStyle
import com.mrzekai.depoakilli.ui.common.bidiWrap
import com.mrzekai.depoakilli.ui.theme.AdLabel
import com.mrzekai.depoakilli.ui.theme.Black
import com.mrzekai.depoakilli.ui.theme.BlackLine
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft

/**
 * The single confirmation before cleaning (T1.7): every selected file is
 * listed and can be removed from the selection. "Clean" here is the only
 * confirmation; no second dialog follows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectedItemsPreviewSheet(
    items: List<CleanableItem>,
    onRemoveItem: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val permanentBytes = items.filter { it.assessment.group.deleteMode == DeleteMode.PERMANENT }
        .sumOf(CleanableItem::sizeBytes)
    val trashBytes = items.filter { it.assessment.group.deleteMode == DeleteMode.TRASH }
        .sumOf(CleanableItem::sizeBytes)
    val hasWhatsApp = items.any { StoragePathRules.isWhatsAppPath(it.relativePath) }
    val days = CleaningThresholds.TRASH_RETENTION_DAYS
    val groups = items.groupBy { it.assessment.group }.entries.sortedByDescending { (_, list) ->
        list.sumOf(CleanableItem::sizeBytes)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = com.mrzekai.depoakilli.ui.common.MakeRoomShapes.Sheet,
        containerColor = Black,
        contentColor = OnBlack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.plan_preview_title),
                color = OnBlack,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
            )
            groups.forEach { (group, groupItems) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(group.titleRes),
                        color = OnBlack,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        pluralStringResource(
                            R.plurals.whatsapp_category_summary,
                            groupItems.size,
                            groupItems.size,
                            ByteFormatter.format(groupItems.sumOf(CleanableItem::sizeBytes)),
                        ),
                        color = OnBlackSoft,
                        fontSize = 13.sp,
                    )
                }
            }
            HorizontalDivider(color = BlackLine)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
            ) {
                items(items, key = CleanableItem::id) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.name,
                                color = OnBlack,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                stringResource(
                                    R.string.swipe_age_days,
                                    bidiWrap(item.relativePath.ifBlank { "/" }),
                                    ByteFormatter.format(item.sizeBytes),
                                ),
                                color = OnBlackSoft,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { onRemoveItem(item.id) }) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.plan_preview_remove),
                                tint = OnBlackSoft,
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = BlackLine)
            Text(
                pluralStringResource(
                    R.plurals.plan_preview_summary,
                    days,
                    ByteFormatter.format(permanentBytes),
                    ByteFormatter.format(trashBytes),
                    days,
                ),
                color = OnBlack,
                fontWeight = FontWeight.Bold,
            )
            if (hasWhatsApp) {
                Text(stringResource(R.string.plan_preview_whatsapp), color = AdLabel, fontSize = 13.sp)
            }
            val cameraCount = items.count { StoragePathRules.isCameraPath(it.relativePath) }
            if (cameraCount > 0) {
                Text(
                    pluralStringResource(R.plurals.plan_preview_camera, cameraCount, cameraCount),
                    color = AdLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(stringResource(R.string.plan_preview_cloud), color = OnBlackSoft, fontSize = 13.sp)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PillButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    style = PillStyle.LIME_OUTLINE,
                    modifier = Modifier.weight(1f),
                )
                PillButton(
                    text = stringResource(R.string.plan_preview_clean),
                    onClick = { onConfirm(items.mapTo(linkedSetOf(), CleanableItem::id)) },
                    style = PillStyle.LIME,
                    enabled = items.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
