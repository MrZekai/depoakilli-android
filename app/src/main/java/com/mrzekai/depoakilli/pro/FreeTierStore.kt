package com.mrzekai.depoakilli.pro

import android.content.Context
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Everything the free tier remembers on this device (Faz 2). */
data class FreeTierSnapshot(
    /** The free first "My pick" cleanup is spent. Users from before 2.0 start with it unused. */
    val firstCleanupUsed: Boolean = false,
    val decisions: DecisionCounter = DecisionCounter(),
    /** The Y1 card stays hidden until this time ("Not now"). */
    val proCardHiddenUntil: Long = 0L,
    val monthly: MonthlyProBytes = MonthlyProBytes(),
    /** Faz 3A-2: the one video a free user may shrink (in the app's lifetime on this device). */
    val videoTrialUsed: Boolean = false,
) {
    fun decisionsToday(today: Long = FreeTierStore.today()): Int = decisions.used(today)

    fun monthBytes(month: Int = FreeTierStore.currentMonth()): Long = monthly.on(month).bytes
}

/** Local, per-device store of the free tier; no server. */
class FreeTierStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<FreeTierSnapshot> = _state.asStateFlow()

    fun markFirstCleanupUsed() = write { it.copy(firstCleanupUsed = true) }

    fun countDecision() = write { it.copy(decisions = it.decisions.increment(today())) }

    fun undoDecision() = write { it.copy(decisions = it.decisions.decrement(today())) }

    fun snoozeProCard(nowMillis: Long = System.currentTimeMillis()) =
        write { it.copy(proCardHiddenUntil = nowMillis + FreeTierRules.PRO_CARD_SNOOZE_MILLIS) }

    fun addProBytes(bytes: Long) = write { it.copy(monthly = it.monthly.add(currentMonth(), bytes)) }

    /** Faz 3A-2: set once the free video has been shrunk (a failed try does not use it up). */
    fun markVideoTrialUsed() = write { it.copy(videoTrialUsed = true) }

    private fun write(change: (FreeTierSnapshot) -> FreeTierSnapshot) {
        val next = change(_state.value)
        _state.value = next
        prefs.edit()
            .putBoolean(KEY_FIRST_USED, next.firstCleanupUsed)
            .putLong(KEY_DECISION_DAY, next.decisions.day)
            .putInt(KEY_DECISION_COUNT, next.decisions.count)
            .putLong(KEY_CARD_HIDDEN_UNTIL, next.proCardHiddenUntil)
            .putInt(KEY_MONTH, next.monthly.month)
            .putLong(KEY_MONTH_BYTES, next.monthly.bytes)
            .putBoolean(KEY_VIDEO_TRIAL, next.videoTrialUsed)
            .apply()
    }

    private fun read() = FreeTierSnapshot(
        firstCleanupUsed = prefs.getBoolean(KEY_FIRST_USED, false),
        decisions = DecisionCounter(prefs.getLong(KEY_DECISION_DAY, Long.MIN_VALUE), prefs.getInt(KEY_DECISION_COUNT, 0)),
        proCardHiddenUntil = prefs.getLong(KEY_CARD_HIDDEN_UNTIL, 0L),
        monthly = MonthlyProBytes(prefs.getInt(KEY_MONTH, 0), prefs.getLong(KEY_MONTH_BYTES, 0L)),
        videoTrialUsed = prefs.getBoolean(KEY_VIDEO_TRIAL, false),
    )

    companion object {
        private const val PREFS = "pro_free_tier"
        private const val KEY_FIRST_USED = "first_cleanup_used"
        private const val KEY_DECISION_DAY = "decision_day"
        private const val KEY_DECISION_COUNT = "decision_count"
        private const val KEY_CARD_HIDDEN_UNTIL = "pro_card_hidden_until"
        private const val KEY_MONTH = "pro_month"
        private const val KEY_MONTH_BYTES = "pro_month_bytes"
        private const val KEY_VIDEO_TRIAL = "video_trial_used"

        /** Local calendar day of the device clock. */
        fun today(): Long = LocalDate.now().toEpochDay()

        /** e.g. 202611 for November 2026. */
        fun currentMonth(): Int = YearMonth.now().let { it.year * 100 + it.monthValue }
    }
}
