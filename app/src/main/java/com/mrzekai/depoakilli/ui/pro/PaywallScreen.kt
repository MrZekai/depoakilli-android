package com.mrzekai.depoakilli.ui.pro

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.billing.ProPlanKind
import com.mrzekai.depoakilli.billing.ProPlanOption
import com.mrzekai.depoakilli.billing.ProScreenState
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.pro.ProGainEstimator
import com.mrzekai.depoakilli.pro.ProPricing
import com.mrzekai.depoakilli.ui.LegalPage
import com.mrzekai.depoakilli.ui.common.LinkButton
import com.mrzekai.depoakilli.ui.common.MakeRoomShapes
import com.mrzekai.depoakilli.ui.common.PillButton
import com.mrzekai.depoakilli.ui.common.PillStyle
import com.mrzekai.depoakilli.ui.common.SystemBarIcons
import com.mrzekai.depoakilli.ui.theme.AppWeights
import com.mrzekai.depoakilli.ui.theme.Black
import com.mrzekai.depoakilli.ui.theme.CardLine
import com.mrzekai.depoakilli.ui.theme.GiantScale
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft
import com.mrzekai.depoakilli.ui.theme.PlanSelected
import java.text.DateFormat
import java.util.Date

/** What the paywall shows besides Play's plans; every figure measured on this phone. */
internal data class PaywallFacts(
    val estimate: ProGainEstimator.Estimate,
    val totalBytes: Long,
    val availableBytes: Long,
    /** Average photo size from MediaStore; null when unknown or no photos. */
    val averagePhotoBytes: Long?,
)

