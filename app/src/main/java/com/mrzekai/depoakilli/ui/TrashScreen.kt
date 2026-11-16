package com.mrzekai.depoakilli.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.data.CleaningThresholds
import com.mrzekai.depoakilli.data.TrashEntry
import com.mrzekai.depoakilli.model.ByteFormatter
import java.io.File
import java.util.concurrent.TimeUnit

private val TrashBackground = Color(0xFF030A1D)
private val TrashCard = Color(0xFF0A1C42)
private val TrashTextSecondary = Color(0xFFB4C4DF)
private val TrashRed = Color(0xFFFF5D68)

/** Whole days until an entry is deleted, rounded up, never negative. */
internal fun trashDaysLeft(entry: TrashEntry, nowMillis: Long): Int {
    val left = entry.expiresAtMillis() - nowMillis
    if (left <= 0L) return 0
    return ((left + TimeUnit.DAYS.toMillis(1) - 1) / TimeUnit.DAYS.toMillis(1)).toInt()
}

/** Trash tab (T1.8): restore, delete forever or empty the in-app trash. */
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

    LaunchedEffect(Unit) { onRefresh() }

    Column(
        modifier
            .fillMaxSize()
            .background(TrashBackground),
    ) {
        if (entries.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.trash_empty_state), color = TrashTextSecondary, fontSize = 16.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries.sortedBy { it.deletedAtMillis }, key = TrashEntry::id) { entry ->
                    val isSelected = entry.id in activeSelection
                    Surface(color = TrashCard, shape = RoundedCornerShape(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .toggleable(
                                    value = isSelected,
                                    role = Role.Checkbox,
                                    onValueChange = { checked ->
                                        selected = if (checked) selected + entry.id else selected - entry.id
                                    },
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = isSelected, onCheckedChange = null)
                            Column(Modifier.weight(1f)) {
                                val original = File(entry.originalPath)
                                Text(
                                    original.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    original.parent.orEmpty(),
                                    color = TrashTextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val days = trashDaysLeft(entry, now)
                                Text(
                                    stringResource(
                                        R.string.swipe_age_days,
                                        ByteFormatter.format(entry.sizeBytes),
                                        pluralStringResource(R.plurals.trash_days_left, days, days),
                                    ),
                                    color = TrashTextSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
                item(key = "trash-footer") {
                    val retention = CleaningThresholds.TRASH_RETENTION_DAYS
                    Text(
                        pluralStringResource(R.plurals.trash_footer, retention, retention),
                        color = TrashTextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF06112A))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            onRestore(activeSelection)
                            selected = emptySet()
                        },
                        enabled = activeSelection.isNotEmpty(),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.trash_restore))
                    }
                    OutlinedButton(
                        onClick = { confirmDelete = activeSelection },
                        enabled = activeSelection.isNotEmpty(),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.trash_delete_forever))
                    }
                }
                Button(
                    onClick = { confirmEmptyAll = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TrashRed, contentColor = Color.White),
                ) {
                    Text(stringResource(R.string.trash_empty), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    confirmDelete?.let { ids ->
        EmptyTrashConfirmDialog(
            bytes = entries.filter { it.id in ids }.sumOf(TrashEntry::sizeBytes),
            onDismiss = { confirmDelete = null },
            onConfirm = {
                confirmDelete = null
                onDeleteForever(ids)
                selected = selected - ids
            },
        )
    }
    if (confirmEmptyAll) {
        EmptyTrashConfirmDialog(
            bytes = entries.sumOf(TrashEntry::sizeBytes),
            onDismiss = { confirmEmptyAll = false },
            onConfirm = {
                confirmEmptyAll = false
                onDeleteForever(null)
                selected = emptySet()
            },
        )
    }
}

/** One line for the Clean tab when the trash holds files (T1.8). */
@Composable
internal fun TrashSummaryLine(entries: List<TrashEntry>, onOpen: () -> Unit) {
    if (entries.isEmpty()) return
    val now = System.currentTimeMillis()
    val days = entries.minOf { trashDaysLeft(it, now) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = TrashCard,
        shape = RoundedCornerShape(16.dp),
        onClick = onOpen,
    ) {
        Text(
            pluralStringResource(
                R.plurals.trash_home_line,
                days,
                ByteFormatter.format(entries.sumOf(TrashEntry::sizeBytes)),
                days,
            ),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}
