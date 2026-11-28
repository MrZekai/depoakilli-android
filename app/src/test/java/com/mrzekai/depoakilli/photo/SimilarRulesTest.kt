package com.mrzekai.depoakilli.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarRulesTest {
    private fun photo(
        id: Long,
        taken: Long = id * 1000L,
        folder: String = "DCIM/Camera",
        name: String = "IMG_$id.jpg",
        w: Int = 4000,
        h: Int = 3000,
        size: Long = 3_000_000L,
    ) = PhotoItem(id, "/storage/emulated/0/$folder/$name", folder, name, size, taken, taken, w, h)

    private fun vec(vararg v: Float): FloatArray {
        val n = Math.sqrt(v.sumOf { (it * it).toDouble() }).toFloat()
        return FloatArray(v.size) { v[it] / n }
    }

    @Test
    fun `dHash sets a bit when the left pixel is darker`() {
        val rising = IntArray(72) { it % 9 }
        assertEquals(-1L, SimilarRules.dHash(rising))
        val falling = IntArray(72) { 9 - it % 9 }
        assertEquals(0L, SimilarRules.dHash(falling))
        assertEquals(64, SimilarRules.hamming(-1L, 0L))
    }

    @Test
    fun `burst frames of one folder are candidates, other folders are not`() {
        val items = listOf(photo(1, 0), photo(2, 30_000), photo(3, 59_000, folder = "Pictures/Other"), photo(4, 200_000))
        val hashes = listOf(0L, -1L, 0x0F0F0F0FL, 0x7777L)
        val pairs = SimilarRules.candidatePairs(items, hashes, maxDistance = 10)
        assertTrue(0 to 1 in pairs)
        assertFalse(0 to 2 in pairs)
        assertFalse(0 to 3 in pairs)
    }

    @Test
    fun `close hashes are candidates even far apart in time`() {
        val items = listOf(photo(1, 0), photo(2, 10_000_000, folder = "Pictures"))
        val pairs = SimilarRules.candidatePairs(items, listOf(0b1011L, 0b0011L), maxDistance = 10)
        assertEquals(setOf(0 to 1), pairs)
    }

    @Test
    fun `clusters are transitive and need the cosine`() {
        val e = listOf(vec(1f, 0f), vec(0.99f, 0.1f), vec(0.97f, 0.2f), vec(0f, 1f))
        val pairs = listOf(0 to 1, 1 to 2, 2 to 3)
        val groups = SimilarRules.cluster(4, pairs, e, minCosine = 0.98)
        assertEquals(listOf(listOf(0, 1, 2)), groups)
    }

    @Test
    fun `unembedded photos never join`() {
        val groups = SimilarRules.cluster(2, listOf(0 to 1), listOf(vec(1f, 0f), null), minCosine = 0.5)
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `keeper - eyes open, then sharpest, then most pixels, then biggest`() {
        val items = listOf(photo(1), photo(2), photo(3, w = 2000, h = 1500), photo(4, size = 9_000_000))
        val f = listOf(PhotoFeatures(0, sharpness = 50.0), PhotoFeatures(0, sharpness = 90.0, eyesOpen = 0.1f), PhotoFeatures(0, sharpness = 90.0), PhotoFeatures(0, sharpness = 50.0))
        // 2 is sharpest but eyes closed; 3 is as sharp with unknown eyes -> 3
        assertEquals(2, SimilarRules.pickKeeper(listOf(0, 1, 2, 3), items, f))
        val g = listOf(PhotoFeatures(0, sharpness = 10.0), PhotoFeatures(0, sharpness = 10.0))
        assertEquals(1, SimilarRules.pickKeeper(listOf(0, 1), listOf(photo(1), photo(2, size = 5_000_000)), g))
    }

    @Test
    fun `edited copies are kept, not offered`() {
        val keeper = photo(1)
        assertTrue(SimilarRules.isEditedCopy(photo(2, name = "IMG_1-edited.jpg"), keeper))
        assertTrue(SimilarRules.isEditedCopy(photo(3, name = "IMG_1~2.jpg"), keeper))
        assertTrue(SimilarRules.isEditedCopy(photo(4, w = 3000, h = 3000), keeper))
        assertFalse(SimilarRules.isEditedCopy(photo(5), keeper))
        val items = listOf(keeper, photo(2, name = "IMG_1-edited.jpg"), photo(5))
        val f = listOf(PhotoFeatures(0, sharpness = 99.0), PhotoFeatures(0, sharpness = 1.0), PhotoFeatures(0, sharpness = 1.0))
        val group = SimilarRules.groups(items, f, listOf(listOf(0, 1, 2))).single()
        assertEquals(keeper, group.keeper)
        assertEquals(listOf(items[1]), group.alsoKept)
        assertEquals(listOf(items[2]), group.others)
        assertEquals(3_000_000L, group.trashBytes)
    }

    @Test
    fun `a group whose only others are edited copies is not offered, nor a dismissed one`() {
        val items = listOf(photo(1), photo(2, name = "IMG_1-edited.jpg"))
        val f = listOf(PhotoFeatures(0, sharpness = 9.0), PhotoFeatures(0))
        assertTrue(SimilarRules.groups(items, f, listOf(listOf(0, 1))).isEmpty())
        val items2 = listOf(photo(1), photo(2))
        val g = SimilarRules.groups(items2, f, listOf(listOf(0, 1))).single()
        assertTrue(SimilarRules.groups(items2, f, listOf(listOf(0, 1)), dismissed = setOf(g.key)).isEmpty())
    }

    @Test
    fun `exclusions - trash, kept, excluded folders, RAW, screenshots`() {
        val p = photo(1)
        assertFalse(SimilarRules.excluded(p, inTrash = false, kept = false, excludedFolder = false))
        assertTrue(SimilarRules.excluded(p, inTrash = true, kept = false, excludedFolder = false))
        assertTrue(SimilarRules.excluded(p, inTrash = false, kept = true, excludedFolder = false))
        assertTrue(SimilarRules.excluded(p, inTrash = false, kept = false, excludedFolder = true))
        assertTrue(SimilarRules.excluded(photo(2, name = "IMG_2.dng"), false, false, false))
        assertTrue(SimilarRules.excluded(photo(3, folder = "Pictures/Screenshots"), false, false, false))
    }

    @Test
    fun `free - three groups for life, Pro - all, approve all after three seen`() {
        assertTrue(SimilarRules.canClean(isPro = false, groupsCleaned = 2))
        assertFalse(SimilarRules.canClean(isPro = false, groupsCleaned = 3))
        assertTrue(SimilarRules.canClean(isPro = true, groupsCleaned = 99))
        assertFalse(SimilarRules.approveAllEnabled(2))
        assertTrue(SimilarRules.approveAllEnabled(3))
    }

    @Test
    fun `camera folder`() {
        assertTrue(SimilarRules.isCameraFolder("DCIM/Camera"))
        assertFalse(SimilarRules.isCameraFolder("DCIM/Screenshots"))
        assertFalse(SimilarRules.isCameraFolder("Pictures/WhatsApp"))
    }

    @Test
    fun `thousands of photos with the same time stay a bounded number of pairs`() {
        val items = (0L until 3000L).map { photo(it, taken = 1_000_000L, folder = "Pictures/Restored") }
        val hashes = items.map { 0L }
        val pairs = SimilarRules.candidatePairs(items, hashes, maxDistance = 10)
        assertTrue(pairs.size <= SimilarRules.MAX_PAIRS)
    }

    @Test
    fun `a long burst still chains into one group`() {
        val items = (0L until 30L).map { photo(it, taken = 1_000L + it * 100L) }
        val pairs = SimilarRules.candidatePairs(items, items.map { it.id * 0x0F0F0F0F0FL }, maxDistance = 0)
        val same = items.map { vec(1f, 0f) }
        assertEquals(listOf(items.indices.toList()), SimilarRules.cluster(items.size, pairs, same).map { it.sorted() })
    }
}

class PhotoMathTest {
    @Test
    fun `flat image has no sharpness, a checkerboard has a lot`() {
        val flat = IntArray(16 * 16) { 128 }
        assertEquals(0.0, PhotoMath.laplacianVariance(flat, 16, 16), 1e-9)
        val board = IntArray(16 * 16) { if ((it % 16 + it / 16) % 2 == 0) 0 else 255 }
        assertTrue(PhotoMath.laplacianVariance(board, 16, 16) > 10_000.0)
    }

    @Test
    fun `quantized embeddings keep the cosine`() {
        val a = floatArrayOf(0.6f, 0.8f, 0f)
        val b = floatArrayOf(0.8f, 0.6f, 0f)
        val exact = SimilarRules.cosine(a, b)
        val q = SimilarRules.cosine(PhotoMath.dequantize(PhotoMath.quantize(a)), PhotoMath.dequantize(PhotoMath.quantize(b)))
        assertEquals(exact, q, 0.01)
    }

    @Test
    fun `luma and mean`() {
        assertEquals(255, PhotoMath.luma(0xFFFFFFFF.toInt()))
        assertEquals(0, PhotoMath.luma(0xFF000000.toInt()))
        assertEquals(50.0, PhotoMath.meanLuma(intArrayOf(0, 100)), 1e-9)
    }
}

class SimilarGainTest {
    @Test
    fun `similar frames are a measured Pro-only row, biggest first`() {
        val e = com.mrzekai.depoakilli.pro.ProGainEstimator.estimate(
            suggestedBytes = 500_000_000L,
            suggestedCount = 10,
            firstCleanupUsed = true,
            similarBytes = 1_800_000_000L,
            similarCount = 312,
        )
        assertEquals(com.mrzekai.depoakilli.pro.ProGainEstimator.Kind.SIMILAR_PHOTOS, e.rows.first().kind)
        assertTrue(e.rows.first().measured)
        assertEquals(1_800_000_000L, e.proOnlyBytes)
        assertTrue(e.qualifies)
    }

    @Test
    fun `switch off - no similar row`() {
        val e = com.mrzekai.depoakilli.pro.ProGainEstimator.estimate(0L, 0, false, similarBytes = 9_000_000_000L, similarCount = 9, similarAvailable = false)
        assertTrue(e.rows.isEmpty())
    }
}
