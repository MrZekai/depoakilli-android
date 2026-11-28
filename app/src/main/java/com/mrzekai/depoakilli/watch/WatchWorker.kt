package com.mrzekai.depoakilli.watch

import com.mrzekai.depoakilli.video.VideoCandidateFinder
import com.mrzekai.depoakilli.data.CleaningThresholds
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mrzekai.depoakilli.DepoAkilliApplication
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.temporal.IsoFields
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Faz 3A-1 Storage Watch. Once a day it only MEASURES: no scan, no move, no
 * delete. No foreground service; past 30 s it stops and reports success.
 * A notice held back by quiet hours is re-checked by a one-time job the next
 * morning and sent only if it still holds.
 */
class WatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        withTimeoutOrNull(RUN_LIMIT_MILLIS) { check(inputData.getString(KEY_DEFERRED)) }
        return Result.success()
    }

    private suspend fun check(deferred: String?) {
        val app = applicationContext as? DepoAkilliApplication ?: return
        val store = app.watch
        if (!store.state.value.settings.enabled) {
            WatchScheduler.cancel(applicationContext)
            return
        }
        val measured = WatchMeasurer(applicationContext).measure()
        val isPro = currentPro(app)
        // Faz 3A-2: new big videos worth shrinking since the last such notice.
        // Headers only (cached); no frame is decoded and nothing is changed.
        var videoPaths: Set<String> = emptySet()
        val sample = if (CleaningThresholds.WATCH_NEW_VIDEO_ENABLED && isPro &&
            store.state.value.settings.newVideoAlerts && store.state.value.settings.enabled
        ) {
            val deadline = System.currentTimeMillis() + VIDEO_LOOK_MILLIS
            val found = runCatching {
                VideoCandidateFinder(applicationContext).find(
                    pendingOriginals = app.pendingVideos.originals(),
                    shouldStop = { System.currentTimeMillis() > deadline },
                )
            }.getOrNull()
            val notified = store.state.value.notifiedVideos
            val fresh = found?.candidates.orEmpty().filter { it.meta.path !in notified }
            videoPaths = found?.candidates.orEmpty().mapTo(HashSet()) { it.meta.path }
            measured.copy(bigVideoCandidates = fresh.size, bigVideoCandidateBytes = fresh.sumOf { it.estimatedSavingBytes })
        } else {
            measured
        }
        store.addSample(sample)
        val now = System.currentTimeMillis()
        val snapshot = store.state.value
        val (nextThreshold, crossed) = WatchRules.threshold(snapshot.threshold, sample.usedPercent)
        // A threshold held back last night still counts if the phone is still that full.
        val deferredTopic = deferred?.let { runCatching { WatchTopic.valueOf(it) }.getOrNull() }
        val stillFull = when (deferredTopic) {
            WatchTopic.THRESHOLD_95 -> (sample.usedPercent ?: 0) >= WatchRules.THRESHOLD_HIGH
            WatchTopic.THRESHOLD_90 -> (sample.usedPercent ?: 0) >= WatchRules.THRESHOLD_LOW
            else -> false
        }
        val local = ZonedDateTime.now()
        val week = local.get(IsoFields.WEEK_BASED_YEAR) * 100 + local.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val growth = WatchRules.weeklyGrowth(store.state.value.samples)
        val input = WatchDecisionInput(
            nowMillis = now,
            localHour = local.hour,
            localWeek = week,
            localDayEpoch = LocalDate.now().toEpochDay(),
            isPro = isPro,
            settings = snapshot.settings,
            threshold = crossed ?: deferredTopic?.takeIf { stillFull },
            growth = growth,
            newVideoBytes = sample.bigVideoCandidateBytes,
            history = WatchRules.pruneHistory(snapshot.history, now),
            lastWeeklyWeek = snapshot.lastWeeklyWeek,
        )
        store.update { it.copy(threshold = nextThreshold, history = input.history) }
        when (val decision = WatchRules.decide(input)) {
            is WatchDecision.Send -> {
                if (WatchNotifier.post(applicationContext, decision.topic, sample, growth)) {
                    store.update {
                        it.copy(
                            history = it.history + SentNotice(decision.topic, now),
                            lastWeeklyWeek = if (decision.topic == WatchTopic.WEEKLY) week else it.lastWeeklyWeek,
                            notifiedVideos = if (decision.topic == WatchTopic.NEW_VIDEOS) it.notifiedVideos + videoPaths else it.notifiedVideos,
                        )
                    }
                }
            }
            is WatchDecision.Defer -> WatchScheduler.scheduleMorning(applicationContext, decision.topic)
            WatchDecision.Nothing -> Unit
        }
    }

    /** Google Play's answer if it comes quickly; otherwise the last known one. */
    private suspend fun currentPro(app: DepoAkilliApplication): Boolean {
        val cached = app.watch.state.value.lastKnownPro
        app.billing.refreshPurchases()
        val fresh = withTimeoutOrNull(BILLING_WAIT_MILLIS) { app.billing.state.drop(1).first { !it.loading } }
        val isPro = fresh?.isPro ?: cached
        app.watch.setPro(isPro)
        return isPro
    }

    companion object {
        const val KEY_DEFERRED = "deferred_topic"
        private const val RUN_LIMIT_MILLIS = 30_000L
        private const val BILLING_WAIT_MILLIS = 8_000L

        /** Of the job's 30 s, at most this long for looking at new videos. */
        private const val VIDEO_LOOK_MILLIS = 15_000L
    }
}

