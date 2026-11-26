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
    /** Recurring price in micros and its ISO currency, for the price anchor (Faz 2.2b). */
    val priceMicros: Long = 0L,
    val currencyCode: String = "",
)

/** What the user owns, as far as the device can tell (Faz 2 Y5). */
enum class OwnedPlan {
    /** Auto-renewing yearly subscription. */
    YEARLY,
    /** Prepaid one-week pass (bought on this device). */
    WEEK_PASS,
    /** `pro_lifetime`. */
    LIFETIME,
    /** A `pro` subscription that does not renew: a cancelled yearly plan or a pass bought elsewhere. */
    SUBSCRIPTION_NOT_RENEWING,
}

/** The `upgrade-yearly` offer (Faz 2 Y6): first-year price and the price after it. */
data class UpgradeOffer(
    val firstYearPrice: String,
    val laterYearPrice: String,
    val offerToken: String,
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
    /** What the user owns; null when not Pro. */
    val ownedPlan: OwnedPlan? = null,
    /** Upgrade offer, when Play returns it (developer-determined; shown only by Y6 rules). */
    val upgrade: UpgradeOffer? = null,
    /**
     * End of a prepaid pass / next renewal. Play Billing Library does not give
     * these to the app (only the Play Developer API on a server does), so they
     * stay null and no date is ever shown or guessed.
     */
    val passEndMillis: Long? = null,
    val renewalMillis: Long? = null,
) {
    val available: Boolean get() = plans.isNotEmpty()
}

/** A pricing phase of a subscription offer (pure copy of Play's PricingPhase). */
data class PricingPhaseInfo(
    val priceMicros: Long,
    val formattedPrice: String,
    /** ISO 8601 period, e.g. P3D, P1W, P1Y. */
    val billingPeriod: String,
    val currencyCode: String = "",
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
    fun build(
        subscriptionOffers: List<SubscriptionOfferInfo>,
        lifetimePrice: String?,
        lifetimeMicros: Long = 0L,
        lifetimeCurrency: String = "",
    ): List<ProPlanOption> {
        val plans = mutableListOf<ProPlanOption>()
        yearly(subscriptionOffers)?.let(plans::add)
        weekPass(subscriptionOffers)?.let(plans::add)
        if (!lifetimePrice.isNullOrBlank()) {
            plans += ProPlanOption(ProPlanKind.LIFETIME, lifetimePrice, priceMicros = lifetimeMicros, currencyCode = lifetimeCurrency)
        }
        return plans
    }

    /** The upgrade offer with both prices (first year, then the yearly price), or null. */
    fun upgrade(offers: List<SubscriptionOfferInfo>): UpgradeOffer? {
        val offer = offers.firstOrNull {
            it.basePlanId == BillingProducts.PLAN_YEARLY && it.offerId == BillingProducts.OFFER_UPGRADE
        } ?: return null
        val paid = offer.phases.filter { it.priceMicros > 0L }
        if (paid.size < 2) return null
        return UpgradeOffer(paid.first().formattedPrice, paid.last().formattedPrice, offer.offerToken)
    }

    private fun yearly(offers: List<SubscriptionOfferInfo>): ProPlanOption? {
        val forPlan = offers.filter { it.basePlanId == BillingProducts.PLAN_YEARLY }
        val base = forPlan.firstOrNull { it.offerId == null } ?: return null
        // Play returns the trial offer only to users who never had one (plan §2.1 "Deneme uygunluğu").
        // The upgrade offer is developer-determined and is not shown here (Faz 2).
        val trial = forPlan.firstOrNull { it.offerId == BillingProducts.OFFER_TRIAL }
        val chosen = trial ?: base
        val recurring = recurringPhase(chosen) ?: recurringPhase(base) ?: return null
        return ProPlanOption(
            kind = ProPlanKind.YEARLY,
            formattedPrice = recurring.formattedPrice,
            trialDays = trial?.let(::trialDays),
            offerToken = chosen.offerToken,
            priceMicros = recurring.priceMicros,
            currencyCode = recurring.currencyCode,
        )
    }

    private fun weekPass(offers: List<SubscriptionOfferInfo>): ProPlanOption? {
        val base = offers.firstOrNull { it.basePlanId == BillingProducts.PLAN_WEEK_PASS && it.offerId == null }
            ?: return null
        val phase = recurringPhase(base) ?: return null
        return ProPlanOption(
            ProPlanKind.WEEK_PASS,
            phase.formattedPrice,
            offerToken = base.offerToken,
            priceMicros = phase.priceMicros,
            currencyCode = phase.currencyCode,
        )
    }

    /** The phase paid after any free phase: the last phase that costs money. */
    private fun recurringPhase(offer: SubscriptionOfferInfo): PricingPhaseInfo? =
        offer.phases.lastOrNull { it.priceMicros > 0L }

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
