package com.mrzekai.depoakilli.photo

/**
 * Classic image measurements (no model): sharpness and brightness of a small
 * grey thumbnail. Pure; unit-tested.
 */
object PhotoMath {
    /** Variance of the 4-neighbour Laplacian; low = blurry. [gray] is row-major, 0..255. */
    fun laplacianVariance(gray: IntArray, width: Int, height: Int): Double {
        if (width < 3 || height < 3 || gray.size < width * height) return 0.0
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val i = row + x
                val v = (gray[i - 1] + gray[i + 1] + gray[i - width] + gray[i + width] - 4 * gray[i]).toDouble()
                sum += v
                sumSq += v * v
                n++
            }
        }
        if (n == 0) return 0.0
        val mean = sum / n
        return sumSq / n - mean * mean
    }

    /** Mean grey value 0..255. */
    fun meanLuma(gray: IntArray): Double = if (gray.isEmpty()) 0.0 else gray.average()

    /** Rec. 601 luma of an ARGB pixel. */
    fun luma(argb: Int): Int {
        val r = argb shr 16 and 0xFF
        val g = argb shr 8 and 0xFF
        val b = argb and 0xFF
        return (299 * r + 587 * g + 114 * b) / 1000
    }

    /** Embeddings are cached as signed bytes (x127 after L2-normalising). */
    fun quantize(v: FloatArray): ByteArray = ByteArray(v.size) { (v[it] * 127f).toInt().coerceIn(-127, 127).toByte() }

    fun dequantize(b: ByteArray): FloatArray = FloatArray(b.size) { b[it] / 127f }
}
