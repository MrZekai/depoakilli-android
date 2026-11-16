package com.mrzekai.depoakilli.ads

import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Counters behind every full-screen ad decision (T1B.1). Pure data. */
data class AdFrequencyState(
    val day: Long = 0L,
    val interstitialsToday: Int = 0,
    val appOpensToday: Int = 0,
    val lastInterstitialAt: Long = 0L,
    val lastAppOpenAt: Long = 0L,
    val lastFullScreenAt: Long = 0L,
    val launchCount: Int = 0,
    val cleanupsCompleted: Int = 0,
)

/** (a) result closed with Done (also after swipe review); (b) back from a tool screen. */
enum class InterstitialTrigger { CLEANUP_DONE, RETURN_TO_CLEAN }

/**
 * Every ad limit lives here (starting values; easy to tighten). Ads are only
 * requested with consent (canRequestAds) and outside the 60-minute ad-free
 * window; those two checks stay with the callers.
 */
object AdPolicy {
    const val APP_OPEN_MIN_LAUNCH = 1
    const val APP_OPEN_MIN_INTERVAL_HOURS = 4L
    const val APP_OPEN_MAX_PER_DAY = 3
    const val APP_OPEN_MAX_WAIT_MS = 3000L
    const val INTERSTITIAL_MIN_INTERVAL_SEC = 180L
    const val INTERSTITIAL_MAX_PER_DAY = 6
    const val FULLSCREEN_GAP_SEC = 60L
    const val NATIVE_LIST_MIN_ROWS = 6
    const val SWIPE_BREAK_EVERY = 8
    const val RETURN_TO_CLEAN_MIN_DWELL_SEC = 20L

    fun dayOf(nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): Long =
        TimeUnit.MILLISECONDS.toDays(nowMillis + zone.getOffset(nowMillis))

    /** Resets the daily counters when the local day changes. */
    fun normalized(state: AdFrequencyState, nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): AdFrequencyState {
        val today = dayOf(nowMillis, zone)
        return if (state.day == today) state else state.copy(day = today, interstitialsToday = 0, appOpensToday = 0)
    }

    fun canShowInterstitial(
        state: AdFrequencyState,
        nowMillis: Long,
        trigger: InterstitialTrigger,
        dwellMillis: Long = Long.MAX_VALUE,
        zone: TimeZone = TimeZone.getDefault(),
    ): Boolean {
        val s = normalized(state, nowMillis, zone)
        // First session: never before the first cleanup.
        if (s.cleanupsCompleted == 0 && s.launchCount <= 1) return false
        if (trigger == InterstitialTrigger.RETURN_TO_CLEAN &&
            dwellMillis < TimeUnit.SECONDS.toMillis(RETURN_TO_CLEAN_MIN_DWELL_SEC)
        ) return false
        if (s.interstitialsToday >= INTERSTITIAL_MAX_PER_DAY) return false
        if (s.lastInterstitialAt > 0L &&
            nowMillis - s.lastInterstitialAt < TimeUnit.SECONDS.toMillis(INTERSTITIAL_MIN_INTERVAL_SEC)
        ) return false
        return fullScreenGapOk(s, nowMillis)
    }

    fun canShowAppOpen(
        state: AdFrequencyState,
        nowMillis: Long,
        zone: TimeZone = TimeZone.getDefault(),
    ): Boolean {
        val s = normalized(state, nowMillis, zone)
        if (s.launchCount < APP_OPEN_MIN_LAUNCH) return false
        if (s.appOpensToday >= APP_OPEN_MAX_PER_DAY) return false
        if (s.lastAppOpenAt > 0L &&
            nowMillis - s.lastAppOpenAt < TimeUnit.HOURS.toMillis(APP_OPEN_MIN_INTERVAL_HOURS)
        ) return false
        return fullScreenGapOk(s, nowMillis)
    }

    fun recordInterstitial(state: AdFrequencyState, nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): AdFrequencyState {
        val s = normalized(state, nowMillis, zone)
        return s.copy(interstitialsToday = s.interstitialsToday + 1, lastInterstitialAt = nowMillis, lastFullScreenAt = nowMillis)
    }

    fun recordAppOpen(state: AdFrequencyState, nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): AdFrequencyState {
        val s = normalized(state, nowMillis, zone)
        return s.copy(appOpensToday = s.appOpensToday + 1, lastAppOpenAt = nowMillis, lastFullScreenAt = nowMillis)
    }

    /** Any full-screen ad (including the rewarded video) counts for the 60 s gap. */
    fun recordFullScreen(state: AdFrequencyState, nowMillis: Long): AdFrequencyState =
        state.copy(lastFullScreenAt = nowMillis)

    private fun fullScreenGapOk(s: AdFrequencyState, nowMillis: Long): Boolean =
        s.lastFullScreenAt <= 0L || nowMillis - s.lastFullScreenAt >= TimeUnit.SECONDS.toMillis(FULLSCREEN_GAP_SEC)
}
