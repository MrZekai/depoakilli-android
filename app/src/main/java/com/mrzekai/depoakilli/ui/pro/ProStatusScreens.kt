package com.mrzekai.depoakilli.ui.pro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.billing.OwnedPlan
import com.mrzekai.depoakilli.billing.ProPlanKind
import com.mrzekai.depoakilli.billing.ProPlanOption
import com.mrzekai.depoakilli.billing.ProScreenState
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.ui.LegalPage
import com.mrzekai.depoakilli.ui.common.BlackCard
import com.mrzekai.depoakilli.ui.common.CardDivider
import com.mrzekai.depoakilli.ui.common.CardRow
import com.mrzekai.depoakilli.ui.common.LinkButton
import com.mrzekai.depoakilli.ui.common.PageFrame
import com.mrzekai.depoakilli.ui.common.PageTitle
import com.mrzekai.depoakilli.ui.common.PillButton
import com.mrzekai.depoakilli.ui.common.PillStyle
import com.mrzekai.depoakilli.ui.theme.AppWeights
import com.mrzekai.depoakilli.ui.theme.Ink
import com.mrzekai.depoakilli.ui.theme.InkSoft
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack

/**
 * Y3 honest paywall: when Pro would clear less than 2 GB on this phone, the
 * app says so instead of selling. "See plans anyway" still opens the paywall.
 */
@Composable
internal fun HonestProScreen(gainBytes: Long, onOk: () -> Unit, onSeePlans: () -> Unit, modifier: Modifier = Modifier) {
    PageFrame(modifier = modifier) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Ink, modifier = Modifier.size(72.dp))
            Text(
                stringResource(R.string.honest_title),
                color = Ink,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = AppWeights.Giant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                if (gainBytes > 0L) {
                    stringResource(R.string.honest_body, ByteFormatter.format(gainBytes))
                } else {
                    stringResource(R.string.honest_body_zero)
                },
                color = InkSoft,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                fontWeight = AppWeights.Strong,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
            PillButton(
                text = stringResource(R.string.honest_ok),
                onClick = onOk,
                style = PillStyle.BLACK,
                modifier = Modifier.padding(top = 28.dp),
            )
            LinkButton(stringResource(R.string.honest_see_plans), onClick = onSeePlans)
        }
    }
}

/**
 * Y5 "Pro is on": the plan the device can name (no renewal or end date:
 * Play Billing does not give one to the app), this month's measured bytes
 * cleared with Pro, Manage / Restore / Terms / Privacy and the cancel path.
 */
@Composable
internal fun ProOnScreen(
    state: ProScreenState,
    monthBytes: Long,
    /** S5: everything cleared with Pro since the start (measured). */
    totalBytes: Long = 0L,
    onManageSubscription: () -> Unit,
    onRestore: () -> Unit,
    onAddTime: (ProPlanOption) -> Unit,
    onOpenLegalPage: (LegalPage) -> Unit,
    modifier: Modifier = Modifier,
    /** Faz 3A-1: notices the watch really posted this month (shown only when > 0). */
    watchNoticesThisMonth: Int = 0,
) {
    val owned = state.ownedPlan
    val weekPlan = state.plans.firstOrNull { it.kind == ProPlanKind.WEEK_PASS }
    PageFrame(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "pro-on-title") {
                Column {
                    PageTitle(stringResource(R.string.pro_active_title))
                    Text(
                        stringResource(
                            when (owned) {
                                OwnedPlan.YEARLY -> R.string.pro_owned_yearly
                                OwnedPlan.WEEK_PASS -> R.string.pro_owned_week
                                OwnedPlan.LIFETIME -> R.string.pro_owned_lifetime
                                OwnedPlan.SUBSCRIPTION_NOT_RENEWING, null -> R.string.pro_owned_not_renewing
                            },
                        ),
                        color = Ink,
                        fontSize = 17.sp,
                        fontWeight = AppWeights.Title,
                    )
                }
            }
            if (state.pending) {
                item(key = "pro-on-pending") {
                    BlackCard(contentPadding = PaddingValues(18.dp)) {
                        Text(stringResource(R.string.pro_pending), color = OnBlack, fontSize = 15.sp, fontWeight = AppWeights.Strong)
                    }
                }
            }
            item(key = "pro-on-month") {
                BlackCard(contentPadding = PaddingValues(20.dp)) {
                    Text(stringResource(R.string.pro_month_label), color = Lime, fontSize = 13.sp, fontWeight = AppWeights.Giant)
                    if (monthBytes > 0L) {
                        Text(
                            stringResource(R.string.pro_month_cleared, ByteFormatter.format(monthBytes)),
                            color = OnBlack,
                            fontSize = 38.sp,
                            lineHeight = 44.sp,
                            fontWeight = AppWeights.Giant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        if (totalBytes > monthBytes) {
                            Text(
                                stringResource(R.string.pro_total_cleared, ByteFormatter.format(totalBytes)),
                                color = OnBlackSoft,
                                fontSize = 15.sp,
                                fontWeight = AppWeights.Strong,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    } else {
                        Text(
                            stringResource(R.string.pro_month_none),
                            color = OnBlack,
                            fontSize = 20.sp,
                            lineHeight = 26.sp,
                            fontWeight = AppWeights.Title,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        if (owned != OwnedPlan.LIFETIME) {
                            PillButton(
                                text = stringResource(R.string.pro_manage),
                                onClick = onManageSubscription,
                                style = PillStyle.LIME,
                                modifier = Modifier.padding(top = 14.dp),
                            )
                        }
                    }
                }
            }
            if (watchNoticesThisMonth > 0) {
                item(key = "pro-on-watch") {
                    BlackCard(contentPadding = PaddingValues(18.dp)) {
                        Text(
                            androidx.compose.ui.res.pluralStringResource(R.plurals.watch_pro_sent, watchNoticesThisMonth, watchNoticesThisMonth),
                            color = OnBlack,
                            fontSize = 15.sp,
                            fontWeight = AppWeights.Strong,
                        )
                    }
                }
            }
            item(key = "pro-on-rows") {
                BlackCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                    if (owned == OwnedPlan.WEEK_PASS && weekPlan != null) {
                        CardRow(
                            title = stringResource(R.string.pro_add_time, weekPlan.formattedPrice),
                            onClick = { onAddTime(weekPlan) },
                        )
                        CardDivider()
                    }
                    if (owned != OwnedPlan.LIFETIME) {
                        CardRow(title = stringResource(R.string.pro_manage), onClick = onManageSubscription)
                        CardDivider()
                    }
                    CardRow(title = stringResource(R.string.pro_restore), onClick = onRestore)
                    CardDivider()
                    CardRow(title = stringResource(R.string.terms_of_service), onClick = { onOpenLegalPage(LegalPage.TERMS) })
                    CardDivider()
                    CardRow(title = stringResource(R.string.privacy_policy), onClick = { onOpenLegalPage(LegalPage.PRIVACY) })
                }
            }
            if (owned != OwnedPlan.LIFETIME) {
                item(key = "pro-on-note") {
                    Text(
                        stringResource(R.string.pro_on_cancel_note),
                        color = Ink,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

