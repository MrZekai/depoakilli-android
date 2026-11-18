package com.mrzekai.depoakilli.ads

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.mrzekai.depoakilli.BuildConfig

/**
 * One in-list native ad card (T1B.4). It carries the visible "Ad" badge and a
 * card style different from content rows, never imitates a checkbox or row
 * arrow, keeps 12 dp above and below, and leaves no gap when nothing loads.
 * Callers place at most one per screen.
 */
@Composable
fun ListNativeAdCard(
    canRequestAds: Boolean,
    modifier: Modifier = Modifier,
    adUnitId: String = BuildConfig.ADMOB_LIST_NATIVE_ID,
) {
    if (!canRequestAds || adUnitId.isBlank()) return
    val context = LocalContext.current
    var nativeAd by remember(adUnitId) { mutableStateOf<NativeAd?>(null) }

    DisposableEffect(context, adUnitId) {
        val loader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { loaded ->
                nativeAd?.destroy()
                nativeAd = loaded
            }
            .withAdListener(
                object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        nativeAd = null
                    }
                },
            )
            .build()
        loader.loadAd(AdRequest.Builder().build())
        onDispose {
            nativeAd?.destroy()
            nativeAd = null
        }
    }

    val loaded = nativeAd ?: return
    val height = (330 + 180 * (LocalDensity.current.fontScale - 1).coerceAtLeast(0f)).dp
    AndroidView(
        factory = { viewContext -> createNativeAdView(viewContext).also { bindNativeAd(it, loaded) } },
        update = { view -> bindNativeAd(view, loaded) },
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .height(height),
    )
}
