package com.mrzekai.depoakilli.watch

import com.mrzekai.depoakilli.data.CleaningThresholds

/**
 * One measurement of the phone (Faz 3A-1 Storage Watch). Measured only:
 * nothing here is scanned, moved or deleted.
 *
 * The kinds are disjoint: WhatsApp first (by path), then videos outside
 * WhatsApp, then photos outside WhatsApp. The app's own Trash and `_sc.mp4`
 * outputs waiting for approval are in none of them.
 */
data class WatchSample(
    val dayEpoch: Long,
    val measuredAtMillis: Long,
    val totalBytes: Long,
    val availableBytes: Long,
    val videoBytes: Long,
    val photoBytes: Long,
    val whatsAppBytes: Long,
    val bigVideoCandidates: Int = 0,
    val bigVideoCandidateBytes: Long = 0L,
) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0L)

    /** Whole percent used, or null without a size. */
    val usedPercent: Int?
        get() = if (totalBytes <= 0L) null else ((usedBytes * 100.0) / totalBytes).toInt().coerceIn(0, 100)
}

enum class WatchKind { VIDEOS, WHATSAPP, PHOTOS, OTHER }

/**
 * What changed between two real samples. [byKind] always sums to [totalBytes]:
 * "Other" (apps, downloads, …) is the used-space change minus the three kinds.
 */
data class WatchGrowth(
    val days: Int,
    val totalBytes: Long,
    val byKind: Map<WatchKind, Long>,
) {
    /** Space was freed (or nothing changed): the app never says "added" then. */
    val decreased: Boolean get() = totalBytes <= 0L

    fun bytes(kind: WatchKind): Long = byKind[kind] ?: 0L

    /** The kind that grew the most, for the weekly summary ("Videos grew the most"). */
    val topKind: WatchKind? get() = byKind.filter { it.value > 0L }.maxByOrNull { it.value }?.key
}

/** Threshold memory: a level fires once on the way up and re-arms below 85 %. */
data class ThresholdState(val fired90: Boolean = false, val fired95: Boolean = false)

enum class WatchTopic(val proOnly: Boolean) {
    THRESHOLD_95(false),
    THRESHOLD_90(false),
    WHATSAPP(true),
    NEW_VIDEOS(true),
    WEEKLY(true),
}

/** A notification that was really posted. */
data class SentNotice(val topic: WatchTopic, val atMillis: Long)

data class WatchSettings(
    val enabled: Boolean = false,
    val thresholdAlerts: Boolean = true,
    val weeklySummary: Boolean = true,
    val whatsAppAlerts: Boolean = true,
    /** Faz 3A-2: on by default like the other Pro alerts (they only run while Pro is on). */
    val newVideoAlerts: Boolean = true,
)

/** Everything the decision needs; built by the worker, pure here. */
data class WatchDecisionInput(
    val nowMillis: Long,
    /** Local hour 0..23. */
    val localHour: Int,
    /** Local ISO week as yyyyww, for "once a week". */
    val localWeek: Int,
    val localDayEpoch: Long,
    val isPro: Boolean,
    val settings: WatchSettings,
    val threshold: WatchTopic?,
    val growth: WatchGrowth?,
    val newVideoBytes: Long,
    val history: List<SentNotice>,
    val lastWeeklyWeek: Int,
)

sealed interface WatchDecision {
    data object Nothing : WatchDecision
    data class Send(val topic: WatchTopic) : WatchDecision
    /** Quiet hours: measure again in the morning and send only if it still holds. */
    data class Defer(val topic: WatchTopic) : WatchDecision
}

object WatchRules {
    const val THRESHOLD_LOW = 90
    const val THRESHOLD_HIGH = 95
    const val THRESHOLD_REARM = 85
    const val WEEK_MIN_DAYS = 6L
    const val WEEK_MAX_DAYS = 8L
    const val KEEP_DAYS = 35L
    const val HISTORY_KEEP_DAYS = 40L
    const val MAX_PER_DAY = 1
    const val MAX_PER_WEEK = 3
    const val QUIET_FROM_HOUR = 21
    const val QUIET_UNTIL_HOUR = 9
    const val STALE_DAYS = 3L
    private const val DAY_MILLIS = 86_400_000L

    /**
     * Change from the sample closest to 7 days before the latest one, looked
     * for 6–8 days back (the job may skip days). None there: null, never a guess.
     * The change is not scaled to 7 days; the screen says "in the last N days".
     */
    fun weeklyGrowth(samples: List<WatchSample>): WatchGrowth? {
        val latest = samples.maxByOrNull { it.measuredAtMillis } ?: return null
        val base = samples
            .filter { latest.dayEpoch - it.dayEpoch in WEEK_MIN_DAYS..WEEK_MAX_DAYS }
            .minByOrNull { kotlin.math.abs((latest.dayEpoch - it.dayEpoch) - 7L) }
            ?: return null
        return growth(base, latest)
    }

