package com.mrzekai.depoakilli.ui.video

import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.ui.common.BlackCard
import com.mrzekai.depoakilli.ui.common.CardDivider
import com.mrzekai.depoakilli.ui.common.GiantNumber
import com.mrzekai.depoakilli.ui.common.LimeProgressBar
import com.mrzekai.depoakilli.ui.common.LinkButton
import com.mrzekai.depoakilli.ui.common.PageFrame
import com.mrzekai.depoakilli.ui.common.PermanentDeleteSheet
import com.mrzekai.depoakilli.ui.common.PillButton
import com.mrzekai.depoakilli.ui.common.PillStyle
import com.mrzekai.depoakilli.ui.common.rememberGiantScale
import com.mrzekai.depoakilli.ui.media.rememberThumbnail
import com.mrzekai.depoakilli.ui.pro.ProBadge
import com.mrzekai.depoakilli.ui.theme.AppWeights
import com.mrzekai.depoakilli.ui.theme.Black
import com.mrzekai.depoakilli.ui.theme.BlackChip
import com.mrzekai.depoakilli.ui.theme.CardLine
import com.mrzekai.depoakilli.ui.theme.Ink
import com.mrzekai.depoakilli.ui.theme.InkSoft
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft
import com.mrzekai.depoakilli.video.Candidate
import com.mrzekai.depoakilli.video.DeviceBlock
import com.mrzekai.depoakilli.video.PendingOutput
import com.mrzekai.depoakilli.video.PendingRules
import com.mrzekai.depoakilli.video.ShrinkJobs
import com.mrzekai.depoakilli.video.ShrinkOutcome
import com.mrzekai.depoakilli.video.VideoCompressionService
import com.mrzekai.depoakilli.video.VideoFinderState
import com.mrzekai.depoakilli.video.VideoQueue
import com.mrzekai.depoakilli.video.VideoRules
import com.mrzekai.depoakilli.video.VideoShrinkRepository
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

/** What the shrinking flow needs from the app. */
internal data class VideoShrinkActions(
    val isPro: Boolean,
    val videoTrialUsed: Boolean,
    val freeBytes: () -> Long,
    val onOpenPaywall: () -> Unit,
    val onTrialUsed: () -> Unit,
    /** Pro: shrunk bytes count into this month's figure (Y5). */
    val onProBytes: (Long) -> Unit,
    val onDeleteOriginalsNow: (List<String>) -> Unit,
    val onDone: () -> Unit,
)

/**
 * Z5–Z8 in one place: the list (Z5), the running round (Z6), watching and
 * approving each copy (Z7) and the result (Z8). Which one shows follows the
 * round's state, so leaving and coming back lands on the right screen.
 */
