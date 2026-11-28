package com.mrzekai.depoakilli.billing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mrzekai.depoakilli.DepoAkilliApplication
import com.mrzekai.depoakilli.MainActivity
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.pro.SalesRules
import com.mrzekai.depoakilli.watch.WatchNotifier
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sales S3: one local reminder a day before a trial ends ("cancel in Google
 * Play > Subscriptions if you don't want to continue"). No sale, no price, its
 * own "account" channel, outside the watch's daily limit. Skipped when the
 * subscription no longer renews.
 */
object TrialReminder {
    const val CHANNEL_ACCOUNT = "account"
    private const val WORK = "trial_reminder"
    private const val NOTIFICATION_ID = 7501

    fun schedule(context: Context, trialEndMillis: Long) {
        val delay = (SalesRules.reminderAt(trialEndMillis) - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<TrialReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun post(context: Context): Boolean {
        if (!WatchNotifier.canPost(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ACCOUNT, context.getString(R.string.trial_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, NOTIFICATION_ID, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL_ACCOUNT)
            .setSmallIcon(R.drawable.ic_stat_watch)
            .setContentTitle(context.getString(R.string.trial_reminder_title))
            .setContentText(context.getString(R.string.trial_reminder_body))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.trial_reminder_body)))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        return runCatching {
            @Suppress("MissingPermission") // checked in canPost()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }
}

class TrialReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? DepoAkilliApplication ?: return Result.success()
        val end = app.billing.trialEndMillis ?: return Result.success()
        app.billing.refreshPurchases()
        withTimeoutOrNull(8_000L) { app.billing.state.drop(1).first { !it.loading } }
        if (SalesRules.sendReminder(System.currentTimeMillis(), end, app.billing.autoRenewing)) {
            TrialReminder.post(applicationContext)
        }
        return Result.success()
    }
}
