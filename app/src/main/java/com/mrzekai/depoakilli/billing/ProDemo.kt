package com.mrzekai.depoakilli.billing

import android.content.Context
import com.mrzekai.depoakilli.BuildConfig

/**
 * Debug builds only: a demo Pro state for store screenshots and layout checks
 * of the Pro screens in every language (emulators have no Play products).
 * The plans come from a debug-only resource; release builds never read it and
 * always show Play's own prices.
 */
object ProDemo {
    /** "plans" = not Pro, plans shown; "on" = Pro is on (yearly). Set from the launch intent. */
    @Volatile
    var mode: String? = null

    fun apply(context: Context, real: ProScreenState): ProScreenState {
        if (!BuildConfig.DEBUG) return real
        val current = mode ?: return real
        @Suppress("DiscouragedApi")
        val id = context.resources.getIdentifier("demo_pro_plans", "array", context.packageName)
        if (id == 0) return real
        val plans = context.resources.getStringArray(id).mapNotNull(::parse)
        @Suppress("DiscouragedApi")
        val upId = context.resources.getIdentifier("demo_pro_upgrade", "string", context.packageName)
        val upgrade = if (upId == 0) null else context.getString(upId).split('|').takeIf { it.size == 2 }
            ?.let { UpgradeOffer(firstYearPrice = it[0], laterYearPrice = it[1], offerToken = "demo") }
        return when (current) {
            "on" -> real.copy(isPro = true, plans = plans, loading = false, ownedPlan = OwnedPlan.YEARLY)
            else -> real.copy(isPro = false, plans = plans, loading = false, ownedPlan = null, upgrade = upgrade)
        }
    }

    private fun parse(line: String): ProPlanOption? {
        val parts = line.split('|')
        if (parts.size < 5) return null
        val kind = runCatching { ProPlanKind.valueOf(parts[0]) }.getOrNull() ?: return null
        return ProPlanOption(
            kind = kind,
            formattedPrice = parts[1],
            trialDays = parts[2].toIntOrNull(),
            offerToken = null,
            priceMicros = parts[3].toLongOrNull() ?: 0L,
            currencyCode = parts[4],
        )
    }
}
