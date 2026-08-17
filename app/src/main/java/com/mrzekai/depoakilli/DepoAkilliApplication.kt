package com.mrzekai.depoakilli

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.mrzekai.depoakilli.ads.AppOpenAdController

class DepoAkilliApplication :
    Application(),
    Application.ActivityLifecycleCallbacks {

    private lateinit var appOpenAds: AppOpenAdController
    private var currentActivity: Activity? = null
    private var suppressNextAppOpenAd = false
    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            if (suppressNextAppOpenAd) {
                suppressNextAppOpenAd = false
                appOpenAds.load()
                return
            }
            currentActivity?.let(appOpenAds::onAppForeground)
        }
    }

    override fun onCreate() {
        super.onCreate()
        appOpenAds = AppOpenAdController(this)
        registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
    }

    fun setAppOpenAdsAllowed(allowed: Boolean) {
        appOpenAds.setAdsAllowed(allowed)
    }

    fun releaseAdMemory() {
        appOpenAds.releaseForMemoryOptimization()
    }

    fun suppressNextAppOpenAd() {
        suppressNextAppOpenAd = true
    }

    override fun onActivityStarted(activity: Activity) {
        if (!appOpenAds.isShowingAd) {
            currentActivity = activity
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) {
            currentActivity = null
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
