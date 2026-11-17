package com.mrzekai.depoakilli.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Home's four looks (T5.1) and the 70 % rule. */
class HomeLogicTest {
    @Test
    fun `no access shows the first-run look even while scanning`() {
        assertEquals(HomeMode.FIRST_RUN, HomeLogic.mode(false, scanning = true, hasPlan = true, planIsTidy = false, usedPercent = 90))
    }

    @Test
    fun `a running scan shows the found-so-far look`() {
        assertEquals(HomeMode.SCANNING, HomeLogic.mode(true, scanning = true, hasPlan = true, planIsTidy = false, usedPercent = 90))
    }

    @Test
    fun `access without any plan is ready to scan`() {
        assertEquals(HomeMode.READY, HomeLogic.mode(true, scanning = false, hasPlan = false, planIsTidy = false, usedPercent = 50))
    }

    @Test
    fun `a plan with something to free shows the plan look`() {
        assertEquals(HomeMode.PLAN, HomeLogic.mode(true, scanning = false, hasPlan = true, planIsTidy = false, usedPercent = 95))
    }

    @Test
    fun `a full phone never reads as tidy`() {
        assertEquals(HomeMode.HARD, HomeLogic.mode(true, false, hasPlan = true, planIsTidy = true, usedPercent = 70))
        assertEquals(HomeMode.HARD, HomeLogic.mode(true, false, hasPlan = true, planIsTidy = true, usedPercent = 99))
        // Unknown fill level: never claim tidy.
        assertEquals(HomeMode.HARD, HomeLogic.mode(true, false, hasPlan = true, planIsTidy = true, usedPercent = null))
    }

    @Test
    fun `below 70 percent a tidy plan shows one sentence`() {
        assertEquals(HomeMode.TIDY, HomeLogic.mode(true, false, hasPlan = true, planIsTidy = true, usedPercent = 69))
    }

    @Test
    fun `used percent is measured from total and free bytes`() {
        assertEquals(85, HomeLogic.usedPercent(100L, 15L))
        assertEquals(100, HomeLogic.usedPercent(100L, -5L))
        assertNull(HomeLogic.usedPercent(0L, 0L))
    }

    @Test
    fun `the giant button confirms safe files, opens findings or scans first`() {
        assertEquals(HomeLogic.CleanAction.CONFIRM_SAFE, HomeLogic.cleanAction(hasLiveItems = true, selectedBytes = 1L))
        assertEquals(HomeLogic.CleanAction.OPEN_FINDINGS, HomeLogic.cleanAction(hasLiveItems = true, selectedBytes = 0L))
        assertEquals(HomeLogic.CleanAction.SCAN, HomeLogic.cleanAction(hasLiveItems = false, selectedBytes = 10L))
    }
}
