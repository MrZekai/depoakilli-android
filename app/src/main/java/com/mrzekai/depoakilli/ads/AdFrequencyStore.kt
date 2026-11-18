package com.mrzekai.depoakilli.ads

import android.content.Context

/** Persists [AdFrequencyState] in SharedPreferences; all rules are in [AdPolicy]. */
class AdFrequencyStore(context: Context) {
    private val prefs = context.getSharedPreferences("ad_frequency", Context.MODE_PRIVATE)

    @Synchronized
    fun load(): AdFrequencyState = AdFrequencyState(
        day = prefs.getLong(DAY, 0L),
        interstitialsToday = prefs.getInt(INTERSTITIALS, 0),
        appOpensToday = prefs.getInt(APP_OPENS, 0),
        lastInterstitialAt = prefs.getLong(LAST_INTERSTITIAL, 0L),
        lastAppOpenAt = prefs.getLong(LAST_APP_OPEN, 0L),
        lastFullScreenAt = prefs.getLong(LAST_FULLSCREEN, 0L),
        launchCount = prefs.getInt(LAUNCHES, 0),
        cleanupsCompleted = prefs.getInt(CLEANUPS, 0),
    )

    @Synchronized
    fun update(change: (AdFrequencyState) -> AdFrequencyState): AdFrequencyState {
        val next = change(load())
        prefs.edit()
            .putLong(DAY, next.day)
            .putInt(INTERSTITIALS, next.interstitialsToday)
            .putInt(APP_OPENS, next.appOpensToday)
            .putLong(LAST_INTERSTITIAL, next.lastInterstitialAt)
            .putLong(LAST_APP_OPEN, next.lastAppOpenAt)
            .putLong(LAST_FULLSCREEN, next.lastFullScreenAt)
            .putInt(LAUNCHES, next.launchCount)
            .putInt(CLEANUPS, next.cleanupsCompleted)
            .apply()
        return next
    }

    fun recordLaunch() = update { it.copy(launchCount = it.launchCount + 1) }

    fun recordCleanupCompleted() = update { it.copy(cleanupsCompleted = it.cleanupsCompleted + 1) }

    private companion object {
        const val DAY = "day"
        const val INTERSTITIALS = "interstitials_today"
        const val APP_OPENS = "app_opens_today"
        const val LAST_INTERSTITIAL = "last_interstitial_at"
        const val LAST_APP_OPEN = "last_app_open_at"
        const val LAST_FULLSCREEN = "last_fullscreen_at"
        const val LAUNCHES = "launch_count"
        const val CLEANUPS = "cleanups_completed"
    }
}
