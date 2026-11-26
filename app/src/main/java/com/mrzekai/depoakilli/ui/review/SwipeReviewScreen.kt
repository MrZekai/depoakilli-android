package com.mrzekai.depoakilli.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.ui.common.LinkButton
import com.mrzekai.depoakilli.ui.common.PillButton
import com.mrzekai.depoakilli.ui.common.PillStyle
import com.mrzekai.depoakilli.ui.common.SystemBarIcons
import com.mrzekai.depoakilli.ui.common.RelativeAge
import com.mrzekai.depoakilli.ui.common.bidiWrap
import com.mrzekai.depoakilli.ui.media.MediaPreviewContent
import com.mrzekai.depoakilli.ui.smart.SelectedItemsPreviewSheet
import com.mrzekai.depoakilli.ui.theme.AppWeights
import com.mrzekai.depoakilli.ui.theme.Black
import com.mrzekai.depoakilli.ui.theme.BlackChip
import com.mrzekai.depoakilli.ui.theme.Coral
import com.mrzekai.depoakilli.ui.theme.CoralInk
import com.mrzekai.depoakilli.ui.theme.GiantScale
import com.mrzekai.depoakilli.ui.theme.Ink
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft
import com.mrzekai.depoakilli.ui.toMediaPreview
import com.mrzekai.depoakilli.ui.toSmartPreview
import kotlin.math.roundToInt

/**
 * Swipe review (T1.11, Faz 5 T5.5): the only black screen that shows a file,
 * one at a time, largest first. Left / DELETE marks, right / KEEP keeps (and
 * is remembered, V10); the two giant buttons are the accessible equivalent of
 * the gestures. Videos play silently. Every 8 cards a break card with a
 * native ad; nothing is deleted here: the end opens one confirmation. No
 * banner on this screen.
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
    groupTitle: String = "",
    onClose: () -> Unit = {},
) {
    val byId = remember(items) { items.associateBy(CleanableItem::id) }
    var showPreview by remember { mutableStateOf(false) }
    var removedFromPreview by remember { mutableStateOf(emptySet<String>()) }
    val marked = review.markedIds
    val openConfirmation = {
        removedFromPreview = emptySet()
        showPreview = true
    }
    SystemBarIcons(darkStatusIcons = false, darkNavigationIcons = false)

    Box(
        modifier
            .fillMaxSize()
            .background(Black),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .widthIn(max = GiantScale.MAX_CONTENT_WIDTH_DP.dp)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.back), tint = OnBlack)
                }
                val shown = (review.reviewedCount + if (review.currentId != null) 1 else 0).coerceAtMost(review.total)
                Text(
                    listOf(groupTitle, stringResource(R.string.swipe_counter, shown, review.total))
                        .filter(String::isNotBlank)
                        .joinToString(" · "),
                    color = OnBlack,
                    fontWeight = AppWeights.Title,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                LinkButton(stringResource(R.string.swipe_list), onClick = onShowGrid, color = Lime)
            }

            if (review.breakPending) {
                // Break card: a short progress summary every few cards.
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                ) {
                    BreakCard(
                        reviewed = review.reviewedCount,
                        markedBytes = review.markedBytes,
                        onContinue = onContinue,
                    )
                }
            } else {
                val currentItem = review.currentId?.let(byId::get)
                if (currentItem != null) {
                    Text(
                        stringResource(R.string.swipe_title),
                        color = OnBlack,
                        fontSize = 26.sp,
                        lineHeight = 32.sp,
                        fontWeight = AppWeights.Giant,
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        SwipeCard(item = currentItem, onMark = onMark)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SwipeChoiceButton(
                            text = stringResource(R.string.swipe_delete),
                            icon = Icons.Outlined.Delete,
                            container = Coral,
                            content = CoralInk,
                            onClick = { onMark(true) },
                            modifier = Modifier.weight(1f),
                        )
                        SwipeChoiceButton(
                            text = stringResource(R.string.swipe_keep),
                            icon = Icons.Outlined.Check,
                            container = Lime,
                            content = Ink,
                            onClick = { onMark(false) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    // Every card decided: one summary and the single confirmation.
                    Column(
                        Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            pluralStringResource(
                                R.plurals.swipe_marked,
                                marked.size,
                                marked.size,
                                ByteFormatter.format(review.markedBytes),
                            ),
                            color = OnBlack,
                            fontSize = 22.sp,
                            lineHeight = 28.sp,
                            fontWeight = AppWeights.Title,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(20.dp))
                        if (marked.isNotEmpty()) {
                            PillButton(
                                text = stringResource(R.string.swipe_finish),
                                onClick = openConfirmation,
                                style = PillStyle.LIME,
                            )
                        } else {
                            PillButton(
                                text = stringResource(R.string.result_done),
                                onClick = onClose,
                                style = PillStyle.LIME,
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    LinkButton(
                        stringResource(R.string.swipe_undo_last),
                        onClick = onUndo,
                        color = OnBlack,
                        enabled = review.reviewedCount > 0,
                    )
                    Spacer(Modifier.weight(1f))
                    if (marked.isNotEmpty() && review.currentId != null) {
                        // Finishing early opens the same single confirmation.
                        LinkButton(
                            stringResource(R.string.swipe_selected_note, ByteFormatter.format(review.markedBytes)),
                            onClick = openConfirmation,
                            color = Lime,
                        )
                    } else {
                        Text(
                            stringResource(R.string.swipe_selected_note, ByteFormatter.format(review.markedBytes)),
                            color = OnBlackSoft,
                            fontSize = 13.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
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

/** One of the two 76 dp choice buttons (Coral DELETE, Lime KEEP). */
@Composable
private fun SwipeChoiceButton(
    text: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 76.dp),
        shape = RoundedCornerShape(38.dp),
        color = container,
        contentColor = content,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 18.dp),
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = content, fontWeight = AppWeights.Giant, fontSize = 20.sp)
        }
    }
}

@Composable
private fun SwipeCard(item: CleanableItem, onMark: (Boolean) -> Unit) {
    var dragX by remember(item.id) { mutableFloatStateOf(0f) }
    val preview = remember(item.id) { item.toSmartPreview() }
    Column(
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
    ) {
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            color = BlackChip,
            shape = RoundedCornerShape(26.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                MediaPreviewContent(preview.toMediaPreview(), allowAudio = false, muted = true)
            }
        }
        // C6: "10 months ago", not "Age (days): 297".
        val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
        val age = remember(item.modifiedAtMillis, locale) { RelativeAge.format(item.modifiedAtMillis, locale) }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        R.string.swipe_age_days,
                        bidiWrap(item.relativePath.ifBlank { "/" }),
                        age,
                    ),
                    color = OnBlackSoft,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.name,
                    color = OnBlack,
                    fontWeight = AppWeights.Strong,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                ByteFormatter.format(item.sizeBytes),
                color = Lime,
                fontWeight = AppWeights.Giant,
                fontSize = 18.sp,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun BreakCard(reviewed: Int, markedBytes: Long, onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            pluralStringResource(R.plurals.swipe_break, reviewed, reviewed, ByteFormatter.format(markedBytes)),
            color = OnBlack,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = AppWeights.Title,
            textAlign = TextAlign.Center,
        )
        PillButton(
            text = stringResource(R.string.swipe_continue),
            onClick = onContinue,
            style = PillStyle.LIME,
        )
    }
}
