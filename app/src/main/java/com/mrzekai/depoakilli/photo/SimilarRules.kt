package com.mrzekai.depoakilli.photo

import com.mrzekai.depoakilli.data.CleaningThresholds

/** One photo as MediaStore and the file system describe it. */
data class PhotoItem(
    val id: Long,
    val path: String,
    /** Relative folder, e.g. "DCIM/Camera". */
    val folder: String,
    val name: String,
    val sizeBytes: Long,
    val modifiedMillis: Long,
    /** Capture time (EXIF/MediaStore DATE_TAKEN), else the modified time. */
    val takenMillis: Long,
    val width: Int,
    val height: Int,
) {
    val pixels: Long get() = width.toLong() * height.toLong()
    val camera: Boolean get() = SimilarRules.isCameraFolder(folder)
}

/**
 * What the app measured about a photo. [embedding] is the image embedder's
 * L2-normalised vector (null until measured: only photos in a candidate pair
 * are embedded). [sharpness] is the Laplacian variance of a small thumbnail
 * (classic image processing, not a model). [eyesOpen] comes from the face
 * model in Faz 3B-2 (null: unknown or no face).
 */
class PhotoFeatures(
    val dHash: Long,
    val embedding: FloatArray? = null,
    val sharpness: Double = 0.0,
    val eyesOpen: Float? = null,
)

/** A group of similar photos: the one that stays and the ones offered for the Trash. */
data class SimilarGroup(
    val keeper: PhotoItem,
    /** Offered for the Trash (preselected). */
    val others: List<PhotoItem>,
    /** Edited copies of the keeper: kept too, never offered (Faz 3B-1). */
    val alsoKept: List<PhotoItem> = emptyList(),
) {
    val members: List<PhotoItem> get() = listOf(keeper) + alsoKept + others
    val trashBytes: Long get() = others.sumOf(PhotoItem::sizeBytes)
    val spanMillis: Long get() = members.maxOf(PhotoItem::takenMillis) - members.minOf(PhotoItem::takenMillis)
    val takenMillis: Long get() = members.minOf(PhotoItem::takenMillis)

    /** Stable while the members stay the same ("not similar" remembers it). */
    val key: String get() = members.map(PhotoItem::id).sorted().joinToString(",")
}

/**
 * Faz 3B-1 similar photos. Pure, unit-tested; thresholds in [CleaningThresholds]
 * (measured on a synthetic set, see the PR report).
 */
object SimilarRules {
    /** Photos of the same folder taken this close together are a candidate pair. */
    const val BURST_WINDOW_MILLIS = 60_000L

    /** First run: the newest photos; the rest only when the user asks. */
    const val FIRST_RUN_PHOTOS = 3_000

    /** Free users may clean this many groups in the app's lifetime on this device. */
    const val FREE_GROUPS = 3

    /** "Approve all groups" (Pro) is enabled after this many groups were opened one by one. */
    const val VIEW_BEFORE_APPROVE_ALL = 3

    private val EDITED_NAME = Regex("""(?i)([_\- ](edit|edited|effects|bearbeitet|modifi[eé]|editado|modificat[oa]|изм)|~\d+)(\.[a-z0-9]+)?$""")

    fun isCameraFolder(folder: String): Boolean {
        val f = folder.trim('/').lowercase()
        return f == "dcim" || f.startsWith("dcim/") && !f.contains("screenshot")
    }

    /** Never part of a similar group. */
    fun excluded(item: PhotoItem, inTrash: Boolean, kept: Boolean, excludedFolder: Boolean): Boolean {
        if (inTrash || kept || excludedFolder) return true
        val ext = item.name.substringAfterLast('.', "").lowercase()
        // RAW files have their own rule (RawPairFinder); screenshots have theirs.
        if (ext == "dng" || ext == "gif") return true
        if (item.folder.lowercase().contains("screenshot") || item.name.lowercase().startsWith("screenshot")) return true
        return item.width <= 0 || item.height <= 0 || item.sizeBytes <= 0L
    }

