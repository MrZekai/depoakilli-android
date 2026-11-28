package com.mrzekai.depoakilli

import android.app.Application
import android.content.ComponentCallbacks2
import com.mrzekai.depoakilli.billing.BillingRepository
import com.mrzekai.depoakilli.data.TrashPurgeWorker
import com.mrzekai.depoakilli.diagnostics.AppDiagnostics
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.pro.FreeTierStore
import com.mrzekai.depoakilli.ui.media.MediaThumbnails
import com.mrzekai.depoakilli.video.PendingOutputStore
import com.mrzekai.depoakilli.video.VideoShrinkRepository
import com.mrzekai.depoakilli.watch.WatchScheduler
import com.mrzekai.depoakilli.watch.WatchStore

class DepoAkilliApplication : Application() {
    /** Google Play Billing and the Pro entitlement; one client for the whole app. */
    val billing: BillingRepository by lazy { BillingRepository(this) }

    /** Free tier memory (Faz 2): local to this device. */
    val freeTier: FreeTierStore by lazy { FreeTierStore(this) }

    /** Faz 3A-1 Storage Watch: settings, daily samples and sent notices, on this device only. */
    val watch: WatchStore by lazy { WatchStore(this) }

    /** Faz 3A-2: shrunk copies waiting for the user's approval (7 days), on this device only. */
    val pendingVideos: PendingOutputStore by lazy { PendingOutputStore(this) }

    /** Faz 3A-2: big videos worth shrinking, and the user's answers for each copy. */
    val videoShrink: VideoShrinkRepository by lazy { VideoShrinkRepository(this, pendingVideos) }

    override fun onCreate() {
        super.onCreate()
        ByteFormatter.install(this)
        AppDiagnostics.initialize(this)
        TrashPurgeWorker.schedule(this)
        // Keeps the existing daily job; it is only ever enqueued after the user turned the watch on.
        if (watch.state.value.settings.enabled) WatchScheduler.schedule(this, restart = false)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            MediaThumbnails.release()
        }
    }
}
