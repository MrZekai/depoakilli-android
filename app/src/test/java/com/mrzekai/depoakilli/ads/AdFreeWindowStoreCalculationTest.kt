package com.mrzekai.depoakilli.ads

import org.junit.Assert.assertEquals
import org.junit.Test

class AdFreeWindowStoreCalculationTest {
    @Test
    fun `fresh grant retains sixty minutes`() {
        assertEquals(
            AdFreeWindowStore.REWARD_DURATION_MILLIS,
            AdFreeWindowStore.calculateRemaining(
                storedRemaining = AdFreeWindowStore.REWARD_DURATION_MILLIS,
                checkpointElapsed = 1_000L,
                nowElapsed = 1_000L,
                expiryWall = 3_600_000L,
                nowWall = 0L,
            ),
        )
    }

    @Test
    fun `same boot subtracts elapsed time`() {
        assertEquals(
            40L * 60L * 1000L,
            AdFreeWindowStore.calculateRemaining(
                storedRemaining = AdFreeWindowStore.REWARD_DURATION_MILLIS,
                checkpointElapsed = 1_000L,
                nowElapsed = 20L * 60L * 1000L + 1_000L,
                expiryWall = 3_600_000L,
                nowWall = 20L * 60L * 1000L,
            ),
        )
    }

    @Test
    fun `reboot falls back to expired wall clock`() {
        assertEquals(
            0L,
            AdFreeWindowStore.calculateRemaining(
                storedRemaining = AdFreeWindowStore.REWARD_DURATION_MILLIS,
                checkpointElapsed = 1_000L,
                nowElapsed = 5_000L,
                expiryWall = 1_000L,
                nowWall = 2_000L,
            ),
        )
    }

    @Test
    fun `wall clock rollback never extends the reward`() {
        assertEquals(
            AdFreeWindowStore.REWARD_DURATION_MILLIS,
            AdFreeWindowStore.calculateRemaining(
                storedRemaining = 0L,
                checkpointElapsed = 0L,
                nowElapsed = 0L,
                expiryWall = 24L * 60L * 60L * 1000L,
                nowWall = 0L,
            ),
        )
    }

    @Test
    fun `nothing stored has no remaining reward`() {
        assertEquals(
            0L,
            AdFreeWindowStore.calculateRemaining(
                storedRemaining = 0L,
                checkpointElapsed = 0L,
                nowElapsed = 0L,
                expiryWall = 0L,
                nowWall = 0L,
            ),
        )
    }
}
