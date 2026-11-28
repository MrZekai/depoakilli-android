package com.mrzekai.depoakilli.ui.photo

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.photo.PhotoItem
import com.mrzekai.depoakilli.photo.SimilarFinder
import com.mrzekai.depoakilli.photo.SimilarGroup
import com.mrzekai.depoakilli.photo.SimilarRepository
import com.mrzekai.depoakilli.photo.SimilarRules
import com.mrzekai.depoakilli.photo.SimilarState
import com.mrzekai.depoakilli.ui.common.BlackCard
import com.mrzekai.depoakilli.ui.common.GiantNumber
import com.mrzekai.depoakilli.ui.common.LimeProgressBar
import com.mrzekai.depoakilli.ui.common.LinkButton
import com.mrzekai.depoakilli.ui.common.PageFrame
import com.mrzekai.depoakilli.ui.common.PillButton
import com.mrzekai.depoakilli.ui.common.PillStyle
import com.mrzekai.depoakilli.ui.common.rememberGiantScale
import com.mrzekai.depoakilli.ui.media.rememberThumbnail
import com.mrzekai.depoakilli.ui.pro.ProBadge
import com.mrzekai.depoakilli.ui.theme.AppWeights
import com.mrzekai.depoakilli.ui.theme.BlackChip
import com.mrzekai.depoakilli.ui.theme.Ink
import com.mrzekai.depoakilli.ui.theme.InkSoft
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

private const val MODE_BAD = "bad"
private const val MODE_TYPES = "types"
private const val MODE_TYPES_REVIEW = "types_review"

/** What the similar-photo screens need from the app. */
internal data class SimilarActions(
    val isPro: Boolean,
    /** Groups cleaned without Pro (lifetime). */
    val freeGroupsUsed: Int,
    val onOpenPaywall: () -> Unit,
    val onFreeGroupUsed: () -> Unit,
    /** Pro: moved bytes count into this month's figure (Y5). */
    val onProBytes: (Long) -> Unit,
    /** Faz 3B-2: one-by-one decisions left today (null with Pro) and counting one. */
    val decisionsLeft: Int? = null,
    val onDecision: () -> Unit = {},
    /** Faz 3B-2: the scan's old screenshots (count, bytes) and opening their review. */
    val screenshots: Pair<Int, Long>? = null,
    val onOpenScreenshots: (() -> Unit)? = null,
)

/**
 * Faz 3B-1: the group list and one group at a time. Nothing moves without
 * the user's tap; every frame goes to the app's Trash (7 days).
 */
@Composable
internal fun SimilarPhotosFlow(repo: SimilarRepository, actions: SimilarActions, modifier: Modifier = Modifier) {
    val state by repo.state.collectAsState()
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { if (state is SimilarState.Idle) repo.start() }
    val result = (state as? SimilarState.Ready)?.result
    val groups = result?.groups.orEmpty()
    val freeLeft = if (actions.isPro) Int.MAX_VALUE else (SimilarRules.FREE_GROUPS - actions.freeGroupsUsed).coerceAtLeast(0)

    fun openGroup(index: Int) {
        if (index >= freeLeft) actions.onOpenPaywall() else open = groups[index].key
    }

    var mode by rememberSaveable { mutableStateOf<String?>(null) }
    androidx.activity.compose.BackHandler(enabled = open != null || mode != null) {
        if (mode == MODE_TYPES_REVIEW) mode = MODE_TYPES else if (mode != null) mode = null else open = null
    }
    PageFrame(modifier = modifier) {
        val index = groups.indexOfFirst { it.key == open }
        val typed = result?.typed.orEmpty()
        if (mode == MODE_BAD && result != null) {
            PhotoReviewScreen(
                photos = result.badShots,
                repo = repo,
                actions = actions,
                onDone = { mode = null },
            )
        } else if (mode == MODE_TYPES && result != null) {
            PhotoTypesScreen(
                documents = typed.filter { it.type == com.mrzekai.depoakilli.photo.PhotoReviewRules.PhotoType.DOCUMENT },
                memes = typed.filter { it.type == com.mrzekai.depoakilli.photo.PhotoReviewRules.PhotoType.MEME },
                screenshots = actions.screenshots,
                onOpenScreenshots = actions.onOpenScreenshots,
                onShow = { mode = MODE_TYPES_REVIEW },
            )
        } else if (mode == MODE_TYPES_REVIEW && result != null) {
            PhotoReviewScreen(
                photos = typed.sortedBy { it.type },
                repo = repo,
                actions = actions,
                onDone = { mode = MODE_TYPES },
            )
        } else if (open != null && index >= 0) {
            GroupScreen(
                group = groups[index],
                position = index + 1,
                total = groups.size,
                repo = repo,
                actions = actions,
                onNext = { removed, usedFree ->
                    val list = (repo.state.value as? SimilarState.Ready)?.result?.groups.orEmpty()
                    val next = if (removed) index else index + 1
                    val used = actions.freeGroupsUsed + if (usedFree) 1 else 0
                    val stillFree = if (actions.isPro) Int.MAX_VALUE else (SimilarRules.FREE_GROUPS - used).coerceAtLeast(0)
                    open = list.getOrNull(next)?.key?.takeIf { next < stillFree }
                },
                eyesClosed = result?.eyesClosed.orEmpty(),
            )
        } else {
            ListScreen(state, result, groups, actions, freeLeft, repo, onOpen = ::openGroup, onMode = { mode = it })
        }
    }
}

