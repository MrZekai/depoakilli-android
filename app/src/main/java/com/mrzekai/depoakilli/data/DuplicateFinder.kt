package com.mrzekai.depoakilli.data

import com.mrzekai.depoakilli.model.AiAssessment
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.CleanupGroup
import com.mrzekai.depoakilli.model.IndexedFile
import java.io.File
import java.io.FileInputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** Progress of full-content hashing, in bytes. */
typealias HashProgress = (bytesDone: Long, bytesTotal: Long) -> Unit

internal fun IndexedFile.toCleanable(assessment: AiAssessment) = CleanableItem(
    id = "$uri-${assessment.category}",
    uri = uri,
    name = name,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    modifiedAtMillis = modifiedAtMillis,
    relativePath = relativePath,
    assessment = assessment,
    selected = assessment.recommended,
    volumeRoot = volumeRoot,
)

/**
 * Finds duplicate files. Files with the same size are compared by content
 * samples first; files up to the full-hash ceiling are then verified with a
 * full SHA-256. Larger sample matches stay "unverified" until the user asks
 * for a full check.
 */
internal class DuplicateFinder(
    private val engine: AiCleaningEngine,
    private val openInput: (String) -> FileInputStream? = ::openFileUri,
) {
    suspend fun find(
        files: List<IndexedFile>,
        maxFullHashBytes: Long,
        onHashProgress: HashProgress = { _, _ -> },
    ): DuplicatePolicy.ScanResult {
        val duplicates = ArrayList<CleanableItem>()
        val protectedUris = hashSetOf<String>()
        val sameSizeGroups = files.asSequence()
            .filter { it.sizeBytes >= CleaningThresholds.MIN_DUPLICATE_BYTES }
            .groupBy(IndexedFile::sizeBytes)
            .values
            .filter { it.size > 1 }
            .sortedByDescending { group -> group.first().sizeBytes * group.size }

        val sampleMatches = ArrayList<List<IndexedFile>>()
        for (sameSizeFiles in sameSizeGroups) {
            coroutineContext.ensureActive()
            sameSizeFiles
                .mapNotNull { file -> sampleFingerprint(file)?.let { it to file } }
                .groupBy({ it.first }, { it.second })
                .values
                .filter { it.size > 1 }
                .forEach(sampleMatches::add)
        }

        val hashTotal = sampleMatches.sumOf { group ->
            group.filter { it.sizeBytes <= maxFullHashBytes }.sumOf(IndexedFile::sizeBytes)
        }
        var hashDone = 0L
        for (sameSampleFiles in sampleMatches) {
            coroutineContext.ensureActive()
            val small = sameSampleFiles.filter { it.sizeBytes <= maxFullHashBytes }
            val large = sameSampleFiles.filter { it.sizeBytes > maxFullHashBytes }
            val contentGroups = small
                .mapNotNull { file ->
                    val base = hashDone
                    val hash = fingerprint(file.uri) { done, _ -> onHashProgress(base + done, hashTotal) }
                    hashDone += file.sizeBytes
                    hash?.let { it to file }
                }
                .groupBy({ it.first }, { it.second })
                .values
                .filter { it.size > 1 }
            for (sameContentFiles in contentGroups) {
                val decision = DuplicatePolicy.choose(sameContentFiles) ?: continue
                protectedUris += decision.keep.uri
                sameContentFiles.filterNot { it.uri == decision.keep.uri }.forEach { duplicate ->
                    duplicates += verifiedCopy(duplicate, decision.keep)
                }
            }
            if (large.size >= 2) {
                val decision = DuplicatePolicy.choose(large) ?: continue
                protectedUris += decision.keep.uri
                large.filterNot { it.uri == decision.keep.uri }.forEach { duplicate ->
                    duplicates += duplicate.toCleanable(engine.sampledDuplicateAssessment())
                        .withOriginal(decision.keep)
                }
            }
        }
        return DuplicatePolicy.ScanResult(duplicates, protectedUris)
    }

    /**
     * Full verification of the largest unverified groups (the "Deep duplicate
     * check"). Each returned entry replaces the unverified item with the same
     * id; a `null` value means the copy turned out to be different and must be
     * removed from the list.
     */
    suspend fun verifyLargeGroups(
        items: List<CleanableItem>,
        onHashProgress: HashProgress = { _, _ -> },
    ): Map<String, CleanableItem?> {
        val groups = largeGroupsToVerify(items)
        val total = groups.sumOf { (keep, copies) -> keep.sizeBytes * (copies.size + 1) }
        var done = 0L
        val result = LinkedHashMap<String, CleanableItem?>()
        for ((keep, copies) in groups) {
            coroutineContext.ensureActive()
            val base = done
            val keepHash = fingerprint(keep.uri) { d, _ -> onHashProgress(base + d, total) }
            done += keep.sizeBytes
            for (copy in copies) {
                val copyBase = done
                val copyHash = fingerprint(copy.uri) { d, _ -> onHashProgress(copyBase + d, total) }
                done += copy.sizeBytes
                result[copy.id] = if (keepHash != null && keepHash == copyHash) upgrade(copy) else null
            }
        }
        return result
    }

    /** Number of groups and the most space [verifyLargeGroups] could confirm. */
    fun largeGroupSummary(items: List<CleanableItem>): Pair<Int, Long> {
        val groups = largeGroupsToVerify(items)
        return groups.size to groups.sumOf { (keep, copies) -> keep.sizeBytes * copies.size }
    }

    /** Full hash of one copy against its protected original. */
    suspend fun verify(copy: CleanableItem, onHashProgress: HashProgress = { _, _ -> }): CleanableItem? {
        val originalUri = copy.protectedDuplicateUri ?: return null
        val total = copy.sizeBytes * 2
        val originalHash = fingerprint(originalUri) { d, _ -> onHashProgress(d, total) } ?: return null
        val copyHash = fingerprint(copy.uri) { d, _ -> onHashProgress(copy.sizeBytes + d, total) } ?: return null
        return if (originalHash == copyHash) upgrade(copy) else null
    }

    private fun largeGroupsToVerify(items: List<CleanableItem>): List<Pair<IndexedFile, List<CleanableItem>>> =
        items.asSequence()
            .filter { it.assessment.group == CleanupGroup.DUPLICATE_UNVERIFIED }
            .filter { it.protectedDuplicateUri != null }
            .filter { it.sizeBytes > CleaningThresholds.QUICK_FULL_HASH_BYTES }
            .filter { it.sizeBytes <= CleaningThresholds.SMART_VERIFY_MAX_BYTES }
            .groupBy { it.protectedDuplicateUri!! }
            .map { (_, copies) -> copies.first().originalFile() to copies }
            .filter { (keep, copies) -> keep.sizeBytes * copies.size >= CleaningThresholds.GROUP_MIN_BYTES }
            .sortedByDescending { (keep, copies) -> keep.sizeBytes * copies.size }
            .take(CleaningThresholds.SMART_VERIFY_MAX_GROUPS)
            .toList()

    private fun upgrade(copy: CleanableItem): CleanableItem {
        val keep = copy.originalFile()
        val safe = DuplicatePolicy.autoSelectable(copy.indexedFile(), keep, copy.volumeRoot, keep.volumeRoot)
        val assessment = engine.duplicateAssessment(safe)
        return copy.copy(assessment = assessment, selected = assessment.recommended)
    }

    private fun verifiedCopy(duplicate: IndexedFile, keep: IndexedFile): CleanableItem {
        val safe = DuplicatePolicy.autoSelectable(duplicate, keep, duplicate.volumeRoot, keep.volumeRoot)
        return duplicate.toCleanable(engine.duplicateAssessment(safe)).withOriginal(keep)
    }

    private fun sampleFingerprint(file: IndexedFile): String? = runCatching {
        openInput(file.uri)?.use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(file.sizeBytes).array())
            val channel = input.channel
            updateDigest(channel, digest, 0L, HASH_SAMPLE_BYTES)
            if (file.sizeBytes > HASH_SAMPLE_BYTES * 2L) {
                updateDigest(channel, digest, (file.sizeBytes / 2L - HASH_SAMPLE_BYTES / 2L).coerceAtLeast(0L), HASH_SAMPLE_BYTES)
            }
            if (file.sizeBytes > HASH_SAMPLE_BYTES) {
                updateDigest(channel, digest, (file.sizeBytes - HASH_SAMPLE_BYTES).coerceAtLeast(0L), HASH_SAMPLE_BYTES)
            }
            digest.digest().toHex()
        }
    }.getOrNull()

    /** Full SHA-256. Cancellation is checked on every read and is rethrown. */
    suspend fun fingerprint(uri: String, onBytes: HashProgress = { _, _ -> }): String? {
        val input = runCatching { openInput(uri) }.getOrNull() ?: return null
        return input.use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val channel = stream.channel
            val total = runCatching { channel.size() }.getOrDefault(0L)
            val buffer = ByteBuffer.allocate(HASH_BUFFER_BYTES)
            var done = 0L
            while (true) {
                coroutineContext.ensureActive()
                val read = runCatching { channel.read(buffer) }.getOrElse { return@use null }
                if (read <= 0) break
                buffer.flip()
                digest.update(buffer)
                buffer.clear()
                done += read
                onBytes(done, total)
            }
            digest.digest().toHex()
        }
    }

    private fun updateDigest(channel: FileChannel, digest: MessageDigest, position: Long, byteCount: Int) {
        channel.position(position)
        val buffer = ByteBuffer.allocate(byteCount)
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) <= 0) break
        }
        buffer.flip()
        digest.update(buffer)
    }

    companion object {
        private const val HASH_BUFFER_BYTES = 256 * 1024
        private const val HASH_SAMPLE_BYTES = 64 * 1024

        fun openFileUri(uri: String): FileInputStream? {
            val parsed = URI(uri)
            if (parsed.scheme != "file") return null
            return FileInputStream(File(parsed.path))
        }
    }
}

private fun CleanableItem.withOriginal(keep: IndexedFile) = copy(
    protectedDuplicateName = keep.name,
    protectedDuplicateUri = keep.uri,
    protectedDuplicateSizeBytes = keep.sizeBytes,
    protectedDuplicateRelativePath = keep.relativePath,
    protectedDuplicateVolumeRoot = keep.volumeRoot,
)

private fun CleanableItem.indexedFile() = IndexedFile(
    uri = uri,
    name = name,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    modifiedAtMillis = modifiedAtMillis,
    relativePath = relativePath,
    volumeRoot = volumeRoot,
)

private fun CleanableItem.originalFile() = IndexedFile(
    uri = protectedDuplicateUri.orEmpty(),
    name = protectedDuplicateName.orEmpty(),
    sizeBytes = protectedDuplicateSizeBytes ?: sizeBytes,
    mimeType = mimeType,
    modifiedAtMillis = 0L,
    relativePath = protectedDuplicateRelativePath.orEmpty(),
    volumeRoot = protectedDuplicateVolumeRoot,
)

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