/**
 * Y2 paywall (Faz 2, v2): black page. A visible ✕ from the first frame and
 * "Restore purchases"; the user's own gain; before/after bar; trust chips;
 * plans in Play's prices with the yearly plan selected; one button; first
 * charge date when a trial is offered; cancellation line with Terms and
 * Privacy. Opens only when the user taps; system Back closes it like ✕.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PaywallScreen(
    state: ProScreenState,
    facts: PaywallFacts,
    onBuy: (ProPlanOption) -> Unit,
    onRestore: () -> Unit,
    onOpenLegalPage: (LegalPage) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    SystemBarIcons(darkStatusIcons = false, darkNavigationIcons = false)
    var selectedKind by rememberSaveable { mutableStateOf(ProPlanKind.YEARLY) }
    // S2: yearly (selected) and the week pass; the one payment behind a small link.
    var showLifetime by rememberSaveable { mutableStateOf(false) }
    val shownPlans = state.plans.filter { it.kind != ProPlanKind.LIFETIME || showLifetime }
        .ifEmpty { state.plans }
    val selected = state.plans.firstOrNull { it.kind == selectedKind } ?: state.plans.firstOrNull()
    val gain = facts.estimate.totalBytes

    Box(modifier.fillMaxSize().background(Black), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .fillMaxSize()
                .widthIn(max = GiantScale.MAX_CONTENT_WIDTH_DP.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.paywall_close),
                        tint = OnBlack,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Box(Modifier.weight(1f))
                LinkButton(stringResource(R.string.pro_restore), onClick = onRestore, color = OnBlackSoft)
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PaywallHeadline(gain, oneTapOnly = facts.estimate.oneTapOnly)
                Text(
                    listOfNotNull(
                        ProPricing.photoEquivalent(gain, facts.averagePhotoBytes)?.let {
                            pluralStringResource(R.plurals.paywall_photos, it, it)
                        },
                        stringResource(R.string.paywall_sub),
                    ).joinToString(" "),
                    color = OnBlackSoft,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                )
                if (facts.estimate.rows.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        facts.estimate.rows.take(3).forEach { row ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(gainRowLabel(row), color = OnBlack, fontSize = 15.sp, fontWeight = AppWeights.Strong, modifier = Modifier.weight(1f))
                                Text(gainRowValue(row), color = Lime, fontSize = 15.sp, fontWeight = AppWeights.Title)
                            }
                        }
                    }
                }
                // Bars only for a gain Pro alone gives; one-tap files can be cleared by hand too.
                if (facts.estimate.showsFillBars) {
                    ProPricing.fillPercents(facts.totalBytes, facts.availableBytes, facts.estimate.proOnlyBytes)?.let { (now, after) ->
                        Surface(color = PlanSelected, shape = MakeRoomShapes.Card, modifier = Modifier.fillMaxWidth()) {
                            FillBars(now, after, Modifier.padding(14.dp), estimateNote = facts.estimate.hasEstimates)
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrustChip(stringResource(R.string.paywall_chip_undo))
                    TrustChip(stringResource(R.string.paywall_chip_on_phone))
                    if (selected != null && selected.kind != ProPlanKind.LIFETIME) TrustChip(stringResource(R.string.paywall_chip_cancel))
                }
                when {
                    state.plans.isEmpty() && state.loading -> PaywallInfo(stringResource(R.string.pro_loading), null)
                    state.plans.isEmpty() -> PaywallInfo(
                        stringResource(R.string.pro_coming_soon_title),
                        stringResource(R.string.pro_coming_soon_body),
                    )
                    else -> {
                        shownPlans.forEach { plan ->
                            PlanCard(
                                plan = plan,
                                selected = plan.kind == selected?.kind,
                                onSelect = { selectedKind = plan.kind },
                            )
                        }
                        if (!showLifetime && state.plans.any { it.kind == ProPlanKind.LIFETIME } && shownPlans.size < state.plans.size) {
                            LinkButton(
                                stringResource(R.string.paywall_other_option),
                                onClick = { showLifetime = true },
                                color = OnBlackSoft,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                if (state.pending) {
                    Text(stringResource(R.string.pro_pending), color = Lime, fontSize = 14.sp, fontWeight = AppWeights.Strong)
                }
            }
            Column(
                Modifier.padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (selected != null) {
                    PillButton(
                        text = buyLabel(selected),
                        onClick = { onBuy(selected) },
                        style = PillStyle.LIME,
                        modifier = Modifier.heightIn(min = 60.dp),
                    )
                    val trial = selected.trialDays
                    if (selected.kind == ProPlanKind.YEARLY && trial != null) {
                        val locale = LocalConfiguration.current.locales[0]
                        val date = remember(trial, locale) {
                            DateFormat.getDateInstance(DateFormat.LONG, locale)
                                .format(Date(ProPricing.firstChargeMillis(System.currentTimeMillis(), trial)))
                                // "29 ноября 2026 г." already ends with a dot; the sentence adds its own.
                                .trimEnd('.')
                        }
                        Text(
                            stringResource(R.string.paywall_first_charge, date),
                            color = OnBlack,
                            fontSize = 14.sp,
                            fontWeight = AppWeights.Strong,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                // >= 12 sp, #9AA3B2 on #07090C (contrast ≈ 7.6:1).
                // Lifetime is one payment: nothing to cancel, so no cancel line.
                if (selected?.kind != ProPlanKind.LIFETIME) {
                    Text(
                        stringResource(R.string.paywall_legal),
                        color = OnBlackSoft,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                // Whole links wrap to a new line; a long word is never split.
                FlowRow(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    LinkButton(stringResource(R.string.terms_of_service), onClick = { onOpenLegalPage(LegalPage.TERMS) }, color = OnBlackSoft)
                    LinkButton(stringResource(R.string.privacy_policy), onClick = { onOpenLegalPage(LegalPage.PRIVACY) }, color = OnBlackSoft)
                }
            }
        }
    }
}

@Composable
private fun PaywallHeadline(gain: Long, oneTapOnly: Boolean) {
    if (gain > 0L) {
        val value = ByteFormatter.format(gain)
        val full = stringResource(if (oneTapOnly) R.string.paywall_title_one_tap else R.string.paywall_title, value)
        val start = full.indexOf(value)
        Text(
            buildAnnotatedString {
                if (start < 0) {
                    append(full)
                } else {
                    append(full.substring(0, start))
                    withStyle(SpanStyle(color = Lime)) { append(value) }
                    append(full.substring(start + value.length))
                }
            },
            color = OnBlack,
            fontSize = 38.sp,
            lineHeight = 42.sp,
            fontWeight = AppWeights.Giant,
        )
    } else {
        Text(stringResource(R.string.paywall_title_unknown), color = OnBlack, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = AppWeights.Giant)
    }
}

@Composable
private fun TrustChip(text: String) {
    Box(
        Modifier
            .border(1.dp, CardLine, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(text, color = OnBlack, fontSize = 13.sp, fontWeight = AppWeights.Title, maxLines = 1)
    }
}

@Composable
private fun PaywallInfo(title: String, body: String?) {
    Surface(color = PlanSelected, shape = MakeRoomShapes.Card, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = OnBlack, fontSize = 18.sp, fontWeight = AppWeights.Title)
            if (body != null) Text(body, color = OnBlackSoft, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun PlanCard(plan: ProPlanOption, selected: Boolean, onSelect: () -> Unit) {
    val trialDays = plan.trialDays
    val header = stringResource(
        when (plan.kind) {
            ProPlanKind.YEARLY -> R.string.paywall_plan_yearly
            ProPlanKind.WEEK_PASS -> R.string.paywall_plan_week
            ProPlanKind.LIFETIME -> R.string.paywall_plan_lifetime
        },
    )
    val price = when (plan.kind) {
        ProPlanKind.YEARLY -> stringResource(R.string.pro_plan_yearly, plan.formattedPrice)
        ProPlanKind.WEEK_PASS -> stringResource(R.string.pro_plan_week, plan.formattedPrice)
        ProPlanKind.LIFETIME -> stringResource(R.string.pro_plan_lifetime, plan.formattedPrice)
    }
    val detail = when (plan.kind) {
        ProPlanKind.YEARLY -> if (trialDays != null) {
            pluralStringResource(R.plurals.pro_plan_yearly_trial_renews, trialDays, trialDays, plan.formattedPrice)
        } else {
            stringResource(R.string.pro_plan_yearly_renews, plan.formattedPrice)
        }
        ProPlanKind.WEEK_PASS -> stringResource(R.string.pro_plan_week_detail)
        ProPlanKind.LIFETIME -> stringResource(R.string.pro_plan_lifetime_detail)
    }
    // S2 yearly: "about X a week" (÷ 52, rounded up), small and below the yearly amount, which stays the biggest.
    val small = if (plan.kind == ProPlanKind.YEARLY) {
        val locale = LocalConfiguration.current.locales[0]
        ProPricing.format(ProPricing.weeklyEquivalentMicros(plan.priceMicros), plan.currencyCode, locale)
            ?.let { stringResource(R.string.paywall_weekly_equivalent, it) }
    } else {
        null
    }
    Surface(
        color = if (selected) PlanSelected else Black,
        shape = MakeRoomShapes.Card,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Lime else CardLine),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(selectedColor = Lime, unselectedColor = OnBlackSoft),
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Text(header, color = OnBlackSoft, fontSize = 12.sp, fontWeight = AppWeights.Title)
                Text(price, color = OnBlack, fontSize = 22.sp, lineHeight = 27.sp, fontWeight = AppWeights.Giant)
                Text(detail, color = OnBlack, fontSize = 13.sp, lineHeight = 18.sp)
                if (small != null) Text(small, color = OnBlackSoft, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

/** Main button text for the selected plan (Y2 §5). */
@Composable
internal fun buyLabel(plan: ProPlanOption): String = when (ProPricing.buttonText(plan)) {
    ProPricing.ButtonText.TRIAL -> {
        val days = plan.trialDays ?: 0
        pluralStringResource(R.plurals.pro_buy_trial, days, days, plan.formattedPrice)
    }
    ProPricing.ButtonText.YEARLY -> stringResource(R.string.pro_buy_yearly, plan.formattedPrice)
    ProPricing.ButtonText.WEEK_PASS -> stringResource(R.string.pro_buy_week, plan.formattedPrice)
    ProPricing.ButtonText.LIFETIME -> stringResource(R.string.pro_buy_lifetime, plan.formattedPrice)
}
