package com.mrzekai.depoakilli.ui.pro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.pro.ProGainEstimator
import com.mrzekai.depoakilli.pro.ProPricing
import com.mrzekai.depoakilli.ui.common.BlackCard
import com.mrzekai.depoakilli.ui.common.LimeProgressBar
import com.mrzekai.depoakilli.ui.common.LinkButton
import com.mrzekai.depoakilli.ui.common.PillButton
import com.mrzekai.depoakilli.ui.common.PillStyle
import com.mrzekai.depoakilli.ui.theme.AppWeights
import com.mrzekai.depoakilli.ui.theme.CardLine
import com.mrzekai.depoakilli.ui.theme.Ink
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft
import java.text.NumberFormat

/** "PRO" badge: Lime, Ink text, weight 900 (12 sp: the project minimum; the design says 11). */
@Composable
internal fun ProBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(Lime, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(stringResource(R.string.pro_badge), color = Ink, fontSize = 12.sp, fontWeight = AppWeights.Giant, maxLines = 1)
    }
}

/** Label of a gain row (only features the app has; Faz 2.2b). */
@Composable
internal fun gainRowLabel(row: ProGainEstimator.Row): String = when (row.kind) {
    ProGainEstimator.Kind.ONE_TAP_PICK -> pluralStringResource(R.plurals.pro_gain_pick, row.count, row.count)
    ProGainEstimator.Kind.VIDEO_COMPRESSION -> pluralStringResource(R.plurals.pro_gain_video, row.count, row.count)
}

/** A gain row's value; estimates say "about". */
@Composable
internal fun gainRowValue(row: ProGainEstimator.Row): String =
    if (row.measured) ByteFormatter.format(row.bytes) else stringResource(R.string.pro_value_about, ByteFormatter.format(row.bytes))

@Composable
internal fun percentLabel(percent: Int): String {
    val locale = LocalConfiguration.current.locales[0]
    return stringResource(R.string.pro_fill_full, NumberFormat.getPercentInstance(locale).format(percent / 100.0))
}

/** "NOW 92 % full" and "WITH PRO 71 % full" (white and Lime; never red). */
@Composable
internal fun FillBars(now: Int, after: Int, modifier: Modifier = Modifier, showNote: Boolean = true, estimateNote: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FillBarRow(stringResource(R.string.pro_fill_now), now, OnBlack, OnBlack)
        FillBarRow(stringResource(R.string.pro_fill_with_pro), after, Lime, Lime)
        if (showNote) {
            Text(
                stringResource(if (estimateNote) R.string.pro_fill_note_estimate else R.string.pro_fill_note),
                color = OnBlackSoft,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun FillBarRow(label: String, percent: Int, barColor: Color, valueColor: Color) {
    // The label gets its own line: long labels ("MAINTENANT", "DENGAN PRO") never cut.
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = OnBlackSoft,
                fontSize = 12.sp,
                fontWeight = AppWeights.Title,
                modifier = Modifier.weight(1f),
            )
            Text(percentLabel(percent), color = valueColor, fontSize = 13.sp, fontWeight = AppWeights.Title, maxLines = 1)
        }
        LimeProgressBar(
            progress = percent / 100f,
            color = barColor,
            trackColor = CardLine,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}

/**
 * Y1: under the free giant button, what Pro would clear on top (measured;
 * estimates say "about"). Only when the gain is at least 2 GB and the user is
 * not Pro; "Not now" hides it for 3 days.
 */
@Composable
internal fun ProGainCard(
    estimate: ProGainEstimator.Estimate,
    totalBytes: Long,
    availableBytes: Long,
    trialDays: Int?,
    onOpenPaywall: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BlackCard(modifier = modifier, contentPadding = PaddingValues(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProBadge()
            Text(
                stringResource(
                    when {
                        estimate.oneTapOnly -> R.string.paywall_title_one_tap
                        estimate.hasEstimates -> R.string.pro_card_title_about
                        else -> R.string.pro_card_title
                    },
                    ByteFormatter.format(estimate.totalBytes),
                ),
                color = OnBlack,
                fontSize = 18.sp,
                lineHeight = 23.sp,
                fontWeight = AppWeights.Title,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            estimate.rows.forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(gainRowLabel(row), color = OnBlack, fontSize = 15.sp, fontWeight = AppWeights.Strong, modifier = Modifier.weight(1f))
                    Text(gainRowValue(row), color = Lime, fontSize = 15.sp, fontWeight = AppWeights.Title)
                }
            }
        }
        // Bars only for a gain Pro alone gives (Fable audit, Faz 2).
        if (estimate.showsFillBars) {
            ProPricing.fillPercents(totalBytes, availableBytes, estimate.proOnlyBytes)?.let { (now, after) ->
                FillBars(now, after, modifier = Modifier.padding(top = 12.dp), showNote = false)
            }
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(
                text = if (trialDays != null) {
                    pluralStringResource(R.plurals.pro_card_try, trialDays, trialDays)
                } else {
                    stringResource(R.string.pro_card_see)
                },
                onClick = onOpenPaywall,
                style = PillStyle.LIME,
                modifier = Modifier.weight(1f),
            )
            LinkButton(stringResource(R.string.action_not_now), onClick = onNotNow, color = OnBlackSoft)
        }
    }
}
