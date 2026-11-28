package com.mrzekai.depoakilli.video

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mrzekai.depoakilli.MainActivity
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.watch.WatchNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Faz 3A-2: shrinking takes minutes, so it runs in a foreground service the
 * user can leave (Z6), with a progress notification that has a Stop button.
 * It starts only from the user's "Shrink" tap (never from the background, a
 * notification or the Watch). Type: mediaProcessing on Android 15+, dataSync
 * on Android 11–14; both may run 6 hours a day, and [onTimeout] stops cleanly.
 *
 * Stopping keeps the copies already finished, deletes only the unfinished
 * temp file and never touches an original.
 */
class VideoCompressionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var work: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRound(byUser = true)
                return START_NOT_STICKY
            }
        }
        val state = ShrinkJobs.state.value
        if (state.queue.isEmpty() || !goForeground(state)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (work?.isActive != true) work = scope.launch { runRound() }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15+: the 6 hours a day are used up; stop within seconds.
        stopRound(byUser = false)
    }

    override fun onDestroy() {
        scope.cancel()
        ShrinkJobs.update { if (it.running) it.copy(running = false) else it }
        super.onDestroy()
    }

    private fun stopRound(byUser: Boolean) {
        work?.cancel()
        ShrinkJobs.update { it.copy(running = false, stoppedByUser = byUser || it.stoppedByUser) }
        finishNotification()
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private suspend fun runRound() {
        ShrinkRound.run(this) { updateNotification() }
        ShrinkJobs.update { it.copy(running = false) }
        finishNotification()
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    // ---------------------------------------------------------- notification

    private fun goForeground(state: ShrinkJobState): Boolean = runCatching {
        ensureChannel(this)
        val notification = progressNotification(state)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        startForeground(NOTIFICATION_ID, notification, type)
        true
    }.getOrDefault(false)

    private var lastNotifiedAt = 0L

    private fun updateNotification() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotifiedAt < 1_000L) return
        lastNotifiedAt = now
        post(progressNotification(ShrinkJobs.state.value))
    }

    private fun progressNotification(state: ShrinkJobState) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_watch)
        .setColor(WatchNotifier.BRAND_COLOR)
        .setContentTitle(getString(R.string.video_notif_progress_title, state.index + 1, state.total.coerceAtLeast(1)))
        .setContentText(getString(R.string.video_notif_progress_body, ByteFormatter.format(state.savedSoFar)))
        .setProgress(100, (state.overallProgress * 100).toInt(), false)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(openApp())
        .addAction(0, getString(R.string.video_stop), stopIntent())
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .build()

    /** "7 videos shrunk · Watch and approve; the originals are still in place." */
    private fun finishNotification() {
        val state = ShrinkJobs.state.value
        val shrunk = state.results.count { it.outcome == ShrinkOutcome.SHRUNK }
        if (shrunk == 0) {
            runCatching { NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID) }
            return
        }
        post(
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_watch)
                .setColor(WatchNotifier.BRAND_COLOR)
                .setContentTitle(resources.getQuantityString(R.plurals.video_notif_done_title, shrunk, shrunk))
                .setContentText(getString(R.string.video_notif_done_body))
                .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.video_notif_done_body)))
                .setContentIntent(openApp())
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun post(notification: android.app.Notification) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        runCatching { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification) }
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        NOTIFICATION_ID,
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(WatchNotifier.EXTRA_TARGET, TARGET_SHRINK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun stopIntent(): PendingIntent = PendingIntent.getService(
        this,
        1,
        Intent(this, VideoCompressionService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val CHANNEL_ID = "video_shrink"
        private const val NOTIFICATION_ID = 7402
        private const val ACTION_STOP = "com.mrzekai.depoakilli.video.STOP"

        /** MainActivity opens the shrinking screens for this target. */
        const val TARGET_SHRINK = "SHRINK"

        fun ensureChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.video_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }

        /**
         * Starts a round. Called only from the user's tap on "Shrink N videos"
         * (or "Try one at no cost"); validator-checked.
         */
        fun startFromUserTap(context: Context, round: List<Candidate>, nextRound: List<Candidate>) {
            if (ShrinkJobs.state.value.running || round.isEmpty()) return
            ShrinkJobs.update { ShrinkJobState(queue = round, nextRound = nextRound, running = true) }
            if (!VideoRunMode.VIDEO_COMPRESSION_FGS) {
                // Switch off (e.g. Play's foreground-service declaration refused):
                // no service; the round runs only while the shrinking screen is open.
                InAppShrink.start(context)
                return
            }
            ContextCompat.startForegroundService(context, Intent(context, VideoCompressionService::class.java))
        }

        fun stop(context: Context) {
            if (!ShrinkJobs.state.value.running) return
            if (!VideoRunMode.VIDEO_COMPRESSION_FGS) {
                InAppShrink.stop(byUser = true)
                return
            }
            context.startService(Intent(context, VideoCompressionService::class.java).setAction(ACTION_STOP))
        }
    }
}