    /**
     * 64-bit difference hash from a 9x8 grey thumbnail ([gray] row-major,
     * 9 values per row, 8 rows): bit = left pixel darker than its right neighbour.
     */
    fun dHash(gray: IntArray): Long {
        require(gray.size == 72) { "9x8 grey values expected" }
        var bits = 0L
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                bits = (bits shl 1) or if (gray[y * 9 + x] < gray[y * 9 + x + 1]) 1L else 0L
            }
        }
        return bits
    }

    fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /** Cosine of two vectors (the embedder's vectors are already L2-normalised). */
    fun cosine(a: FloatArray, b: FloatArray): Double {
        if (a.size != b.size || a.isEmpty()) return 0.0
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        return if (na == 0.0 || nb == 0.0) 0.0 else dot / Math.sqrt(na * nb)
    }

    /**
     * Candidate pairs (indices into [items]): same folder and taken within a
     * minute, or dHash distance at most [maxDistance]. Only these are embedded.
     */
    fun candidatePairs(
        items: List<PhotoItem>,
        hashes: List<Long>,
        maxDistance: Int = CleaningThresholds.DHASH_MAX_DISTANCE,
    ): Set<Pair<Int, Int>> {
        val pairs = LinkedHashSet<Pair<Int, Int>>()
        val byTime = items.indices.sortedBy { items[it].takenMillis }
        for ((p, i) in byTime.withIndex()) {
            var q = p + 1
            while (q < byTime.size && items[byTime[q]].takenMillis - items[i].takenMillis <= BURST_WINDOW_MILLIS) {
                val j = byTime[q]
                if (items[i].folder.equals(items[j].folder, ignoreCase = true)) pairs += ordered(i, j)
                q++
            }
        }
        for (i in items.indices) {
            for (j in i + 1 until items.size) {
                if (hamming(hashes[i], hashes[j]) <= maxDistance) pairs += ordered(i, j)
            }
        }
        return pairs
    }

    private fun ordered(i: Int, j: Int) = if (i < j) i to j else j to i

    /**
     * Transitive groups of pairs whose embeddings are at least [minCosine]
     * apart (union-find). Returns index groups of two or more.
     */
    fun cluster(
        size: Int,
        pairs: Collection<Pair<Int, Int>>,
        embeddings: List<FloatArray?>,
        minCosine: Double = CleaningThresholds.SIMILAR_COSINE_MIN,
    ): List<List<Int>> {
        val parent = IntArray(size) { it }
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (parent[c] != r) {
                val n = parent[c]
                parent[c] = r
                c = n
            }
            return r
        }
        pairs.forEach { (i, j) ->
            val a = embeddings[i] ?: return@forEach
            val b = embeddings[j] ?: return@forEach
            if (cosine(a, b) >= minCosine) parent[find(i)] = find(j)
        }
        return (0 until size).groupBy(::find).values.filter { it.size >= 2 }
    }

    /**
     * The photo that stays: eyes open (when known) > sharpest > most pixels >
     * biggest file > newest. Ties are stable.
     */
    fun pickKeeper(members: List<Int>, items: List<PhotoItem>, features: List<PhotoFeatures>): Int =
        members.sortedWith(
            compareByDescending<Int> { eyesRank(features[it].eyesOpen) }
                .thenByDescending { features[it].sharpness }
                .thenByDescending { items[it].pixels }
                .thenByDescending { items[it].sizeBytes }
                .thenByDescending { items[it].takenMillis },
        ).first()

    private fun eyesRank(p: Float?): Int = when {
        p == null -> 1
        p >= CleaningThresholds.EYES_OPEN_MIN -> 2
        else -> 0
    }

    /**
     * An edited copy (name suffix such as "-edited" / "~2", or a different
     * shape) is kept alongside the keeper, never offered for the Trash.
     */
    fun isEditedCopy(item: PhotoItem, keeper: PhotoItem): Boolean {
        if (EDITED_NAME.containsMatchIn(item.name.substringBeforeLast('.') + "." + item.name.substringAfterLast('.', ""))) return true
        val a = ratio(item)
        val b = ratio(keeper)
        return Math.abs(a - b) / b > 0.02
    }

    private fun ratio(p: PhotoItem): Double {
        val long = maxOf(p.width, p.height).toDouble()
        val short = minOf(p.width, p.height).toDouble().coerceAtLeast(1.0)
        return long / short
    }

    /** Builds the groups: keeper, edited copies kept, the rest offered. Dismissed groups never come back. */
    fun groups(
        items: List<PhotoItem>,
        features: List<PhotoFeatures>,
        clusters: List<List<Int>>,
        dismissed: Set<String> = emptySet(),
    ): List<SimilarGroup> = clusters.mapNotNull { members ->
        val keeperIndex = pickKeeper(members, items, features)
        val keeper = items[keeperIndex]
        val rest = members.filter { it != keeperIndex }.map { items[it] }
        val (edited, offered) = rest.partition { isEditedCopy(it, keeper) }
        val group = SimilarGroup(keeper, offered.sortedBy(PhotoItem::takenMillis), edited)
        group.takeIf { it.others.isNotEmpty() && it.key !in dismissed }
    }.sortedByDescending(SimilarGroup::takenMillis)

    /** Free: the first [FREE_GROUPS] groups (lifetime); Pro: all. */
    fun canClean(isPro: Boolean, groupsCleaned: Int): Boolean = isPro || groupsCleaned < FREE_GROUPS

    /** "Approve all groups": Pro, after [VIEW_BEFORE_APPROVE_ALL] groups seen one by one. */
    fun approveAllEnabled(groupsViewed: Int): Boolean = groupsViewed >= VIEW_BEFORE_APPROVE_ALL
}
