package com.mrzekai.depoakilli.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.data.TrashEntry
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.ui.common.CardDivider
import com.mrzekai.depoakilli.ui.common.GiantNumber
import com.mrzekai.depoakilli.ui.common.LinkButton
import com.mrzekai.depoakilli.ui.common.MakeRoomShapes
import com.mrzekai.depoakilli.ui.common.PageFrame
import com.mrzekai.depoakilli.ui.common.PageLabel
import com.mrzekai.depoakilli.ui.common.PermanentDeleteSheet
import com.mrzekai.depoakilli.ui.common.PillButton
import com.mrzekai.depoakilli.ui.common.PillStyle
import com.mrzekai.depoakilli.ui.common.bidiWrap
import com.mrzekai.depoakilli.ui.common.rememberGiantScale
import com.mrzekai.depoakilli.ui.theme.AppWeights
import com.mrzekai.depoakilli.ui.theme.Black
import com.mrzekai.depoakilli.ui.theme.Ink
import com.mrzekai.depoakilli.ui.theme.InkSoft
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft
import java.io.File
import java.util.concurrent.TimeUnit

/** Whole days until an entry is deleted, rounded up, never negative. */
internal fun trashDaysLeft(entry: TrashEntry, nowMillis: Long): Int {
    val left = entry.expiresAtMillis() - nowMillis
    if (left <= 0L) return 0
    return ((left + TimeUnit.DAYS.toMillis(1) - 1) / TimeUnit.DAYS.toMillis(1)).toInt()
}

/**
 * Trash tab (T1.8, Faz 5 T5.9) on Lime: the giant amount waiting in Trash and
 * that it is freed only when Trash is emptied; the files in one black card
 * (days left in Lime); a big black "Restore all" (or the selected files) and
 * "Empty Trash and free X" behind the permanent-delete sheet. An empty Trash
 * is one sentence without buttons.
 */
@Composable
internal fun TrashScreen(
    entries: List<TrashEntry>,
    onRefresh: () -> Unit,
    onRestore: (Set<String>) -> Unit,
    onDeleteForever: (Set<String>?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var confirmDelete by remember { mutableStateOf<Set<String>?>(null) }
    var confirmEmptyAll by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val visibleIds = entries.mapTo(hashSetOf(), TrashEntry::id)
    val activeSelection = selected intersect visibleIds
    val totalBytes = entries.sumOf(TrashEntry::sizeBytes)
    val scale = rememberGiantScale()

    LaunchedEffect(Unit) { onRefresh() }

    PageFrame(modifier = modifier) {
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.trash_empty_state),
                        color = Ink,
                        fontSize = 26.sp,
                        lineHeight = 32.sp,
                        fontWeight = AppWeights.Giant,
                        textAlign = TextAlign.Center,
                    )
                    // C6: one line on what Trash is for.
                    Text(
                        stringResource(R.string.trash_empty_hint),
                        color = InkSoft,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        fontWeight = AppWeights.Strong,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            return@PageFrame
        }
        val sorted = entries.sortedBy { it.deletedAtMillis }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item(key = "trash-head") {
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    GiantNumber(ByteFormatter.format(totalBytes), scale, modifier = Modifier.fillMaxWidth())
                    PageLabel(stringResource(R.string.trash_waiting), strong = true)
                    PageLabel(stringResource(R.string.trash_note_v2))
                }
            }
            itemsIndexed(sorted, key = { _, entry -> entry.id }) { index, entry ->
                val shape = when {
                    sorted.size == 1 -> MakeRoomShapes.Card
                    index == 0 -> androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    index == sorted.lastIndex -> androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                    else -> androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
                }
                androidx.compose.material3.Surface(color = Black, contentColor = OnBlack, shape = shape) {
                    Column {
                        TrashRow(
                            entry = entry,
                            selected = entry.id in activeSelection,
                            daysLeft = trashDaysLeft(entry, now),
                            onToggle = { checked ->
                                selected = if (checked) selected + entry.id else selected - entry.id
                            },
                        )
                        if (index != sorted.lastIndex) CardDivider()
                    }
                }
            }
            item(key = "trash-actions") {
                Column(
                    Modifier.fillMaxWidth().padding(top = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (activeSelection.isEmpty()) {
                        PillButton(
                            text = stringResource(R.string.trash_restore_all),
                            onClick = { onRestore(visibleIds) },
                            style = PillStyle.BLACK,
                        )
                        LinkButton(
                            text = stringResource(R.string.trash_empty_v2, ByteFormatter.format(totalBytes)),
                            onClick = { confirmEmptyAll = true },
                        )
                    } else {
                        PillButton(
                            text = stringResource(R.string.trash_restore),
                            onClick = {
                                onRestore(activeSelection)
                                selected = emptySet()
                            },
                            style = PillStyle.BLACK,
                        )
                        LinkButton(
                            text = stringResource(R.string.trash_delete_forever),
                            onClick = { confirmDelete = activeSelection },
                        )
                    }
                }
            }
        }
    }

    confirmDelete?.let { ids ->
        val bytes = entries.filter { it.id in ids }.sumOf(TrashEntry::sizeBytes)
        PermanentDeleteSheet(
            bytes = bytes,
            body = stringResource(R.string.result_empty_trash_confirm, ByteFormatter.format(bytes)),
            onDismiss = { confirmDelete = null },
            onDeletePermanently = {
                confirmDelete = null
                onDeleteForever(ids)
                selected = selected - ids
            },
        )
    }
    if (confirmEmptyAll) {
        PermanentDeleteSheet(
            bytes = totalBytes,
            body = stringResource(R.string.result_empty_trash_confirm, ByteFormatter.format(totalBytes)),
            onDismiss = { confirmEmptyAll = false },
            onDeletePermanently = {
                confirmEmptyAll = false
                onDeleteForever(null)
                selected = emptySet()
            },
        )
    }
}

@Composable
private fun TrashRow(entry: TrashEntry, selected: Boolean, daysLeft: Int, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = onToggle)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = Lime, checkmarkColor = Ink, uncheckedColor = OnBlackSoft),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Column(Modifier.weight(1f)) {
            val original = File(entry.originalPath)
            Text(
                original.name,
                color = OnBlack,
                fontWeight = AppWeights.Strong,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                bidiWrap(original.parent.orEmpty()),
                color = OnBlackSoft,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                ByteFormatter.format(entry.sizeBytes),
                color = OnBlackSoft,
                fontSize = 13.sp,
            )
        }
        Text(
            pluralStringResource(R.plurals.trash_days_left, daysLeft, daysLeft),
            color = Lime,
            fontWeight = AppWeights.Title,
            fontSize = 14.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp),
        )
    }
}
