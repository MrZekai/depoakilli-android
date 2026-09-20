package com.mrzekai.depoakilli.ads

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stores a single, non-stackable rewarded-ad benefit. The elapsed-realtime
 * checkpoint prevents an in-session wall-clock rollback from extending it.
 */
class AdFreeWindowStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _remainingMillis = MutableStateFlow(0L)

    val remainingMillis: StateFlow<Long> = _remainingMillis.asStateFlow()

    init {
        refresh()
    }

    fun grant() {
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        preferences.edit()
            .putLong(KEY_EXPIRY_WALL_MILLIS, nowWall + REWARD_DURATION_MILLIS)
            .putLong(KEY_REMAINING_AT_CHECKPOINT_MILLIS, REWARD_DURATION_MILLIS)
            .putLong(KEY_CHECKPOINT_ELAPSED_MILLIS, nowElapsed)
            .apply()
        refresh()
    }

    fun refresh(): Long {
        val remaining = calculateRemainingMillis()
        _remainingMillis.value = remaining
        mainHandler.removeCallbacks(expiryRunnable)
        if (remaining > 0L) {
            mainHandler.postDelayed(expiryRunnable, remaining.coerceAtMost(MAX_TIMER_DELAY_MILLIS))
        }
        return remaining
    }

    fun isActiveNow(): Boolean = refresh() > 0L

    private fun calculateRemainingMillis(): Long {
        val storedRemaining = preferences.getLong(KEY_REMAINING_AT_CHECKPOINT_MILLIS, 0L)
        val checkpointElapsed = preferences.getLong(KEY_CHECKPOINT_ELAPSED_MILLIS, 0L)
        val nowElapsed = SystemClock.elapsedRealtime()

        if (storedRemaining > 0L && checkpointElapsed > 0L && nowElapsed >= checkpointElapsed) {
            return (storedRemaining - (nowElapsed - checkpointElapsed)).coerceAtLeast(0L)
        }

        // After a device reboot elapsedRealtime restarts. Fall back to the
        // persisted expiry, while never granting more than one reward window.
        return (preferences.getLong(KEY_EXPIRY_WALL_MILLIS, 0L) - System.currentTimeMillis())
            .coerceIn(0L, REWARD_DURATION_MILLIS)
    }

    private val expiryRunnable = Runnable { refresh() }

    companion object {
        const val REWARD_DURATION_MILLIS = 60L * 60L * 1000L
        private const val MAX_TIMER_DELAY_MILLIS = 60L * 60L * 1000L
        private const val PREFERENCES_NAME = "rewarded_ad_free_window"
        private const val KEY_EXPIRY_WALL_MILLIS = "expiry_wall_millis"
        private const val KEY_REMAINING_AT_CHECKPOINT_MILLIS = "remaining_at_checkpoint_millis"
        private const val KEY_CHECKPOINT_ELAPSED_MILLIS = "checkpoint_elapsed_millis"
    }
}
