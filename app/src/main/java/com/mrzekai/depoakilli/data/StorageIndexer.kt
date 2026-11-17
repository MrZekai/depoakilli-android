package com.mrzekai.depoakilli.data

import android.os.SystemClock
import com.mrzekai.depoakilli.model.IndexedFile
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Walks shared storage for the scanners (T3.5). Free of Context so the
 * symlink, cap and skip rules are covered by plain JVM tests.
 */
internal class StorageIndexer(
    private val fileLimit: () -> Int,
    private val makeIndexedFile: (storageRoot: File, file: File) -> IndexedFile,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) {
    suspend fun indexSharedStorage(
        storageRoots: List<File>,
        onTraversalProgress: (visitedDirectories: Int, discoveredFiles: Int) -> Unit,
    ): List<IndexedFile> {
        val roots = storageRoots.mapNotNull(::scanRoot)
        if (roots.isEmpty()) return emptyList()
        // An unreadable primary volume must surface as a failed scan, never as
        // an empty "nothing to clean" result or a fake storage-change snapshot.
        if (runCatching { roots.first().file.listFiles() }.getOrNull() == null) {
            throw java.io.IOException("Shared storage root is not readable")
        }

        val output = ArrayList<IndexedFile>()
        val pending = ArrayDeque<Pair<ScanRoot, File>>()
        roots.forEach { root -> pending.addLast(root to root.file) }
        // Shared storage can contain symlinks and bind mounts (vendor "internal
        // storage" aliases, user-created links). Without a canonical-path guard
        // the traversal can loop indefinitely and, worse, index one physical file
        // several times under different paths - which the duplicate finder would
        // then offer to "clean", deleting a real and unique file.
        val visitedDirectoryPaths = hashSetOf<String>()
        val visitedFilePaths = hashSetOf<String>()
        var visitedDirectories = 0
        var lastProgressDirectories = 0
        var lastProgressFiles = 0
        var lastProgressAt = 0L

        val indexedFileLimit = fileLimit()
        while (pending.isNotEmpty() && output.size < indexedFileLimit) {
            coroutineContext.ensureActive()
            val (root, directory) = pending.removeFirst()
            val directoryPath = canonicalPathWithinRoot(root.canonicalPath, directory) ?: continue
            if (!visitedDirectoryPaths.add(directoryPath)) continue
            visitedDirectories++
            val children = runCatching { directory.listFiles() }.getOrNull() ?: continue

            for (child in children) {
                coroutineContext.ensureActive()
                if (child.isDirectory) {
                    if (
                        canonicalPathWithinRoot(root.canonicalPath, child) != null &&
                        !shouldSkipDirectory(root.file, child)
                    ) {
                        pending.addLast(root to child)
                    }
                } else if (child.isFile) {
                    // Only Android's trash and unfinished-write files are indexed among dot-files (V4).
                    if (StoragePathRules.isSkippedHiddenFile(child.name)) continue
                    val relative = runCatching { child.parentFile?.relativeTo(root.file)?.invariantSeparatorsPath }.getOrNull() ?: continue
                    if (StoragePathRules.isProtectedUserDataPath(relative, child.name)) continue
                    val filePath = canonicalPathWithinRoot(root.canonicalPath, child) ?: continue
                    if (!visitedFilePaths.add(filePath)) continue
                    val size = child.length().coerceAtLeast(0L)
                    if (size <= 0L) continue
                    output += makeIndexedFile(root.file, child)
                    if (output.size >= indexedFileLimit) break
                }
            }

            val now = clock()
            val enoughProgress =
                visitedDirectories - lastProgressDirectories >= 12 ||
                    output.size - lastProgressFiles >= 300
            if (enoughProgress && now - lastProgressAt >= SCAN_PROGRESS_THROTTLE_MILLIS) {
                onTraversalProgress(visitedDirectories, output.size)
                lastProgressDirectories = visitedDirectories
                lastProgressFiles = output.size
                lastProgressAt = now
            }
        }

        if (visitedDirectories != lastProgressDirectories || output.size != lastProgressFiles) {
            onTraversalProgress(visitedDirectories, output.size)
        }
        return output
    }

    suspend fun indexWhatsAppFiles(
        whatsAppRootPairs: List<Pair<File, File>>,
        maxFiles: Int,
        onDiscovered: (Int, Int) -> Unit,
    ): List<IndexedFile> {
        val rootPairs = whatsAppRootPairs.mapNotNull { (storageRoot, mediaRoot) ->
            scanRoot(storageRoot)?.let { it to mediaRoot }
        }
        if (rootPairs.isEmpty()) return emptyList()

        val output = ArrayList<IndexedFile>()
        val pending = ArrayDeque<Pair<ScanRoot, File>>()
        rootPairs.forEach(pending::addLast)
        val visitedDirectories = hashSetOf<String>()
        val visitedFiles = hashSetOf<String>()

        while (pending.isNotEmpty() && output.size < maxFiles) {
            coroutineContext.ensureActive()
            val (storageRoot, directory) = pending.removeFirst()
            val directoryPath = canonicalPathWithinRoot(storageRoot.canonicalPath, directory) ?: continue
            if (!visitedDirectories.add(directoryPath)) continue
            val children = runCatching { directory.listFiles() }.getOrNull() ?: continue

            for (child in children) {
                coroutineContext.ensureActive()
                if (child.isDirectory) {
                    if (canonicalPathWithinRoot(storageRoot.canonicalPath, child) != null &&
                        !shouldSkipDirectory(storageRoot.file, child)) {
                        pending.addLast(storageRoot to child)
                    }
                } else if (child.isFile && child.length() > 0L) {
                    if (StoragePathRules.isSystemMarkerFile(child.name)) continue
                    val relative = runCatching { child.parentFile?.relativeTo(storageRoot.file)?.invariantSeparatorsPath }.getOrNull() ?: continue
                    if (StoragePathRules.isProtectedUserDataPath(relative, child.name)) continue
                    val filePath = canonicalPathWithinRoot(storageRoot.canonicalPath, child) ?: continue
                    if (!visitedFiles.add(filePath)) continue
                    output += makeIndexedFile(storageRoot.file, child)
                    if (output.size % 50 == 0) onDiscovered(visitedDirectories.size, output.size)
                    if (output.size >= maxFiles) break
                }
            }
        }
        onDiscovered(visitedDirectories.size, output.size)
        return output.distinctBy(IndexedFile::uri)
    }
}

internal data class ScanRoot(
    val file: File,
    val canonicalPath: String,
)

private const val SCAN_PROGRESS_THROTTLE_MILLIS = 120L

internal fun canonicalPathOf(file: File): String =
    runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)

internal fun canonicalPathWithinRoot(rootPath: String, file: File): String? {
    val filePath = canonicalPathOf(file)
    if (rootPath.isEmpty()) return null
    return filePath.takeIf {
        it == rootPath || it.startsWith("$rootPath${File.separator}")
    }
}

internal fun scanRoot(file: File): ScanRoot? =
    canonicalPathOf(file)
        .trimEnd(File.separatorChar)
        .takeIf(String::isNotEmpty)
        ?.let { ScanRoot(file = file, canonicalPath = it) }

internal fun shouldSkipDirectory(root: File, directory: File): Boolean {
    val relative = StoragePathRules.normalizePath(
        runCatching { directory.relativeTo(root).invariantSeparatorsPath }
            .getOrDefault(directory.invariantSeparatorsPath),
    )
    return StoragePathRules.isProtectedAppPrivatePath(relative) ||
        (directory.name.startsWith(".") && !StoragePathRules.isHiddenDirectoryAllowed(directory.name))
}
