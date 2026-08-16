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
    Application.ActivityLifecycleCallbacks,
    DefaultLifecycleObserver {

    private lateinit var appOpenAds: AppOpenAdController
    private var currentActivity: Activity? = null

    override fun onCreate() {
        super.onCreate()
        appOpenAds = AppOpenAdController(this)
        registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun setAppOpenAdsAllowed(allowed: Boolean) {
        appOpenAds.setAdsAllowed(allowed)
    }

    fun releaseAdMemory() {
        appOpenAds.releaseForMemoryOptimization()
    }

    override fun onStart(owner: LifecycleOwner) {
        currentActivity?.let(appOpenAds::onAppForeground)
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
