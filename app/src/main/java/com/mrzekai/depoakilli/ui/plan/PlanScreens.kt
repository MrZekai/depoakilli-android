package com.mrzekai.depoakilli.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
import com.mrzekai.depoakilli.plan.FileFilter
import com.mrzekai.depoakilli.plan.FilterDate
import com.mrzekai.depoakilli.plan.FilterSize
import com.mrzekai.depoakilli.plan.FilterSource
import com.mrzekai.depoakilli.plan.FilterType
import com.mrzekai.depoakilli.plan.Plan
import com.mrzekai.depoakilli.plan.PlanBuilder
import com.mrzekai.depoakilli.plan.PlanCandidate
import com.mrzekai.depoakilli.plan.PlanTarget
import com.mrzekai.depoakilli.plan.PreferenceRules
import com.mrzekai.depoakilli.plan.PreferenceSnapshot
import com.mrzekai.depoakilli.plan.SavedPlan
import com.mrzekai.depoakilli.plan.StepReason
import com.mrzekai.depoakilli.ui.common.BlackCard
import com.mrzekai.depoakilli.ui.common.CardDivider
import com.mrzekai.depoakilli.ui.common.LimeProgressBar
import com.mrzekai.depoakilli.ui.common.LinkButton
import com.mrzekai.depoakilli.ui.common.PageFrame
import com.mrzekai.depoakilli.ui.common.PillButton
import com.mrzekai.depoakilli.ui.common.PillStyle
import com.mrzekai.depoakilli.ui.pro.ProBadge
import com.mrzekai.depoakilli.ui.theme.AppWeights
import com.mrzekai.depoakilli.ui.theme.Black
import com.mrzekai.depoakilli.ui.theme.BlackChip
import com.mrzekai.depoakilli.ui.theme.CardLine
import com.mrzekai.depoakilli.ui.theme.Ink
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft

/** What the Personal Plan screens need from the app (all measured, all on this phone). */
internal data class PlanInputs(
    val candidates: List<PlanCandidate>,
    val totalBytes: Long,
    val availableBytes: Long,
    val isPro: Boolean,
    val learner: PreferenceSnapshot,
    val saved: SavedPlan?,
    /** Title of a step (an ASK step shows its cleanup group's title). */
    val titleOf: @Composable (PlanCandidate) -> String,
)

internal data class PlanActions(
    val onStart: (SavedPlan) -> Unit,
    val onClear: () -> Unit,
    /** Opens the step's own screen (with its own approval). */
    val onOpenStep: (PlanCandidate) -> Unit,
    val onOpenFilters: () -> Unit,
    val onOpenLearned: () -> Unit,
)

/** Faz 3C: goal -> plan. The plan never moves a file itself. */
@Composable
internal fun PersonalPlanFlow(inputs: PlanInputs, actions: PlanActions, modifier: Modifier = Modifier) {
    var editing by rememberSaveable { mutableStateOf(inputs.saved == null) }
    PageFrame(modifier = modifier) {
        val saved = inputs.saved
        if (editing || saved == null) {
            TargetScreen(inputs) { target ->
                val bytes = target.bytesNeeded(inputs.totalBytes, inputs.availableBytes)
                val plan = PlanBuilder.build(inputs.candidates, bytes, inputs.learner)
                actions.onStart(SavedPlan(bytes, inputs.availableBytes, plan.steps.map { it.candidate.key }, System.currentTimeMillis()))
                editing = false
            }
        } else {
            PlanScreen(inputs, saved, actions, onChangeGoal = { editing = true })
        }
    }
}

