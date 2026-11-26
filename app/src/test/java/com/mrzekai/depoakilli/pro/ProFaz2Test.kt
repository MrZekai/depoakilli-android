package com.mrzekai.depoakilli.pro

import com.mrzekai.depoakilli.billing.BillingConnect
import com.mrzekai.depoakilli.billing.EntitlementRules
import com.mrzekai.depoakilli.billing.OwnedPlan
import com.mrzekai.depoakilli.billing.OwnedPurchase
import com.mrzekai.depoakilli.billing.PricingPhaseInfo
import com.mrzekai.depoakilli.billing.ProOffers
import com.mrzekai.depoakilli.billing.ProPlanKind
import com.mrzekai.depoakilli.billing.ProPlanOption
import com.mrzekai.depoakilli.billing.SubscriptionOfferInfo
import java.util.Locale
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProFaz2Test {
    private val gb = 1_000_000_000L

    // ---- Billing connection time limit ---------------------------------

    @Test
    fun `a Play Store that never answers times out every attempt and gives up`() = runTest {
        var attempts = 0
        val connected = BillingConnect.connect {
            attempts++
            awaitCancellation()
        }
        assertFalse(connected)
        assertEquals(BillingConnect.ATTEMPTS, attempts)
        // 3 × 10 s limit + 1 s + 2 s back-off, in virtual time: the call always returns.
        assertEquals(3 * BillingConnect.ATTEMPT_TIMEOUT_MILLIS + 3_000L, currentTime)
    }

    @Test
    fun `a timed-out attempt is retried and a later answer connects`() = runTest {
        var attempts = 0
        val connected = BillingConnect.connect {
            attempts++
            if (attempts == 1) awaitCancellation() else BillingConnect.Outcome.OK
        }
        assertTrue(connected)
        assertEquals(2, attempts)
    }

    @Test
    fun `no Play billing on the device stops at once`() = runTest {
        var attempts = 0
        assertFalse(BillingConnect.connect { attempts++; BillingConnect.Outcome.GIVE_UP })
        assertEquals(1, attempts)
    }

    // ---- ProGainEstimator ------------------------------------------------

    @Test
    fun `before the free first cleanup is spent, one-tap cleanup is not a Pro gain`() {
        val estimate = ProGainEstimator.estimate(suggestedBytes = 18 * gb, suggestedCount = 40, firstCleanupUsed = false)
        assertTrue(estimate.rows.isEmpty())
        assertEquals(0L, estimate.totalBytes)
        assertFalse(estimate.qualifies)
    }

    @Test
    fun `after it, the My pick layer is a measured gain and 2 GB is the line`() {
        val estimate = ProGainEstimator.estimate(suggestedBytes = 18 * gb, suggestedCount = 40, firstCleanupUsed = true)
        assertEquals(18 * gb, estimate.totalBytes)
        assertTrue(estimate.qualifies)
        assertTrue(estimate.rows.single().measured)
        assertFalse(ProGainEstimator.estimate(1_999_999_999L, 3, true).qualifies)
        assertTrue(ProGainEstimator.estimate(2 * gb, 3, true).qualifies)
    }

    @Test
    fun `fill bars only for a gain Pro alone gives`() {
        // One-tap "My pick" only: the same files can be cleared by hand, so no bars.
        val pickOnly = ProGainEstimator.estimate(18 * gb, 40, true)
        assertTrue(pickOnly.oneTapOnly)
        assertFalse(pickOnly.showsFillBars)
        assertEquals(0L, pickOnly.proOnlyBytes)
        // With video compression (Pro only), the bars show that part alone.
        val withVideo = ProGainEstimator.estimate(18 * gb, 40, true, listOf(5 * gb), videoCompressionAvailable = true)
        assertFalse(withVideo.oneTapOnly)
        assertTrue(withVideo.showsFillBars)
        assertEquals(withVideo.totalBytes - 18 * gb, withVideo.proOnlyBytes)
        assertFalse(ProGainEstimator.estimate(0L, 0, false).oneTapOnly)
    }

    @Test
    fun `video compression stays out in Faz 2 even with big videos`() {
        val videos = listOf(5 * gb, 3 * gb, 50_000_000L)
        val off = ProGainEstimator.estimate(0L, 0, true, videos)
        assertFalse(ProGainEstimator.VIDEO_COMPRESSION_AVAILABLE)
        assertEquals(0L, off.totalBytes)
        // When Faz 3.4 turns it on: 30 % of videos of 100 MB or more, as an estimate.
        val on = ProGainEstimator.estimate(0L, 0, true, videos, videoCompressionAvailable = true)
        val row = on.rows.single()
        assertEquals((8 * gb * 0.30).toLong(), row.bytes)
        assertEquals(2, row.count)
        assertFalse(row.measured)
        assertTrue(on.hasEstimates)
    }

    // ---- Free tier -----------------------------------------------------

    @Test
    fun `the first My pick cleanup is free, later ones need Pro`() {
        assertTrue(FreeTierRules.canUseOneTapPick(isPro = false, firstCleanupUsed = false))
        assertFalse(FreeTierRules.canUseOneTapPick(isPro = false, firstCleanupUsed = true))
        assertTrue(FreeTierRules.canUseOneTapPick(isPro = true, firstCleanupUsed = true))
        // A user who installed before 2.0 starts with the free cleanup unused.
        assertFalse(FreeTierSnapshot().firstCleanupUsed)
    }

    @Test
    fun `twenty decisions a day, reset on a new day`() {
        var counter = DecisionCounter()
        repeat(20) { counter = counter.increment(today = 100L) }
        assertEquals(20, counter.used(100L))
        assertTrue(FreeTierRules.decisionsLocked(isPro = false, usedToday = counter.used(100L)))
        assertFalse(FreeTierRules.decisionsLocked(isPro = true, usedToday = counter.used(100L)))
        assertEquals(0, counter.used(101L))
        assertEquals(1, counter.increment(101L).used(101L))
    }

    @Test
    fun `setting the clock back does not reset the count`() {
        var counter = DecisionCounter()
        repeat(20) { counter = counter.increment(today = 100L) }
        // Clock moved back to yesterday: still 20, still locked.
        assertEquals(20, counter.used(99L))
        counter = counter.increment(99L)
        assertEquals(100L, counter.day)
        assertEquals(21, counter.count)
        // Back to the real day: same count; the next real day resets it.
        assertEquals(21, counter.used(100L))
        assertEquals(0, counter.used(101L))
    }

    @Test
    fun `undo gives a decision back, never below zero`() {
        val counter = DecisionCounter().increment(5L).decrement(5L).decrement(5L)
        assertEquals(0, counter.used(5L))
    }

    @Test
    fun `Y1 card needs 2 GB, no Pro and no snooze`() {
        val big = ProGainEstimator.estimate(5 * gb, 10, true)
        val small = ProGainEstimator.estimate(gb, 10, true)
        assertTrue(FreeTierRules.showProCard(false, big, nowMillis = 10L, hiddenUntilMillis = 0L))
        assertFalse(FreeTierRules.showProCard(true, big, 10L, 0L))
        assertFalse(FreeTierRules.showProCard(false, small, 10L, 0L))
        assertFalse(FreeTierRules.showProCard(false, big, 10L, 10L + FreeTierRules.PRO_CARD_SNOOZE_MILLIS))
    }

    @Test
    fun `monthly Pro bytes start over each month`() {
        val month = MonthlyProBytes().add(202611, 3 * gb).add(202611, gb)
        assertEquals(4 * gb, month.on(202611).bytes)
        assertEquals(0L, month.on(202612).bytes)
    }

    @Test
    fun `the upgrade sheet needs a known pass end, which Play Billing does not give`() {
        val day = 24L * 60 * 60 * 1000
        assertFalse(UpgradeRules.shouldShow(passEndMillis = null, nowMillis = 0L, offerAvailable = true, alreadyShown = false))
        assertTrue(UpgradeRules.shouldShow(2 * day, 0L, true, false))
        assertFalse(UpgradeRules.shouldShow(5 * day, 0L, true, false))
        assertFalse(UpgradeRules.shouldShow(2 * day, 0L, true, alreadyShown = true))
        assertFalse(UpgradeRules.shouldShow(2 * day, 0L, offerAvailable = false, alreadyShown = false))
    }

    // ---- Owned plan ------------------------------------------------------

    @Test
    fun `owned plan comes from renewal state and what this device bought`() {
        fun sub(renewing: Boolean) = OwnedPurchase(listOf("pro"), OwnedPurchase.State.PURCHASED, true, "t", renewing)
        assertEquals(OwnedPlan.YEARLY, EntitlementRules.ownedPlan(listOf(sub(true)), lastBoughtWeekPass = true))
        assertEquals(OwnedPlan.WEEK_PASS, EntitlementRules.ownedPlan(listOf(sub(false)), lastBoughtWeekPass = true))
        assertEquals(OwnedPlan.SUBSCRIPTION_NOT_RENEWING, EntitlementRules.ownedPlan(listOf(sub(false)), false))
        val life = OwnedPurchase(listOf("pro_lifetime"), OwnedPurchase.State.PURCHASED, true, "l")
        assertEquals(OwnedPlan.LIFETIME, EntitlementRules.ownedPlan(listOf(sub(true), life), false))
        assertNull(EntitlementRules.ownedPlan(emptyList(), false))
    }

    // ---- Paywall figures ---------------------------------------------------

    @Test
    fun `price anchor is yearly over weekly, rounded down`() {
        assertEquals(5, ProPricing.anchorWeeks(24_990_000L, 4_990_000L))
        assertNull(ProPricing.anchorWeeks(24_990_000L, 0L))
        assertNull(ProPricing.anchorWeeks(0L, 4_990_000L))
    }

    @Test
    fun `weekly equivalent is smaller than the yearly amount`() {
        val weekly = ProPricing.weeklyEquivalentMicros(24_990_000L)
        assertEquals(480_576L, weekly)
        assertTrue(weekly < 24_990_000L)
        assertEquals("$0.48", ProPricing.format(weekly, "USD", Locale.US))
        assertNull(ProPricing.format(weekly, "", Locale.US))
    }

    @Test
    fun `fill bar and photo equivalent are computed, not guessed`() {
        assertEquals(92 to 71, ProPricing.fillPercents(100 * gb, 8 * gb, 21 * gb))
        assertEquals(50 to 0, ProPricing.fillPercents(100L, 50L, 80L))
        assertNull(ProPricing.fillPercents(0L, 0L, gb))
        assertEquals(4_500, ProPricing.photoEquivalent(18 * gb, 4_000_000L))
        assertNull(ProPricing.photoEquivalent(gb, null))
        assertNull(ProPricing.photoEquivalent(200_000_000L, 4_000_000L))
        assertEquals(3 * 86_400_000L + 5L, ProPricing.firstChargeMillis(5L, 3))
    }

    @Test
    fun `main button text follows the selected plan`() {
        assertEquals(ProPricing.ButtonText.TRIAL, ProPricing.buttonText(ProPlanOption(ProPlanKind.YEARLY, "$24.99", trialDays = 3)))
        assertEquals(ProPricing.ButtonText.YEARLY, ProPricing.buttonText(ProPlanOption(ProPlanKind.YEARLY, "$24.99")))
        assertEquals(ProPricing.ButtonText.WEEK_PASS, ProPricing.buttonText(ProPlanOption(ProPlanKind.WEEK_PASS, "$4.99")))
        assertEquals(ProPricing.ButtonText.LIFETIME, ProPricing.buttonText(ProPlanOption(ProPlanKind.LIFETIME, "$49.99")))
    }

    @Test
    fun `upgrade offer carries both prices`() {
        val offer = SubscriptionOfferInfo(
            "yearly",
            "upgrade-yearly",
            "up",
            listOf(PricingPhaseInfo(19_990_000L, "$19.99", "P1Y"), PricingPhaseInfo(24_990_000L, "$24.99", "P1Y")),
        )
        val upgrade = ProOffers.upgrade(listOf(offer))
        assertNotNull(upgrade)
        upgrade!!
        assertEquals("$19.99", upgrade.firstYearPrice)
        assertEquals("$24.99", upgrade.laterYearPrice)
        assertNull(ProOffers.upgrade(emptyList()))
    }
}
