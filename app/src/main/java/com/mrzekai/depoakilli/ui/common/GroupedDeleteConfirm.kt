package com.mrzekai.depoakilli.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.data.StoragePathRules
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.CleanupGroup
import com.mrzekai.depoakilli.ui.assessmentReason
import com.mrzekai.depoakilli.ui.media.rememberThumbnail
import com.mrzekai.depoakilli.ui.smart.FilePreviewDialog
import com.mrzekai.depoakilli.ui.theme.AdLabel
import com.mrzekai.depoakilli.ui.theme.AppWeights
import com.mrzekai.depoakilli.ui.theme.Black
import com.mrzekai.depoakilli.ui.theme.BlackChip
import com.mrzekai.depoakilli.ui.theme.Ink
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft
import com.mrzekai.depoakilli.ui.toSmartPreview

/**
 * What the grouped confirmation will really remove (D2): the files whose
 * group is still ticked and that were not unticked one by one.
 */
internal object ConfirmSelection {
    fun selected(
        items: List<CleanableItem>,
        excludedGroups: Set<CleanupGroup>,
        excludedIds: Set<String>,
    ): List<CleanableItem> = items.filter { it.assessment.group !in excludedGroups && it.id !in excludedIds }
}

/**
 * Faz 5D (D2): the confirmation lists the selection as group rows — a box
 * (ticked), the name, the file count and size, an arrow. Unticking a group
 * leaves it out of this delete; a tap opens the group's files with previews,
 * where single files can be left out too. The title, the split lines and the
 * deleted ids always follow what is still ticked.
 */
@Composable
internal fun GroupedDeleteConfirm(
    items: List<CleanableItem>,
    inProgress: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
    initiallyOpenGroup: CleanupGroup? = null,
) {
    var excludedGroupNames by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var excludedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var openGroupName by rememberSaveable { mutableStateOf(initiallyOpenGroup?.name) }
    val excludedGroups = excludedGroupNames.mapNotNull { name -> CleanupGroup.entries.firstOrNull { it.name == name } }.toSet()
    val selected = ConfirmSelection.selected(items, excludedGroups, excludedIds.toSet())
    val selectedIds = selected.mapTo(HashSet(), CleanableItem::id)
    val split = DeleteSplit.of(selected)
    val groups = ConfirmGroups.of(items)
    val confirm = { onConfirm(selected.mapTo(linkedSetOf(), CleanableItem::id)) }
    val rows: @Composable () -> Unit = {
        groups.forEach { group ->
            val groupItems = items.filter { it.assessment.group == group }
            val included = groupItems.filter { it.id in selectedIds }
            ConfirmGroupRow(
                group = group,
                checked = group !in excludedGroups && included.isNotEmpty(),
                count = included.size.takeIf { group !in excludedGroups } ?: groupItems.size,
                bytes = (if (group in excludedGroups) groupItems else included).sumOf(CleanableItem::sizeBytes),
                cameraCount = included.count { StoragePathRules.isCameraPath(it.relativePath) },
                onCheckedChange = { checked ->
                    excludedGroupNames = if (checked) excludedGroupNames - group.name else excludedGroupNames + group.name
                },
                onOpen = { openGroupName = group.name },
            )
        }
    }
    if (split.sheet == DeleteSplit.Sheet.PERMANENT) {
        PermanentDeleteSheet(
            bytes = split.permanentBytes,
            body = stringResource(R.string.confirm_permanent_body),
            onDismiss = onDismiss,
            onDeletePermanently = confirm,
            inProgress = inProgress,
            extraContent = rows,
        )
    } else {
        AppSheet(onDismiss = onDismiss, dismissible = !inProgress) {
            SheetTitle(
                stringResource(
                    if (split.sheet == DeleteSplit.Sheet.TRASH) R.string.confirm_trash_title else R.string.confirm_clean_title,
                    ByteFormatter.format(split.totalBytes),
                ),
            )
            rows()
            DeleteSplitLines(split)
            // WhatsApp media stop opening in the chat; camera files are flagged per group row.
            if (selected.any { StoragePathRules.isWhatsAppPath(it.relativePath) }) {
                SheetBody(stringResource(R.string.plan_preview_whatsapp), soft = true)
            }
            PillButton(
                text = stringResource(R.string.confirm_yes_clean),
                onClick = confirm,
                style = PillStyle.LIME,
                enabled = selected.isNotEmpty(),
                busy = inProgress,
            )
            LinkButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
                color = OnBlack,
                enabled = !inProgress,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    val openGroup = openGroupName?.let { name -> groups.firstOrNull { it.name == name } }
    if (openGroup != null) {
        ConfirmGroupFiles(
            group = openGroup,
            items = items.filter { it.assessment.group == openGroup },
            isIncluded = { item -> openGroup !in excludedGroups && item.id !in excludedIds },
            onToggle = { item ->
                if (openGroup in excludedGroups) {
                    // Ticking a file of an unticked group brings the group back with only that file.
                    excludedGroupNames = excludedGroupNames - openGroup.name
                    excludedIds = (excludedIds + items.filter { it.assessment.group == openGroup }.map(CleanableItem::id)) - item.id
                } else {
                    excludedIds = if (item.id in excludedIds) excludedIds - item.id else excludedIds + item.id
                }
            },
            onBack = { openGroupName = null },
        )
    }
}

@Composable
private fun ConfirmGroupRow(
    group: CleanupGroup,
    checked: Boolean,
    count: Int,
    bytes: Long,
    cameraCount: Int,
    onCheckedChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    Surface(
        onClick = onOpen,
        color = BlackChip,
        contentColor = OnBlack,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 4.dp, end = 10.dp, top = 6.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = CheckboxDefaults.colors(checkedColor = Lime, uncheckedColor = OnBlackSoft, checkmarkColor = Ink),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(group.titleRes),
                        color = OnBlack,
                        fontWeight = AppWeights.Title,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        pluralStringResource(R.plurals.home_scan_files_count, count, count) + " · " + ByteFormatter.format(bytes),
                        color = OnBlackSoft,
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = stringResource(R.string.plan_suggest_see), tint = Lime)
            }
            if (checked && cameraCount > 0) {
                // D1 rule 3: camera files may be in a user's "Select all"; say so here.
                Text(
                    pluralStringResource(R.plurals.confirm_camera_files, cameraCount, cameraCount),
                    color = AdLabel,
                    fontSize = 13.sp,
                    fontWeight = AppWeights.Strong,
                    modifier = Modifier.padding(start = 14.dp, top = 2.dp),
                )
            }
        }
    }
}

