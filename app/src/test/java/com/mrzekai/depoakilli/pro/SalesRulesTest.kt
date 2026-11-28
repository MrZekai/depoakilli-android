package com.mrzekai.depoakilli.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesRulesTest {
    private val day = SalesRules.DAY_MILLIS

    @Test
    fun `S1 - once, only when it qualifies, never for Pro`() {
        val twoGb = ProGainEstimator.PRO_MIN_GAIN_BYTES
        assertTrue(SalesRules.autoPaywall(isPro = false, proOnlyBytes = twoGb, alreadyShown = false))
        assertFalse(SalesRules.autoPaywall(isPro = false, proOnlyBytes = twoGb, alreadyShown = true))
        assertFalse(SalesRules.autoPaywall(isPro = false, proOnlyBytes = twoGb - 1, alreadyShown = false))
        assertFalse(SalesRules.autoPaywall(isPro = true, proOnlyBytes = twoGb, alreadyShown = false))
    }

    @Test
    fun `S4 - once, needs the offer and trial eligibility`() {
        assertTrue(SalesRules.leaveOffer(false, offerAvailable = true, trialEligible = true, alreadyShown = false))
        assertFalse(SalesRules.leaveOffer(false, true, true, alreadyShown = true))
        assertFalse(SalesRules.leaveOffer(false, offerAvailable = false, trialEligible = true, alreadyShown = false))
        assertFalse(SalesRules.leaveOffer(false, true, trialEligible = false, alreadyShown = false))
        assertFalse(SalesRules.leaveOffer(isPro = true, true, true, false))
    }

    @Test
    fun `S3 - 24 hours before the end, not after a cancel`() {
        val end = SalesRules.trialEnd(1_000 * day, 3)
        assertEquals(1_003 * day, end)
        assertEquals(1_002 * day, SalesRules.reminderAt(end))
        assertTrue(SalesRules.sendReminder(1_002 * day, end, autoRenewing = true))
        assertFalse(SalesRules.sendReminder(1_002 * day, end, autoRenewing = false))
        assertFalse(SalesRules.sendReminder(1_003 * day, end, autoRenewing = true))
    }

    @Test
    fun `S3 - card only without notifications, during the last day`() {
        val end = 1_003 * day
        assertTrue(SalesRules.showReminderCard(1_002 * day + 1, end, autoRenewing = true, notificationsAllowed = false))
        assertFalse(SalesRules.showReminderCard(1_002 * day + 1, end, autoRenewing = true, notificationsAllowed = true))
        assertFalse(SalesRules.showReminderCard(1_001 * day, end, autoRenewing = true, notificationsAllowed = false))
        assertFalse(SalesRules.showReminderCard(1_002 * day + 1, end, autoRenewing = false, notificationsAllowed = false))
        assertFalse(SalesRules.showReminderCard(1_002 * day + 1, null, autoRenewing = true, notificationsAllowed = false))
    }

    @Test
    fun `S2 - weekly line rounds up to a cent`() {
        assertEquals(490_000L, SalesRules.weeklyMicrosRoundedUp(24_990_000L))
        assertEquals(1_000_000L, SalesRules.weeklyMicrosRoundedUp(52_000_000L))
        assertEquals(0L, SalesRules.weeklyMicrosRoundedUp(0L))
    }

    @Test
    fun `S1 counts only what Pro alone can clear`() {
        // 3 GB of one-tap "My pick" (free by hand) + 1 GB of video shrinking: no auto paywall.
        val est = ProGainEstimator.Estimate(
            listOf(
                ProGainEstimator.Row(ProGainEstimator.Kind.ONE_TAP_PICK, 3_000_000_000L, 10, measured = true),
                ProGainEstimator.Row(ProGainEstimator.Kind.VIDEO_COMPRESSION, 1_000_000_000L, 3, measured = false),
            ),
        )
        assertTrue(est.qualifies)
        assertFalse(SalesRules.autoPaywall(isPro = false, proOnlyBytes = est.proOnlyBytes, alreadyShown = false))
    }

    @Test
    fun `S1 not met at the first cleanup is checked again at a later one`() {
        // First cleanup: the similar scan has not run yet (0 B); nothing is marked.
        assertFalse(SalesRules.autoPaywall(isPro = false, proOnlyBytes = 0L, alreadyShown = false))
        // A later cleanup, after the scan found 2.5 GB that only Pro clears: it opens.
        assertTrue(SalesRules.autoPaywall(isPro = false, proOnlyBytes = 2_500_000_000L, alreadyShown = false))
    }
}
