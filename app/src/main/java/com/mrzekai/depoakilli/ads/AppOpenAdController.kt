package com.mrzekai.depoakilli.ads

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.mrzekai.depoakilli.BuildConfig
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App open ad (T1B.2), following AdMob's app-open guidance: shown only over
 * the app's own loading cover, before the user can use the content.
 *
 * - Cold start: from the first launch on (after consent), the cover waits at most
 *   [AdPolicy.APP_OPEN_MAX_WAIT_MS]; if no ad is ready by then nothing is shown
 *   and the content opens. Once the content is visible, no app open ad appears.
 * - Return from background: only after the user left the app himself
 *   (Home/Recents) for at least [MIN_BACKGROUND_MILLIS]. Returning from system
 *   screens the app opened (all-files access, usage access, cache dialog,
 *   uninstall, Play review, share/email) never shows one.
 * - Never with an empty unit id, without consent, inside the ad-free window,
 *   or while another full-screen ad is open; [AdPolicy] limits frequency.
 */
class AppOpenAdController(
    private val context: Context,
    private val frequency: AdFrequencyStore,
    private val adUnitId: String = BuildConfig.ADMOB_APP_OPEN_ID,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var ad: AppOpenAd? = null
    private var loadedAtElapsed = 0L
    private var loading = false
    private var showing = false
    private var adsAllowed = false
    private var coldStartPending = false
    private var leftByUserAtElapsed = 0L

    private val _coverVisible = MutableStateFlow(false)
    /** True while the loading cover is up (cold start wait or ad on screen). */
    val coverVisible: StateFlow<Boolean> = _coverVisible.asStateFlow()

    private val enabled: Boolean get() = adUnitId.isNotBlank()

    fun setAdsAllowed(allowed: Boolean) {
        adsAllowed = allowed && enabled
        if (adsAllowed) load() else ad = null
    }

    /**
     * Called once per cold start before the content is used. Shows the cover
     * only when an app open ad may be shown at all.
     */
    fun onColdStart(activity: Activity, adsAllowedNow: Boolean, otherFullScreenActive: () -> Boolean) {
        adsAllowed = adsAllowedNow && enabled
        if (!adsAllowed || !AdPolicy.canShowAppOpen(frequency.load(), System.currentTimeMillis())) return
        // Offline: no ad can arrive, so the content opens without any wait.
        if (!isOnline()) return
        coldStartPending = true
        _coverVisible.value = true
        load()
        val deadline = SystemClock.elapsedRealtime() + AdPolicy.APP_OPEN_MAX_WAIT_MS
        fun poll() {
            if (!coldStartPending) return
            when {
                isFresh() && !otherFullScreenActive() -> {
                    coldStartPending = false
                    show(activity)
                }
                SystemClock.elapsedRealtime() >= deadline || activity.isFinishing -> {
                    coldStartPending = false
                    _coverVisible.value = false
                }
                else -> handler.postDelayed(::poll, POLL_MILLIS)
            }
        }
        poll()
    }

    private var appStartedScreen = false

    /**
     * The app is about to open another screen itself (settings, permissions,
     * cache dialog, uninstall, Play review, share, an ad). Android may still
     * report a "user leave hint" for it, so the next return never shows an ad.
     */
    fun onAppStartingScreen() {
        appStartedScreen = true
        leftByUserAtElapsed = 0L
    }

    /** The user pressed Home or opened Recents. Ignored after the app opened a screen itself. */
    fun onUserLeftApp() {
        if (appStartedScreen) return
        leftByUserAtElapsed = SystemClock.elapsedRealtime()
    }

    /** Returning to the app; shows a preloaded ad only after a real user-initiated absence. */
    fun onReturnedToForeground(activity: Activity, otherFullScreenActive: Boolean, blocked: Boolean) {
        val leftAt = leftByUserAtElapsed
        leftByUserAtElapsed = 0L
        val fromAppScreen = appStartedScreen
        appStartedScreen = false
        if (fromAppScreen) return
        if (leftAt == 0L || coldStartPending || showing) return
        if (SystemClock.elapsedRealtime() - leftAt < MIN_BACKGROUND_MILLIS) return
        if (!adsAllowed || otherFullScreenActive || blocked) return
        if (!AdPolicy.canShowAppOpen(frequency.load(), System.currentTimeMillis())) return
        if (!isFresh()) {
            load()
            return
        }
        _coverVisible.value = true
        show(activity)
    }

    /** Ends the cold-start wait early (e.g. the content must take over). */
    fun cancelColdStart() {
        if (!coldStartPending) return
        coldStartPending = false
        _coverVisible.value = false
    }

    private fun isOnline(): Boolean = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(true)

    private fun load() {
        if (!adsAllowed || loading || isFresh()) return
        loading = true
        AppOpenAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(loaded: AppOpenAd) {
                    loading = false
                    ad = loaded
                    loadedAtElapsed = SystemClock.elapsedRealtime()
                    Log.i(TAG, "APP_OPEN/LOAD_OK")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    ad = null
                    Log.w(TAG, "APP_OPEN/LOAD_FAIL code=${error.code}")
                }
            },
        )
    }

    private fun isFresh(): Boolean =
        ad != null && SystemClock.elapsedRealtime() - loadedAtElapsed < MAX_AD_AGE_MILLIS

    private fun show(activity: Activity) {
        val current = ad
        if (current == null || activity.isFinishing || activity.isDestroyed) {
            _coverVisible.value = false
            return
        }
        ad = null
        showing = true
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                frequency.update { AdPolicy.recordAppOpen(it, System.currentTimeMillis()) }
            }

            override fun onAdDismissedFullScreenContent() = finish()

            override fun onAdFailedToShowFullScreenContent(error: AdError) = finish()
        }
        runCatching { current.show(activity) }.onFailure { finish() }
    }

    private fun finish() {
        showing = false
        _coverVisible.value = false
        load()
    }

    private companion object {
        const val TAG = "AdDiag"
        const val POLL_MILLIS = 100L
        val MIN_BACKGROUND_MILLIS = TimeUnit.SECONDS.toMillis(30)
        val MAX_AD_AGE_MILLIS = TimeUnit.HOURS.toMillis(4)
    }
}
