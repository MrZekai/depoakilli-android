package com.mrzekai.depoakilli.ui.media

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaThumbnailsTest {
    @Test
    fun `sample size never decodes below the requested side`() {
        assertEquals(1, thumbnailSampleSize(500, 300, 520))
        assertEquals(1, thumbnailSampleSize(1000, 800, 520))
        assertEquals(2, thumbnailSampleSize(1040, 800, 520))
        assertEquals(4, thumbnailSampleSize(4000, 3000, 520))
        assertEquals(2, thumbnailSampleSize(3000, 4000, 1600))
    }
}
