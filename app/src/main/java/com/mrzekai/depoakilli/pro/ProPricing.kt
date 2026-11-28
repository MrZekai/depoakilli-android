package com.mrzekai.depoakilli.pro

import com.mrzekai.depoakilli.billing.ProPlanKind
import com.mrzekai.depoakilli.billing.ProPlanOption
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Figures on the paywall (Faz 2.2b); every one measured or computed. Pure; unit-tested. */
object ProPricing {
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    /** "N week passes' price for 52 weeks": yearly ÷ weekly, rounded down; null without both prices. */
    fun anchorWeeks(yearlyMicros: Long, weeklyMicros: Long): Int? {
        if (yearlyMicros <= 0L || weeklyMicros <= 0L) return null
        return (yearlyMicros / weeklyMicros).toInt().takeIf { it in 1..51 }
    }

    /** Yearly price ÷ 52 in micros, rounded up to a cent (a smaller, secondary line; never the main price). */
    fun weeklyEquivalentMicros(yearlyMicros: Long): Long = SalesRules.weeklyMicrosRoundedUp(yearlyMicros)

    /** Formats [micros] in Play's currency; null when the currency is unknown. */
    fun format(micros: Long, currencyCode: String, locale: Locale): String? {
        if (currencyCode.isBlank() || micros <= 0L) return null
        return runCatching {
            NumberFormat.getCurrencyInstance(locale).apply { currency = Currency.getInstance(currencyCode) }
                .format(micros / 1_000_000.0)
        }.getOrNull()
    }

    /** "No charge today. First payment on …": today + trial days. */
    fun firstChargeMillis(nowMillis: Long, trialDays: Int): Long = nowMillis + trialDays * DAY_MILLIS

    /** Percent full now, and after Pro's gain (used − gain) ÷ total; null without a size. */
    fun fillPercents(totalBytes: Long, availableBytes: Long, gainBytes: Long): Pair<Int, Int>? {
        if (totalBytes <= 0L) return null
        val used = (totalBytes - availableBytes).coerceIn(0L, totalBytes)
        val after = (used - gainBytes).coerceAtLeast(0L)
        fun pct(value: Long) = ((value * 100.0) / totalBytes).toInt().coerceIn(0, 100)
        return pct(used) to pct(after)
    }

    /** "About N photos' worth": gain ÷ average photo, down to hundreds; null below 100 or without photos. */
    fun photoEquivalent(gainBytes: Long, averagePhotoBytes: Long?): Int? {
        if (averagePhotoBytes == null || averagePhotoBytes <= 0L || gainBytes <= 0L) return null
        val photos = gainBytes / averagePhotoBytes
        val hundreds = (photos / 100L) * 100L
        return hundreds.takeIf { it >= 100L }?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()
    }

    /** Which main-button text a plan gets (Y2 §5). */
    enum class ButtonText { TRIAL, YEARLY, WEEK_PASS, LIFETIME }

    fun buttonText(plan: ProPlanOption): ButtonText = when (plan.kind) {
        ProPlanKind.YEARLY -> if (plan.trialDays != null) ButtonText.TRIAL else ButtonText.YEARLY
        ProPlanKind.WEEK_PASS -> ButtonText.WEEK_PASS
        ProPlanKind.LIFETIME -> ButtonText.LIFETIME
    }
}
