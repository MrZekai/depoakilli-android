package com.mrzekai.depoakilli.data

import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.AiAssessment
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.CleanupGroup
import com.mrzekai.depoakilli.model.IndexedFile
import com.mrzekai.depoakilli.model.ScanFocus
import java.util.concurrent.TimeUnit

/** Which files each scan focus looks at and lists (T3.5). */
internal object ScanFocusRules {
    val MEDIA_CATEGORIES = setOf(
        CleanCategory.SCREENSHOT,
        CleanCategory.LARGE_FILE,
        CleanCategory.DUPLICATE,
    )
    val DOWNLOAD_TOOL_GROUPS = setOf(
        CleanupGroup.DOWNLOAD_FILE,
        CleanupGroup.DOWNLOAD_DOCUMENT,
        CleanupGroup.PARTIAL_DOWNLOADS,
        CleanupGroup.TEMP_FILES,
        CleanupGroup.LARGE_VIDEO,
        CleanupGroup.OLD_VIDEO,
        CleanupGroup.LARGE_OTHER,
    )

    fun matchesFocus(item: CleanableItem, focus: ScanFocus): Boolean =
        when (focus) {
            ScanFocus.SMART,
            ScanFocus.DEEP -> true
            ScanFocus.JUNK -> item.assessment.category == CleanCategory.JUNK
            ScanFocus.DUPLICATES -> item.assessment.category == CleanCategory.DUPLICATE
            ScanFocus.LARGE_FILES -> item.assessment.category == CleanCategory.LARGE_FILE
            ScanFocus.MEDIA -> item.assessment.category in MEDIA_CATEGORIES
            ScanFocus.SCREENSHOTS -> item.assessment.category == CleanCategory.SCREENSHOT
            ScanFocus.DOWNLOADS -> item.assessment.group in DOWNLOAD_TOOL_GROUPS
            ScanFocus.APKS -> item.assessment.category == CleanCategory.APK_PACKAGE
            ScanFocus.ANALYZE -> false
        }

    fun assessmentForFocus(aiEngine: AiCleaningEngine, file: IndexedFile, focus: ScanFocus): AiAssessment? {
        return when (focus) {
            ScanFocus.SMART -> aiEngine.assess(file)
            ScanFocus.DEEP -> aiEngine.assessDeep(file)

            ScanFocus.JUNK -> {
                if (StoragePathRules.isDownloadPath(file.relativePath)) {
                    null
                } else {
                    aiEngine.assess(file)?.takeIf {
                        it.category == CleanCategory.JUNK
                    }
                }
            }

            ScanFocus.DUPLICATES,
            ScanFocus.ANALYZE -> null

            ScanFocus.APKS -> aiEngine.assess(file)?.takeIf {
                it.category == CleanCategory.APK_PACKAGE
            }

            ScanFocus.LARGE_FILES -> if (file.sizeBytes >= CleaningThresholds.LARGE_BYTES) {
                AiAssessment(
                    category = CleanCategory.LARGE_FILE,
                    safetyScore = 60,
                    reasonRes = 0,
                    reasonPluralRes = R.plurals.reason_large_file,
                    reasonQuantity = ageDays(file.modifiedAtMillis).coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
                    reasonArgs = listOf(ageDays(file.modifiedAtMillis)),
                    recommended = false,
                )
            } else {
                null
            }

            ScanFocus.MEDIA -> {
                if (!file.mimeType.startsWith("image/") && !file.mimeType.startsWith("video/")) {
                    null
                } else {
                    aiEngine.assessDeep(file)?.takeIf {
                        it.category == CleanCategory.SCREENSHOT || it.category == CleanCategory.LARGE_FILE
                    }
                }
            }

            ScanFocus.SCREENSHOTS -> aiEngine.assessFocusedScreenshot(file)

            ScanFocus.DOWNLOADS -> {
                if (!StoragePathRules.isDownloadPath(file.relativePath)) {
                    null
                } else {
                    aiEngine.assessDeep(file)?.takeIf { it.group in DOWNLOAD_TOOL_GROUPS }
                }
            }
        }
    }

    fun ageDays(modifiedAtMillis: Long): Long {
        if (modifiedAtMillis <= 0L) return 0L
        return TimeUnit.MILLISECONDS.toDays(
            (System.currentTimeMillis() - modifiedAtMillis).coerceAtLeast(0L),
        )
    }
}
