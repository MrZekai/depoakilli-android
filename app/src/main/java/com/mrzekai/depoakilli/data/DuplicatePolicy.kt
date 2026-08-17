package com.mrzekai.depoakilli.data

import com.mrzekai.depoakilli.model.IndexedFile

internal object DuplicatePolicy {
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
        val path = file.relativePath.lowercase()
        return path.contains("dcim/") || path.contains("/dcim") ||
            path.contains("camera/") || path.contains("/camera")
    }
}
