package com.mrzekai.depoakilli.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.mrzekai.depoakilli.BuildConfig
import com.mrzekai.depoakilli.diagnostics.AppDiagnostics
import java.util.concurrent.atomic.AtomicBoolean

/** A user-initiated rewarded placement; it never gates cleaning or scanning. */
class RewardedAdController(private val context: Context) {
    private var rewardedAd: RewardedAd? = null
    private var loading = false
    private var loadedAtElapsed = 0L
    private var adsAllowed = false
    private var resumedActivity: Activity? = null
    private var showing = false

    fun setAdsAllowed(allowed: Boolean) {
        adsAllowed = allowed
        if (allowed) load()
        else clear()
    }

    fun onHostResumed(activity: Activity) {
        resumedActivity = activity
        if (adsAllowed) load()
    }

    fun onHostPaused(activity: Activity) {
        if (resumedActivity === activity) resumedActivity = null
    }

    fun load() {
        if (!adsAllowed || loading || showing || isFresh()) return
        loading = true
        Log.i(AD_DIAG_TAG, "REWARDED/LOAD_REQUEST")
        RewardedAd.load(
            context,
            BuildConfig.ADMOB_REWARDED_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    loading = false
                    if (!adsAllowed || resumedActivity == null) return
                    rewardedAd = ad
                    loadedAtElapsed = SystemClock.elapsedRealtime()
                    logAd("LOAD_OK", ad)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    rewardedAd = null
                    loadedAtElapsed = 0L
                    Log.w(AD_DIAG_TAG, "REWARDED/LOAD_FAIL code=${error.code} domain=${error.domain} message=${error.message}")
                }
            },
        )
    }

    fun show(
        activity: Activity,
        onWillShow: () -> Unit,
        onUserEarnedReward: () -> Unit,
        onFinished: () -> Unit,
        onUnavailable: () -> Unit,
    ) {
        val host = resumedActivity
        val ad = rewardedAd
        if (!adsAllowed || showing || host !== activity || host.isFinishing || host.isDestroyed || ad == null || !isFresh()) {
            rewardedAd = null
            loadedAtElapsed = 0L
            Log.i(AD_DIAG_TAG, "REWARDED/SHOW_SKIP no-fresh-ad")
            load()
            onUnavailable()
            return
        }

        rewardedAd = null
        loadedAtElapsed = 0L
        showing = true
        val completed = AtomicBoolean(false)
        fun finish(reason: String) {
            if (!completed.compareAndSet(false, true)) return
            showing = false
            Log.i(AD_DIAG_TAG, "REWARDED/FLOW_FINISH via=$reason")
            onFinished()
            load()
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                logAd("SHOWED", ad)
                AppDiagnostics.breadcrumb("rewarded_show")
            }

            override fun onAdDismissedFullScreenContent() {
                logAd("DISMISSED", ad)
                finish("dismissed")
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(AD_DIAG_TAG, "REWARDED/FAIL_TO_SHOW code=${error.code} message=${error.message}")
                finish("failed-to-show")
            }
        }

        onWillShow()
        logAd("SHOW_REQUEST", ad)
        runCatching {
            ad.show(host) { reward ->
                Log.i(AD_DIAG_TAG, "REWARDED/EARNED_REWARD type=${reward.type} amount=${reward.amount}")
                AppDiagnostics.breadcrumb("rewarded_earned")
                onUserEarnedReward()
            }
        }.onFailure {
            Log.e(AD_DIAG_TAG, "REWARDED/SHOW_EXCEPTION", it)
            AppDiagnostics.captureException(it, "rewarded_show_exception")
            finish("show-exception")
        }
    }

    fun releaseCachedAd() = clear()

    private fun isFresh(): Boolean =
        rewardedAd != null &&
            loadedAtElapsed > 0L &&
            SystemClock.elapsedRealtime() - loadedAtElapsed < REWARDED_TTL_MILLIS

    private fun clear() {
        rewardedAd = null
        loading = false
        loadedAtElapsed = 0L
    }

    private fun logAd(stage: String, ad: RewardedAd) {
        val info = ad.responseInfo
        Log.i(AD_DIAG_TAG, "REWARDED/$stage responseId=${info?.responseId} mediation=${info?.mediationAdapterClassName}")
    }

    private companion object {
        const val AD_DIAG_TAG = "AdDiag"
        const val REWARDED_TTL_MILLIS = 50L * 60L * 1000L
    }
}
