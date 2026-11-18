package com.mrzekai.depoakilli.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import com.mrzekai.depoakilli.data.DeviceRepository.FailureReason
import com.mrzekai.depoakilli.model.CleanupGroup
import com.mrzekai.depoakilli.model.DeleteMode
import java.io.File

/**
 * Moves one shared-storage file to the in-app trash or deletes it, after
 * the snapshot, path-protection and canonical-root checks (T3.5).
 */
internal class SharedStorageRemover(
    private val resolver: ContentResolver,
    private val trashStore: TrashStore,
    private val onRemoved: () -> Unit,
) {
    fun removeFile(
        uriString: String,
        expectedSizeBytes: Long,
        expectedModifiedAtMillis: Long,
        mode: DeleteMode,
        group: CleanupGroup,
        roots: List<File>,
    ): Removal = runCatching {
        val uri = Uri.parse(uriString)
        if (uri.scheme != "file") return@runCatching Removal.Failed(FailureReason.DENIED)
        val file = File(uri.path ?: return@runCatching Removal.Failed(FailureReason.DENIED))
        if (!file.exists()) return@runCatching Removal.Failed(FailureReason.MISSING)
        if (!fileSnapshotStillMatches(uriString, expectedSizeBytes, expectedModifiedAtMillis)) {
            return@runCatching Removal.Failed(FailureReason.CHANGED)
        }
        // Path rules expect a path relative to the storage volume root
        // (e.g. "WhatsApp/Databases"), not the absolute path.
        val relativeDir = volumeRootOf(file, roots)?.let { root ->
            runCatching { file.parentFile?.relativeTo(root)?.invariantSeparatorsPath }.getOrNull()
        } ?: return@runCatching Removal.Failed(FailureReason.PROTECTED)
        if (
            !isDeletableSharedStorageFile(file, roots) ||
            StoragePathRules.isSkippedHiddenFile(file.name) ||
            StoragePathRules.isProtectedUserDataPath(relativeDir, file.name)
        ) {
            return@runCatching Removal.Failed(FailureReason.PROTECTED)
        }
        val removal = when (mode) {
            DeleteMode.TRASH -> {
                val root = volumeRootOf(file, roots)
                val entry = root?.let { trashStore.moveToTrash(file, it, group) }
                when {
                    entry != null -> Removal.Trashed(entry.id)
                    // The Trash folder itself cannot be created on this volume
                    // (some manufacturers block it): reported separately.
                    root == null || !trashStore.canUseTrash(root) -> Removal.Failed(FailureReason.TRASH_FOLDER_UNAVAILABLE)
                    else -> Removal.Failed(FailureReason.TRASH_UNAVAILABLE)
                }
            }
            DeleteMode.PERMANENT -> {
                if (FileDeleter.deleteUnprotected(file, relativeDir)) {
                    removeDeletedPathFromMediaStore(resolver, file.absolutePath)
                    Removal.Deleted
                } else {
                    Removal.Failed(FailureReason.DENIED)
                }
            }
        }
        if (removal !is Removal.Failed) onRemoved()
        removal
    }.getOrDefault(Removal.Failed(FailureReason.DENIED))
}

internal fun volumeRootOf(file: File, roots: List<File>): File? = roots.firstOrNull { root ->
    canonicalPathWithinRoot(canonicalPathOf(root).trimEnd(File.separatorChar), file) != null
}

/**
 * Defence in depth for every destructive path.
 *
 * Scan results already originate from the shared-storage roots, but deletion is
 * irreversible, so the final gate re-resolves the target through its
 * canonical path and refuses anything that is not inside a shared-storage
 * volume, or that lives in the app-private Android/data and Android/obb
 * trees this engine deliberately never manages. This also neutralises
 * symlinks that point outside shared storage.
 */
internal fun isDeletableSharedStorageFile(file: File, roots: List<File>): Boolean {
    val canonical = canonicalPathOf(file)
    val root = roots.firstOrNull { root ->
        canonicalPathWithinRoot(canonicalPathOf(root).trimEnd(File.separatorChar), file) != null
    } ?: return false
    val rootPath = canonicalPathOf(root).trimEnd('/')
    val relative = StoragePathRules.normalizePath(canonical.removePrefix(rootPath))
    return !StoragePathRules.isProtectedAppPrivatePath(relative) &&
        !StoragePathRules.isProtectedUserDataPath(relative.substringBeforeLast('/', ""), file.name)
}

@Suppress("DEPRECATION")
internal fun removeDeletedPathFromMediaStore(resolver: ContentResolver, absolutePath: String) {
    runCatching {
        // B4: also match rows in Android's trash and pending rows, so a
        // file deleted from "Already in Android's trash" leaves no ghost
        // entry in the Gallery trash. Errors are ignored as before.
        val extras = android.os.Bundle().apply {
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.MediaColumns.DATA}=?")
            putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(absolutePath))
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
            putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
        }
        resolver.delete(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), extras)
    }
}

internal fun fileSnapshotStillMatches(
    uriString: String,
    expectedSizeBytes: Long,
    expectedModifiedAtMillis: Long,
): Boolean = runCatching {
    val uri = Uri.parse(uriString)
    if (uri.scheme != "file") return@runCatching true
    val file = File(requireNotNull(uri.path))
    file.isFile &&
        file.length() == expectedSizeBytes &&
        (expectedModifiedAtMillis <= 0L || file.lastModified() == expectedModifiedAtMillis)
}.getOrDefault(false)

internal sealed interface Removal {
    data object Deleted : Removal
    data class Trashed(val entryId: String) : Removal
    data class Failed(val reason: FailureReason) : Removal
}

internal class RemovalTally {
    val removedIds = hashSetOf<String>()
    val failed = LinkedHashMap<String, FailureReason>()
    val trashEntryIds = ArrayList<String>()
    var deletedBytes = 0L
    var trashedBytes = 0L

    fun record(id: String, sizeBytes: Long, removal: Removal) {
        when (removal) {
            Removal.Deleted -> {
                removedIds += id
                deletedBytes += sizeBytes
            }
            is Removal.Trashed -> {
                removedIds += id
                trashedBytes += sizeBytes
                trashEntryIds += removal.entryId
            }
            is Removal.Failed -> failed[id] = removal.reason
        }
    }

    fun toDirectResult(attemptedIds: Set<String>) = DeviceRepository.DirectDeleteResult(
        attemptedIds = attemptedIds,
        deletedIds = removedIds,
        deletedBytes = deletedBytes,
        trashedBytes = trashedBytes,
        trashEntryIds = trashEntryIds,
        failed = failed,
    )
}
