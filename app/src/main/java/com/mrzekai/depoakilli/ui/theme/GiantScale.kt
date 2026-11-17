package com.mrzekai.depoakilli.ui.theme

/**
 * Size of the giant figure and the giant button (Faz 5 scale rule). Pure so
 * the thresholds are covered by unit tests.
 *
 * | usable height          | figure | button |
 * |------------------------|--------|--------|
 * | ≥ 760 dp               | 104 sp | 208 dp |
 * | 640–759 dp             |  84 sp | 176 dp |
 * | < 640 dp or font ≥ 1.3 |  64 sp | 144 dp |
 */
data class GiantScale(val numberSp: Int, val buttonDp: Int) {
    companion object {
        val LARGE = GiantScale(numberSp = 104, buttonDp = 208)
        val MEDIUM = GiantScale(numberSp = 84, buttonDp = 176)
        val SMALL = GiantScale(numberSp = 64, buttonDp = 144)

        /** The giant figure never shrinks below this when it has to fit one line. */
        const val MIN_NUMBER_SP = 44

        /** Content is centred and never wider than this on landscape and large screens. */
        const val MAX_CONTENT_WIDTH_DP = 480

        fun of(usableHeightDp: Int, fontScale: Float): GiantScale = when {
            usableHeightDp < 640 || fontScale >= 1.3f -> SMALL
            usableHeightDp < 760 -> MEDIUM
            else -> LARGE
        }
    }
}
