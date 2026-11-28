package com.mrzekai.depoakilli.ui.photo

import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.photo.PhotoReviewRules
import com.mrzekai.depoakilli.photo.ReviewPhoto
import com.mrzekai.depoakilli.photo.SimilarRepository
import com.mrzekai.depoakilli.pro.FreeTierRules
import com.mrzekai.depoakilli.ui.common.BlackCard
import com.mrzekai.depoakilli.ui.common.CardDivider
import com.mrzekai.depoakilli.ui.common.PillButton
import com.mrzekai.depoakilli.ui.common.PillStyle
import com.mrzekai.depoakilli.ui.media.rememberThumbnail
import com.mrzekai.depoakilli.ui.theme.AppWeights
import com.mrzekai.depoakilli.ui.theme.BlackChip
import com.mrzekai.depoakilli.ui.theme.Ink
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

/**
 * Faz 3B-2: one photo at a time (keep / Trash). No bulk delete here; each
 * decision counts toward the free daily decisions (Pro: unlimited).
 */
@Composable
internal fun PhotoReviewScreen(
    photos: List<ReviewPhoto>,
    repo: SimilarRepository,
    actions: SimilarActions,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var index by rememberSaveable { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    // The list shrinks as photos are decided; the first one left is always current.
    val total = remember { photos.size.coerceAtLeast(1) }
    val current = photos.firstOrNull()
    val left = actions.decisionsLeft
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.review_position, (index + 1).coerceAtMost(total), total),
                color = Ink,
                fontSize = 17.sp,
                fontWeight = AppWeights.Title,
                modifier = Modifier.weight(1f),
            )
            if (left != null) {
                Text(pluralStringResource(R.plurals.review_decisions_left, left, left), color = Ink, fontSize = 13.sp, fontWeight = AppWeights.Strong)
            }
        }
        if (current == null) {
            Text(stringResource(R.string.review_all_done), color = Ink, fontSize = 26.sp, lineHeight = 31.sp, fontWeight = AppWeights.Giant, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(16.dp))
            PillButton(text = stringResource(R.string.review_done), onClick = onDone, style = PillStyle.BLACK_LIME)
            return
        }
        BlackCard(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
            Column(Modifier.fillMaxSize()) {
                ReviewImage(current, Modifier.weight(1f).fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text(reviewTitle(current), color = OnBlack, fontSize = 19.sp, fontWeight = AppWeights.Giant)
                Text(
                    listOf(
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(current.item.takenMillis)),
                        ByteFormatter.format(current.item.sizeBytes),
                        current.item.folder.trim('/').substringAfterLast('/'),
                    ).joinToString(" · ") + ". " + stringResource(if (current.badKind != null) R.string.review_keep_if_meant else R.string.review_keep_if_needed),
                    color = OnBlackSoft,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                if (current.important) {
                    Text(stringResource(R.string.review_important), color = Lime, fontSize = 14.sp, fontWeight = AppWeights.Title, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(
                text = stringResource(R.string.review_keep),
                onClick = {
                    if (left == 0) {
                        actions.onOpenPaywall()
                        return@PillButton
                    }
                    actions.onDecision()
                    repo.keepReviewed(current)
                    index++
                },
                style = PillStyle.INK_OUTLINE,
                enabled = !busy,
                modifier = Modifier.weight(1f),
            )
            PillButton(
                text = stringResource(R.string.review_trash),
                onClick = {
                    if (left == 0) {
                        actions.onOpenPaywall()
                        return@PillButton
                    }
                    busy = true
                    scope.launch {
                        val moved = repo.trashReviewed(current)
                        busy = false
                        if (moved > 0L) {
                            actions.onDecision()
                            if (actions.isPro) actions.onProBytes(moved)
                            index++
                        } else {
                            Toast.makeText(context, context.getString(R.string.review_move_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                style = PillStyle.BLACK_LIME,
                enabled = !busy,
                busy = busy,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            stringResource(R.string.review_no_bulk),
            color = Ink,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        )
    }
}

@Composable
private fun reviewTitle(photo: ReviewPhoto): String = stringResource(
    when {
        photo.badKind == PhotoReviewRules.BadKind.BLURRY -> R.string.review_blurry
        photo.badKind == PhotoReviewRules.BadKind.DARK -> R.string.review_dark
        photo.type == PhotoReviewRules.PhotoType.MEME -> R.string.review_meme
        else -> R.string.review_document
    },
)

@Composable
private fun ReviewImage(photo: ReviewPhoto, modifier: Modifier) {
    val uri = remember(photo.item.path) { Uri.fromFile(File(photo.item.path)).toString() }
    val bitmap by rememberThumbnail(uri, "image/jpeg")
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(BlackChip)) {
        bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
    }
}

/** Faz 3B-2 photo types ("work hidden in your gallery"): the rows and "Show one by one". */
@Composable
internal fun PhotoTypesScreen(
    documents: List<ReviewPhoto>,
    memes: List<ReviewPhoto>,
    screenshots: Pair<Int, Long>?,
    onOpenScreenshots: (() -> Unit)?,
    onShow: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.types_title), color = Ink, fontSize = 38.sp, lineHeight = 42.sp, fontWeight = AppWeights.Giant, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.types_body), color = Ink, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = AppWeights.Strong, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
        BlackCard(contentPadding = PaddingValues(vertical = 4.dp)) {
            TypeRow(
                stringResource(R.string.types_documents),
                pluralStringResource(R.plurals.types_photo_count, documents.size, documents.size) +
                    if (documents.any(ReviewPhoto::important)) " · " + stringResource(R.string.types_important_note) else "",
                documents.sumOf { it.item.sizeBytes },
                documents.firstOrNull(),
                onClick = null,
            )
            if (screenshots != null && screenshots.first > 0) {
                CardDivider()
                TypeRow(
                    stringResource(R.string.types_screenshots),
                    pluralStringResource(R.plurals.types_photo_count, screenshots.first, screenshots.first),
                    screenshots.second,
                    null,
                    onClick = onOpenScreenshots,
                )
            }
            CardDivider()
            TypeRow(
                stringResource(R.string.types_memes),
                pluralStringResource(R.plurals.types_photo_count, memes.size, memes.size),
                memes.sumOf { it.item.sizeBytes },
                memes.firstOrNull(),
                onClick = null,
            )
        }
        Text(stringResource(R.string.types_note_ai), color = Ink, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 14.dp))
        Spacer(Modifier.height(20.dp))
        PillButton(
            text = stringResource(R.string.types_show),
            onClick = onShow,
            style = PillStyle.BLACK_LIME,
            enabled = documents.isNotEmpty() || memes.isNotEmpty(),
        )
    }
}

@Composable
private fun TypeRow(title: String, subtitle: String, bytes: Long, sample: ReviewPhoto?, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(BlackChip)) {
            if (sample != null) {
                val uri = remember(sample.item.path) { Uri.fromFile(File(sample.item.path)).toString() }
                val bitmap by rememberThumbnail(uri, "image/jpeg")
                bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = OnBlack, fontSize = 16.sp, fontWeight = AppWeights.Title)
            Text(subtitle, color = OnBlackSoft, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Text(ByteFormatter.format(bytes), color = Lime, fontSize = 15.sp, fontWeight = AppWeights.Giant)
    }
}

/** Entry cards on the similar-photo list (Faz 3B-2). */
@Composable
internal fun ReviewEntryCard(title: String, subtitle: String, bytes: Long, onClick: () -> Unit) {
    BlackCard(modifier = Modifier.clickable(role = Role.Button, onClick = onClick), contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = OnBlack, fontSize = 16.sp, fontWeight = AppWeights.Title)
                Text(subtitle, color = OnBlackSoft, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Text(ByteFormatter.format(bytes), color = Lime, fontSize = 15.sp, fontWeight = AppWeights.Giant)
        }
    }
}

/** Decisions left today for a free user (null with Pro). */
internal fun decisionsLeft(isPro: Boolean, usedToday: Int): Int? =
    if (isPro) null else (FreeTierRules.DAILY_DECISIONS - usedToday).coerceAtLeast(0)