@Composable
private fun percentText(fraction: Double): String =
    java.text.NumberFormat.getPercentInstance(LocalConfiguration.current.locales[0]).format(fraction)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetScreen(inputs: PlanInputs, onBuild: (PlanTarget) -> Unit) {
    var target by rememberSaveable { mutableStateOf(-1) } // index: 0..2 GB presets, 3 = below 80 %
    val choice: PlanTarget? = when (target) {
        in PlanBuilder.PRESET_GB.indices -> PlanTarget.Bytes(PlanBuilder.PRESET_GB[target] * PlanBuilder.GB)
        3 -> PlanTarget.BelowPercent(PlanBuilder.PRESET_PERCENT)
        else -> null
    }
    val needed = choice?.bytesNeeded(inputs.totalBytes, inputs.availableBytes)
    val preview = needed?.let { PlanBuilder.build(inputs.candidates, it, inputs.learner) }
    val used = (inputs.totalBytes - inputs.availableBytes).coerceAtLeast(0L)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.plan_question), color = Ink, fontSize = 40.sp, lineHeight = 44.sp, fontWeight = AppWeights.Giant, modifier = Modifier.semantics { heading() })
        Text(
            stringResource(
                R.string.plan_now,
                percentText(if (inputs.totalBytes > 0) used.toDouble() / inputs.totalBytes else 0.0),
                ByteFormatter.format(inputs.availableBytes),
            ),
            color = Ink,
            fontSize = 16.sp,
            lineHeight = 21.sp,
            fontWeight = AppWeights.Strong,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PlanBuilder.PRESET_GB.forEachIndexed { i, gb ->
                Chip(ByteFormatter.format(gb * PlanBuilder.GB), selected = target == i, modifier = Modifier.weight(1f)) { target = i }
            }
        }
        Spacer(Modifier.height(10.dp))
        Chip(stringResource(R.string.plan_below, percentText(PlanBuilder.PRESET_PERCENT / 100.0)), selected = target == 3, modifier = Modifier.fillMaxWidth()) { target = 3 }
        Spacer(Modifier.height(16.dp))
        if (preview != null) {
            BlackCard(contentPadding = PaddingValues(18.dp)) {
                Text(stringResource(R.string.plan_found_label), color = Lime, fontSize = 13.sp, fontWeight = AppWeights.Giant)
                Text(
                    if (preview.reachesTarget) {
                        stringResource(R.string.plan_enough, ByteFormatter.format(preview.targetBytes))
                    } else {
                        stringResource(R.string.plan_not_enough, ByteFormatter.format(preview.reachableBytes))
                    },
                    color = OnBlack,
                    fontSize = 26.sp,
                    lineHeight = 30.sp,
                    fontWeight = AppWeights.Giant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    stringResource(R.string.plan_total, ByteFormatter.format(preview.reachableBytes), ByteFormatter.format(preview.withoutDeletingBytes)),
                    color = OnBlackSoft,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        PillButton(text = stringResource(R.string.plan_build), onClick = { choice?.let(onBuild) }, style = PillStyle.BLACK_LIME, enabled = choice != null)
        Text(stringResource(R.string.plan_footer), color = Ink, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, dark: Boolean = false, onClick: () -> Unit) {
    val fg = when {
        selected && dark -> Black
        selected -> Lime
        dark -> OnBlack
        else -> Ink
    }
    val bg = when {
        selected && dark -> Lime
        selected -> Black
        else -> Color.Transparent
    }
    Box(
        modifier
            .heightIn(min = 48.dp)
            .background(bg, RoundedCornerShape(28.dp))
            .border(2.dp, if (dark) (if (selected) Lime else CardLine) else Ink, RoundedCornerShape(28.dp))
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 15.sp, fontWeight = AppWeights.Title, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PlanScreen(inputs: PlanInputs, saved: SavedPlan, actions: PlanActions, onChangeGoal: () -> Unit) {
    val byKey = inputs.candidates.associateBy(PlanCandidate::key)
    val gained = (inputs.availableBytes - saved.startAvailableBytes).coerceAtLeast(0L)
    val plan: Plan = PlanBuilder.build(inputs.candidates, saved.targetBytes, inputs.learner)
    // The started order stays; steps found since then come after it.
    val keys = (saved.keys + plan.steps.map { it.candidate.key }).distinct()
    val next = keys.firstNotNullOfOrNull { k -> byKey[k]?.takeIf { it.bytes > 0L } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.plan_goal, ByteFormatter.format(saved.targetBytes)), color = Ink, fontSize = 17.sp, fontWeight = AppWeights.Title)
        Text(
            ByteFormatter.format(gained) + " / " + ByteFormatter.format(saved.targetBytes),
            color = Ink,
            fontSize = 48.sp,
            lineHeight = 52.sp,
            fontWeight = AppWeights.Giant,
        )
        LimeProgressBar(
            progress = if (saved.targetBytes > 0) (gained.toFloat() / saved.targetBytes).coerceIn(0f, 1f) else 1f,
            trackColor = Black,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        )
        if (!inputs.learner.learned) {
            Text(
                stringResource(R.string.plan_learning, inputs.learner.totalDecisions, PreferenceRules.LEARN_MIN_DECISIONS),
                color = Ink,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        BlackCard(contentPadding = PaddingValues(vertical = 6.dp)) {
            keys.forEachIndexed { i, key ->
                val c = byKey[key]
                if (i > 0) CardDivider()
                StepRow(i + 1, c, key, inputs, onClick = c?.takeIf { it.bytes > 0 }?.let { { actions.onOpenStep(it) } })
            }
        }
        Spacer(Modifier.height(20.dp))
        if (gained >= saved.targetBytes) {
            Text(stringResource(R.string.plan_reached), color = Ink, fontSize = 22.sp, fontWeight = AppWeights.Giant)
        } else if (next != null) {
            PillButton(text = stringResource(R.string.plan_next, inputs.titleOf(next)), onClick = { actions.onOpenStep(next) }, style = PillStyle.BLACK_LIME)
        }
        Text(
            if (plan.freeReachableBytes >= saved.targetBytes) {
                stringResource(R.string.plan_free_enough)
            } else {
                stringResource(R.string.plan_free_footer, ByteFormatter.format(plan.freeReachableBytes))
            },
            color = Ink,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LinkButton(stringResource(R.string.plan_change), onClick = onChangeGoal)
            LinkButton(stringResource(R.string.filters_tools), onClick = actions.onOpenFilters)
            LinkButton(stringResource(R.string.learned_link), onClick = actions.onOpenLearned)
        }
    }
}

@Composable
private fun StepRow(number: Int, c: PlanCandidate?, key: String, inputs: PlanInputs, onClick: (() -> Unit)?) {
    val done = c == null || c.bytes <= 0L
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).background(if (done) Lime else BlackChip, CircleShape), contentAlignment = Alignment.Center) {
            if (done) Icon(Icons.Outlined.Check, contentDescription = null, tint = Black, modifier = Modifier.size(18.dp))
            else Text("$number", color = OnBlack, fontSize = 13.sp, fontWeight = AppWeights.Title)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (c != null) inputs.titleOf(c) else stringResource(R.string.plan_step_done_title),
                    color = OnBlack,
                    fontSize = 15.sp,
                    fontWeight = AppWeights.Title,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (c != null && c.proOnly && !inputs.isPro && !done) {
                    Spacer(Modifier.width(8.dp))
                    ProBadge()
                }
            }
            Text(
                if (done) stringResource(R.string.plan_step_done) else stringResource(reasonRes(PlanBuilder.reasonFor(c!!, inputs.learner))),
                color = OnBlackSoft,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        if (c != null && !done) {
            Text(
                if (c.estimate) stringResource(R.string.video_about, ByteFormatter.format(c.bytes)) else ByteFormatter.format(c.bytes),
                color = Lime,
                fontSize = 14.sp,
                fontWeight = AppWeights.Giant,
            )
        }
    }
}

private fun reasonRes(r: StepReason): Int = when (r) {
    StepReason.NO_LOSS -> R.string.plan_reason_no_loss
    StepReason.WITHOUT_DELETING -> R.string.plan_reason_without_deleting
    StepReason.BEST_STAYS -> R.string.plan_reason_best_stays
    StepReason.YOU_DELETE_THESE -> R.string.plan_reason_you_delete
    StepReason.YOU_KEEP_THESE -> R.string.plan_reason_you_keep
    StepReason.OLD_FILES -> R.string.plan_reason_old
    StepReason.YOUR_CALL -> R.string.plan_reason_your_call
}

// ------------------------------------------------------------------ filters

internal data class FilterResult(val count: Int, val bytes: Long)

/** Faz 3C ready-made filters on black: chips, live result, "Show N files". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FiltersScreen(
    count: suspend (FileFilter) -> FilterResult,
    onShow: (FileFilter, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf(FileFilter()) }
    var result by remember { mutableStateOf<FilterResult?>(null) }
    LaunchedEffect(filter) {
        result = null
        result = if (filter.isEmpty) null else count(filter)
    }
    val label = stringResource(R.string.filters_tools)
    PageFrame(modifier = modifier, background = Black) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.filters_label), color = Lime, fontSize = 13.sp, fontWeight = AppWeights.Giant)
            val r = result
            Text(
                if (r != null) ByteFormatter.format(r.bytes) else "—",
                color = OnBlack,
                fontSize = 52.sp,
                lineHeight = 56.sp,
                fontWeight = AppWeights.Giant,
            )
            Text(
                when {
                    filter.isEmpty -> stringResource(R.string.filters_pick)
                    r == null -> stringResource(R.string.filters_counting)
                    else -> pluralStringResource(R.plurals.filters_match, r.count, r.count)
                },
                color = OnBlackSoft,
                fontSize = 15.sp,
            )
            Section(stringResource(R.string.filters_shortcuts)) {
                Chip(stringResource(R.string.filters_preset_screenshots), filter == FileFilter.LAST_YEAR_SCREENSHOTS, dark = true) { filter = FileFilter.LAST_YEAR_SCREENSHOTS }
                Chip(stringResource(R.string.filters_preset_whatsapp), filter == FileFilter.WHATSAPP_BIG_VIDEOS, dark = true) { filter = FileFilter.WHATSAPP_BIG_VIDEOS }
                Chip(stringResource(R.string.filters_preset_downloads), filter == FileFilter.OLD_DOWNLOADS, dark = true) { filter = FileFilter.OLD_DOWNLOADS }
            }
            Section(stringResource(R.string.filters_row_type)) {
                FilterType.entries.forEach { t ->
                    Chip(stringResource(typeRes(t)), t in filter.types, dark = true) {
                        filter = filter.copy(types = if (t in filter.types) filter.types - t else filter.types + t)
                    }
                }
            }
            Section(stringResource(R.string.filters_row_source)) {
                FilterSource.entries.forEach { s ->
                    Chip(stringResource(sourceRes(s)), s in filter.sources, dark = true) {
                        filter = filter.copy(sources = if (s in filter.sources) filter.sources - s else filter.sources + s)
                    }
                }
            }
            Section(stringResource(R.string.filters_row_date)) {
                FilterDate.entries.forEach { d ->
                    Chip(stringResource(dateRes(d)), filter.date == d, dark = true) { filter = filter.copy(date = if (filter.date == d) null else d) }
                }
            }
            Section(stringResource(R.string.filters_row_size)) {
                FilterSize.entries.forEach { s ->
                    Chip(ByteFormatter.format(s.minBytes) + "+", filter.size == s, dark = true) { filter = filter.copy(size = if (filter.size == s) null else s) }
                }
            }
            Spacer(Modifier.height(20.dp))
            val n = r?.count ?: 0
            PillButton(
                text = pluralStringResource(R.plurals.filters_show, n, n),
                onClick = { onShow(filter, label) },
                style = PillStyle.LIME,
                enabled = n > 0,
            )
            Text(stringResource(R.string.filters_footer), color = OnBlackSoft, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(title, color = OnBlackSoft, fontSize = 13.sp, fontWeight = AppWeights.Giant, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

private fun typeRes(t: FilterType) = when (t) {
    FilterType.PHOTO -> R.string.filter_photo
    FilterType.VIDEO -> R.string.filter_video
    FilterType.SCREENSHOT -> R.string.filter_screenshot
    FilterType.DOWNLOADED -> R.string.filter_downloaded
    FilterType.AUDIO -> R.string.filter_audio
    FilterType.DOCUMENT -> R.string.filter_document
}

private fun sourceRes(s: FilterSource) = when (s) {
    FilterSource.CAMERA -> R.string.filter_camera
    FilterSource.WHATSAPP -> R.string.filter_whatsapp
    FilterSource.OTHER_APPS -> R.string.filter_other_apps
    FilterSource.DOWNLOADS -> R.string.filter_downloads
}

private fun dateRes(d: FilterDate) = when (d) {
    FilterDate.THIS_YEAR -> R.string.filter_this_year
    FilterDate.LAST_YEAR -> R.string.filter_last_year
    FilterDate.OLDER_6_MONTHS -> R.string.filter_older_6m
    FilterDate.OLDER_1_YEAR -> R.string.filter_older_1y
}

// ------------------------------------------------------------------ learned

@Composable
internal fun LearnedScreen(
    learner: PreferenceSnapshot,
    titleOf: @Composable (String) -> String,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PageFrame(modifier = modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.learned_title), color = Ink, fontSize = 38.sp, lineHeight = 42.sp, fontWeight = AppWeights.Giant, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.learned_body), color = Ink, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = AppWeights.Strong, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
            val rows = learner.counts.filter { it.value.total > 0 }.entries.sortedByDescending { it.value.total }
            if (!learner.learned) {
                Text(
                    stringResource(R.string.plan_learning, learner.totalDecisions, PreferenceRules.LEARN_MIN_DECISIONS),
                    color = Ink,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            if (rows.isNotEmpty()) {
                BlackCard(contentPadding = PaddingValues(18.dp)) {
                    rows.forEachIndexed { i, (key, c) ->
                        if (i > 0) Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(titleOf(key), color = OnBlack, fontSize = 15.sp, fontWeight = AppWeights.Title, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.learned_row, c.delete, c.total), color = OnBlackSoft, fontSize = 12.sp)
                        }
                        LimeProgressBar(progress = c.deleteRate.toFloat(), trackColor = CardLine, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }
            }
            Text(stringResource(R.string.learned_footer), color = Ink, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 14.dp))
            Spacer(Modifier.height(24.dp))
            PillButton(text = stringResource(R.string.learned_reset), onClick = onReset, style = PillStyle.INK_OUTLINE, enabled = rows.isNotEmpty())
        }
    }
}

/** Keys of the steps that are not a cleanup group. */
internal const val NO_LOSS_KEY = "NO_LOSS"
internal const val SHRINK_KEY = "SHRINK"
internal const val SIMILAR_KEY = "SIMILAR"
