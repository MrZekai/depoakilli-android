package com.mrzekai.depoakilli.watch

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** What the screens read; everything is local to this device (no server). */
data class WatchSnapshot(
    val settings: WatchSettings = WatchSettings(),
    val samples: List<WatchSample> = emptyList(),
    val history: List<SentNotice> = emptyList(),
    val threshold: ThresholdState = ThresholdState(),
    val lastWeeklyWeek: Int = 0,
    /** Last Pro answer from Google Play, kept for the background job. */
    val lastKnownPro: Boolean = false,
    /** Android 13+: the notification permission was asked once (never again). */
    val notificationAsked: Boolean = false,
    /** The app-hibernation explanation was shown once (never again). */
    val hibernationExplained: Boolean = false,
    /** Home's "Want a heads-up before it fills?" card was answered. */
    val promoDone: Boolean = false,
) {
    val latest: WatchSample? get() = samples.maxByOrNull { it.measuredAtMillis }
    val lastLookMillis: Long get() = latest?.measuredAtMillis ?: 0L
    val growth: WatchGrowth? get() = WatchRules.weeklyGrowth(samples)

    fun sentThisMonth(): Int {
        val start = LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return WatchRules.sentThisMonth(history, start)
    }
}

class WatchStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<WatchSnapshot> = _state.asStateFlow()

    @Synchronized
    fun update(change: (WatchSnapshot) -> WatchSnapshot): WatchSnapshot {
        val next = change(_state.value)
        _state.value = next
        write(next)
        return next
    }

    fun setSettings(change: (WatchSettings) -> WatchSettings) = update { it.copy(settings = change(it.settings)) }

    fun addSample(sample: WatchSample) = update { it.copy(samples = WatchRules.add(it.samples, sample)) }

    fun setPro(isPro: Boolean) {
        if (_state.value.lastKnownPro != isPro) update { it.copy(lastKnownPro = isPro) }
    }

    /** Off: the job is cancelled by the caller; samples expire on their own after 35 days. */
    fun disable() = setSettings { it.copy(enabled = false) }

    private fun write(s: WatchSnapshot) {
        prefs.edit()
            .putBoolean(K_ENABLED, s.settings.enabled)
            .putBoolean(K_THRESHOLD, s.settings.thresholdAlerts)
            .putBoolean(K_WEEKLY, s.settings.weeklySummary)
            .putBoolean(K_WHATSAPP, s.settings.whatsAppAlerts)
            .putBoolean(K_NEW_VIDEOS, s.settings.newVideoAlerts)
            .putString(K_SAMPLES, JSONArray(s.samples.map(::sampleJson)).toString())
            .putString(K_HISTORY, JSONArray(s.history.map { JSONObject().put("t", it.topic.name).put("at", it.atMillis) }).toString())
            .putBoolean(K_FIRED90, s.threshold.fired90)
            .putBoolean(K_FIRED95, s.threshold.fired95)
            .putInt(K_WEEKLY_WEEK, s.lastWeeklyWeek)
            .putBoolean(K_PRO, s.lastKnownPro)
            .putBoolean(K_NOTIF_ASKED, s.notificationAsked)
            .putBoolean(K_HIBERNATION, s.hibernationExplained)
            .putBoolean(K_PROMO, s.promoDone)
            .apply()
    }

    private fun read(): WatchSnapshot = WatchSnapshot(
        settings = WatchSettings(
            enabled = prefs.getBoolean(K_ENABLED, false),
            thresholdAlerts = prefs.getBoolean(K_THRESHOLD, true),
            weeklySummary = prefs.getBoolean(K_WEEKLY, true),
            whatsAppAlerts = prefs.getBoolean(K_WHATSAPP, true),
            newVideoAlerts = prefs.getBoolean(K_NEW_VIDEOS, false),
        ),
        samples = runCatching {
            val array = JSONArray(prefs.getString(K_SAMPLES, "[]"))
            (0 until array.length()).map { sampleFrom(array.getJSONObject(it)) }
        }.getOrDefault(emptyList()),
        history = runCatching {
            val array = JSONArray(prefs.getString(K_HISTORY, "[]"))
            (0 until array.length()).mapNotNull { index ->
                val o = array.getJSONObject(index)
                runCatching { SentNotice(WatchTopic.valueOf(o.getString("t")), o.getLong("at")) }.getOrNull()
            }
        }.getOrDefault(emptyList()),
        threshold = ThresholdState(prefs.getBoolean(K_FIRED90, false), prefs.getBoolean(K_FIRED95, false)),
        lastWeeklyWeek = prefs.getInt(K_WEEKLY_WEEK, 0),
        lastKnownPro = prefs.getBoolean(K_PRO, false),
        notificationAsked = prefs.getBoolean(K_NOTIF_ASKED, false),
        hibernationExplained = prefs.getBoolean(K_HIBERNATION, false),
        promoDone = prefs.getBoolean(K_PROMO, false),
    )

    private fun sampleJson(s: WatchSample) = JSONObject()
        .put("d", s.dayEpoch).put("at", s.measuredAtMillis)
        .put("tot", s.totalBytes).put("av", s.availableBytes)
        .put("v", s.videoBytes).put("p", s.photoBytes).put("w", s.whatsAppBytes)
        .put("bc", s.bigVideoCandidates).put("bb", s.bigVideoCandidateBytes)

    private fun sampleFrom(o: JSONObject) = WatchSample(
        dayEpoch = o.getLong("d"),
        measuredAtMillis = o.getLong("at"),
        totalBytes = o.getLong("tot"),
        availableBytes = o.getLong("av"),
        videoBytes = o.getLong("v"),
        photoBytes = o.getLong("p"),
        whatsAppBytes = o.getLong("w"),
        bigVideoCandidates = o.optInt("bc"),
        bigVideoCandidateBytes = o.optLong("bb"),
    )

    companion object {
        private const val PREFS = "storage_watch"
        private const val K_ENABLED = "enabled"
        private const val K_THRESHOLD = "threshold_alerts"
        private const val K_WEEKLY = "weekly_summary"
        private const val K_WHATSAPP = "whatsapp_alerts"
        private const val K_NEW_VIDEOS = "new_video_alerts"
        private const val K_SAMPLES = "samples"
        private const val K_HISTORY = "history"
        private const val K_FIRED90 = "fired90"
        private const val K_FIRED95 = "fired95"
        private const val K_WEEKLY_WEEK = "weekly_week"
        private const val K_PRO = "last_known_pro"
        private const val K_NOTIF_ASKED = "notification_asked"
        private const val K_HIBERNATION = "hibernation_explained"
        private const val K_PROMO = "promo_done"
    }
}
