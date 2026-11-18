package com.mrzekai.depoakilli.data

import com.mrzekai.depoakilli.model.IndexedFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RawPairFinderTest {
    private fun file(name: String, path: String = "DCIM/Camera", root: String = "/storage/emulated/0") = IndexedFile(
        uri = "file://$root/$path/$name",
        name = name,
        sizeBytes = 20_000_000L,
        mimeType = "image/x-adobe-dng",
        modifiedAtMillis = 1L,
        relativePath = path,
        volumeRoot = root,
    )

    @Test
    fun `a dng with a jpeg or heic partner in the same folder is found`() {
        val pairs = RawPairFinder.find(
            listOf(
                file("IMG_1.dng"), file("IMG_1.JPG"),
                file("IMG_2.DNG"), file("img_2.heic"),
                file("IMG_3.dng"),
                file("IMG_4.dng", "DCIM/Raw"), file("IMG_4.jpg", "DCIM/Camera"),
                file("IMG_5.dng", root = "/storage/ABCD-1234"), file("IMG_5.jpg"),
            ),
        )
        assertEquals(setOf("file:///storage/emulated/0/DCIM/Camera/IMG_1.dng", "file:///storage/emulated/0/DCIM/Camera/IMG_2.DNG"), pairs.keys)
        assertEquals("IMG_1.JPG", pairs["file:///storage/emulated/0/DCIM/Camera/IMG_1.dng"])
    }

    @Test
    fun `heif counts as a partner`() {
        val pairs = RawPairFinder.find(listOf(file("IMG_7.dng"), file("IMG_7.HEIF")))
        assertEquals("IMG_7.HEIF", pairs.values.single())
    }

    @Test
    fun `no partner means no candidate`() {
        assertTrue(RawPairFinder.find(listOf(file("IMG_9.dng"), file("IMG_9.png"))).isEmpty())
    }
}
