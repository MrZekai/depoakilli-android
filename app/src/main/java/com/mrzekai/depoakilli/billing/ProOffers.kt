package com.mrzekai.depoakilli.billing

/** The three things a user can buy (plan §4). */
enum class ProPlanKind { YEARLY, WEEK_PASS, LIFETIME }

/** One plan as the paywall shows it. [formattedPrice] is Play's, in the user's currency. */
data class ProPlanOption(
    val kind: ProPlanKind,
    val formattedPrice: String,
    /** Trial length read from the trial offer; null when Play returned no trial (not eligible). */
    val trialDays: Int? = null,
    /** Offer token for subscriptions; null for the one-time product. */
    val offerToken: String? = null,
)

/** What the Pro page needs; built from Billing state. */
data class ProScreenState(
    val isPro: Boolean = false,
    /** True while the first connection or product query runs. */
    val loading: Boolean = false,
    /** Plans Play returned. Empty = products not set up in Play yet, or billing unavailable ("Coming soon"). */
    val plans: List<ProPlanOption> = emptyList(),
    /** A purchase waits for payment; it grants nothing until Play completes it. */
    val pending: Boolean = false,
) {
    val available: Boolean get() = plans.isNotEmpty()
}

/** A pricing phase of a subscription offer (pure copy of Play's PricingPhase). */
data class PricingPhaseInfo(
    val priceMicros: Long,
    val formattedPrice: String,
    /** ISO 8601 period, e.g. P3D, P1W, P1Y. */
    val billingPeriod: String,
)

/** A subscription offer (pure copy of Play's SubscriptionOfferDetails). */
data class SubscriptionOfferInfo(
    val basePlanId: String,
    /** Null for the base plan's own offer. */
    val offerId: String?,
    val offerToken: String,
    val phases: List<PricingPhaseInfo>,
)

/** Pure mapping from Play's answer to the plans the paywall lists (unit-tested). */
object ProOffers {
    fun build(subscriptionOffers: List<SubscriptionOfferInfo>, lifetimePrice: String?): List<ProPlanOption> {
        val plans = mutableListOf<ProPlanOption>()
        yearly(subscriptionOffers)?.let(plans::add)
        weekPass(subscriptionOffers)?.let(plans::add)
        if (!lifetimePrice.isNullOrBlank()) {
            plans += ProPlanOption(ProPlanKind.LIFETIME, lifetimePrice)
        }
        return plans
    }

    private fun yearly(offers: List<SubscriptionOfferInfo>): ProPlanOption? {
        val forPlan = offers.filter { it.basePlanId == BillingProducts.PLAN_YEARLY }
        val base = forPlan.firstOrNull { it.offerId == null } ?: return null
        // Play returns the trial offer only to users who never had one (plan §2.1 "Deneme uygunluğu").
        // The upgrade offer is developer-determined and is not shown here (Faz 2).
        val trial = forPlan.firstOrNull { it.offerId == BillingProducts.OFFER_TRIAL }
        val chosen = trial ?: base
        val recurring = recurringPrice(chosen) ?: recurringPrice(base) ?: return null
        return ProPlanOption(
            kind = ProPlanKind.YEARLY,
            formattedPrice = recurring,
            trialDays = trial?.let(::trialDays),
            offerToken = chosen.offerToken,
        )
    }

    private fun weekPass(offers: List<SubscriptionOfferInfo>): ProPlanOption? {
        val base = offers.firstOrNull { it.basePlanId == BillingProducts.PLAN_WEEK_PASS && it.offerId == null }
            ?: return null
        val price = recurringPrice(base) ?: return null
        return ProPlanOption(ProPlanKind.WEEK_PASS, price, offerToken = base.offerToken)
    }

    /** The price paid after any free phase: the last phase that costs money. */
    private fun recurringPrice(offer: SubscriptionOfferInfo): String? =
        offer.phases.lastOrNull { it.priceMicros > 0L }?.formattedPrice

    /** Length of the free phase in days; null when the offer has none. */
    fun trialDays(offer: SubscriptionOfferInfo): Int? =
        offer.phases.firstOrNull { it.priceMicros == 0L }?.let { periodDays(it.billingPeriod) }

    /** Days in an ISO 8601 period such as P3D, P1W or P2W1D; null for months/years or bad input. */
    fun periodDays(period: String): Int? {
        val match = Regex("^P(?:(\\d+)W)?(?:(\\d+)D)?$").matchEntire(period) ?: return null
        val weeks = match.groupValues[1].toIntOrNull() ?: 0
        val days = match.groupValues[2].toIntOrNull() ?: 0
        val total = weeks * 7 + days
        return total.takeIf { it > 0 }
    }
}
