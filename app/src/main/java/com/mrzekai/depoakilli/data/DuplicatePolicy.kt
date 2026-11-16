package com.mrzekai.depoakilli.data

import com.mrzekai.depoakilli.model.IndexedFile
import com.mrzekai.depoakilli.model.CleanableItem

internal object DuplicatePolicy {
    data class ScanResult(
        val copies: List<CleanableItem> = emptyList(),
        val protectedUris: Set<String> = emptySet(),
    ) {
        fun mergeWith(assessments: List<CleanableItem>): Sequence<CleanableItem> =
            (copies.asSequence() + assessments.asSequence())
                .filterNot { it.uri in protectedUris }
                .distinctBy(CleanableItem::uri)
    }

    data class Decision(
        val keep: IndexedFile,
    )

    fun choose(files: List<IndexedFile>): Decision? {
        if (files.size < 2) return null
        val keep = files.minWithOrNull(
            compareByDescending<IndexedFile>(::isCameraOriginal)
                .thenBy(IndexedFile::modifiedAtMillis)
                .thenBy { it.relativePath.length },
        ) ?: return null
        return Decision(keep = keep)
    }

    // Called only after both files have a matching full SHA-256 hash.
    fun autoSelectable(copy: IndexedFile, keep: IndexedFile, copyVolumeRoot: String, keepVolumeRoot: String): Boolean {
        if (copyVolumeRoot.isBlank() || copyVolumeRoot != keepVolumeRoot) return false
        val path = StoragePathRules.normalizePath(copy.relativePath)
        if (path.contains("/android/media/") || path.startsWith("/whatsapp/") ||
            path.startsWith("/whatsapp business/")) return false
        val name = StoragePathRules.normalizeText(copy.name).substringBeforeLast('.', copy.name)
        val copyName = Regex("""(?i).*( \([1-9][0-9]?\)| - copy|_copy| copy)$""").matches(name)
        return StoragePathRules.isDownloadPath(copy.relativePath) ||
            path == StoragePathRules.normalizePath(keep.relativePath) || copyName
    }

    private fun isCameraOriginal(file: IndexedFile): Boolean =
        StoragePathRules.normalizePath(file.relativePath).startsWith("/dcim/")
}
