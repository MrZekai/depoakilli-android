package com.mrzekai.depoakilli.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesRulesTest {
    private val day = SalesRules.DAY_MILLIS

    @Test
    fun `S1 - once, only when it qualifies, never for Pro`() {
        assertTrue(SalesRules.autoPaywall(isPro = false, qualifies = true, alreadyDecided = false))
        assertFalse(SalesRules.autoPaywall(isPro = false, qualifies = true, alreadyDecided = true))
        assertFalse(SalesRules.autoPaywall(isPro = false, qualifies = false, alreadyDecided = false))
        assertFalse(SalesRules.autoPaywall(isPro = true, qualifies = true, alreadyDecided = false))
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
}
