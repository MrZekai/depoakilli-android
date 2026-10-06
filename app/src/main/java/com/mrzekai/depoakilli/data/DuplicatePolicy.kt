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
        val automaticSelectionIsSafe: Boolean,
    )

    fun choose(files: List<IndexedFile>): Decision? {
        if (files.size < 2) return null
        val keep = files.minWithOrNull(
            compareByDescending<IndexedFile>(::isCameraOriginal)
                .thenBy(IndexedFile::modifiedAtMillis)
                .thenBy { it.relativePath.length },
        ) ?: return null
        return Decision(
            keep = keep,
            automaticSelectionIsSafe = files.count(::isCameraOriginal) == 1,
        )
    }

    private fun isCameraOriginal(file: IndexedFile): Boolean {
        val path = StoragePathRules.normalizePath(file.relativePath)
        return path.contains("dcim/") || path.contains("/dcim") ||
            path.contains("camera/") || path.contains("/camera")
    }
}
