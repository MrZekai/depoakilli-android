package com.mrzekai.depoakilli.ads

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.ui.common.MakeRoomShapes
import com.mrzekai.depoakilli.ui.theme.AppWeights
import com.mrzekai.depoakilli.ui.theme.Black
import com.mrzekai.depoakilli.ui.theme.Ink
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * Faz 5D (D5): after an interstitial closes, at most once a day, a 6-second bar
 * offers the 60-minute ad-free window. Limits and the reward are unchanged;
 * the rewarded video only ever starts from the user's tap.
 */
object AdFreeOfferPolicy {
    const val BAR_MILLIS = 6_000L

    fun dayOf(wallMillis: Long): Long = TimeUnit.MILLISECONDS.toDays(wallMillis)

    /** Once a day, only when ads may be shown, no window is running and a video is ready. */
    fun shouldOffer(lastOfferDay: Long?, today: Long, adsAllowed: Boolean, adFreeActive: Boolean, rewardedReady: Boolean): Boolean =
        adsAllowed && !adFreeActive && rewardedReady && lastOfferDay != today
}

/** Remembers the day the bar was last shown. */
class AdFreeOfferStore(context: Context) {
    private val preferences = context.getSharedPreferences("ad_free_offer", Context.MODE_PRIVATE)

    fun lastOfferDay(): Long? = preferences.getLong(KEY_DAY, -1L).takeIf { it >= 0L }

    fun markOffered(day: Long) {
        preferences.edit().putLong(KEY_DAY, day).apply()
    }

    private companion object {
        const val KEY_DAY = "last_offer_day"
    }
}

@Composable
fun AdFreeOfferBar(onWatch: () -> Unit, onTimeout: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) {
        delay(AdFreeOfferPolicy.BAR_MILLIS)
        onTimeout()
    }
    Surface(
        color = Black,
        contentColor = OnBlack,
        shape = MakeRoomShapes.Card,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.ad_free_offer_bar),
                color = OnBlack,
                fontSize = 14.sp,
                fontWeight = AppWeights.Strong,
                modifier = Modifier.weight(1f),
            )
            Surface(
                onClick = onWatch,
                color = Lime,
                contentColor = Ink,
                shape = MakeRoomShapes.Pill,
            ) {
                Text(
                    stringResource(R.string.ad_free_offer_watch),
                    color = Ink,
                    fontSize = 15.sp,
                    fontWeight = AppWeights.Giant,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        }
    }
}
