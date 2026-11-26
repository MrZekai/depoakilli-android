package com.mrzekai.depoakilli.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.billing.ProPlanKind
import com.mrzekai.depoakilli.billing.ProPlanOption
import com.mrzekai.depoakilli.billing.ProScreenState
import com.mrzekai.depoakilli.ui.common.BlackCard
import com.mrzekai.depoakilli.ui.common.CardDivider
import com.mrzekai.depoakilli.ui.common.LinkButton
import com.mrzekai.depoakilli.ui.common.PageFrame
import com.mrzekai.depoakilli.ui.common.PillButton
import com.mrzekai.depoakilli.ui.common.PillStyle
import com.mrzekai.depoakilli.ui.theme.AppWeights
import com.mrzekai.depoakilli.ui.theme.Ink
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft

/**
 * Smart Cleaner Pro (2.0 Faz 1): the paywall skeleton, opened only from
 * Settings. Pro unlocks nothing yet. Prices always come from Google Play; when
 * Play returns no plans (products not created yet, or no Play billing) the page
 * says "Coming soon" instead. The final design arrives in Faz 2.
 */
@Composable
internal fun ProScreen(
    state: ProScreenState,
    onBuy: (ProPlanOption) -> Unit,
    onRestore: () -> Unit,
    onManageSubscription: () -> Unit,
    onOpenLegalPage: (LegalPage) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The yearly plan is selected first (plan §5).
    var selectedKind by rememberSaveable { mutableStateOf(ProPlanKind.YEARLY) }
    val selected = state.plans.firstOrNull { it.kind == selectedKind } ?: state.plans.firstOrNull()

    PageFrame(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "pro-intro") {
                Text(
                    stringResource(R.string.pro_subtitle),
                    color = Ink,
                    fontSize = 17.sp,
                    lineHeight = 23.sp,
                    fontWeight = AppWeights.Strong,
                )
            }
            when {
                state.isPro -> item(key = "pro-active") {
                    InfoCard(
                        title = stringResource(R.string.pro_active_title),
                        body = stringResource(R.string.pro_active_body),
                    )
                }
                state.loading && !state.available -> item(key = "pro-loading") {
                    InfoCard(title = stringResource(R.string.pro_loading), body = null)
                }
                !state.available -> item(key = "pro-soon") {
                    InfoCard(
                        title = stringResource(R.string.pro_coming_soon_title),
                        body = stringResource(R.string.pro_coming_soon_body),
                    )
                }
                else -> {
                    item(key = "pro-plans") {
                        BlackCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                            state.plans.forEachIndexed { index, plan ->
                                if (index > 0) CardDivider()
                                PlanRow(
                                    plan = plan,
                                    selected = plan.kind == selected?.kind,
                                    onSelect = { selectedKind = plan.kind },
                                )
                            }
                        }
                    }
                    if (selected != null) {
                        item(key = "pro-buy") {
                            PillButton(
                                text = buyLabel(selected),
                                onClick = { onBuy(selected) },
                                style = PillStyle.BLACK_LIME,
                            )
                        }
                    }
                }
            }
            if (state.pending) {
                item(key = "pro-pending") {
                    Text(stringResource(R.string.pro_pending), color = Ink, fontSize = 14.sp, fontWeight = AppWeights.Strong)
                }
            }
            item(key = "pro-legal") {
                // Readable (>= 12 sp, Ink on Lime), with Terms, Privacy and Restore (plan §7).
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.pro_legal_renews), color = Ink, fontSize = 13.sp, lineHeight = 18.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LinkButton(stringResource(R.string.terms_of_service), onClick = { onOpenLegalPage(LegalPage.TERMS) })
                        LinkButton(stringResource(R.string.privacy_policy), onClick = { onOpenLegalPage(LegalPage.PRIVACY) })
                    }
                    LinkButton(stringResource(R.string.pro_restore), onClick = onRestore)
                    if (state.isPro) {
                        LinkButton(stringResource(R.string.pro_manage), onClick = onManageSubscription)
                    }
                }
            }
            item(key = "pro-close") {
                LinkButton(
                    stringResource(R.string.action_not_now),
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String?) {
    BlackCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Text(title, color = OnBlack, fontSize = 18.sp, fontWeight = AppWeights.Title)
        if (body != null) {
            Text(body, color = OnBlackSoft, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun PlanRow(plan: ProPlanOption, selected: Boolean, onSelect: () -> Unit) {
    val title = when (plan.kind) {
        ProPlanKind.YEARLY -> stringResource(R.string.pro_plan_yearly, plan.formattedPrice)
        ProPlanKind.WEEK_PASS -> stringResource(R.string.pro_plan_week, plan.formattedPrice)
        ProPlanKind.LIFETIME -> stringResource(R.string.pro_plan_lifetime, plan.formattedPrice)
    }
    val trialDays = plan.trialDays
    val detail = when (plan.kind) {
        ProPlanKind.YEARLY -> trialDays?.let {
            pluralStringResource(R.plurals.pro_plan_yearly_trial, it, it, plan.formattedPrice)
        }
        ProPlanKind.WEEK_PASS -> stringResource(R.string.pro_plan_week_detail)
        ProPlanKind.LIFETIME -> stringResource(R.string.pro_plan_lifetime_detail)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = Lime, unselectedColor = OnBlackSoft),
        )
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(title, color = OnBlack, fontSize = 18.sp, fontWeight = AppWeights.Title)
            if (detail != null) Text(detail, color = OnBlackSoft, fontSize = 13.sp)
        }
    }
}

@Composable
private fun buyLabel(plan: ProPlanOption): String {
    val trialDays = plan.trialDays
    return when (plan.kind) {
        ProPlanKind.YEARLY -> if (trialDays != null) {
            pluralStringResource(R.plurals.pro_buy_trial, trialDays, trialDays, plan.formattedPrice)
        } else {
            stringResource(R.string.pro_buy_yearly, plan.formattedPrice)
        }
        ProPlanKind.WEEK_PASS -> stringResource(R.string.pro_buy_week, plan.formattedPrice)
        ProPlanKind.LIFETIME -> stringResource(R.string.pro_buy_lifetime, plan.formattedPrice)
    }
}
