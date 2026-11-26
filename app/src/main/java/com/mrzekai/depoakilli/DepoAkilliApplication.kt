package com.mrzekai.depoakilli

import android.app.Application
import android.content.ComponentCallbacks2
import com.mrzekai.depoakilli.billing.BillingRepository
import com.mrzekai.depoakilli.data.TrashPurgeWorker
import com.mrzekai.depoakilli.diagnostics.AppDiagnostics
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.pro.FreeTierStore
import com.mrzekai.depoakilli.ui.media.MediaThumbnails

class DepoAkilliApplication : Application() {
    /** Google Play Billing and the Pro entitlement; one client for the whole app. */
    val billing: BillingRepository by lazy { BillingRepository(this) }

    /** Free tier memory (Faz 2): local to this device. */
    val freeTier: FreeTierStore by lazy { FreeTierStore(this) }

    override fun onCreate() {
        super.onCreate()
        ByteFormatter.install(this)
        AppDiagnostics.initialize(this)
        TrashPurgeWorker.schedule(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            MediaThumbnails.release()
        }
    }
}
