package com.mrzekai.depoakilli

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.mrzekai.depoakilli.ads.AppOpenAdController
import com.mrzekai.depoakilli.ui.releasePremiumToolThumbnailMemory
import com.mrzekai.depoakilli.ui.releaseWhatsAppThumbnailMemory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DepoAkilliApplication :
    Application(),
    Application.ActivityLifecycleCallbacks {

    private lateinit var appOpenAds: AppOpenAdController
    private var currentActivity: MainActivity? = null
    private var externalLaunchPending = false
    private var suppressNextAppOpenAd = false
    private var criticalTaskActive = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _fullScreenAdSurfaceActive = MutableStateFlow(false)
    val fullScreenAdSurfaceActive: StateFlow<Boolean> = _fullScreenAdSurfaceActive.asStateFlow()

    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            if (criticalTaskActive) {
                appOpenAds.load()
                _fullScreenAdSurfaceActive.value = false
                return
            }

            if (suppressNextAppOpenAd) {
                suppressNextAppOpenAd = false
                appOpenAds.load()
                _fullScreenAdSurfaceActive.value = false
                return
            }

            val activity = currentActivity
            if (activity == null) {
                appOpenAds.load()
                _fullScreenAdSurfaceActive.value = false
                return
            }

            // Banner was hidden when the app moved to background. Keep it hidden
            // until App Open either finishes or decides not to show.
            appOpenAds.onAppForeground(activity) {
                _fullScreenAdSurfaceActive.value = false
            }
        }

        override fun onStop(owner: LifecycleOwner) {
            if (externalLaunchPending) {
                // The app actually left foreground after an app-initiated external flow.
                // Suppress exactly that return; ordinary Home/app-switch returns remain eligible.
                suppressNextAppOpenAd = true
                externalLaunchPending = false
            }
            appOpenAds.onAppBackgrounded()
            // Ensures a returning App Open never overlays an already-visible banner.
            _fullScreenAdSurfaceActive.value = true
        }
    }

    override fun onCreate() {
        super.onCreate()
        appOpenAds = AppOpenAdController(this)
        registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            releaseWhatsAppThumbnailMemory()
            releasePremiumToolThumbnailMemory()
        }
        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND &&
            ::appOpenAds.isInitialized &&
            isSystemUnderMemoryPressure()
        ) {
            appOpenAds.releaseForMemoryOptimization()
        }
    }

    fun isSystemUnderMemoryPressure(): Boolean {
        val manager = getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return info.lowMemory || (
            info.threshold > 0L &&
                info.availMem <= info.threshold * 3L / 2L
        )
    }

    fun setAppOpenAdsAllowed(allowed: Boolean) {
        appOpenAds.setAdsAllowed(allowed)
        if (!allowed) {
            _fullScreenAdSurfaceActive.value = false
        }
    }

    fun setCriticalTaskActive(active: Boolean) {
        criticalTaskActive = active
    }

    fun releaseAdMemory() {
        appOpenAds.releaseForMemoryOptimization()
    }

    fun suppressNextAppOpenAd() {
        // Arm suppression only for a real app-initiated external launch.
        // If the app never leaves foreground (for example an in-app dialog),
        // the arm expires instead of suppressing a later genuine return.
        externalLaunchPending = true
        mainHandler.postDelayed(
            {
                if (externalLaunchPending) {
                    externalLaunchPending = false
                }
            },
            EXTERNAL_LAUNCH_ARM_MILLIS,
        )
    }

    fun beginInterstitialSurface() {
        _fullScreenAdSurfaceActive.value = true
    }

    fun endInterstitialSurface() {
        _fullScreenAdSurfaceActive.value = false
    }

    override fun onActivityStarted(activity: Activity) {
        if (activity is MainActivity && !appOpenAds.isShowingAd) {
            currentActivity = activity
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) {
            currentActivity = null
        }
    }

    private companion object {
        const val EXTERNAL_LAUNCH_ARM_MILLIS = 3_000L
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        logAdActivityLifecycle("CREATED", activity)
    }

    override fun onActivityResumed(activity: Activity) {
        logAdActivityLifecycle("RESUMED", activity)
    }

    override fun onActivityPaused(activity: Activity) {
        logAdActivityLifecycle("PAUSED", activity)
    }

    override fun onActivityStopped(activity: Activity) {
        logAdActivityLifecycle("STOPPED", activity)
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private fun logAdActivityLifecycle(event: String, activity: Activity) {
        if (
            BuildConfig.DEBUG &&
            activity.javaClass.name.startsWith("com.google.android.gms.ads")
        ) {
            Log.i(
                "AdDiag",
                "AD_ACTIVITY/$event class=${activity.javaClass.name} " +
                    "finishing=${activity.isFinishing} taskRoot=${activity.isTaskRoot}",
            )
        }
    }
}
