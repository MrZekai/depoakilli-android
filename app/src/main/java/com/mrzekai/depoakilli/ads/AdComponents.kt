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
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.appopen.AppOpenAd
import com.mrzekai.depoakilli.BuildConfig

@Composable
fun BannerAd(
    canRequestAds: Boolean,
    modifier: Modifier = Modifier,
) {
    FixedBannerAd(
        canRequestAds = canRequestAds,
        adUnitId = BuildConfig.ADMOB_BANNER_ID,
        adSize = AdSize.BANNER,
        heightDp = 54,
        modifier = modifier,
    )
}

@Composable
fun MediumRectangleAd(
    canRequestAds: Boolean,
    modifier: Modifier = Modifier,
) {
    FixedBannerAd(
        canRequestAds = canRequestAds,
        adUnitId = BuildConfig.ADMOB_MEDIUM_RECTANGLE_ID,
        adSize = AdSize.MEDIUM_RECTANGLE,
        heightDp = 250,
        modifier = modifier,
    )
}

@Composable
private fun FixedBannerAd(
    canRequestAds: Boolean,
    adUnitId: String,
    adSize: AdSize,
    heightDp: Int,
    modifier: Modifier,
) {
    if (!canRequestAds) return
    val context = LocalContext.current
    val adView = remember(context, adUnitId, adSize) {
        AdView(context).apply {
            this.adUnitId = adUnitId
            setAdSize(adSize)
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(adView) {
        onDispose { adView.destroy() }
    }
    Box(
        modifier = modifier.fillMaxWidth().height(heightDp.dp),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(factory = { adView })
    }
}

class InterstitialAdController(private val context: Context) {
    private var interstitial: InterstitialAd? = null
    private var loading = false
    private var lastShownAt = 0L
    private var adsAllowed = false

    fun setAdsAllowed(allowed: Boolean) {
        adsAllowed = allowed
        if (allowed) {
            load()
        } else {
            interstitial = null
            loading = false
        }
    }

    fun load() {
        if (!adsAllowed || loading || interstitial != null) return
        loading = true
        InterstitialAd.load(
            context,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loading = false
                    if (adsAllowed) interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    interstitial = null
                }
            },
        )
    }

    fun showAfterCleanup(activity: Activity) {
        if (!adsAllowed || activity.isFinishing || activity.isDestroyed) return
        val now = System.currentTimeMillis()
        val ad = interstitial ?: return
        if (now - lastShownAt < MIN_INTERVAL_MILLIS) return
        interstitial = null
        lastShownAt = now
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                load()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                load()
            }
        }
        ad.show(activity)
    }

    fun releaseForMemoryOptimization() {
        interstitial = null
        loading = false
    }

    companion object {
        private const val MIN_INTERVAL_MILLIS = 5L * 60L * 1000L
    }
}

class AppOpenAdController(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private var appOpenAd: AppOpenAd? = null
    private var loading = false
    private var loadTime = 0L
    private var adsAllowed = false

    var isShowingAd: Boolean = false
        private set

    fun setAdsAllowed(allowed: Boolean) {
        adsAllowed = allowed
        if (allowed) {
            load()
        } else {
            appOpenAd = null
            loading = false
        }
    }

    fun onAppForeground(activity: Activity) {
        val foregroundCount = preferences.getInt(KEY_FOREGROUND_COUNT, 0) + 1
        preferences.edit().putInt(KEY_FOREGROUND_COUNT, foregroundCount).apply()

        if (!adsAllowed || foregroundCount < MIN_FOREGROUNDS_BEFORE_FIRST_AD) {
            load()
            return
        }

        val lastShownAt = preferences.getLong(KEY_LAST_SHOWN_AT, 0L)
        if (System.currentTimeMillis() - lastShownAt < MIN_SHOW_INTERVAL_MILLIS) {
            load()
            return
        }
        showIfAvailable(activity)
    }

    fun load() {
        if (!adsAllowed || loading || isAdAvailable()) return
        appOpenAd = null
        loading = true
        AppOpenAd.load(
            context,
            BuildConfig.ADMOB_APP_OPEN_ID,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    loading = false
                    if (adsAllowed) {
                        appOpenAd = ad
                        loadTime = System.currentTimeMillis()
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    appOpenAd = null
                }
            },
        )
    }

    fun releaseForMemoryOptimization() {
        if (!isShowingAd) {
            appOpenAd = null
        }
        loading = false
    }

    private fun showIfAvailable(activity: Activity) {
        if (isShowingAd || activity.isFinishing || activity.isDestroyed) return
        val ad = appOpenAd
        if (ad == null || !isAdAvailable()) {
            appOpenAd = null
            load()
            return
        }

        appOpenAd = null
        isShowingAd = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                finishShowing()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                finishShowing()
            }

            override fun onAdShowedFullScreenContent() {
                preferences.edit().putLong(KEY_LAST_SHOWN_AT, System.currentTimeMillis()).apply()
            }
        }
        ad.show(activity)
    }

    private fun finishShowing() {
        isShowingAd = false
        load()
    }

    private fun isAdAvailable(): Boolean =
        appOpenAd != null && System.currentTimeMillis() - loadTime < APP_OPEN_EXPIRY_MILLIS

    private companion object {
        private const val PREFERENCES_NAME = "app_open_ads"
        private const val KEY_FOREGROUND_COUNT = "foreground_count"
        private const val KEY_LAST_SHOWN_AT = "last_shown_at"
        private const val MIN_FOREGROUNDS_BEFORE_FIRST_AD = 3
        private const val MIN_SHOW_INTERVAL_MILLIS = 2L * 60L * 60L * 1000L
        private const val APP_OPEN_EXPIRY_MILLIS = 4L * 60L * 60L * 1000L
    }
}