@Composable
internal fun VideoShrinkFlow(repo: VideoShrinkRepository, actions: VideoShrinkActions, modifier: Modifier = Modifier) {
    val finder by repo.state.collectAsState()
    val job by ShrinkJobs.state.collectAsState()
    val pending by repo.pending.state.collectAsState()
    var reviewing by rememberSaveable { mutableStateOf(false) }
    var showResult by rememberSaveable { mutableStateOf(false) }
    val approvedIds = remember { mutableStateListOf<String>() }
    var approvedBytes by rememberSaveable { mutableStateOf(0L) }
    var approvedCount by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { repo.refresh() }
    // A finished round goes straight to watching and approving.
    LaunchedEffect(job.running, job.results.size) {
        if (!job.running && job.results.isNotEmpty()) {
            if (!actions.isPro && job.results.any { it.outcome == ShrinkOutcome.SHRUNK }) actions.onTrialUsed()
            if (pending.isNotEmpty()) reviewing = true else showResult = true
        }
    }

    // The last copy was approved or dropped: show what was gained (or the list
    // again when every original was kept), with fresh figures.
    LaunchedEffect(reviewing, pending.isEmpty()) {
        if (reviewing && pending.isEmpty()) {
            // Let the approval that emptied the list finish counting first.
            kotlinx.coroutines.delay(300)
            reviewing = false
            showResult = approvedCount > 0
            repo.refresh()
        }
    }

    PageFrame(modifier = modifier) {
        when {
            job.running -> ShrinkProgressScreen()
            reviewing && pending.isNotEmpty() -> CompareScreen(
                pending = pending,
                onApprove = { item, onDone ->
                    approvedBytes += item.savedBytes
                    approvedCount++
                    if (actions.isPro) actions.onProBytes(item.savedBytes)
                    onDone()
                },
                approve = { item -> repo.approve(item)?.also { approvedIds += it } },
                keep = { item -> repo.keepOriginal(item) },
                onClose = {
                    reviewing = false
                    showResult = approvedCount > 0
                },
            )
            showResult -> ShrinkResultScreen(
                approvedBytes = approvedBytes,
                approvedCount = approvedCount,
                skipped = job.results.filter { it.outcome != ShrinkOutcome.SHRUNK }.map { it.outcome },
                estimate = job.results.filter { it.outcome == ShrinkOutcome.SHRUNK }.sumOf { it.candidate.estimatedSavingBytes },
                onDeleteNow = {
                    actions.onDeleteOriginalsNow(approvedIds.toList())
                    approvedIds.clear()
                },
                canDeleteNow = approvedIds.isNotEmpty(),
                onDone = {
                    showResult = false
                    approvedBytes = 0L
                    approvedCount = 0
                    ShrinkJobs.reset()
                    repo.refresh()
                    actions.onDone()
                },
            )
            else -> CandidatesScreen(
                finder = finder,
                pending = pending,
                actions = actions,
                lastBlock = job.blocked,
                onReviewPending = { reviewing = true },
            )
        }
    }
}

// ------------------------------------------------------------------ Z5

