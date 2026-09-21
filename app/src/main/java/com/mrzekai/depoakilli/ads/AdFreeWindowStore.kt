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
    private val expiryRunnable = Runnable { refresh() }

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
        return calculateRemaining(
            storedRemaining = storedRemaining,
            checkpointElapsed = checkpointElapsed,
            nowElapsed = SystemClock.elapsedRealtime(),
            expiryWall = preferences.getLong(KEY_EXPIRY_WALL_MILLIS, 0L),
            nowWall = System.currentTimeMillis(),
        )
    }

    companion object {
        const val REWARD_DURATION_MILLIS = 60L * 60L * 1000L
        private const val MAX_TIMER_DELAY_MILLIS = 60_000L
        private const val PREFERENCES_NAME = "rewarded_ad_free_window"
        private const val KEY_EXPIRY_WALL_MILLIS = "expiry_wall_millis"
        private const val KEY_REMAINING_AT_CHECKPOINT_MILLIS = "remaining_at_checkpoint_millis"
        private const val KEY_CHECKPOINT_ELAPSED_MILLIS = "checkpoint_elapsed_millis"

        internal fun calculateRemaining(
            storedRemaining: Long,
            checkpointElapsed: Long,
            nowElapsed: Long,
            expiryWall: Long,
            nowWall: Long,
        ): Long {
            val wallBased = (expiryWall - nowWall).coerceIn(0L, REWARD_DURATION_MILLIS)
            if (storedRemaining > 0L && checkpointElapsed > 0L && nowElapsed >= checkpointElapsed) {
                val elapsedBased = (storedRemaining - (nowElapsed - checkpointElapsed)).coerceAtLeast(0L)
                return minOf(elapsedBased, wallBased)
            }
            return wallBased
        }
    }
}
