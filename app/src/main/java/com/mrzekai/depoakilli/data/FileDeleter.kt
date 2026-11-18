package com.mrzekai.depoakilli.data

import java.io.File

internal object FileDeleter {
    enum class Result { DELETED, ORIGINAL_CHANGED, DELETE_FAILED }

    fun deleteUnprotected(file: File, relativeDirPath: String): Boolean {
        if (StoragePathRules.isProtectedUserDataPath(relativeDirPath, file.name)) return false
        return file.delete()
    }

    fun deleteIfOriginalUnchanged(original: File?, expectedSize: Long?, delete: () -> Boolean): Result {
        if (original == null || expectedSize == null || !original.isFile || original.length() != expectedSize) {
            return Result.ORIGINAL_CHANGED
        }
        return if (delete()) Result.DELETED else Result.DELETE_FAILED
    }
}
