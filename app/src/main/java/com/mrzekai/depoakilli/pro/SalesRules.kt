package com.mrzekai.depoakilli.pro

/**
 * Sales optimization (after Faz 3C): when the paywall may open by itself,
 * the one-time offer after a "no", and the trial reminder. No countdown, no
 * fake discount, no price in code. Pure; unit-tested.
 */
object SalesRules {
    const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    /** The reminder comes this long before the trial ends. */
    const val REMINDER_BEFORE_MILLIS = DAY_MILLIS

    /**
     * S1: the paywall opens by itself only once in the app's life, right after
     * the first successful cleanup's result is closed, and only when Pro alone
     * would clear at least 2 GB on this phone. If that first time does not
     * qualify, it never opens by itself.
     */
    fun autoPaywall(isPro: Boolean, qualifies: Boolean, alreadyDecided: Boolean): Boolean =
        !isPro && qualifies && !alreadyDecided

    /**
     * S4: after the first "no" (the paywall closed without a purchase), once:
     * the upgrade-yearly offer, when Play returns it, the user is not Pro and
     * is still trial-eligible (a used trial means no offer).
     */
    fun leaveOffer(isPro: Boolean, offerAvailable: Boolean, trialEligible: Boolean, alreadyShown: Boolean): Boolean =
        !isPro && offerAvailable && trialEligible && !alreadyShown

    /** S3: the end of a trial started now. */
    fun trialEnd(purchaseMillis: Long, trialDays: Int): Long = purchaseMillis + trialDays * DAY_MILLIS

    fun reminderAt(trialEndMillis: Long): Long = trialEndMillis - REMINDER_BEFORE_MILLIS

    /** S3: the reminder is sent only while the subscription still renews and the trial has not ended. */
    fun sendReminder(nowMillis: Long, trialEndMillis: Long, autoRenewing: Boolean): Boolean =
        autoRenewing && nowMillis < trialEndMillis

    /** S3 without notifications: the same text as a Home card during the last day. */
    fun showReminderCard(nowMillis: Long, trialEndMillis: Long?, autoRenewing: Boolean, notificationsAllowed: Boolean): Boolean =
        trialEndMillis != null && !notificationsAllowed && autoRenewing &&
            nowMillis >= reminderAt(trialEndMillis) && nowMillis < trialEndMillis

    /** S2: yearly ÷ 52, rounded up to a whole cent (10 000 micros). */
    fun weeklyMicrosRoundedUp(yearlyMicros: Long): Long {
        if (yearlyMicros <= 0L) return 0L
        val exact = (yearlyMicros + 51L) / 52L
        return ((exact + 9_999L) / 10_000L) * 10_000L
    }
}
