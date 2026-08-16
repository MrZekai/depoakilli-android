package com.mrzekai.depoakilli.ads

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.mrzekai.depoakilli.BuildConfig

@Composable
fun BannerAd(
    canRequestAds: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!canRequestAds) return
    val context = LocalContext.current
    val adView = remember {
        AdView(context).apply {
            adUnitId = BuildConfig.ADMOB_BANNER_ID
            setAdSize(AdSize.BANNER)
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(adView) {
        onDispose { adView.destroy() }
    }
    Box(
        modifier = modifier.fillMaxWidth().height(54.dp),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(factory = { adView })
    }
}

class InterstitialAdController(private val context: Context) {
    private var interstitial: InterstitialAd? = null
    private var loading = false
    private var lastShownAt = 0L

    fun load() {
        if (loading || interstitial != null) return
        loading = true
        InterstitialAd.load(
            context,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loading = false
                    interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    interstitial = null
                }
            },
        )
    }

    fun showAfterCleanup(activity: Activity) {
        val now = System.currentTimeMillis()
        val ad = interstitial ?: return
        if (now - lastShownAt < MIN_INTERVAL_MILLIS) return
        interstitial = null
        lastShownAt = now
        ad.show(activity)
        load()
    }

    companion object {
        private const val MIN_INTERVAL_MILLIS = 5L * 60L * 1000L
    }
}
