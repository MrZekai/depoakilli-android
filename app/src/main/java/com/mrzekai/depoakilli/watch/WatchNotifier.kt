package com.mrzekai.depoakilli.watch

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mrzekai.depoakilli.MainActivity
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import java.text.NumberFormat

/**
 * The thin Android layer of the watch notices. What to send is decided in
 * [WatchRules]; here it is only written and posted. A notice never sells Pro,
 * never announces a discount and never opens a purchase screen: a tap opens the
 * related screen, where the user starts anything themselves.
 */
object WatchNotifier {
    const val CHANNEL_STORAGE = "watch_storage"
    const val CHANNEL_WEEKLY = "watch_weekly"
    const val CHANNEL_GROWTH = "watch_growth"

    /** Extra on the launch intent: which screen a notice opens. */
    const val EXTRA_TARGET = "watch_target"
    const val TARGET_WATCH = "watch"
    const val TARGET_WHATSAPP = "whatsapp"
    const val TARGET_VIDEOS = "videos"
    const val TARGET_SIMILAR = "similar"

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_STORAGE, context.getString(R.string.watch_channel_storage), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_WEEKLY, context.getString(R.string.watch_channel_weekly), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_GROWTH, context.getString(R.string.watch_channel_growth), NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    /** Posts the notice; false when Android does not let the app post. */
    fun post(context: Context, topic: WatchTopic, sample: WatchSample, growth: WatchGrowth?): Boolean {
        if (!canPost(context)) return false
        ensureChannels(context)
        val text = texts(context, topic, sample, growth) ?: return false
        val channel = when (topic) {
            WatchTopic.THRESHOLD_90, WatchTopic.THRESHOLD_95 -> CHANNEL_STORAGE
            WatchTopic.WEEKLY -> CHANNEL_WEEKLY
            WatchTopic.WHATSAPP, WatchTopic.NEW_VIDEOS, WatchTopic.NEW_SIMILAR -> CHANNEL_GROWTH
        }
        val target = when (topic) {
            WatchTopic.WHATSAPP -> TARGET_WHATSAPP
            WatchTopic.NEW_VIDEOS -> TARGET_VIDEOS
            WatchTopic.NEW_SIMILAR -> TARGET_SIMILAR
            else -> TARGET_WATCH
        }
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_TARGET, target)
        val pending = PendingIntent.getActivity(
            context,
            topic.ordinal,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_watch)
            .setColor(BRAND_COLOR)
            .setContentTitle(text.first)
            .setContentText(text.second)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.second))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        return runCatching {
            @Suppress("MissingPermission") // checked in canPost()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BASE + topic.ordinal, notification)
            true
        }.getOrDefault(false)
    }

    /** Title and body; every number is measured (only the video gain is "about"). */
    fun texts(context: Context, topic: WatchTopic, sample: WatchSample, growth: WatchGrowth?): Pair<String, String>? {
        val locale = context.resources.configuration.locales[0]
        return when (topic) {
            WatchTopic.THRESHOLD_90, WatchTopic.THRESHOLD_95 -> {
                val percent = sample.usedPercent ?: return null
                context.getString(R.string.watch_notif_full_title, NumberFormat.getPercentInstance(locale).format(percent / 100.0)) to
                    context.getString(R.string.watch_notif_full_body, ByteFormatter.format(sample.availableBytes))
            }
            WatchTopic.WEEKLY -> {
                val g = growth ?: return null
                val top = g.topKind ?: return null
                context.getString(R.string.watch_notif_weekly_title, ByteFormatter.format(g.totalBytes)) to
                    context.getString(R.string.watch_notif_weekly_body, context.getString(kindName(top)), ByteFormatter.format(g.bytes(top)))
            }
            WatchTopic.WHATSAPP -> {
                val g = growth ?: return null
                context.getString(R.string.watch_notif_whatsapp_title, ByteFormatter.format(g.bytes(WatchKind.WHATSAPP))) to
                    context.getString(R.string.watch_notif_whatsapp_body)
            }
            WatchTopic.NEW_VIDEOS -> {
                if (sample.bigVideoCandidates <= 0) return null
                context.resources.getQuantityString(R.plurals.watch_notif_videos_title, sample.bigVideoCandidates, sample.bigVideoCandidates) to
                    context.getString(R.string.watch_notif_videos_body, ByteFormatter.format(sample.bigVideoCandidateBytes))
            }
            WatchTopic.NEW_SIMILAR -> {
                if (sample.newSimilarFrames <= 0) return null
                context.resources.getQuantityString(R.plurals.watch_notif_similar_title, sample.newSimilarFrames, sample.newSimilarFrames) to
                    context.getString(R.string.watch_notif_similar_body, ByteFormatter.format(sample.newSimilarBytes))
            }
        }
    }

    /** Deep brand green behind the small icon (Fable review). */
    const val BRAND_COLOR = 0xFF0B4A25.toInt()

    fun kindName(kind: WatchKind): Int = when (kind) {
        WatchKind.VIDEOS -> R.string.watch_kind_videos
        WatchKind.WHATSAPP -> R.string.watch_kind_whatsapp
        WatchKind.PHOTOS -> R.string.watch_kind_photos
        WatchKind.OTHER -> R.string.watch_kind_other
    }

    /** Android's own notification settings for this app (after a "no", the app never asks again). */
    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** App info (battery / background) for "The watch couldn't look for N days". */
    fun appInfoIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private const val NOTIFICATION_ID_BASE = 4100
}
