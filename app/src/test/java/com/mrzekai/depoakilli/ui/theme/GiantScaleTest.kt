package com.mrzekai.depoakilli.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/** The Faz 5 scale rule: usable height and font scale decide the giant sizes. */
class GiantScaleTest {
    @Test
    fun `tall screens get the largest figure and button`() {
        assertEquals(GiantScale(104, 208), GiantScale.of(usableHeightDp = 891, fontScale = 1f))
        assertEquals(GiantScale.LARGE, GiantScale.of(usableHeightDp = 760, fontScale = 1.15f))
    }

    @Test
    fun `medium screens get the medium sizes`() {
        assertEquals(GiantScale(84, 176), GiantScale.of(usableHeightDp = 759, fontScale = 1f))
        assertEquals(GiantScale.MEDIUM, GiantScale.of(usableHeightDp = 640, fontScale = 1f))
    }

    @Test
    fun `short screens and large text get the small sizes`() {
        assertEquals(GiantScale(64, 144), GiantScale.of(usableHeightDp = 639, fontScale = 1f))
        assertEquals(GiantScale.SMALL, GiantScale.of(usableHeightDp = 891, fontScale = 1.3f))
        assertEquals(GiantScale.SMALL, GiantScale.of(usableHeightDp = 700, fontScale = 2f))
    }

    @Test
    fun `the figure never shrinks below 44 sp and content stays 480 dp wide`() {
        assertEquals(44, GiantScale.MIN_NUMBER_SP)
        assertEquals(480, GiantScale.MAX_CONTENT_WIDTH_DP)
    }
}
