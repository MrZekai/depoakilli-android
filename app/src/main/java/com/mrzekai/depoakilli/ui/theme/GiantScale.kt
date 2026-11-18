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

        /** One size down (SMALL stays SMALL). */
        fun stepDown(scale: GiantScale): GiantScale = when (scale) {
            LARGE -> MEDIUM
            else -> SMALL
        }

        /**
         * Height the plan Home needs at [scale] (D7): label, figure, line under
         * it, button, the split sentence, "See what else" and — when files wait
         * in Trash — the Trash line with 12 dp to the banner, plus the page padding.
         */
        fun homeNeedDp(scale: GiantScale, fontScale: Float, trashLine: Boolean): Float {
            val text = fontScale.coerceAtLeast(1f)
            val figure = scale.numberSp * 1.15f * text
            val lines = (24f + 24f + 42f) * text
            val links = 48f + if (trashLine) 48f + 12f else 0f
            return 36f + 8f + figure + lines + 20f + scale.buttonDp + 16f + links
        }

        /**
         * Faz 5D (D7): the Home scale is chosen for the height the page really
         * has (screen minus status bar, banner, menu and navigation bar). With
         * the Trash line it is one size smaller; then it steps down until the
         * content fits. If even SMALL does not fit, the page scrolls.
         */
        fun forHome(screenHeightDp: Int, availableHeightDp: Float, fontScale: Float, trashLine: Boolean): GiantScale {
            var scale = of(screenHeightDp, fontScale)
            if (trashLine) scale = stepDown(scale)
            while (scale != SMALL && homeNeedDp(scale, fontScale, trashLine) > availableHeightDp) {
                scale = stepDown(scale)
            }
            return scale
        }
    }
}
