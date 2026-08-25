package com.mrzekai.depoakilli

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import com.mrzekai.depoakilli.diagnostics.AppDiagnostics
import com.mrzekai.depoakilli.ui.releasePremiumToolThumbnailMemory
import com.mrzekai.depoakilli.ui.releaseWhatsAppThumbnailMemory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DepoAkilliApplication : Application() {
    private val _fullScreenAdSurfaceActive = MutableStateFlow(false)
    val fullScreenAdSurfaceActive: StateFlow<Boolean> = _fullScreenAdSurfaceActive.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        AppDiagnostics.initialize(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            releaseWhatsAppThumbnailMemory()
            releasePremiumToolThumbnailMemory()
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

    fun beginInterstitialSurface() {
        _fullScreenAdSurfaceActive.value = true
    }

    fun endInterstitialSurface() {
        _fullScreenAdSurfaceActive.value = false
    }
}
