package com.mrzekai.depoakilli.ui.common

import android.text.BidiFormatter

/**
 * Wraps a left-to-right fragment (file path, file name, size) so it keeps
 * its order inside right-to-left text such as Arabic (T2.7).
 */
internal fun bidiWrap(text: String): String =
    runCatching { BidiFormatter.getInstance().unicodeWrap(text) }.getOrDefault(text)
