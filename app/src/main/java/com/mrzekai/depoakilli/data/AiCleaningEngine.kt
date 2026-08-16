package com.mrzekai.depoakilli.data

import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.AiAssessment
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.IndexedFile
import java.util.concurrent.TimeUnit

/**
 * Conservative, fully on-device recommendation engine.
 *
 * Version 1 uses explainable signals rather than uploading user files to a remote model.
 * Every recommendation has a reason and no file is deleted without Android system consent.
 */
class AiCleaningEngine(
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    fun assess(file: IndexedFile): AiAssessment? {
        val ageDays = ageDays(file.modifiedAtMillis)
        val path = file.relativePath.lowercase()
        val name = file.name.lowercase()
        val mime = file.mimeType.lowercase()

        if (name.endsWith(".apk") || mime == APK_MIME) {
            val score = when {
                ageDays >= 30 -> 96
                ageDays >= 7 -> 90
                else -> 72
            }
            return AiAssessment(
                category = CleanCategory.APK_PACKAGE,
                safetyScore = score,
                reasonRes = if (ageDays >= 7) R.string.reason_old_installer else R.string.reason_downloaded_installer,
                reasonArgs = if (ageDays >= 7) listOf(ageDays) else emptyList(),
                recommended = ageDays >= 7,
            )
        }

        val isScreenshot = path.contains("screenshot") ||
            name.startsWith("screenshot") ||
            name.startsWith("ekran_görüntüsü") ||
            name.startsWith("ekran görüntüsü")
        if (isScreenshot && ageDays >= 14) {
            return AiAssessment(
                category = CleanCategory.SCREENSHOT,
                safetyScore = if (ageDays >= 90) 91 else 82,
                reasonRes = R.string.reason_old_screenshot,
                reasonArgs = listOf(ageDays),
                recommended = true,
            )
        }

        if (mime.startsWith("video/") && file.sizeBytes >= LARGE_VIDEO_BYTES) {
            val score = when {
                ageDays >= 180 -> 88
                ageDays >= 60 -> 79
                else -> 64
            }
            return AiAssessment(
                category = CleanCategory.LARGE_VIDEO,
                safetyScore = score,
                reasonRes = R.string.reason_large_video,
                reasonArgs = listOf(ageDays.coerceAtLeast(0)),
                recommended = ageDays >= 30,
            )
        }

        val isDownload = path.contains("download") || path.contains("indirilen")
        if (isDownload && ageDays >= 90) {
            return AiAssessment(
                category = CleanCategory.OLD_DOWNLOAD,
                safetyScore = if (ageDays >= 365) 90 else 76,
                reasonRes = R.string.reason_old_download,
                reasonArgs = listOf(ageDays),
                recommended = true,
            )
        }

        return null
    }

    fun duplicateAssessment(isExact: Boolean): AiAssessment = AiAssessment(
        category = CleanCategory.DUPLICATE,
        safetyScore = if (isExact) 99 else 72,
        reasonRes = if (isExact) R.string.reason_exact_duplicate else R.string.reason_probable_duplicate,
        recommended = isExact,
    )

    private fun ageDays(modifiedAtMillis: Long): Long {
        if (modifiedAtMillis <= 0L) return 0
        return TimeUnit.MILLISECONDS.toDays((nowMillis() - modifiedAtMillis).coerceAtLeast(0))
    }

    companion object {
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val LARGE_VIDEO_BYTES = 150L * 1024L * 1024L
    }
}
