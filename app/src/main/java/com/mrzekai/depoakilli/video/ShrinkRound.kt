package com.mrzekai.depoakilli.video

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.mrzekai.depoakilli.DepoAkilliApplication

/**
 * One shrinking round over [ShrinkJobs]' queue, shared by the foreground
 * service and the in-app runner ([VideoRunMode.VIDEO_COMPRESSION_FGS] off).
 * Originals are never touched; each finished copy waits for the user's
 * approval in [DepoAkilliApplication.pendingVideos].
 */
internal object ShrinkRound {
    private const val TAG = "SmartCleanerVideo"

    suspend fun run(context: Context, onTick: () -> Unit) {
        val app = context.applicationContext as DepoAkilliApplication
        val compressor = VideoCompressor(app)
        val queue = ShrinkJobs.state.value.queue
        ShrinkJobs.update { it.copy(running = true, index = 0, progress = 0, results = emptyList(), stoppedByUser = false, blocked = null) }
        var encodeMs = 0L
        var videoMs = 0L
        for ((index, candidate) in queue.withIndex()) {
            val block = deviceBlock(app)
            if (block != null) {
                ShrinkJobs.update { it.copy(blocked = block) }
                break
            }
            ShrinkJobs.update { it.copy(index = index, progress = 0) }
            onTick()
            val started = SystemClock.elapsedRealtime()
            val outcome = compressor.compress(candidate, candidate.hevc) { progress ->
                ShrinkJobs.update { it.copy(progress = progress) }
                onTick()
            }
            val result = when (outcome) {
                is VideoCompressor.Outcome.Shrunk -> {
                    app.pendingVideos.add(
                        PendingOutput(
                            originalPath = candidate.meta.path,
                            outputPath = outcome.output.absolutePath,
                            createdMillis = System.currentTimeMillis(),
                            originalBytes = candidate.meta.sizeBytes,
                            outputBytes = outcome.outputBytes,
                            estimatedSavingBytes = candidate.estimatedSavingBytes,
                        ),
                    )
                    val real = candidate.meta.sizeBytes - outcome.outputBytes
                    Log.w(TAG, "shrunk ${candidate.meta.sizeBytes}→${outcome.outputBytes} real/estimate=${PendingRules.accuracy(real, candidate.estimatedSavingBytes)}")
                    ShrinkResult(candidate, ShrinkOutcome.SHRUNK, outcome.outputBytes)
                }
                is VideoCompressor.Outcome.Failed -> {
                    Log.w(TAG, "skipped: ${outcome.reason}")
                    ShrinkResult(
                        candidate,
                        if (outcome.reason == VerifyFailure.NOT_SMALLER) ShrinkOutcome.TOO_LITTLE_GAIN else ShrinkOutcome.COULD_NOT,
                    )
                }
                VideoCompressor.Outcome.Cancelled -> break
            }
            encodeMs += SystemClock.elapsedRealtime() - started
            videoMs += candidate.meta.durationMs
            val factor = if (videoMs > 0L) encodeMs.toDouble() / videoMs else null
            ShrinkJobs.update { it.copy(results = it.results + result, progress = 100, speedFactor = factor) }
        }
    }

    /** Low battery (below 20 % and not charging) or a hot phone stops before the next video. */
    private fun deviceBlock(context: Context): DeviceBlock? {
        val battery = context.getSystemService(BatteryManager::class.java)
        val percent = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        val charging = battery?.isCharging == true
        val thermal = context.getSystemService(PowerManager::class.java)?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
        return VideoQueue.block(percent, charging, thermal >= PowerManager.THERMAL_STATUS_SEVERE)
    }
}