@Composable
private fun CandidatesScreen(
    finder: VideoFinderState,
    pending: List<PendingOutput>,
    actions: VideoShrinkActions,
    lastBlock: DeviceBlock?,
    onReviewPending: () -> Unit,
) {
    val context = LocalContext.current
    val result = (finder as? VideoFinderState.Ready)?.result
    val candidates = result?.candidates.orEmpty()
    val selected = remember(candidates) { mutableStateListOf<String>().apply { addAll(candidates.map { it.meta.path }) } }
    var notice by remember { mutableStateOf<String?>(null) }
    val chosen = candidates.filter { it.meta.path in selected }
    val noRoomText = stringResource(R.string.video_no_room, ByteFormatter.format(VideoQueue.RESERVE_BYTES))
    val lowBatteryText = stringResource(R.string.video_low_battery)
    val hotText = stringResource(R.string.video_too_hot)

    fun start(list: List<Candidate>) {
        val plan = VideoQueue.plan(list, actions.freeBytes())
        if (plan.noRoom) {
            notice = noRoomText
            return
        }
        val battery = context.getSystemService(android.os.BatteryManager::class.java)
        val thermal = context.getSystemService(android.os.PowerManager::class.java)?.currentThermalStatus ?: 0
        when (
            VideoQueue.block(
                battery?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100,
                battery?.isCharging == true,
                thermal >= android.os.PowerManager.THERMAL_STATUS_SEVERE,
            )
        ) {
            DeviceBlock.LOW_BATTERY -> notice = lowBatteryText
            DeviceBlock.TOO_HOT -> notice = hotText
            null -> VideoCompressionService.startFromUserTap(context, plan.now, plan.nextRound)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (pending.isNotEmpty()) {
            item(key = "pending") {
                BlackCard(contentPadding = PaddingValues(18.dp)) {
                    Text(
                        pluralStringResource(R.plurals.video_pending_title, pending.size, pending.size),
                        color = OnBlack,
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        fontWeight = AppWeights.Title,
                    )
                    Text(stringResource(R.string.video_pending_note), color = OnBlackSoft, fontSize = 13.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(10.dp))
                    PillButton(text = stringResource(R.string.video_pending_review), onClick = onReviewPending, style = PillStyle.LIME)
                }
            }
        }
        when {
            finder is VideoFinderState.Looking || finder is VideoFinderState.Idle -> item(key = "looking") {
                Row(Modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Ink, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.video_looking), color = Ink, fontSize = 16.sp, fontWeight = AppWeights.Strong)
                }
            }
            candidates.isEmpty() -> item(key = "none") {
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text(
                        stringResource(R.string.video_none_title),
                        color = Ink,
                        fontSize = 28.sp,
                        lineHeight = 33.sp,
                        fontWeight = AppWeights.Giant,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(stringResource(R.string.video_none_body, ByteFormatter.format(VideoRules.MIN_VIDEO_BYTES)), color = InkSoft, fontSize = 15.sp, lineHeight = 21.sp)
                }
            }
            else -> {
                item(key = "head") {
                    val saving = chosen.sumOf(Candidate::estimatedSavingBytes)
                    Column {
                        Text(stringResource(R.string.video_head_label), color = Ink, fontSize = 17.sp, fontWeight = AppWeights.Title)
                        GiantNumber(ByteFormatter.format(saving), rememberGiantScale(), modifier = Modifier.fillMaxWidth())
                        Text(
                            pluralStringResource(R.plurals.video_head_body, chosen.size, chosen.size),
                            color = Ink,
                            fontSize = 16.sp,
                            lineHeight = 21.sp,
                            fontWeight = AppWeights.Strong,
                        )
                    }
                }
                item(key = "list") {
                    BlackCard {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                pluralStringResource(
                                    R.plurals.video_list_label,
                                    candidates.size,
                                    candidates.size,
                                    ByteFormatter.format(candidates.sumOf { it.meta.sizeBytes }),
                                ),
                                color = Lime,
                                fontSize = 13.sp,
                                fontWeight = AppWeights.Giant,
                                modifier = Modifier.weight(1f),
                            )
                            val all = selected.size == candidates.size
                            LinkButton(
                                stringResource(if (all) R.string.video_select_none else R.string.video_select_all),
                                onClick = {
                                    selected.clear()
                                    if (!all) selected.addAll(candidates.map { it.meta.path })
                                },
                                color = OnBlackSoft,
                            )
                        }
                        candidates.forEachIndexed { index, candidate ->
                            if (index > 0) CardDivider()
                            CandidateRow(
                                candidate = candidate,
                                checked = candidate.meta.path in selected,
                                onChecked = { on ->
                                    if (on) selected.add(candidate.meta.path) else selected.remove(candidate.meta.path)
                                },
                            )
                        }
                    }
                }
                item(key = "notes") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.video_note_originals), color = Ink, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = AppWeights.Strong)
                        Text(stringResource(R.string.video_note_backup), color = InkSoft, fontSize = 13.sp, lineHeight = 18.sp)
                        val message = notice ?: when (lastBlock) {
                            DeviceBlock.LOW_BATTERY -> lowBatteryText
                            DeviceBlock.TOO_HOT -> hotText
                            null -> null
                        }
                        if (message != null) {
                            Text(message, color = Ink, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = AppWeights.Title)
                        }
                    }
                }
                item(key = "buttons") {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (actions.isPro) {
                            PillButton(
                                text = pluralStringResource(R.plurals.video_shrink_button, chosen.size, chosen.size),
                                onClick = { start(chosen) },
                                style = PillStyle.BLACK_LIME,
                                enabled = chosen.isNotEmpty(),
                            )
                        } else {
                            Box(contentAlignment = Alignment.CenterStart) {
                                PillButton(
                                    text = pluralStringResource(R.plurals.video_shrink_button, chosen.size, chosen.size),
                                    onClick = actions.onOpenPaywall,
                                    style = PillStyle.BLACK_LIME,
                                    enabled = chosen.isNotEmpty(),
                                )
                                ProBadge(modifier = Modifier.padding(start = 22.dp))
                            }
                            if (!actions.videoTrialUsed) {
                                LinkButton(
                                    stringResource(R.string.video_try_one),
                                    onClick = { chosen.firstOrNull()?.let { start(listOf(it)) } },
                                    enabled = chosen.isNotEmpty(),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(candidate: Candidate, checked: Boolean, onChecked: (Boolean) -> Unit) {
    val meta = candidate.meta
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChecked)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VideoThumb(meta.path, Modifier.size(width = 64.dp, height = 56.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(videoTitle(meta.modifiedMillis, meta.relativeFolder), color = OnBlack, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = AppWeights.Title, maxLines = 1)
            Text(
                resolutionLabel(meta.shortSide) + " · " + durationLabel(meta.durationMs),
                color = OnBlackSoft,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ByteFormatter.format(meta.sizeBytes) + " → ", color = OnBlack, fontSize = 13.sp, fontWeight = AppWeights.Title, maxLines = 1)
                Text(
                    stringResource(R.string.video_about, ByteFormatter.format(candidate.estimatedOutputBytes)),
                    color = Lime,
                    fontSize = 13.sp,
                    fontWeight = AppWeights.Title,
                    maxLines = 1,
                )
            }
        }
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = Lime, checkmarkColor = Black, uncheckedColor = OnBlackSoft),
        )
    }
}

