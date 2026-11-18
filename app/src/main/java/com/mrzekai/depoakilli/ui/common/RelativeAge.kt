package com.mrzekai.depoakilli.ui.common

import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import java.util.Locale
import java.util.concurrent.TimeUnit

/** How old a file is, in the unit people say it (C6): days, then months, then years. */
object RelativeAge {
    enum class AgeUnit { TODAY, DAYS, MONTHS, YEARS }

    data class Age(val unit: AgeUnit, val amount: Int)

    fun of(modifiedAtMillis: Long, nowMillis: Long = System.currentTimeMillis()): Age {
        if (modifiedAtMillis <= 0L) return Age(AgeUnit.TODAY, 0)
        val days = TimeUnit.MILLISECONDS.toDays((nowMillis - modifiedAtMillis).coerceAtLeast(0L)).toInt()
        return when {
            days < 1 -> Age(AgeUnit.TODAY, 0)
            days < 30 -> Age(AgeUnit.DAYS, days)
            days < 365 -> Age(AgeUnit.MONTHS, (days / 30).coerceAtLeast(1))
            else -> Age(AgeUnit.YEARS, days / 365)
        }
    }

    /** "10 months ago" / "10 ay önce", localized by ICU for every app language. */
    fun format(modifiedAtMillis: Long, locale: Locale, nowMillis: Long = System.currentTimeMillis()): String {
        val age = of(modifiedAtMillis, nowMillis)
        val formatter = RelativeDateTimeFormatter.getInstance(ULocale.forLocale(locale))
        return when (age.unit) {
            AgeUnit.TODAY -> formatter.format(
                RelativeDateTimeFormatter.Direction.THIS,
                RelativeDateTimeFormatter.AbsoluteUnit.DAY,
            )
            AgeUnit.DAYS -> formatter.format(
                age.amount.toDouble(),
                RelativeDateTimeFormatter.Direction.LAST,
                RelativeDateTimeFormatter.RelativeUnit.DAYS,
            )
            AgeUnit.MONTHS -> formatter.format(
                age.amount.toDouble(),
                RelativeDateTimeFormatter.Direction.LAST,
                RelativeDateTimeFormatter.RelativeUnit.MONTHS,
            )
            AgeUnit.YEARS -> formatter.format(
                age.amount.toDouble(),
                RelativeDateTimeFormatter.Direction.LAST,
                RelativeDateTimeFormatter.RelativeUnit.YEARS,
            )
        }
    }
}
