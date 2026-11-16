package com.mrzekai.depoakilli.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.ui.ContrastTokens
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.ui.smart.SelectedItemsPreviewSheet
import com.mrzekai.depoakilli.ui.media.MediaPreviewContent
import com.mrzekai.depoakilli.ui.toMediaPreview
import com.mrzekai.depoakilli.ui.toSmartPreview
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

private val SwipeBackground = Color(0xFF030A1D)
private val SwipeSecondary = Color(0xFFB4C4DF)
private val SwipeRed = Color(0xFFFF5D68)
private val SwipeGreen = Color(0xFF28E58A)
private val SwipeButtonText = Color(0xFF03122B)

/**
 * Swipe review (T1.11): one file at a time, largest first. Left / "Delete"
 * marks, right / "Keep" keeps; the buttons are the accessible equivalent of
 * the gestures. Nothing is deleted here: "Finish" opens the single preview
 * confirmation.
 */
@Composable
internal fun SwipeReviewScreen(
    review: SwipeReviewState,
    items: List<CleanableItem>,
    onMark: (Boolean) -> Unit,
    onUndo: () -> Unit,
    onContinue: () -> Unit,
    onShowGrid: () -> Unit,
    onConfirmClean: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    breakCardContent: @Composable () -> Unit = {},
) {
    val byId = remember(items) { items.associateBy(CleanableItem::id) }
    var showPreview by remember { mutableStateOf(false) }
    var removedFromPreview by remember { mutableStateOf(emptySet<String>()) }
    val marked = review.markedIds

    Column(
        modifier
            .fillMaxSize()
            .background(SwipeBackground)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val shown = (review.reviewedCount + if (review.currentId != null) 1 else 0).coerceAtMost(review.total)
            Text(
                stringResource(R.string.swipe_counter, shown, review.total),
                color = Color.White,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onShowGrid, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.swipe_grid_view))
            }
        }

        if (review.breakPending) {
            // Break card: scrollable so the ad is never clipped; no other
            // buttons on screen while it is shown.
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            ) {
                BreakCard(
                    reviewed = review.reviewedCount,
                    markedBytes = review.markedBytes,
                    onContinue = onContinue,
                    adContent = breakCardContent,
                )
            }
        } else {

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val currentItem = review.currentId?.let(byId::get)
                when {
                    currentItem != null -> SwipeCard(item = currentItem, onMark = onMark)
                    else -> Text(
                        pluralStringResource(
                            R.plurals.swipe_marked,
                            marked.size,
                            marked.size,
                            ByteFormatter.format(review.markedBytes),
                        ),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Text(
                pluralStringResource(
                    R.plurals.swipe_marked,
                    marked.size,
                    marked.size,
                    ByteFormatter.format(review.markedBytes),
                ),
                color = SwipeSecondary,
            )
            if (review.currentId != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onMark(true) },
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SwipeRed, contentColor = ContrastTokens.OnBrightAction),
                    ) { Text(stringResource(R.string.swipe_delete), fontWeight = FontWeight.Black) }
                    Button(
                        onClick = { onMark(false) },
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SwipeGreen, contentColor = SwipeButtonText),
                    ) { Text(stringResource(R.string.swipe_keep), fontWeight = FontWeight.Black) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onUndo,
                    enabled = review.reviewedCount > 0,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.swipe_undo)) }
                OutlinedButton(
                    onClick = {
                        removedFromPreview = emptySet()
                        showPreview = true
                    },
                    enabled = marked.isNotEmpty(),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.swipe_finish)) }
            }
        }
    }

    if (showPreview) {
        SelectedItemsPreviewSheet(
            items = marked.filterNot { it in removedFromPreview }.mapNotNull(byId::get),
            onRemoveItem = { id -> removedFromPreview = removedFromPreview + id },
            onDismiss = { showPreview = false },
            onConfirm = { ids ->
                showPreview = false
                onConfirmClean(ids)
            },
        )
    }
}

@Composable
private fun SwipeCard(item: CleanableItem, onMark: (Boolean) -> Unit) {
    var dragX by remember(item.id) { mutableFloatStateOf(0f) }
    val preview = remember(item.id) { item.toSmartPreview() }
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(dragX.roundToInt(), 0) }
            .pointerInput(item.id) {
                val threshold = size.width * 0.28f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            dragX <= -threshold -> onMark(true)
                            dragX >= threshold -> onMark(false)
                        }
                        dragX = 0f
                    },
                    onDragCancel = { dragX = 0f },
                ) { change, amount ->
                    change.consume()
                    dragX += amount
                }
            },
        color = Color(0xFF0A1C42),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                MediaPreviewContent(preview.toMediaPreview(), allowAudio = false)
            }
            Column(Modifier.padding(14.dp)) {
                Text(
                    item.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(item.relativePath, color = SwipeSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val days = if (item.modifiedAtMillis > 0L) {
                    TimeUnit.MILLISECONDS.toDays((System.currentTimeMillis() - item.modifiedAtMillis).coerceAtLeast(0L)).toInt()
                } else {
                    0
                }
                Text(
                    stringResource(
                        R.string.swipe_age_days,
                        ByteFormatter.format(item.sizeBytes),
                        pluralStringResource(R.plurals.reason_large_file, days, days).substringAfter("• "),
                    ),
                    color = SwipeSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun BreakCard(reviewed: Int, markedBytes: Long, onContinue: () -> Unit, adContent: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            pluralStringResource(R.plurals.swipe_break, reviewed, reviewed, ByteFormatter.format(markedBytes)),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SwipeGreen, contentColor = SwipeButtonText),
        ) { Text(stringResource(R.string.swipe_continue), fontWeight = FontWeight.Black) }
        adContent()
    }
}