@Composable
private fun VideoThumb(path: String, modifier: Modifier) {
    val uri = remember(path) { Uri.fromFile(File(path)).toString() }
    val bitmap by rememberThumbnail(uri, "video/mp4")
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(BlackChip), contentAlignment = Alignment.Center) {
        bitmap?.let {
            androidx.compose.foundation.Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = OnBlack, modifier = Modifier.size(22.dp))
    }
}

// ------------------------------------------------------------------ Z6

@Composable
private fun ShrinkProgressScreen() {
    val context = LocalContext.current
    val job by ShrinkJobs.state.collectAsState()
    val current = job.current
    BlackCard(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.video_progress_label), color = Lime, fontSize = 13.sp, fontWeight = AppWeights.Giant)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${job.index + 1}", color = OnBlack, fontSize = 88.sp, lineHeight = 92.sp, fontWeight = AppWeights.Giant)
                Text(" / ${job.total}", color = OnBlackSoft, fontSize = 88.sp, lineHeight = 92.sp, fontWeight = AppWeights.Giant)
            }
            LimeProgressBar(progress = job.overallProgress, trackColor = CardLine, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp))
            Text(
                stringResource(R.string.video_progress_so_far, ByteFormatter.format(job.savedSoFar)),
                color = OnBlack,
                fontSize = 18.sp,
                fontWeight = AppWeights.Title,
            )
            job.remainingMs?.let { remaining ->
                Text(
                    stringResource(R.string.video_progress_remaining, durationLabel(remaining)),
                    color = OnBlackSoft,
                    fontSize = 14.sp,
                )
            }
            if (current != null) {
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(BlackChip).padding(16.dp),
                ) {
                    Text(
                        videoTitle(current.meta.modifiedMillis, current.meta.relativeFolder),
                        color = OnBlack,
                        fontSize = 16.sp,
                        fontWeight = AppWeights.Title,
                        maxLines = 1,
                    )
                    Text(
                        resolutionLabel(current.meta.shortSide) + " · " + durationLabel(current.meta.durationMs) + " · " +
                            java.text.NumberFormat.getPercentInstance(LocalConfiguration.current.locales[0]).format(job.progress / 100.0),
                        color = OnBlackSoft,
                        fontSize = 13.sp,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.video_progress_note), color = OnBlackSoft, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.weight(1f).height(24.dp))
            PillButton(text = stringResource(R.string.video_stop), onClick = { VideoCompressionService.stop(context) }, style = PillStyle.LIME_OUTLINE)
            Text(
                stringResource(R.string.video_stop_note),
                color = OnBlackSoft,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

// ------------------------------------------------------------------ Z7

@Composable
private fun CompareScreen(
    pending: List<PendingOutput>,
    onApprove: (PendingOutput, () -> Unit) -> Unit,
    approve: suspend (PendingOutput) -> String?,
    keep: suspend (PendingOutput) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val item = pending.first()
    var playedSmall by remember(item.outputPath) { mutableStateOf(false) }
    var approvedByHand by rememberSaveable { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember(item.outputPath) { mutableStateOf(false) }
    val original = File(item.originalPath)

    BlackCard(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.video_compare_label, pending.size, videoTitle(original.lastModified(), "")),
                    color = Lime,
                    fontSize = 13.sp,
                    fontWeight = AppWeights.Giant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                LinkButton(stringResource(R.string.video_compare_later), onClick = onClose, color = OnBlackSoft)
            }
            Text(
                stringResource(R.string.video_compare_title),
                color = OnBlack,
                fontSize = 28.sp,
                lineHeight = 33.sp,
                fontWeight = AppWeights.Giant,
                modifier = Modifier.padding(top = 6.dp, bottom = 14.dp).semantics { heading() },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompareSide(
                    label = stringResource(R.string.video_original),
                    bytes = item.originalBytes,
                    path = item.originalPath,
                    accent = OnBlack,
                    modifier = Modifier.weight(1f),
                    onPlayed = {},
                )
                CompareSide(
                    label = stringResource(R.string.video_smaller),
                    bytes = item.outputBytes,
                    path = item.outputPath,
                    accent = Lime,
                    modifier = Modifier.weight(1f),
                    onPlayed = { playedSmall = true },
                )
            }
            Spacer(Modifier.height(12.dp))
            FactRow(stringResource(R.string.video_fact_resolution), stringResource(R.string.video_fact_same))
            FactRow(stringResource(R.string.video_fact_gain), ByteFormatter.format(item.savedBytes))
            if (!playedSmall) {
                Text(
                    stringResource(R.string.video_compare_play_first),
                    color = OnBlackSoft,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (failed) {
                Text(stringResource(R.string.video_compare_failed), color = OnBlack, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(18.dp))
            PillButton(
                text = stringResource(R.string.video_approve),
                onClick = {
                    busy = true
                    scope.launch {
                        val id = approve(item)
                        busy = false
                        if (id != null) {
                            approvedByHand++
                            onApprove(item) {}
                        } else {
                            failed = true
                        }
                    }
                },
                style = PillStyle.LIME,
                enabled = PendingRules.canApprove(playedSmall) && !busy,
                busy = busy,
            )
            val rest = pending.size - 1
            if (rest > 0) {
                Spacer(Modifier.height(10.dp))
                PillButton(
                    text = pluralStringResource(R.plurals.video_apply_rest, rest, rest),
                    onClick = {
                        busy = true
                        scope.launch {
                            pending.drop(1).forEach { next ->
                                if (approve(next) != null) onApprove(next) {}
                            }
                            busy = false
                        }
                    },
                    style = PillStyle.INK_OUTLINE,
                    enabled = PendingRules.canApplyToRest(approvedByHand, rest) && !busy,
                )
            }
            LinkButton(
                stringResource(R.string.video_keep_original),
                onClick = {
                    busy = true
                    scope.launch {
                        keep(item)
                        busy = false
                    }
                },
                enabled = !busy,
                color = OnBlack,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.video_compare_note),
                color = OnBlackSoft,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CompareSide(label: String, bytes: Long, path: String, accent: Color, modifier: Modifier, onPlayed: () -> Unit) {
    var playing by remember(path) { mutableStateOf(false) }
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(16.dp)).background(BlackChip),
            contentAlignment = Alignment.Center,
        ) {
            if (playing) {
                InlinePlayer(path, onPlayed)
            } else {
                VideoThumb(path, Modifier.fillMaxSize())
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Black)
                        .clickable(role = Role.Button) { playing = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(R.string.video_play, label), tint = OnBlack)
                }
            }
        }
        Text(label, color = OnBlackSoft, fontSize = 12.sp, fontWeight = AppWeights.Title, modifier = Modifier.padding(top = 8.dp))
        Text(ByteFormatter.format(bytes), color = accent, fontSize = 24.sp, fontWeight = AppWeights.Giant)
    }
}

