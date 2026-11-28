package com.mrzekai.depoakilli.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoRunModeTest {
    @Test
    fun `switch on uses the foreground service and keeps running after leaving`() {
        assertEquals(VideoRunMode.Runner.FOREGROUND_SERVICE, VideoRunMode.runner(fgs = true))
        assertFalse(VideoRunMode.stopsOnLeave(fgs = true))
    }

    @Test
    fun `switch off runs in the app only and stops when the app is left`() {
        assertEquals(VideoRunMode.Runner.IN_APP, VideoRunMode.runner(fgs = false))
        assertTrue(VideoRunMode.stopsOnLeave(fgs = false))
    }
}
