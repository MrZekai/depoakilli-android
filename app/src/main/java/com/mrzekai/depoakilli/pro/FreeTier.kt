package com.mrzekai.depoakilli.pro

/** What stays free and what Pro opens (Faz 2.3, Y4, Y7). Pure; unit-tested. */
object FreeTierRules {
    /** One-by-one decisions per day without Pro (Y4). */
    const val DAILY_DECISIONS = 20

    /** "Not now" on the Y1 card hides it this long. */
    const val PRO_CARD_SNOOZE_MILLIS = 3L * 24L * 60L * 60L * 1000L

    /** The first "My pick" cleanup is free and unlimited; later ones need Pro. */
    fun canUseOneTapPick(isPro: Boolean, firstCleanupUsed: Boolean): Boolean = isPro || !firstCleanupUsed

    /** No more one-by-one decisions today. */
    fun decisionsLocked(isPro: Boolean, usedToday: Int): Boolean = !isPro && usedToday >= DAILY_DECISIONS

    /** The Y1 card: a real gain of at least 2 GB, not Pro, not snoozed. */
    fun showProCard(isPro: Boolean, estimate: ProGainEstimator.Estimate, nowMillis: Long, hiddenUntilMillis: Long): Boolean =
        !isPro && estimate.qualifies && nowMillis >= hiddenUntilMillis
}

/**
 * Today's one-by-one decisions (Y4). [day] is the local epoch day of the last
 * count. A new day resets the count; a clock set back to an earlier day does
 * not (the count and its day stay), so turning the clock back gives no extra
 * decisions.
 */
data class DecisionCounter(val day: Long = Long.MIN_VALUE, val count: Int = 0) {
    fun on(today: Long): DecisionCounter = if (today > day) DecisionCounter(today, 0) else this

    fun used(today: Long): Int = on(today).count

    fun increment(today: Long): DecisionCounter = on(today).let { it.copy(count = it.count + 1) }

    fun decrement(today: Long): DecisionCounter = on(today).let { it.copy(count = (it.count - 1).coerceAtLeast(0)) }
}

/** This month's bytes cleared with Pro features (Y5). A new month starts at zero. */
data class MonthlyProBytes(val month: Int = 0, val bytes: Long = 0L) {
    fun on(currentMonth: Int): MonthlyProBytes = if (currentMonth != month) MonthlyProBytes(currentMonth, 0L) else this

    fun add(currentMonth: Int, cleared: Long): MonthlyProBytes =
        on(currentMonth).let { it.copy(bytes = it.bytes + cleared.coerceAtLeast(0L)) }
}

/** Y6 (upgrade from a week pass): only with a known pass end, within 3 days, once, with the offer. */
object UpgradeRules {
    const val WINDOW_MILLIS = 3L * 24L * 60L * 60L * 1000L

    fun shouldShow(passEndMillis: Long?, nowMillis: Long, offerAvailable: Boolean, alreadyShown: Boolean): Boolean =
        passEndMillis != null &&
            offerAvailable &&
            !alreadyShown &&
            nowMillis < passEndMillis &&
            passEndMillis - nowMillis <= WINDOW_MILLIS
}

/** Which button started a cleanup, so the free tier and the monthly Pro figure can count it. */
enum class CleanupOrigin {
    /** "My pick" cleaned with one tap. */
    ONE_TAP_PICK,
    /** The confirmation at the end of the one-by-one review. */
    SWIPE,
}
