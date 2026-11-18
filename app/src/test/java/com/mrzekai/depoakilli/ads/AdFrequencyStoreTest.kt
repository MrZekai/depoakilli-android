package com.mrzekai.depoakilli.ads

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdFrequencyStoreTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val day = 86_400_000L
    private val start = 20_000L * day + 3_600_000L
    private val returning = AdFrequencyState(day = AdPolicy.dayOf(start, utc), launchCount = 3, cleanupsCompleted = 1)

    private fun can(state: AdFrequencyState, now: Long, trigger: InterstitialTrigger = InterstitialTrigger.CLEANUP_DONE, dwell: Long = Long.MAX_VALUE) =
        AdPolicy.canShowInterstitial(state, now, trigger, dwell, utc)

    @Test
    fun `interstitial respects the two minute interval`() {
        val shown = AdPolicy.recordInterstitial(returning, start, utc)
        assertFalse(can(shown, start + 119_000))
        assertTrue(can(shown, start + 120_000))
    }

    @Test
    fun `two interstitials two minutes apart in one session`() {
        assertTrue(can(returning, start))
        val first = AdPolicy.recordInterstitial(returning, start, utc)
        assertTrue(can(first, start + 120_000))
    }

    @Test
    fun `daily cap and day change`() {
        var s = returning
        var now = start
        repeat(AdPolicy.INTERSTITIAL_MAX_PER_DAY) {
            assertTrue(can(s, now))
            s = AdPolicy.recordInterstitial(s, now, utc)
            now += 130_000
        }
        assertFalse(can(s, now))
        val tomorrow = (AdPolicy.dayOf(now, utc) + 1) * day + 1
        assertTrue(can(s, tomorrow))
        assertEquals(0, AdPolicy.normalized(s, tomorrow, utc).interstitialsToday)
    }

    @Test
    fun `sixty seconds between any two full screen ads`() {
        val afterRewarded = AdPolicy.recordFullScreen(returning, start)
        assertFalse(can(afterRewarded, start + 59_000))
        assertTrue(can(afterRewarded, start + 60_000))
        val afterAppOpen = AdPolicy.recordAppOpen(returning, start, utc)
        assertFalse(can(afterAppOpen, start + 30_000))
    }

    @Test
    fun `first session waits for the first cleanup`() {
        val firstRun = AdFrequencyState(launchCount = 1, cleanupsCompleted = 0)
        assertFalse(can(firstRun, start))
        assertTrue(can(firstRun.copy(cleanupsCompleted = 1), start))
    }

    @Test
    fun `return to clean needs twenty seconds on the tool screen`() {
        assertFalse(can(returning, start, InterstitialTrigger.RETURN_TO_CLEAN, dwell = 19_000))
        assertTrue(can(returning, start, InterstitialTrigger.RETURN_TO_CLEAN, dwell = 20_000))
    }

    @Test
    fun `app open eligibility`() {
        assertTrue(AdPolicy.canShowAppOpen(AdFrequencyState(launchCount = 1), start, utc))
        assertFalse(AdPolicy.canShowAppOpen(AdFrequencyState(launchCount = 0), start, utc))
        val shown = AdPolicy.recordAppOpen(returning, start, utc)
        assertFalse(AdPolicy.canShowAppOpen(shown, start + 1 * 3_600_000L, utc))
        assertTrue(AdPolicy.canShowAppOpen(shown, start + 2 * 3_600_000L, utc))
    }
}
