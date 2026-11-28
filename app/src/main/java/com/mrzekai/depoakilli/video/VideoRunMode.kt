package com.mrzekai.depoakilli.video

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Faz 3A-2 (task 2.4): how a shrinking round runs. */
object VideoRunMode {
    /**
     * true: a foreground service the user can leave (needs Play's foreground
     * service declaration). false: no service ever starts; shrinking runs only
     * while the shrinking screen is open and stops when the app goes to the
     * background (finished copies are kept). Turn off if the declaration is
     * refused; then also remove the video service from the manifest and mark
     * android.permission.FOREGROUND_SERVICE tools:node="remove" (a library adds
     * it); validator- and build-checked.
     */
    const val VIDEO_COMPRESSION_FGS = true

    enum class Runner { FOREGROUND_SERVICE, IN_APP }

    fun runner(fgs: Boolean): Runner = if (fgs) Runner.FOREGROUND_SERVICE else Runner.IN_APP

    /** In-app mode: leaving the screen (app in the background) stops the round. */
    fun stopsOnLeave(fgs: Boolean): Boolean = runner(fgs) == Runner.IN_APP
}

/** The round without a service ([VideoRunMode.VIDEO_COMPRESSION_FGS] off). */
internal object InAppShrink {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var work: Job? = null

    fun start(context: Context) {
        if (work?.isActive == true) return
        val app = context.applicationContext
        work = scope.launch {
            ShrinkRound.run(app) {}
            ShrinkJobs.update { it.copy(running = false) }
        }
    }

    /** Keeps the copies already finished; the unfinished temp file is removed by the compressor. */
    fun stop(byUser: Boolean) {
        work?.cancel()
        ShrinkJobs.update { it.copy(running = false, stoppedByUser = byUser || it.stoppedByUser) }
    }
}
