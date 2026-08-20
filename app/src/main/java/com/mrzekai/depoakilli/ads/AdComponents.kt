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
    val context = LocalContext.current
    val adSize = AdSize.BANNER

    Box(
        modifier = modifier.fillMaxWidth().height(50.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (canRequestAds) {
            val adView = remember(context, BuildConfig.ADMOB_BANNER_ID) {
                AdView(context).apply {
                    adUnitId = BuildConfig.ADMOB_BANNER_ID
                    setAdSize(adSize)
                    loadAd(AdRequest.Builder().build())
                }
            }
            DisposableEffect(adView) {
                onDispose { adView.destroy() }
            }
            AndroidView(factory = { adView })
        }
    }
}

class InterstitialAdController(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private var interstitial: InterstitialAd? = null
    private var loading = false
    private var lastShownAt = preferences.getLong(KEY_LAST_SHOWN_AT, 0L)
    private var lastReleasedAt = 0L
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
        if (System.currentTimeMillis() - lastReleasedAt < MIN_RELOAD_AFTER_RELEASE_MILLIS) return
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

    /**
     * Shows an interstitial only at a completed-task / natural-break point.
     * If the ad is unavailable, frequency-capped, or too close to App Open,
     * the caller continues immediately and the app never blocks the task.
     */
    fun showAtNaturalBreak(
        activity: Activity,
        onWillShow: () -> Unit = {},
        onFinished: () -> Unit = {},
    ) {
        if (!adsAllowed || activity.isFinishing || activity.isDestroyed) {
            onFinished()
            return
        }

        val now = System.currentTimeMillis()
        val ad = interstitial
        if (ad == null) {
            load()
            onFinished()
            return
        }
        if (now - lastShownAt < MIN_INTERVAL_MILLIS) {
            onFinished()
            return
        }
        if (AppOpenAdController.wasShownWithin(context, FULL_SCREEN_SEPARATION_MILLIS)) {
            onFinished()
            return
        }

        interstitial = null
        var completed = false

        fun finishFlow() {
            if (completed) return
            completed = true
            load()
            onFinished()
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                lastShownAt = System.currentTimeMillis()
                preferences.edit().putLong(KEY_LAST_SHOWN_AT, lastShownAt).apply()
            }

            override fun onAdDismissedFullScreenContent() {
                finishFlow()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                finishFlow()
            }
        }

        onWillShow()
        runCatching { ad.show(activity) }
            .onFailure { finishFlow() }
    }

    fun releaseForMemoryOptimization() {
        interstitial = null
        loading = false
        lastReleasedAt = System.currentTimeMillis()
    }

    companion object {
        private const val PREFERENCES_NAME = "interstitial_ads"
        private const val KEY_LAST_SHOWN_AT = "last_shown_at"
        private const val MIN_INTERVAL_MILLIS = 5L * 60L * 1000L
        private const val MIN_RELOAD_AFTER_RELEASE_MILLIS = 60L * 1000L
        const val FULL_SCREEN_SEPARATION_MILLIS = 90L * 1000L

        fun wasShownWithin(context: Context, intervalMillis: Long): Boolean {
            val lastShownAt = context.getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ).getLong(KEY_LAST_SHOWN_AT, 0L)
            return lastShownAt > 0L &&
                System.currentTimeMillis() - lastShownAt < intervalMillis
        }
    }
}

class AppOpenAdController(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private var appOpenAd: AppOpenAd? = null
    private var loading = false
    private var loadTime = 0L
    private var lastReleasedAt = 0L
    private var lastBackgroundedAt = 0L
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

    fun onAppBackgrounded() {
        lastBackgroundedAt = System.currentTimeMillis()
    }

    /**
     * App Open is reserved for genuine app returns, not cold launch and not
     * Android permission/settings/delete-consent returns. The Application
     * layer handles one-shot suppression for those internal flows.
     */
    fun onAppForeground(
        activity: Activity,
        onFinished: () -> Unit = {},
    ) {
        val now = System.currentTimeMillis()
        val backgroundedAt = lastBackgroundedAt
        lastBackgroundedAt = 0L

        if (
            !adsAllowed ||
            backgroundedAt <= 0L ||
            now - backgroundedAt < MIN_BACKGROUND_DURATION_MILLIS
        ) {
            load()
            onFinished()
            return
        }

        val eligibleReturnCount = preferences.getInt(KEY_ELIGIBLE_RETURN_COUNT, 0) + 1
        preferences.edit().putInt(KEY_ELIGIBLE_RETURN_COUNT, eligibleReturnCount).apply()
        if (eligibleReturnCount < MIN_ELIGIBLE_RETURNS_BEFORE_FIRST_AD) {
            load()
            onFinished()
            return
        }

        val lastShownAt = preferences.getLong(KEY_LAST_SHOWN_AT, 0L)
        if (lastShownAt > 0L && now - lastShownAt < MIN_SHOW_INTERVAL_MILLIS) {
            load()
            onFinished()
            return
        }

        if (
            InterstitialAdController.wasShownWithin(
                context,
                InterstitialAdController.FULL_SCREEN_SEPARATION_MILLIS,
            )
        ) {
            load()
            onFinished()
            return
        }

        showIfAvailable(activity, onFinished)
    }

    fun load() {
        if (!adsAllowed || loading || isAdAvailable()) return
        if (System.currentTimeMillis() - lastReleasedAt < MIN_RELOAD_AFTER_RELEASE_MILLIS) return
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
        lastReleasedAt = System.currentTimeMillis()
    }

    private fun showIfAvailable(
        activity: Activity,
        onFinished: () -> Unit,
    ) {
        if (isShowingAd || activity.isFinishing || activity.isDestroyed) {
            onFinished()
            return
        }

        val ad = appOpenAd
        if (ad == null || !isAdAvailable()) {
            appOpenAd = null
            load()
            onFinished()
            return
        }

        appOpenAd = null
        isShowingAd = true
        var completed = false

        fun finishShowing() {
            if (completed) return
            completed = true
            isShowingAd = false
            onFinished()
            load()
        }

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

        runCatching { ad.show(activity) }
            .onFailure { finishShowing() }
    }

    private fun isAdAvailable(): Boolean =
        appOpenAd != null && System.currentTimeMillis() - loadTime < APP_OPEN_EXPIRY_MILLIS

    companion object {
        private const val PREFERENCES_NAME = "app_open_ads"
        private const val KEY_ELIGIBLE_RETURN_COUNT = "eligible_return_count"
        private const val KEY_LAST_SHOWN_AT = "last_shown_at"

        // Conservative monetization: only genuine returns after a meaningful absence.
        private const val MIN_BACKGROUND_DURATION_MILLIS = 30L * 1000L
        private const val MIN_ELIGIBLE_RETURNS_BEFORE_FIRST_AD = 3
        private const val MIN_SHOW_INTERVAL_MILLIS = 60L * 60L * 1000L
        private const val APP_OPEN_EXPIRY_MILLIS = 4L * 60L * 60L * 1000L
        private const val MIN_RELOAD_AFTER_RELEASE_MILLIS = 60L * 1000L

        fun wasShownWithin(context: Context, intervalMillis: Long): Boolean {
            val lastShownAt = context.getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ).getLong(KEY_LAST_SHOWN_AT, 0L)
            return lastShownAt > 0L &&
                System.currentTimeMillis() - lastShownAt < intervalMillis
        }
    }
}
