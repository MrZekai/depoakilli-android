package com.mrzekai.depoakilli.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.mrzekai.depoakilli.BuildConfig
import com.mrzekai.depoakilli.diagnostics.AppDiagnostics
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun BannerAd(
    canRequestAds: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val widthDp = configuration.screenWidthDp.coerceAtLeast(320)
    val adSize = remember(context, widthDp) {
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
    }

    Box(
        modifier = modifier.fillMaxWidth().height(adSize.height.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (canRequestAds) {
            val adView = remember(context, BuildConfig.ADMOB_BANNER_ID, widthDp) {
                AdView(context).apply {
                    adUnitId = BuildConfig.ADMOB_BANNER_ID
                    setAdSize(adSize)
                    loadAd(AdRequest.Builder().build())
                }
            }
            DisposableEffect(adView, lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> adView.resume()
                        Lifecycle.Event.ON_PAUSE -> adView.pause()
                        Lifecycle.Event.ON_DESTROY -> adView.destroy()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    adView.resume()
                }
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    adView.destroy()
                }
            }
            AndroidView(
                factory = { adView },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

class InterstitialAdController(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var interstitial: InterstitialAd? = null
    private var loading = false
    private var loadedAtElapsed = 0L
    private var lastShownAt = preferences.getLong(KEY_LAST_SHOWN_AT, 0L)
    private var lastReleasedAt = 0L
    private var adsAllowed = false
    private var isShowing = false
    private var shownThisProcess = false

    private var resumedActivity: Activity? = null
    private var hostResumedAtElapsed = 0L
    private var loadGeneration = 0L

    fun setAdsAllowed(allowed: Boolean) {
        adsAllowed = allowed
        if (allowed && !shownThisProcess) {
            scheduleStableLoad("ads-allowed")
        } else if (!allowed) {
            interstitial = null
            loading = false
            loadedAtElapsed = 0L
            loadGeneration++
        }
    }

    fun onHostResumed(activity: Activity) {
        resumedActivity = activity
        hostResumedAtElapsed = SystemClock.elapsedRealtime()
        if (!shownThisProcess) {
            scheduleStableLoad("host-resumed")
        }
    }

    fun onHostPaused(activity: Activity) {
        if (resumedActivity === activity) {
            resumedActivity = null
            loadGeneration++
        }
    }

    fun load() {
        scheduleStableLoad("explicit")
    }

    private fun scheduleStableLoad(reason: String) {
        val generation = ++loadGeneration
        mainHandler.postDelayed(
            {
                if (generation != loadGeneration || !adsAllowed || isShowing || shownThisProcess) return@postDelayed
                val host = resumedActivity
                if (host == null || host.isFinishing || host.isDestroyed) return@postDelayed

                val stableFor = SystemClock.elapsedRealtime() - hostResumedAtElapsed
                if (stableFor < HOST_STABLE_DELAY_MILLIS) {
                    scheduleStableLoad(reason)
                    return@postDelayed
                }
                loadNow(reason)
            },
            HOST_STABLE_DELAY_MILLIS,
        )
    }

    private fun loadNow(reason: String) {
        if (!adsAllowed || loading || isShowing || shownThisProcess || isFresh()) return
        if (System.currentTimeMillis() - lastReleasedAt < MIN_RELOAD_AFTER_RELEASE_MILLIS) return

        interstitial = null
        loadedAtElapsed = 0L
        loading = true
        Log.i(AD_DIAG_TAG, "INTERSTITIAL/LOAD_REQUEST reason=$reason")

        InterstitialAd.load(
            context,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loading = false
                    if (!adsAllowed || resumedActivity == null) {
                        interstitial = null
                        loadedAtElapsed = 0L
                        return
                    }
                    interstitial = ad
                    loadedAtElapsed = SystemClock.elapsedRealtime()
                    logAd("LOAD_OK", ad)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    interstitial = null
                    loadedAtElapsed = 0L
                    Log.w(
                        AD_DIAG_TAG,
                        "INTERSTITIAL/LOAD_FAIL code=${error.code} domain=${error.domain} message=${error.message}",
                    )
                }
            },
        )
    }

    private fun isFresh(): Boolean =
        interstitial != null &&
            loadedAtElapsed > 0L &&
            SystemClock.elapsedRealtime() - loadedAtElapsed < INTERSTITIAL_TTL_MILLIS

    fun showAtNaturalBreak(
        activity: Activity,
        onWillShow: () -> Unit = {},
        onFinished: () -> Unit = {},
    ) {
        if (shownThisProcess) {
            Log.i(AD_DIAG_TAG, "INTERSTITIAL/SHOW_SKIP session-cap")
            onFinished()
            return
        }

        val host = resumedActivity
        if (
            !adsAllowed ||
            isShowing ||
            host == null ||
            host !== activity ||
            host.isFinishing ||
            host.isDestroyed
        ) {
            Log.i(AD_DIAG_TAG, "INTERSTITIAL/SHOW_SKIP host-not-resumed")
            onFinished()
            scheduleStableLoad("show-skip-host")
            return
        }

        val now = System.currentTimeMillis()
        val ad = interstitial
        if (ad == null || !isFresh()) {
            interstitial = null
            loadedAtElapsed = 0L
            Log.i(AD_DIAG_TAG, "INTERSTITIAL/SHOW_SKIP no-fresh-ad")
            onFinished()
            scheduleStableLoad("show-skip-no-ad")
            return
        }
        if (now - lastShownAt < MIN_INTERVAL_MILLIS) {
            Log.i(AD_DIAG_TAG, "INTERSTITIAL/SHOW_SKIP cooldown")
            onFinished()
            return
        }
        interstitial = null
        loadedAtElapsed = 0L
        isShowing = true
        loadGeneration++

        val completed = AtomicBoolean(false)
        fun finishFlow(reason: String) {
            if (!completed.compareAndSet(false, true)) return
            isShowing = false
            Log.i(AD_DIAG_TAG, "INTERSTITIAL/FLOW_FINISH via=$reason")
            AppDiagnostics.breadcrumb("interstitial_finish", mapOf("via" to reason))
            onFinished()
            if (!shownThisProcess) {
                scheduleStableLoad("after-$reason")
            }
        }

        // Let Google AdActivity own its default system UI/insets.
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                shownThisProcess = true
                loadGeneration++
                lastShownAt = System.currentTimeMillis()
                preferences.edit().putLong(KEY_LAST_SHOWN_AT, lastShownAt).apply()
                logAd("SHOWED", ad)
                AppDiagnostics.breadcrumb("interstitial_show")
            }

            override fun onAdImpression() {
                logAd("IMPRESSION", ad)
            }

            override fun onAdDismissedFullScreenContent() {
                logAd("DISMISSED", ad)
                finishFlow("dismissed")
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(
                    AD_DIAG_TAG,
                    "INTERSTITIAL/FAIL_TO_SHOW code=${adError.code} message=${adError.message}",
                )
                finishFlow("failed-to-show")
            }
        }

        onWillShow()
        logAd("SHOW_REQUEST", ad)
        runCatching { ad.show(host) }
            .onFailure {
                Log.e(AD_DIAG_TAG, "INTERSTITIAL/SHOW_EXCEPTION", it)
                AppDiagnostics.captureException(it, "interstitial_show_exception")
                finishFlow("show-exception")
            }
    }

    private fun logAd(stage: String, ad: InterstitialAd) {
        val info = ad.responseInfo
        Log.i(
            AD_DIAG_TAG,
            "INTERSTITIAL/$stage responseId=${info?.responseId} " +
                "mediation=${info?.mediationAdapterClassName} " +
                "host=${resumedActivity?.javaClass?.simpleName}",
        )
    }

    fun releaseCachedAd() {
        interstitial = null
        loading = false
        loadedAtElapsed = 0L
        loadGeneration++
        lastReleasedAt = System.currentTimeMillis()
    }

    companion object {
        private const val PREFERENCES_NAME = "interstitial_ads"
        private const val KEY_LAST_SHOWN_AT = "last_shown_at"
        private const val AD_DIAG_TAG = "AdDiag"
        private const val MIN_INTERVAL_MILLIS = 5L * 60L * 1000L
        private const val MIN_RELOAD_AFTER_RELEASE_MILLIS = 60L * 1000L
        private const val HOST_STABLE_DELAY_MILLIS = 1_200L
        private const val INTERSTITIAL_TTL_MILLIS = 50L * 60L * 1000L

    }
}
