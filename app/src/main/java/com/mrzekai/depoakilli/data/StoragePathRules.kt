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

    fun isWhatsAppPath(path: String): Boolean {
        val normalized = normalizePath(path)
        return normalized.contains("/android/media/com.whatsapp/") ||
            normalized.contains("/android/media/com.whatsapp.w4b/") ||
            normalized.startsWith("/whatsapp/") ||
            normalized.startsWith("/whatsapp business/")
    }

    fun isProtectedUserDataPath(relativeDirPath: String, fileName: String): Boolean {
        val path = normalizePath(relativeDirPath)
        val name = normalizeText(fileName)
        return (isWhatsAppPath(path) && (path.contains("/databases/") || path.contains("/backups/"))) ||
            Regex(""".*\.crypt\d+$""").matches(name) || name.startsWith("msgstore")
    }

    /**
     * The user's own camera folder (B3): DCIM/… except screenshots and hidden
     * caches such as DCIM/.thumbnails.
     */
    fun isCameraPath(relativePath: String): Boolean {
        val normalized = normalizePath(relativePath)
        return normalized.startsWith("/dcim/") &&
            !normalized.startsWith("/dcim/.") &&
            !normalized.contains("/screenshots/")
    }

    /**
     * Media saved by another app (V6): Android/media/<package>/ except WhatsApp,
     * or a Telegram folder at the volume root.
     */
    fun isOtherAppMediaPath(path: String): Boolean {
        val normalized = normalizePath(path)
        if (isWhatsAppPath(normalized)) return false
        val segments = normalized.trim('/').split('/')
        val inAndroidMedia = segments.size >= 3 && segments[0] == "android" && segments[1] == "media" && segments[2].isNotBlank()
        return inAndroidMedia || normalized.startsWith("/telegram/")
    }

    fun isHiddenDirectoryAllowed(name: String): Boolean =
        normalizeText(name) in setOf(".thumbnails", ".statuses")

    /** Hidden dot-files may be system metadata or personal content. Never offer or delete them. */
    fun isSystemMarkerFile(name: String): Boolean =
        normalizeText(name.trim()).startsWith(".")

    private val SYSTEM_TRASH_NAME = Regex("""^\.trashed-\d+-.+""")
    private val STALE_PENDING_NAME = Regex("""^\.pending-\d+-.+""")

    /** A file in Android's own MediaStore trash (deleted in Gallery/Files, kept ~30 days). */
    fun isSystemTrashFile(name: String): Boolean = SYSTEM_TRASH_NAME.matches(name.trim())

    /** A MediaStore write that never finished. */
    fun isStalePendingFile(name: String): Boolean = STALE_PENDING_NAME.matches(name.trim())

    /** The only hidden files that are indexed and may be deleted (V4). */
    fun isIndexableHiddenFile(name: String): Boolean = isSystemTrashFile(name) || isStalePendingFile(name)

    /**
     * Files for the storage lists (Storage Analysis, type totals, folder lists):
     * Android's trash and unfinished writes appear only in the cleanup plan (B1).
     */
    fun <T> visibleInStorageLists(files: List<T>, name: (T) -> String): List<T> =
        files.filterNot { isSystemMarkerFile(name(it)) }

    /** Hidden files that stay invisible: every dot-file except the two MediaStore patterns. */
    fun isSkippedHiddenFile(name: String): Boolean = isSystemMarkerFile(name) && !isIndexableHiddenFile(name)

    fun isProtectedAppPrivatePath(path: String): Boolean {
        val normalized = normalizePath(path)
        return normalized == "/android/data/" ||
            normalized.startsWith("/android/data/") ||
            normalized == "/android/obb/" ||
            normalized.startsWith("/android/obb/")
    }
}