@Composable
private fun ListScreen(
    state: SimilarState,
    result: SimilarFinder.Result?,
    groups: List<SimilarGroup>,
    actions: SimilarActions,
    freeLeft: Int,
    repo: SimilarRepository,
    onOpen: (Int) -> Unit,
    onMode: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmAll by remember { mutableStateOf(false) }
    val viewed = repo.store.groupsViewed
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (state is SimilarState.Looking || state is SimilarState.Idle) {
            item(key = "looking") {
                val looking = state as? SimilarState.Looking
                Column(Modifier.padding(vertical = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = Ink, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.similar_looking), color = Ink, fontSize = 16.sp, fontWeight = AppWeights.Strong)
                    }
                    if (looking != null && looking.total > 1) {
                        LimeProgressBar(
                            progress = looking.done.toFloat() / looking.total,
                            trackColor = InkSoft.copy(alpha = 0.25f),
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        )
                    }
                    Text(stringResource(R.string.similar_paused), color = InkSoft, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 10.dp))
                }
            }
            return@LazyColumn
        }
        result ?: return@LazyColumn
        if (result.modelMissing) {
            item(key = "model") { Text(stringResource(R.string.similar_model_missing), color = Ink, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = AppWeights.Strong) }
        }
        if (groups.isEmpty()) {
            item(key = "none") {
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text(stringResource(R.string.similar_none_title), color = Ink, fontSize = 28.sp, lineHeight = 33.sp, fontWeight = AppWeights.Giant, modifier = Modifier.semantics { heading() })
                    Text(pluralStringResource(R.plurals.similar_none_body, result.photosLooked, result.photosLooked), color = InkSoft, fontSize = 15.sp, lineHeight = 21.sp)
                }
            }
        } else {
            item(key = "head") {
                Column {
                    GiantNumber(ByteFormatter.format(result.trashBytes), rememberGiantScale(), modifier = Modifier.fillMaxWidth())
                    Text(
                        pluralStringResource(R.plurals.similar_head_body, result.trashCount, result.trashCount, groups.size),
                        color = Ink,
                        fontSize = 16.sp,
                        lineHeight = 21.sp,
                        fontWeight = AppWeights.Strong,
                    )
                }
            }
            itemsIndexed(groups, key = { _, g -> g.key }) { index, group ->
                GroupCard(group, locked = index >= freeLeft, onClick = { onOpen(index) })
            }
            item(key = "notes") {
                Text(stringResource(R.string.similar_note_ai), color = Ink, fontSize = 13.sp, lineHeight = 18.sp)
            }
            item(key = "buttons") {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(text = stringResource(R.string.similar_open_first), onClick = { onOpen(0) }, style = PillStyle.BLACK_LIME)
                    Box(contentAlignment = Alignment.CenterStart) {
                        PillButton(
                            text = stringResource(R.string.similar_approve_all),
                            onClick = { if (actions.isPro) confirmAll = true else actions.onOpenPaywall() },
                            style = PillStyle.INK_OUTLINE,
                            enabled = SimilarRules.approveAllEnabled(viewed),
                        )
                        ProBadge(modifier = Modifier.padding(start = 22.dp))
                    }
                    if (!SimilarRules.approveAllEnabled(viewed)) {
                        val left = SimilarRules.VIEW_BEFORE_APPROVE_ALL - viewed
                        Text(pluralStringResource(R.plurals.similar_approve_all_locked, left, left), color = InkSoft, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                    if (!actions.isPro) {
                        Text(
                            stringResource(R.string.similar_free_note, SimilarRules.FREE_GROUPS),
                            color = Ink,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        // Faz 3B-2: one-by-one reviews, behind their switches.
        if (com.mrzekai.depoakilli.pro.ProGainEstimator.BAD_SHOTS_AVAILABLE && result.badShots.isNotEmpty()) {
            item(key = "bad") {
                ReviewEntryCard(
                    stringResource(R.string.review_bad_title),
                    pluralStringResource(R.plurals.types_photo_count, result.badShots.size, result.badShots.size),
                    result.badShots.sumOf { it.item.sizeBytes },
                ) { onMode(MODE_BAD) }
            }
        }
        if (com.mrzekai.depoakilli.pro.ProGainEstimator.PHOTO_TYPES_AVAILABLE && result.typed.isNotEmpty()) {
            item(key = "types") {
                ReviewEntryCard(
                    stringResource(R.string.types_entry),
                    pluralStringResource(R.plurals.types_photo_count, result.typed.size, result.typed.size),
                    result.typed.sumOf { it.item.sizeBytes },
                ) { onMode(MODE_TYPES) }
            }
        }
        if (result.photosLeft > 0) {
            item(key = "more") {
                LinkButton(
                    pluralStringResource(R.plurals.similar_more, result.photosLeft, result.photosLeft),
                    onClick = { repo.start(all = true) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (confirmAll) {
        val count = groups.sumOf { it.others.size }
        val bytes = groups.sumOf(SimilarGroup::trashBytes)
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            text = { Text(stringResource(R.string.similar_trash_note)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmAll = false
                    scope.launch {
                        var moved = 0L
                        groups.forEach { g -> moved += repo.moveToTrash(g, g.others) }
                        actions.onProBytes(moved)
                        Toast.makeText(context, context.resources.getQuantityString(R.plurals.similar_moved, count, count, ByteFormatter.format(moved)), Toast.LENGTH_SHORT).show()
                    }
                }) { Text(pluralStringResource(R.plurals.similar_move_button, count, count, ByteFormatter.format(bytes))) }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

@Composable
private fun GroupCard(group: SimilarGroup, locked: Boolean, onClick: () -> Unit) {
    BlackCard(modifier = Modifier.clickable(role = Role.Button, onClick = onClick), contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pluralStringResource(R.plurals.similar_group_title, group.members.size, dateLabel(group.takenMillis), group.members.size),
                        color = OnBlack,
                        fontSize = 16.sp,
                        fontWeight = AppWeights.Title,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (locked) {
                        Spacer(Modifier.width(8.dp))
                        ProBadge()
                    }
                }
                Text(groupSubtitle(group, locked), color = OnBlackSoft, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2)
            }
            Text(ByteFormatter.format(group.trashBytes), color = Lime, fontSize = 15.sp, fontWeight = AppWeights.Giant)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PhotoTile(group.keeper, Modifier.size(52.dp), keeper = true, small = true)
            group.others.take(3).forEach { PhotoTile(it, Modifier.size(52.dp), checked = true, small = true) }
            if (group.others.size > 3) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(BlackChip), contentAlignment = Alignment.Center) {
                    Text("+${group.others.size - 3}", color = OnBlack, fontSize = 14.sp, fontWeight = AppWeights.Title)
                }
            }
        }
    }
}

@Composable
private fun groupSubtitle(group: SimilarGroup, locked: Boolean): String {
    val folder = folderLabel(group.keeper.folder)
    if (locked) return folder + " · " + stringResource(R.string.similar_sub_pro, SimilarRules.FREE_GROUPS + 1)
    val seconds = (group.spanMillis / 1000L).toInt()
    val span = when {
        seconds <= 90 -> stringResource(R.string.similar_within_seconds, seconds.coerceAtLeast(1))
        seconds <= 60 * 60 -> stringResource(R.string.similar_within_minutes, (seconds + 59) / 60)
        else -> stringResource(R.string.similar_sub_alike)
    }
    return "$folder · $span"
}

private fun folderLabel(folder: String): String {
    val last = folder.trim('/').substringAfterLast('/')
    return if (last.isBlank()) folder else last
}

private fun dateLabel(millis: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

@Composable
private fun PhotoTile(
    item: PhotoItem,
    modifier: Modifier,
    keeper: Boolean = false,
    checked: Boolean = false,
    small: Boolean = false,
    label: String? = null,
) {
    val uri = remember(item.path) { Uri.fromFile(File(item.path)).toString() }
    val bitmap by rememberThumbnail(uri, "image/jpeg")
    Box(
        modifier
            .clip(RoundedCornerShape(if (small) 10.dp else 14.dp))
            .background(BlackChip)
            .then(if (keeper) Modifier.border(2.dp, Lime, RoundedCornerShape(if (small) 10.dp else 14.dp)) else Modifier),
    ) {
        bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        if (keeper) {
            Text(
                stringResource(R.string.similar_keep_badge),
                color = Ink,
                fontSize = 12.sp,
                fontWeight = AppWeights.Giant,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .background(Lime, RoundedCornerShape(5.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        if (checked) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(if (small) 18.dp else 22.dp).background(Lime, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = Ink, modifier = Modifier.size(if (small) 13.dp else 16.dp))
            }
        }
        if (label != null) {
            Text(
                label,
                color = OnBlack,
                fontSize = 12.sp,
                fontWeight = AppWeights.Title,
                maxLines = 1,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).background(BlackChip, RoundedCornerShape(5.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun GroupScreen(
    group: SimilarGroup,
    position: Int,
    total: Int,
    repo: SimilarRepository,
    actions: SimilarActions,
    onNext: (removed: Boolean, usedFree: Boolean) -> Unit,
    eyesClosed: Set<Long> = emptySet(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Frames the user chose to keep too (unchecked).
    val keep = remember(group.key) { mutableStateListOf<Long>() }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(group.key) { repo.markViewed() }
    val chosen = group.others.filter { it.id !in keep }
    val bytes = chosen.sumOf(PhotoItem::sizeBytes)
    BlackCard(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.similar_detail_position, position, total, dateLabel(group.takenMillis)).uppercase(),
                    color = Lime,
                    fontSize = 13.sp,
                    fontWeight = AppWeights.Giant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                LinkButton(
                    stringResource(R.string.similar_not_similar),
                    onClick = {
                        repo.dismiss(group)
                        onNext(true, false)
                    },
                    color = OnBlackSoft,
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth()) {
                PhotoTile(group.keeper, Modifier.fillMaxWidth().aspectRatio(1f), keeper = true)
                Text(
                    listOfNotNull(
                        stringResource(R.string.similar_sharpest),
                        stringResource(R.string.similar_eyes_open).takeIf { group.keeper.id !in eyesClosed && group.members.any { it.id in eyesClosed } },
                        ByteFormatter.format(group.keeper.sizeBytes),
                    ).joinToString(" · "),
                    color = OnBlack,
                    fontSize = 13.sp,
                    fontWeight = AppWeights.Title,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .background(BlackChip, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                pluralStringResource(R.plurals.similar_to_trash_label, chosen.size, chosen.size),
                color = OnBlackSoft,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = AppWeights.Strong,
            )
            Spacer(Modifier.height(10.dp))
            val tiles = group.alsoKept.map { it to true } + group.others.map { it to false }
            tiles.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    row.forEach { (item, edited) ->
                        val checked = !edited && item.id !in keep
                        val desc = if (checked) stringResource(R.string.similar_keep_badge) else ""
                        PhotoTile(
                            item,
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clickable(enabled = !edited, role = Role.Checkbox) {
                                    if (item.id in keep) keep.remove(item.id) else keep.add(item.id)
                                }
                                .semantics { stateDescription = desc },
                            checked = checked,
                            small = true,
                            label = when {
                                edited || !checked -> stringResource(R.string.similar_also_kept)
                                item.id in eyesClosed -> stringResource(R.string.similar_eyes_closed)
                                else -> null
                            },
                        )
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            PillButton(
                text = pluralStringResource(R.plurals.similar_move_button, chosen.size, chosen.size, ByteFormatter.format(bytes)),
                onClick = {
                    if (!SimilarRules.canClean(actions.isPro, actions.freeGroupsUsed)) {
                        actions.onOpenPaywall()
                        return@PillButton
                    }
                    busy = true
                    scope.launch {
                        val moved = repo.moveToTrash(group, chosen)
                        busy = false
                        if (moved > 0L) {
                            if (actions.isPro) actions.onProBytes(moved) else actions.onFreeGroupUsed()
                            Toast.makeText(
                                context,
                                context.resources.getQuantityString(R.plurals.similar_moved, chosen.size, chosen.size, ByteFormatter.format(moved)),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        onNext(moved > 0L, moved > 0L && !actions.isPro)
                    }
                },
                style = PillStyle.LIME,
                enabled = chosen.isNotEmpty() && !busy,
                busy = busy,
            )
            LinkButton(stringResource(R.string.similar_skip), onClick = { onNext(false, false) }, color = OnBlack, modifier = Modifier.fillMaxWidth())
            Text(
                stringResource(R.string.similar_trash_note),
                color = OnBlackSoft,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}