/**
 * The files of one group in the confirmation (D2): a small preview, name,
 * size and age, and a box; a tap opens the file preview (photo, video,
 * document name and folder).
 */
@Composable
private fun ConfirmGroupFiles(
    group: CleanupGroup,
    items: List<CleanableItem>,
    isIncluded: (CleanableItem) -> Boolean,
    onToggle: (CleanableItem) -> Unit,
    onBack: () -> Unit,
) {
    var previewId by remember { mutableStateOf<String?>(null) }
    val locale = LocalConfiguration.current.locales[0]
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Black, contentColor = OnBlack) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back), tint = OnBlack)
                    }
                    Text(
                        stringResource(group.titleRes),
                        color = OnBlack,
                        fontWeight = AppWeights.Title,
                        fontSize = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp),
                ) {
                    items(items.sortedByDescending(CleanableItem::sizeBytes), key = CleanableItem::id) { item ->
                        ConfirmFileRow(
                            item = item,
                            included = isIncluded(item),
                            age = RelativeAge.format(item.modifiedAtMillis, locale),
                            onToggle = { onToggle(item) },
                            onOpen = { previewId = item.id },
                        )
                    }
                }
            }
        }
    }
    items.firstOrNull { it.id == previewId }?.let { item ->
        FilePreviewDialog(
            file = item.toSmartPreview(),
            selected = isIncluded(item),
            reason = assessmentReason(item.assessment),
            onToggleSelection = { onToggle(item) },
            onDismiss = { previewId = null },
        )
    }
}

@Composable
private fun ConfirmFileRow(
    item: CleanableItem,
    included: Boolean,
    age: String,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val thumbnail by rememberThumbnail(item.uri, item.mimeType)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(BlackChip)
            .clickable(onClick = onOpen)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Black),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = thumbnail
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(FileVisuals.icon(item.mimeType, item.name), contentDescription = null, tint = Lime, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, color = OnBlack, fontWeight = AppWeights.Strong, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                ByteFormatter.format(item.sizeBytes) + " · " + age,
                color = OnBlackSoft,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        Checkbox(
            checked = included,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = Lime, uncheckedColor = OnBlackSoft, checkmarkColor = Ink),
        )
    }
}
