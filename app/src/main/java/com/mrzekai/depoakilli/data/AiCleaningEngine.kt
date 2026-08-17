package com.mrzekai.depoakilli.data

import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.AiAssessment
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.IndexedFile
import java.util.concurrent.TimeUnit

/**
 * Conservative on-device rules used by Smart Scan.
 *
 * The engine only preselects items that are strongly likely to be disposable
 * (temporary files, stale installers, WhatsApp statuses, and unambiguous exact
 * duplicate copies). User-created downloads, screenshots, large files and sent
 * media are shown for review but are not silently selected.
 */
class AiCleaningEngine(
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    fun assess(file: IndexedFile): AiAssessment? {
        val ageDays = ageDays(file.modifiedAtMillis)
        val path = normalize(file.relativePath)
        val name = file.name.lowercase()
        val mime = file.mimeType.lowercase()

        val isWhatsApp = path.contains("whatsapp")
        val isWhatsAppStatus = isWhatsApp && path.contains("/.statuses/")
        val isWhatsAppSent = isWhatsApp && (path.contains("/sent/") || path.endsWith("/sent"))
        if (isWhatsAppStatus || (isWhatsAppSent && ageDays >= 30)) {
            return AiAssessment(
                category = CleanCategory.WHATSAPP_MEDIA,
                safetyScore = if (isWhatsAppStatus) 98 else 78,
                reasonRes = if (isWhatsAppStatus) R.string.reason_whatsapp_status else R.string.reason_whatsapp_sent,
                reasonArgs = if (isWhatsAppStatus) emptyList() else listOf(ageDays),
                recommended = isWhatsAppStatus,
            )
        }

        if (isTemporary(file, path, name, ageDays)) {
            return AiAssessment(
                category = CleanCategory.JUNK,
                safetyScore = if (path.contains("/.thumbnails/")) 97 else 94,
                reasonRes = if (path.contains("/.thumbnails/")) {
                    R.string.reason_thumbnail_cache
                } else {
                    R.string.reason_temporary_file
                },
                reasonArgs = listOf(ageDays),
                recommended = true,
            )
        }

        if (name.endsWith(".apk") || mime == APK_MIME) {
            val oldEnough = ageDays >= 7
            return AiAssessment(
                category = CleanCategory.APK_PACKAGE,
                safetyScore = if (ageDays >= 30) 96 else if (oldEnough) 91 else 70,
                reasonRes = if (oldEnough) R.string.reason_old_installer else R.string.reason_downloaded_installer,
                reasonArgs = if (oldEnough) listOf(ageDays) else emptyList(),
                recommended = oldEnough,
            )
        }

        val isScreenshot = path.contains("/screenshots/") ||
            name.startsWith("screenshot") ||
            name.startsWith("ekran_görüntüsü") ||
            name.startsWith("ekran görüntüsü")
        if (isScreenshot && ageDays >= 14) {
            return AiAssessment(
                category = CleanCategory.SCREENSHOT,
                safetyScore = if (ageDays >= 90) 88 else 78,
                reasonRes = R.string.reason_old_screenshot,
                reasonArgs = listOf(ageDays),
                recommended = false,
            )
        }

        val isDownload = path.contains("/download/") || path.contains("/downloads/") || path.contains("/indirilen")
        if (isDownload && ageDays >= 90) {
            return AiAssessment(
                category = CleanCategory.OLD_DOWNLOAD,
                safetyScore = if (ageDays >= 365) 84 else 72,
                reasonRes = R.string.reason_old_download,
                reasonArgs = listOf(ageDays),
                recommended = false,
            )
        }

        if (file.sizeBytes >= LARGE_FILE_BYTES) {
            return AiAssessment(
                category = CleanCategory.LARGE_FILE,
                safetyScore = if (ageDays >= 180) 76 else 60,
                reasonRes = R.string.reason_large_file,
                reasonArgs = listOf(ageDays.coerceAtLeast(0)),
                recommended = false,
            )
        }

        return null
    }

    fun duplicateAssessment(automaticSelectionIsSafe: Boolean): AiAssessment = AiAssessment(
        category = CleanCategory.DUPLICATE,
        safetyScore = if (automaticSelectionIsSafe) 99 else 86,
        reasonRes = R.string.reason_exact_duplicate,
        recommended = automaticSelectionIsSafe,
    )

    private fun isTemporary(
        file: IndexedFile,
        path: String,
        name: String,
        ageDays: Long,
    ): Boolean {
        if (ageDays < TEMP_MIN_AGE_DAYS) return false
        val tempExtension = TEMP_EXTENSIONS.any(name::endsWith)
        val thumbnail = path.contains("/.thumbnails/") || path.contains("/thumbnails/")
        val tempFolder = path.contains("/temp/") || path.contains("/tmp/") || path.contains("/temporary/")
        val knownTinyArtifact = file.sizeBytes <= 10L * 1024L * 1024L &&
            (name == "thumbs.db" || name.endsWith(".log.old") || name.endsWith(".bak.tmp"))
        return tempExtension || thumbnail || tempFolder || knownTinyArtifact
    }

    private fun ageDays(modifiedAtMillis: Long): Long {
        if (modifiedAtMillis <= 0L) return 0
        return TimeUnit.MILLISECONDS.toDays((nowMillis() - modifiedAtMillis).coerceAtLeast(0))
    }

    private fun normalize(path: String): String = "/${path.replace('\\', '/').trim('/')}/".lowercase()

    companion object {
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val LARGE_FILE_BYTES = 100L * 1024L * 1024L
        private const val TEMP_MIN_AGE_DAYS = 3L
        private val TEMP_EXTENSIONS = listOf(".tmp", ".temp", ".part", ".crdownload", ".download", ".cache")
    }
}
