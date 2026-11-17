package com.mrzekai.depoakilli.ui.common

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.data.CleaningThresholds
import com.mrzekai.depoakilli.data.StoragePathRules
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.CleanupGroup
import com.mrzekai.depoakilli.model.DeleteMode
import com.mrzekai.depoakilli.ui.theme.AppWeights
import com.mrzekai.depoakilli.ui.theme.Black
import com.mrzekai.depoakilli.ui.theme.Coral
import com.mrzekai.depoakilli.ui.theme.CoralInk
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft

/** One row of the confirmation: name, its folder and, for copies, the original that stays. */
internal data class DeleteConfirmEntry(
    val name: String,
    val folder: String,
    val keptOriginal: String? = null,
)

/**
 * Group names shown in the "Move to Trash?" body (T5.3), produced from the
 * selection itself: every group of the selected items once, biggest first.
 * Never a fixed sentence about what is or is not touched.
 */
internal object ConfirmGroups {
    fun of(items: List<CleanableItem>): List<CleanupGroup> =
        items.groupBy { it.assessment.group }
            .entries
            .sortedByDescending { (_, groupItems) -> groupItems.sumOf(CleanableItem::sizeBytes) }
            .map { it.key }
}

/** Black bottom sheet with 34 dp top corners (disclosures and confirmations). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppSheet(
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    content: @Composable () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { dismissible },
    )
    ModalBottomSheet(
        onDismissRequest = { if (dismissible) onDismiss() },
        sheetState = sheetState,
        shape = MakeRoomShapes.Sheet,
        containerColor = Black,
        contentColor = OnBlack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

/** Title of a sheet: 24 sp, weight 800, light on black. */
@Composable
internal fun SheetTitle(text: String) {
    Text(text, color = OnBlack, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = AppWeights.Title)
}

/** Body text of a sheet. */
@Composable
internal fun SheetBody(text: String, soft: Boolean = false) {
    Text(
        text,
        color = if (soft) OnBlackSoft else OnBlack,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        fontWeight = AppWeights.Body,
    )
}

/**
 * "Move X to Trash?" (T5.3): size in the title, count and the real group
 * names in the body, "restore for 7 days", a link to every file, a Lime
 * "Yes, clean up" and a text "Cancel". No ad on this sheet.
 */
