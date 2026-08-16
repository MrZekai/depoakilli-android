package com.mrzekai.depoakilli.model

enum class CleanCategory(
    val title: String,
    val shortDescription: String,
) {
    DUPLICATE("Yinelenenler", "Aynı içeriğin gereksiz kopyaları"),
    SCREENSHOT("Ekran görüntüleri", "Eski ekran görüntüleri"),
    LARGE_VIDEO("Büyük videolar", "Depolamayı en çok kullanan videolar"),
    OLD_DOWNLOAD("Eski indirilenler", "Uzun süredir açılmayan dosyalar"),
    APK_PACKAGE("APK paketleri", "Kurulumdan kalan paketler"),
    APP_CACHE("Önbellek", "DepoAkıllı geçici dosyaları"),
}

data class IndexedFile(
    val uri: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val modifiedAtMillis: Long,
    val relativePath: String,
)

data class AiAssessment(
    val category: CleanCategory,
    val safetyScore: Int,
    val reason: String,
    val recommended: Boolean,
)

data class CleanableItem(
    val id: String,
    val uri: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val modifiedAtMillis: Long,
    val relativePath: String,
    val assessment: AiAssessment,
    val selected: Boolean = true,
)

data class StorageSnapshot(
    val totalBytes: Long = 0,
    val availableBytes: Long = 0,
) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0)
    val usedFraction: Float
        get() = if (totalBytes == 0L) 0f else usedBytes.toFloat() / totalBytes.toFloat()
}

data class MemorySnapshot(
    val totalBytes: Long = 0,
    val availableBytes: Long = 0,
    val lowMemory: Boolean = false,
) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0)
    val usedFraction: Float
        get() = if (totalBytes == 0L) 0f else usedBytes.toFloat() / totalBytes.toFloat()
}

data class ScanSummary(
    val items: List<CleanableItem> = emptyList(),
    val scannedFileCount: Int = 0,
    val limitedAccess: Boolean = false,
) {
    val selectedItems: List<CleanableItem> get() = items.filter(CleanableItem::selected)
    val selectedBytes: Long get() = selectedItems.sumOf(CleanableItem::sizeBytes)
    val totalSuggestedBytes: Long get() = items.sumOf(CleanableItem::sizeBytes)
    val byCategory: Map<CleanCategory, List<CleanableItem>> get() = items.groupBy { it.assessment.category }
}

object ByteFormatter {
    private val units = listOf("B", "KB", "MB", "GB", "TB")

    fun format(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024
            unitIndex++
        }
        return if (value >= 100 || unitIndex == 0) {
            "%.0f %s".format(value, units[unitIndex])
        } else {
            "%.1f %s".format(value, units[unitIndex])
        }
    }
}
