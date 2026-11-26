package com.mrzekai.depoakilli.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingRulesTest {
    private fun purchase(
        product: String,
        state: OwnedPurchase.State = OwnedPurchase.State.PURCHASED,
        acknowledged: Boolean = true,
    ) = OwnedPurchase(listOf(product), state, acknowledged, token = "t-$product-$state")

    // ---- Entitlement ---------------------------------------------------

    @Test
    fun `an active subscription or the lifetime product means Pro`() {
        assertTrue(EntitlementRules.isPro(listOf(purchase(BillingProducts.SUBSCRIPTION))))
        assertTrue(EntitlementRules.isPro(listOf(purchase(BillingProducts.LIFETIME))))
        assertFalse(EntitlementRules.isPro(emptyList()))
        assertFalse(EntitlementRules.isPro(listOf(purchase("something_else"))))
    }

    @Test
    fun `a pending purchase never unlocks Pro but is reported as pending`() {
        val pending = listOf(purchase(BillingProducts.SUBSCRIPTION, OwnedPurchase.State.PENDING, acknowledged = false))
        assertFalse(EntitlementRules.isPro(pending))
        assertTrue(EntitlementRules.hasPending(pending))
        assertFalse(EntitlementRules.isPro(listOf(purchase(BillingProducts.LIFETIME, OwnedPurchase.State.UNSPECIFIED))))
    }

    @Test
    fun `every purchased but unacknowledged purchase is acknowledged, pending ones are not`() {
        val owned = listOf(
            purchase(BillingProducts.SUBSCRIPTION, acknowledged = false),
            purchase(BillingProducts.LIFETIME, acknowledged = true),
            purchase(BillingProducts.LIFETIME, OwnedPurchase.State.PENDING, acknowledged = false),
        )
        val toAck = EntitlementRules.needingAcknowledge(owned)
        assertEquals(1, toAck.size)
        assertEquals(listOf(BillingProducts.SUBSCRIPTION), toAck.single().products)
    }

    // ---- Offers --------------------------------------------------------

    private fun phase(micros: Long, price: String, period: String) = PricingPhaseInfo(micros, price, period)

    private val yearlyBase = SubscriptionOfferInfo(
        basePlanId = BillingProducts.PLAN_YEARLY,
        offerId = null,
        offerToken = "yearly-base",
        phases = listOf(phase(24_990_000L, "$24.99", "P1Y")),
    )
    private val yearlyTrial = SubscriptionOfferInfo(
        basePlanId = BillingProducts.PLAN_YEARLY,
        offerId = BillingProducts.OFFER_TRIAL,
        offerToken = "yearly-trial",
        phases = listOf(phase(0L, "Free", "P3D"), phase(24_990_000L, "$24.99", "P1Y")),
    )
    private val yearlyUpgrade = SubscriptionOfferInfo(
        basePlanId = BillingProducts.PLAN_YEARLY,
        offerId = BillingProducts.OFFER_UPGRADE,
        offerToken = "yearly-upgrade",
        phases = listOf(phase(19_990_000L, "$19.99", "P1Y"), phase(24_990_000L, "$24.99", "P1Y")),
    )
    private val weekPass = SubscriptionOfferInfo(
        basePlanId = BillingProducts.PLAN_WEEK_PASS,
        offerId = null,
        offerToken = "week",
        phases = listOf(phase(4_990_000L, "$4.99", "P1W")),
    )

    @Test
    fun `an eligible user gets the trial offer with its length and the yearly price`() {
        val plans = ProOffers.build(listOf(yearlyBase, yearlyTrial, yearlyUpgrade, weekPass), "$49.99")
        assertEquals(listOf(ProPlanKind.YEARLY, ProPlanKind.WEEK_PASS, ProPlanKind.LIFETIME), plans.map { it.kind })
        val yearly = plans.first()
        assertEquals(3, yearly.trialDays)
        assertEquals("$24.99", yearly.formattedPrice)
        assertEquals("yearly-trial", yearly.offerToken)
        assertEquals("$4.99", plans[1].formattedPrice)
        assertNull(plans[1].trialDays)
        assertEquals("$49.99", plans[2].formattedPrice)
        assertNull(plans[2].offerToken)
    }

    @Test
    fun `without the trial offer only the price is shown, and the upgrade offer is never picked here`() {
        val yearly = ProOffers.build(listOf(yearlyBase, yearlyUpgrade), null).single()
        assertNull(yearly.trialDays)
        assertEquals("yearly-base", yearly.offerToken)
        assertEquals("$24.99", yearly.formattedPrice)
    }

    @Test
    fun `no products from Play means no plans (the page says Coming soon)`() {
        assertTrue(ProOffers.build(emptyList(), null).isEmpty())
        assertTrue(ProScreenState().plans.isEmpty())
        assertFalse(ProScreenState().available)
    }

    @Test
    fun `ISO periods turn into days`() {
        assertEquals(3, ProOffers.periodDays("P3D"))
        assertEquals(7, ProOffers.periodDays("P1W"))
        assertEquals(10, ProOffers.periodDays("P1W3D"))
        assertNull(ProOffers.periodDays("P1M"))
        assertNull(ProOffers.periodDays("P1Y"))
        assertNull(ProOffers.periodDays("garbage"))
    }
}