@Composable
internal fun TrashConfirmSheet(
    totalBytes: Long,
    count: Int,
    groupNames: List<String>,
    permanentBytes: Long,
    onSeeAll: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    inProgress: Boolean = false,
    notes: List<String> = emptyList(),
    extraContent: (@Composable () -> Unit)? = null,
) {
    AppSheet(onDismiss = onDismiss, dismissible = !inProgress) {
        SheetTitle(stringResource(R.string.confirm_trash_title, ByteFormatter.format(totalBytes)))
        SheetBody(
            pluralStringResource(
                R.plurals.confirm_trash_body,
                count,
                count,
                groupNames.joinToString(", "),
            ),
        )
        if (permanentBytes > 0L) {
            // Part of the selection cannot go to Trash (thumbnails, temporary files…).
            Text(
                deleteModeSentence(totalBytes - permanentBytes, permanentBytes, CleaningThresholds.TRASH_RETENTION_DAYS),
                color = Coral,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = AppWeights.Strong,
            )
        }
        notes.forEach { SheetBody(it, soft = true) }
        extraContent?.invoke()
        LinkButton(
            text = pluralStringResource(R.plurals.confirm_see_files, count, count),
            onClick = onSeeAll,
            color = com.mrzekai.depoakilli.ui.theme.Lime,
            modifier = Modifier.padding(start = 0.dp),
        )
        PillButton(
            text = stringResource(R.string.confirm_yes_clean),
            onClick = onConfirm,
            style = PillStyle.LIME,
            enabled = count > 0,
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

/**
 * Permanent delete (T5.3): a Coral warning line, the size in the title, why
 * Trash cannot be used, and the safe choice as the big button: a large
 * white "Cancel" above a Coral-outlined "Delete permanently".
 */
@Composable
internal fun PermanentDeleteSheet(
    bytes: Long,
    body: String,
    onDismiss: () -> Unit,
    onDeletePermanently: () -> Unit,
    inProgress: Boolean = false,
    extraContent: (@Composable () -> Unit)? = null,
) {
    AppSheet(onDismiss = onDismiss, dismissible = !inProgress) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Coral)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = CoralInk, modifier = Modifier.size(20.dp))
            Text(
                stringResource(R.string.confirm_permanent_flag),
                color = CoralInk,
                fontWeight = AppWeights.Giant,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        SheetTitle(stringResource(R.string.confirm_permanent_title, ByteFormatter.format(bytes)))
        SheetBody(body)
        extraContent?.invoke()
        PillButton(
            text = stringResource(R.string.cancel),
            onClick = onDismiss,
            style = PillStyle.WHITE,
            enabled = !inProgress,
        )
        PillButton(
            text = stringResource(R.string.confirm_permanent_action),
            onClick = onDeletePermanently,
            style = PillStyle.CORAL_OUTLINE,
            busy = inProgress,
        )
    }
}

/** Names of the files (with folder), shown when the user opens "See all N files". */
@Composable
internal fun ConfirmFileList(entries: List<DeleteConfirmEntry>) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
        itemsIndexed(entries) { _, entry ->
            // Name plus folder, so two files with the same name can be told
            // apart; copies also show the original that is kept.
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(
                    entry.name,
                    color = OnBlack,
                    fontSize = 14.sp,
                    fontWeight = AppWeights.Strong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    bidiWrap(entry.folder.ifBlank { "/" }),
                    color = OnBlackSoft,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                entry.keptOriginal?.let { original ->
                    Text(
                        stringResource(R.string.duplicate_original_kept, bidiWrap(original)),
                        color = OnBlackSoft,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * The one delete confirmation of the tool, WhatsApp and Storage Review
 * screens. Files that go to Trash get the "Move to Trash?" sheet; a
 * selection that can only be deleted for good gets the permanent sheet.
 */
@Composable
internal fun DeleteConfirmDialog(
    entries: List<DeleteConfirmEntry>,
    trashBytes: Long,
    permanentBytes: Long,
    groupNames: List<String>,
    inProgress: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    notes: List<String> = emptyList(),
    extraContent: (@Composable () -> Unit)? = null,
) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val fileList: @Composable () -> Unit = {
        extraContent?.invoke()
        if (showAll) ConfirmFileList(entries)
    }
    if (trashBytes <= 0L && permanentBytes > 0L) {
        PermanentDeleteSheet(
            bytes = permanentBytes,
            body = stringResource(R.string.confirm_permanent_body),
            onDismiss = onDismiss,
            onDeletePermanently = onConfirm,
            inProgress = inProgress,
            extraContent = {
                fileList()
                if (!showAll) {
                    LinkButton(
                        text = pluralStringResource(R.plurals.confirm_see_files, entries.size, entries.size),
                        onClick = { showAll = true },
                        color = OnBlack,
                    )
                }
            },
        )
    } else {
        TrashConfirmSheet(
            totalBytes = trashBytes + permanentBytes,
            count = entries.size,
            groupNames = groupNames,
            permanentBytes = permanentBytes,
            onSeeAll = { showAll = !showAll },
            onDismiss = onDismiss,
            onConfirm = onConfirm,
            inProgress = inProgress,
            notes = notes,
            extraContent = fileList,
        )
    }
}

@Composable
private fun deleteModeSentence(trashBytes: Long, permanentBytes: Long, days: Int): String = when {
    trashBytes > 0L && permanentBytes > 0L -> pluralStringResource(
        R.plurals.plan_preview_summary,
        days,
        ByteFormatter.format(permanentBytes),
        ByteFormatter.format(trashBytes),
        days,
    )
    else -> stringResource(R.string.delete_confirm_permanent, ByteFormatter.format(permanentBytes))
}

/** Notes that must stay with a delete: WhatsApp chats lose the media, camera files are personal. */
@Composable
internal fun deleteSafetyNotes(items: List<CleanableItem>): List<String> {
    val notes = ArrayList<String>()
    // WhatsApp media deleted here no longer opens in the chat (V2 risk).
    if (items.any { StoragePathRules.isWhatsAppPath(it.relativePath) }) {
        notes += stringResource(R.string.plan_preview_whatsapp)
    }
    val cameraCount = items.count { StoragePathRules.isCameraPath(it.relativePath) }
    if (cameraCount > 0) notes += pluralStringResource(R.plurals.plan_preview_camera, cameraCount, cameraCount)
    return notes
}

/** Convenience for [CleanableItem] lists: splits the bytes by each group's delete mode. */
@Composable
internal fun DeleteConfirmDialog(
    items: List<CleanableItem>,
    inProgress: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val (permanent, trash) = items.partition { it.assessment.group.deleteMode == DeleteMode.PERMANENT }
    DeleteConfirmDialog(
        entries = items.map { item ->
            DeleteConfirmEntry(
                name = item.name,
                folder = item.relativePath,
                keptOriginal = item.protectedDuplicateName?.let { originalName ->
                    listOfNotNull(item.protectedDuplicateRelativePath?.trim('/')?.takeIf(String::isNotBlank), originalName)
                        .joinToString("/")
                },
            )
        },
        trashBytes = trash.sumOf(CleanableItem::sizeBytes),
        permanentBytes = permanent.sumOf(CleanableItem::sizeBytes),
        groupNames = ConfirmGroups.of(items).map { stringResource(it.titleRes) },
        inProgress = inProgress,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        notes = deleteSafetyNotes(items),
    )
}
