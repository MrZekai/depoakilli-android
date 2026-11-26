package com.mrzekai.depoakilli.billing

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Connection retries for Google Play Billing (Faz 2 fix): each attempt has a
 * time limit, so a Play Store that never answers cannot hold the connection
 * lock forever. A timed-out attempt counts as a failed one. Pure; unit-tested.
 */
object BillingConnect {
    const val ATTEMPTS = 3
    const val ATTEMPT_TIMEOUT_MILLIS = 10_000L
    const val RETRY_BASE_MILLIS = 1_000L

    /** Result of one attempt: Play's response, or null when it did not answer in time. */
    enum class Outcome { OK, RETRY, GIVE_UP }

    /**
     * Runs [attempt] up to [attempts] times. [attempt] returns [Outcome.OK],
     * [Outcome.RETRY] or [Outcome.GIVE_UP] (no Play billing on this device:
     * retrying will not help). Returns true once an attempt succeeds.
     */
    suspend fun connect(
        attempts: Int = ATTEMPTS,
        timeoutMillis: Long = ATTEMPT_TIMEOUT_MILLIS,
        retryBaseMillis: Long = RETRY_BASE_MILLIS,
        attempt: suspend () -> Outcome,
    ): Boolean {
        repeat(attempts) { index ->
            when (withTimeoutOrNull(timeoutMillis) { attempt() } ?: Outcome.RETRY) {
                Outcome.OK -> return true
                Outcome.GIVE_UP -> return false
                Outcome.RETRY -> if (index < attempts - 1) delay(retryBaseMillis shl index)
            }
        }
        return false
    }
}
