package com.mrzekai.depoakilli.watch

import android.content.Context
import com.mrzekai.depoakilli.BuildConfig
import com.mrzekai.depoakilli.DepoAkilliApplication

/**
 * Debug builds only: Device QA and the store-shots job turn the watch on with
 * a week of history and post each notice once, so the screens and notices
 * can be checked in every language (an emulator has no real week of data).
 * Release builds never run any of this.
 */
object WatchDemo {
    fun apply(context: Context, mode: String?) {
        if (!BuildConfig.DEBUG || mode.isNullOrBlank()) return
        val app = context.applicationContext as? DepoAkilliApplication ?: return
        val now = WatchMeasurer(context).measure()
        when (mode) {
            "seed" -> {
                val base = now.copy(
                    dayEpoch = now.dayEpoch - 7,
                    measuredAtMillis = now.measuredAtMillis - 7 * 86_400_000L,
                    availableBytes = now.availableBytes + 3_400_000_000L,
                    videoBytes = (now.videoBytes - 2_100_000_000L).coerceAtLeast(0L),
                    whatsAppBytes = (now.whatsAppBytes - 900_000_000L).coerceAtLeast(0L),
                    photoBytes = (now.photoBytes - 400_000_000L).coerceAtLeast(0L),
                )
                app.watch.update {
                    it.copy(
                        settings = it.settings.copy(enabled = true),
                        samples = WatchRules.add(WatchRules.add(emptyList(), base), now),
                        notificationAsked = true,
                        hibernationExplained = true,
                    )
                }
            }
            // Every notice once (a forced restart would clear earlier ones).
            "notices" -> listOf(WatchTopic.WEEKLY, WatchTopic.WHATSAPP, WatchTopic.THRESHOLD_90).forEach { apply(context, it.name) }
            else -> {
                val topic = runCatching { WatchTopic.valueOf(mode) }.getOrNull() ?: return
                val demo = now.copy(
                    availableBytes = now.totalBytes / 10,
                    bigVideoCandidates = 4,
                    bigVideoCandidateBytes = 1_800_000_000L,
                )
                val growth = WatchGrowth(
                    days = 7,
                    totalBytes = 3_400_000_000L,
                    byKind = mapOf(
                        WatchKind.VIDEOS to 2_100_000_000L,
                        WatchKind.WHATSAPP to if (topic == WatchTopic.WHATSAPP) 1_300_000_000L else 900_000_000L,
                        WatchKind.PHOTOS to 400_000_000L,
                        WatchKind.OTHER to if (topic == WatchTopic.WHATSAPP) -400_000_000L else 0L,
                    ),
                )
                WatchNotifier.post(context, topic, demo, growth)
            }
        }
    }
}
