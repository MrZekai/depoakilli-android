package com.mrzekai.depoakilli.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.data.CleaningThresholds
import com.mrzekai.depoakilli.data.StoragePathRules
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.DeleteMode
import com.mrzekai.depoakilli.ui.ContrastTokens

private val DeleteRed = Color(0xFFFF5D68)

/** One row of the confirmation: name, its folder and, for copies, the original that stays. */
internal data class DeleteConfirmEntry(
    val name: String,
    val folder: String,
    val keptOriginal: String? = null,
)
private const val PREVIEW_NAMES = 5

/**
 * The one delete confirmation of the tool, WhatsApp and Storage Review screens:
 * count and size, the first names with "Show all", where the files go
 * (Trash or permanent) and "Delete" / "Cancel".
 */
@Composable
internal fun DeleteConfirmDialog(
    entries: List<DeleteConfirmEntry>,
    trashBytes: Long,
    permanentBytes: Long,
    inProgress: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    extraContent: (@Composable () -> Unit)? = null,
) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val count = entries.size
    val days = CleaningThresholds.TRASH_RETENTION_DAYS
    AlertDialog(
        onDismissRequest = { if (!inProgress) onDismiss() },
        icon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null, tint = DeleteRed) },
        title = { Text(pluralStringResource(R.plurals.delete_confirm_title, count, count), fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val visible = if (showAll) entries else entries.take(PREVIEW_NAMES)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                    itemsIndexed(visible) { _, entry ->
                        // Name plus folder, so two files with the same name can be told
                        // apart; copies also show the original that is kept.
                        Column(Modifier.padding(vertical = 3.dp)) {
                            Text(
                                "• ${entry.name}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                bidiWrap(entry.folder.ifBlank { "/" }),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            entry.keptOriginal?.let { original ->
                                Text(
                                    stringResource(R.string.duplicate_original_kept, bidiWrap(original)),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                if (count > PREVIEW_NAMES) {
                    TextButton(onClick = { showAll = !showAll }) {
                        Text(
                            if (showAll) {
                                stringResource(R.string.delete_confirm_show_less)
                            } else {
                                stringResource(R.string.delete_confirm_show_all) + " (" + count + ")"
                            },
                        )
                    }
                }
                extraContent?.invoke()
                Text(
                    deleteModeSentence(trashBytes, permanentBytes, days),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (permanentBytes > 0L) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = count > 0 && !inProgress,
                colors = ButtonDefaults.buttonColors(containerColor = DeleteRed, contentColor = ContrastTokens.OnBrightAction),
            ) {
                Text(stringResource(R.string.delete_confirm_action), fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !inProgress) { Text(stringResource(R.string.cancel)) }
        },
    )
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
    permanentBytes > 0L -> stringResource(R.string.delete_confirm_permanent, ByteFormatter.format(permanentBytes))
    else -> pluralStringResource(R.plurals.delete_confirm_trash, days, ByteFormatter.format(trashBytes), days)
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
        inProgress = inProgress,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        // WhatsApp media deleted here no longer opens in the chat (V2 risk).
        extraContent = if (items.any { StoragePathRules.isWhatsAppPath(it.relativePath) }) {
            { Text(stringResource(R.string.plan_preview_whatsapp), style = MaterialTheme.typography.bodySmall) }
        } else {
            null
        },
    )
}