object WatchScheduler {
    private const val PERIODIC = "storage_watch_daily"
    private const val MORNING = "storage_watch_morning"
    private const val DAY_FROM_HOUR = 10
    private const val DAY_UNTIL_HOUR = 18

    /**
     * Daily, anchored in local daytime (10:00–18:00, random minute) so quiet
     * hours do not swallow every notice. Only "battery not low"; no charging
     * or idle constraint (it might never run). No battery-optimisation
     * exemption is asked for.
     */
    fun schedule(context: Context, restart: Boolean) {
        runCatching {
            val request = PeriodicWorkRequestBuilder<WatchWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayToNextDaytime(LocalDateTime.now(), Random.Default), TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                if (restart) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }

    fun scheduleMorning(context: Context, topic: WatchTopic) {
        runCatching {
            val request = OneTimeWorkRequestBuilder<WatchWorker>()
                .setInitialDelay(delayToNextMorning(LocalDateTime.now(), Random.Default), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(WatchWorker.KEY_DEFERRED to topic.name))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(MORNING, ExistingWorkPolicy.REPLACE, request)
        }
    }

    fun cancel(context: Context) {
        runCatching {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
            WorkManager.getInstance(context).cancelUniqueWork(MORNING)
        }
    }

    /** Milliseconds to a random minute between 10:00 and 18:00 local (today if still ahead). */
    fun delayToNextDaytime(now: LocalDateTime, random: Random): Long {
        val minute = random.nextInt((DAY_UNTIL_HOUR - DAY_FROM_HOUR) * 60)
        var target = now.toLocalDate().atTime(DAY_FROM_HOUR, 0).plusMinutes(minute.toLong())
        if (!target.isAfter(now.plusMinutes(5))) target = target.plusDays(1)
        return java.time.Duration.between(now, target).toMillis()
    }

    /** Milliseconds to a random minute between 09:00 and 10:00 local, the next such morning. */
    fun delayToNextMorning(now: LocalDateTime, random: Random): Long {
        var target = now.toLocalDate().atTime(WatchRules.QUIET_UNTIL_HOUR, 0).plusMinutes(random.nextInt(60).toLong())
        if (!target.isAfter(now)) target = target.plusDays(1)
        return java.time.Duration.between(now, target).toMillis()
    }
}

/** Android 12+ puts apps unused for months to sleep (hibernation): no jobs, no notices. */
object WatchHibernation {
    fun isRestricted(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { !context.packageManager.isAutoRevokeWhitelisted }.getOrDefault(false)

    fun settingsIntent(context: Context): Intent =
        Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