@Composable
private fun InlinePlayer(path: String, onPlayed: () -> Unit) {
    val context = LocalContext.current
    val player = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) onPlayed()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player } },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun FactRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = OnBlackSoft, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = OnBlack, fontSize = 14.sp, fontWeight = AppWeights.Title)
    }
}

// ------------------------------------------------------------------ Z8

@Composable
private fun ShrinkResultScreen(
    approvedBytes: Long,
    approvedCount: Int,
    skipped: List<ShrinkOutcome>,
    estimate: Long,
    canDeleteNow: Boolean,
    onDeleteNow: () -> Unit,
    onDone: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    if (confirm) {
        PermanentDeleteSheet(
            bytes = approvedBytes,
            body = stringResource(R.string.video_delete_now_confirm, ByteFormatter.format(approvedBytes)),
            onDismiss = { confirm = false },
            onDeletePermanently = {
                confirm = false
                onDeleteNow()
            },
        )
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GiantNumber(ByteFormatter.format(approvedBytes), rememberGiantScale(), modifier = Modifier.fillMaxWidth())
        Text(
            stringResource(R.string.video_result_title),
            color = Ink,
            fontSize = 20.sp,
            lineHeight = 25.sp,
            fontWeight = AppWeights.Giant,
            modifier = Modifier.semantics { heading() },
        )
        BlackCard(contentPadding = PaddingValues(18.dp)) {
            Text(stringResource(R.string.video_result_what), color = Lime, fontSize = 13.sp, fontWeight = AppWeights.Giant)
            FactRow(pluralStringResource(R.plurals.video_result_shrunk, approvedCount, approvedCount), ByteFormatter.format(approvedBytes))
            val low = skipped.count { it == ShrinkOutcome.TOO_LITTLE_GAIN }
            val failed = skipped.count { it == ShrinkOutcome.COULD_NOT }
            if (low > 0) FactRow(pluralStringResource(R.plurals.video_result_skipped, low, low), stringResource(R.string.video_skip_low_gain))
            if (failed > 0) FactRow(pluralStringResource(R.plurals.video_result_skipped, failed, failed), stringResource(R.string.video_skip_failed))
            if (estimate > 0L) FactRow(stringResource(R.string.video_result_estimate), stringResource(R.string.video_about, ByteFormatter.format(estimate)))
        }
        if (approvedCount > 0) {
            BlackCard(contentPadding = PaddingValues(18.dp)) {
                Text(stringResource(R.string.video_result_trash_note), color = OnBlack, fontSize = 15.sp, lineHeight = 21.sp)
                if (canDeleteNow) {
                    Spacer(Modifier.height(12.dp))
                    PillButton(text = stringResource(R.string.video_delete_now), onClick = { confirm = true }, style = PillStyle.CORAL_OUTLINE)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        PillButton(text = stringResource(R.string.video_done), onClick = onDone, style = PillStyle.BLACK_LIME)
    }
}

// ------------------------------------------------------------------ labels

@Composable
private fun videoTitle(modifiedMillis: Long, folder: String): String {
    val locale = LocalConfiguration.current.locales[0]
    val date = DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(modifiedMillis))
    val place = folder.trim('/').substringAfterLast('/')
    return if (place.isBlank()) date else "$place · $date"
}

private fun resolutionLabel(shortSide: Int): String = when {
    shortSide >= 2160 -> "4K"
    shortSide >= 1440 -> "1440p"
    shortSide >= 1080 -> "1080p"
    shortSide >= 720 -> "720p"
    else -> "${shortSide}p"
}

@Composable
private fun durationLabel(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> stringResource(R.string.video_duration_hm, hours.toInt(), minutes.toInt())
        minutes > 0 -> stringResource(R.string.video_duration_ms, minutes.toInt(), seconds.toInt())
        else -> stringResource(R.string.video_duration_s, seconds.toInt())
    }
}
