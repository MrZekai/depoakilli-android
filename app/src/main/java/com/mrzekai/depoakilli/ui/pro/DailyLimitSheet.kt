package com.mrzekai.depoakilli.ui.pro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.ui.common.LinkButton
import com.mrzekai.depoakilli.ui.common.MakeRoomShapes
import com.mrzekai.depoakilli.ui.common.PillButton
import com.mrzekai.depoakilli.ui.common.PillStyle
import com.mrzekai.depoakilli.ui.theme.AppWeights
import com.mrzekai.depoakilli.ui.theme.CardLine
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft
import com.mrzekai.depoakilli.ui.theme.PlanSelected

/**
 * Y4: the day's free one-by-one decisions are used. The main action cleans
 * what was marked (the usual confirmation); Pro is the second, outlined
 * choice; "I'll continue tomorrow" closes. Nothing opens by itself.
 */
@Composable
internal fun DailyLimitSheet(
    used: Int,
    limit: Int,
    markedBytes: Long,
    onClean: () -> Unit,
    onOpenPaywall: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        color = PlanSelected,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 28.dp, bottomEnd = 28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(width = 44.dp, height = 4.dp).background(CardLine, RoundedCornerShape(2.dp)))
            Text(
                stringResource(R.string.limit_header, used.coerceAtMost(limit), limit),
                color = Lime,
                fontSize = 13.sp,
                fontWeight = AppWeights.Giant,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            Text(
                if (markedBytes > 0L) {
                    stringResource(R.string.limit_title, ByteFormatter.format(markedBytes))
                } else {
                    stringResource(R.string.limit_title_none)
                },
                color = OnBlack,
                fontSize = 26.sp,
                lineHeight = 31.sp,
                fontWeight = AppWeights.Giant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text(
                pluralStringResource(R.plurals.limit_body, limit, limit),
                color = OnBlackSoft,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            PillButton(
                text = if (markedBytes > 0L) {
                    stringResource(R.string.limit_clear, ByteFormatter.format(markedBytes))
                } else {
                    stringResource(R.string.honest_ok)
                },
                onClick = if (markedBytes > 0L) onClean else onClose,
                style = PillStyle.LIME,
                modifier = Modifier.padding(top = 18.dp),
            )
            Surface(
                onClick = onOpenPaywall,
                shape = MakeRoomShapes.Pill,
                color = PlanSelected,
                border = BorderStroke(2.dp, OnBlackSoft),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 56.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    ProBadge()
                    Text(
                        stringResource(R.string.limit_pro),
                        color = OnBlack,
                        fontSize = 16.sp,
                        fontWeight = AppWeights.Giant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
            LinkButton(stringResource(R.string.limit_tomorrow), onClick = onClose, color = OnBlackSoft)
        }
    }
}
