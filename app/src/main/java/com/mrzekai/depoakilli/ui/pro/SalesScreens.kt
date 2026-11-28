package com.mrzekai.depoakilli.ui.pro

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.billing.UpgradeOffer
import com.mrzekai.depoakilli.ui.common.BlackCard
import com.mrzekai.depoakilli.ui.common.LinkButton
import com.mrzekai.depoakilli.ui.common.PillButton
import com.mrzekai.depoakilli.ui.common.PillStyle
import com.mrzekai.depoakilli.ui.theme.AppWeights
import com.mrzekai.depoakilli.ui.theme.Black
import com.mrzekai.depoakilli.ui.theme.GiantScale
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft

/**
 * Sales S4: shown once, after the first "no", only with Play's upgrade-yearly
 * offer. Calm wording; the price comes from Play.
 */
@Composable
internal fun LeaveOfferScreen(offer: UpgradeOffer, onAccept: () -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Black), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.fillMaxSize().widthIn(max = GiantScale.MAX_CONTENT_WIDTH_DP.dp).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.paywall_close), tint = OnBlack, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.offer_label), color = Lime, fontSize = 13.sp, fontWeight = AppWeights.Giant)
            Text(
                stringResource(R.string.offer_title, offer.firstYearPrice, offer.laterYearPrice),
                color = OnBlack,
                fontSize = 32.sp,
                lineHeight = 38.sp,
                fontWeight = AppWeights.Giant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(stringResource(R.string.paywall_legal), color = OnBlackSoft, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 12.dp))
            Spacer(Modifier.weight(1f))
            PillButton(text = stringResource(R.string.offer_accept, offer.firstYearPrice), onClick = onAccept, style = PillStyle.LIME)
            LinkButton(stringResource(R.string.offer_decline), onClick = onClose, color = OnBlackSoft, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
        }
    }
}

/** Sales S3 without notifications: the reminder as a Home card on the last day. */
@Composable
internal fun TrialReminderCard(onManage: () -> Unit, modifier: Modifier = Modifier) {
    BlackCard(modifier = modifier, contentPadding = PaddingValues(18.dp)) {
        Text(stringResource(R.string.trial_reminder_title), color = Lime, fontSize = 17.sp, fontWeight = AppWeights.Giant)
        Text(stringResource(R.string.trial_reminder_body), color = OnBlack, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp))
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PillButton(text = stringResource(R.string.pro_manage), onClick = onManage, style = PillStyle.LIME_OUTLINE)
        }
    }
}
