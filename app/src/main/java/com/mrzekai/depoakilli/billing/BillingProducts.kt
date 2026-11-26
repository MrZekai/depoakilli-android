package com.mrzekai.depoakilli.billing

/**
 * Every Google Play product and offer id the app uses, in one place
 * (SMART-CLEANER-ABONELIK-PLANI §8). Prices are never written in code; they
 * always come from Play's ProductDetails.
 */
object BillingProducts {
    /** Subscription product. */
    const val SUBSCRIPTION = "pro"

    /** Auto-renewing base plan of [SUBSCRIPTION]. */
    const val PLAN_YEARLY = "yearly"

    /** Prepaid (non-renewing) one-week base plan of [SUBSCRIPTION]. */
    const val PLAN_WEEK_PASS = "week-pass"

    /** Trial offer on [PLAN_YEARLY]; Play returns it only to eligible users. */
    const val OFFER_TRIAL = "trial-3d"

    /** Developer-determined upgrade offer on [PLAN_YEARLY] (used from Faz 2). */
    const val OFFER_UPGRADE = "upgrade-yearly"

    /** One-time product: Pro for life. Acknowledged, never consumed. */
    const val LIFETIME = "pro_lifetime"

    const val PACKAGE_NAME = "com.mrzekai.depoakilli"

    /** Play's own subscription page; opens in the browser or Play, no app permission needed. */
    const val MANAGE_SUBSCRIPTIONS_URL =
        "https://play.google.com/store/account/subscriptions?sku=$SUBSCRIPTION&package=$PACKAGE_NAME"

    /** Products whose ownership means Pro. */
    val PRO_PRODUCTS = setOf(SUBSCRIPTION, LIFETIME)
}