    fun growth(base: WatchSample, latest: WatchSample): WatchGrowth {
        val total = latest.usedBytes - base.usedBytes
        val videos = latest.videoBytes - base.videoBytes
        val whatsApp = latest.whatsAppBytes - base.whatsAppBytes
        val photos = latest.photoBytes - base.photoBytes
        return WatchGrowth(
            days = (latest.dayEpoch - base.dayEpoch).toInt(),
            totalBytes = total,
            byKind = mapOf(
                WatchKind.VIDEOS to videos,
                WatchKind.WHATSAPP to whatsApp,
                WatchKind.PHOTOS to photos,
                WatchKind.OTHER to total - videos - whatsApp - photos,
            ),
        )
    }

    /** When the watch is switched on: levels already passed do not fire now. */
    fun initialThreshold(percent: Int?): ThresholdState = when {
        percent == null -> ThresholdState()
        else -> ThresholdState(fired90 = percent >= THRESHOLD_LOW, fired95 = percent >= THRESHOLD_HIGH)
    }

    /** Only an upward crossing fires; below 85 % both levels re-arm. */
    fun threshold(state: ThresholdState, percent: Int?): Pair<ThresholdState, WatchTopic?> {
        if (percent == null) return state to null
        if (percent < THRESHOLD_REARM) return ThresholdState() to null
        if (percent >= THRESHOLD_HIGH && !state.fired95) return ThresholdState(true, true) to WatchTopic.THRESHOLD_95
        if (percent >= THRESHOLD_LOW && !state.fired90) return state.copy(fired90 = true) to WatchTopic.THRESHOLD_90
        return state to null
    }

    fun isQuiet(localHour: Int): Boolean = localHour >= QUIET_FROM_HOUR || localHour < QUIET_UNTIL_HOUR

    /** Topics that hold now, highest priority first: threshold > WhatsApp > new videos > weekly. */
    fun candidates(input: WatchDecisionInput): List<WatchTopic> {
        val s = input.settings
        if (!s.enabled) return emptyList()
        val list = mutableListOf<WatchTopic>()
        if (s.thresholdAlerts && input.threshold != null) list += input.threshold
        val growth = input.growth
        if (s.whatsAppAlerts && growth != null && growth.bytes(WatchKind.WHATSAPP) >= CleaningThresholds.WATCH_WHATSAPP_GROWTH_BYTES) {
            list += WatchTopic.WHATSAPP
        }
        if (CleaningThresholds.WATCH_NEW_VIDEO_ENABLED && s.newVideoAlerts &&
            input.newVideoBytes >= CleaningThresholds.WATCH_NEW_VIDEO_BYTES
        ) {
            list += WatchTopic.NEW_VIDEOS
        }
        // Once a week: on Monday, or the first run after it in the same week.
        val weeklyDue = input.lastWeeklyWeek != input.localWeek
        if (s.weeklySummary && weeklyDue && growth != null && !growth.decreased &&
            growth.totalBytes >= CleaningThresholds.WATCH_WEEKLY_MIN_BYTES
        ) {
            list += WatchTopic.WEEKLY
        }
        // Pro alerts stop quietly when Pro is not (or no longer) on.
        return list.filter { input.isPro || !it.proOnly }
    }

    fun decide(input: WatchDecisionInput): WatchDecision {
        val now = input.nowMillis
        val sent = input.history
        if (sent.count { now - it.atMillis < DAY_MILLIS } >= MAX_PER_DAY) return WatchDecision.Nothing
        if (sent.count { now - it.atMillis < 7 * DAY_MILLIS } >= MAX_PER_WEEK) return WatchDecision.Nothing
        val topic = candidates(input).firstOrNull { topic ->
            // The same topic is not repeated within 7 days.
            sent.none { it.topic == topic && now - it.atMillis < 7 * DAY_MILLIS }
        } ?: return WatchDecision.Nothing
        return if (isQuiet(input.localHour)) WatchDecision.Defer(topic) else WatchDecision.Send(topic)
    }

    /** Samples and history older than their windows are dropped. */
    fun prune(samples: List<WatchSample>, todayEpoch: Long): List<WatchSample> =
        samples.filter { todayEpoch - it.dayEpoch < KEEP_DAYS }.sortedBy { it.dayEpoch }

    fun pruneHistory(history: List<SentNotice>, nowMillis: Long): List<SentNotice> =
        history.filter { nowMillis - it.atMillis < HISTORY_KEEP_DAYS * DAY_MILLIS }

    /** One sample per day: a newer one of the same day replaces the older. */
    fun add(samples: List<WatchSample>, sample: WatchSample): List<WatchSample> =
        prune(samples.filter { it.dayEpoch != sample.dayEpoch } + sample, sample.dayEpoch)

    /** Days since the last real look, for "The watch couldn't look for N days". */
    fun daysSince(lastMillis: Long, nowMillis: Long): Long =
        if (lastMillis <= 0L) 0L else ((nowMillis - lastMillis) / DAY_MILLIS).coerceAtLeast(0L)

    fun isStale(lastMillis: Long, nowMillis: Long): Boolean =
        lastMillis > 0L && daysSince(lastMillis, nowMillis) >= STALE_DAYS

    /** Notices really posted in this month (for Y5). */
    fun sentThisMonth(history: List<SentNotice>, monthStartMillis: Long): Int =
        history.count { it.atMillis >= monthStartMillis }
}
