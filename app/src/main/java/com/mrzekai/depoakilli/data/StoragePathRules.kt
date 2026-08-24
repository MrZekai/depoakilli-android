package com.mrzekai.depoakilli.data

import java.text.Normalizer
import java.util.Locale

/**
 * Locale-independent normalization for storage-path and filename decisions.
 *
 * Locale.ROOT prevents Turkish-I case conversion from depending on the device
 * language. NFD + combining-mark folding also makes both "İndirilenler" and
 * "INDIRILENLER" converge to the same searchable form.
 */
internal object StoragePathRules {
    private val combiningMarks = Regex("\\p{M}+")
    private val downloadMarkers = listOf(
        "/download/",
        "/downloads/",
        "/indirilen/",
        "/indirilenler/",
    )

    fun normalizeText(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
            .lowercase(Locale.ROOT)
        val folded = combiningMarks
            .replace(decomposed, "")
            .replace('ı', 'i')
        return Normalizer.normalize(folded, Normalizer.Form.NFC)
    }

    fun normalizePath(path: String): String {
        val slashed = path.replace('\\', '/')
        return "/${normalizeText(slashed).trim('/')}/"
    }

    fun isDownloadPath(path: String): Boolean {
        val normalized = normalizePath(path)
        return downloadMarkers.any(normalized::contains)
    }
}
