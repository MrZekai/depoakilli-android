package com.mrzekai.depoakilli.plan

/**
 * Faz 3C Personal Plan: rules and statistics on this phone only (no server,
 * no account, no network, no model). Pure; unit-tested.
 */

/** Counts of keep / delete decisions for one kind of file (no names, no paths). */
data class DecisionCounts(val keep: Int = 0, val delete: Int = 0) {
    val total: Int get() = keep + delete
    val deleteRate: Double get() = if (total == 0) 0.0 else delete.toDouble() / total
}

/** What the learner knows. Effective only after [PreferenceRules.LEARN_MIN_DECISIONS]. */
data class PreferenceSnapshot(val counts: Map<String, DecisionCounts> = emptyMap()) {
    val totalDecisions: Int get() = counts.values.sumOf(DecisionCounts::total)
    val learned: Boolean get() = totalDecisions >= PreferenceRules.LEARN_MIN_DECISIONS

    /** Delete rate of a kind, only when learned and the kind has enough decisions. */
    fun deleteRate(key: String): Double? =
        counts[key]?.takeIf { learned && it.total >= PreferenceRules.MIN_PER_KIND }?.deleteRate

    /** Pre-selection hint: true = usually deleted, false = usually kept, null = no opinion. */
    fun preselect(key: String): Boolean? = when (val rate = deleteRate(key)) {
        null -> null
        else -> when {
            rate >= PreferenceRules.MOSTLY_DELETE -> true
            rate <= PreferenceRules.MOSTLY_KEEP -> false
            else -> null
        }
    }

    fun record(key: String, delete: Boolean): PreferenceSnapshot {
        val c = counts[key] ?: DecisionCounts()
        return copy(counts = counts + (key to if (delete) c.copy(delete = c.delete + 1) else c.copy(keep = c.keep + 1)))
    }
}

object PreferenceRules {
    /** Before this many decisions the app says "Still learning" and changes nothing. */
    const val LEARN_MIN_DECISIONS = 20
    const val MIN_PER_KIND = 5
    const val MOSTLY_DELETE = 0.7
    const val MOSTLY_KEEP = 0.3
}

enum class StepKind(val tier: Int) {
    /** Leftovers and exact copies: nothing is lost. */
    NO_LOSS(0),

    /** Shrinking videos: space without deleting. */
    SHRINK(1),

    /** Similar photos: the best of each group stays. */
    SIMILAR(2),

    /** Files the user decides one by one ("I'll ask you"). */
    ASK(3),
}

/** One measured source of space (bytes come from the scan, never from a guess). */
data class PlanCandidate(
    /** Learner key and the screen it opens (e.g. a cleanup group name). */
    val key: String,
    val kind: StepKind,
    val bytes: Long,
    val count: Int,
    /** Goes to the Trash (restorable) rather than being deleted for good. */
    val reversible: Boolean = true,
    val ageDays: Long = 0L,
    /** The step needs Pro beyond its free part (shown with a PRO badge). */
    val proOnly: Boolean = false,
    /** A lower-bound estimate ("about"), e.g. video shrinking. */
    val estimate: Boolean = false,
)

enum class StepReason { NO_LOSS, WITHOUT_DELETING, BEST_STAYS, YOU_DELETE_THESE, YOU_KEEP_THESE, OLD_FILES, YOUR_CALL }

data class PlanStep(val candidate: PlanCandidate, val reason: StepReason, val cumulativeBytes: Long)

data class Plan(
    val steps: List<PlanStep>,
    val targetBytes: Long,
    /** Everything the plan could open with all steps. */
    val reachableBytes: Long,
    /** Without Pro steps. */
    val freeReachableBytes: Long,
    /** The part that frees space without deleting anything (shrinking). */
    val withoutDeletingBytes: Long,
    val learned: Boolean,
) {
    val reachesTarget: Boolean get() = reachableBytes >= targetBytes
    val freeReachesTarget: Boolean get() = freeReachableBytes >= targetBytes
}

/** The user's goal. */
sealed interface PlanTarget {
    data class Bytes(val bytes: Long) : PlanTarget
    /** Get the phone below this fill percent. */
    data class BelowPercent(val percent: Int) : PlanTarget

    fun bytesNeeded(totalBytes: Long, availableBytes: Long): Long = when (this) {
        is Bytes -> bytes
        is BelowPercent -> {
            val used = (totalBytes - availableBytes).coerceAtLeast(0L)
            (used - totalBytes * percent / 100).coerceAtLeast(0L)
        }
    }
}

/** Builds the least painful order to reach a goal. Never deletes anything itself. */
object PlanBuilder {
    const val GB = 1_000_000_000L
    val PRESET_GB = listOf(5L, 10L, 20L)
    const val PRESET_PERCENT = 80

    fun build(candidates: List<PlanCandidate>, targetBytes: Long, learner: PreferenceSnapshot): Plan {
        val usable = candidates.filter { it.bytes > 0L && it.count > 0 }
        val ordered = usable.sortedWith(
            compareBy<PlanCandidate> { if (learner.preselect(it.key) == false) 1 else 0 } // usually kept: last
                .thenBy { it.kind.tier }
                .thenByDescending { it.reversible }
                .thenByDescending { learner.deleteRate(it.key) ?: -1.0 }
                .thenByDescending { it.ageDays }
                .thenByDescending { it.bytes },
        )
        var sum = 0L
        val steps = mutableListOf<PlanStep>()
        for (c in ordered) {
            if (targetBytes > 0L && sum >= targetBytes) break
            sum += c.bytes
            steps += PlanStep(c, reasonFor(c, learner), sum)
        }
        return Plan(
            steps = steps,
            targetBytes = targetBytes,
            reachableBytes = usable.sumOf(PlanCandidate::bytes),
            freeReachableBytes = usable.filterNot(PlanCandidate::proOnly).sumOf(PlanCandidate::bytes),
            withoutDeletingBytes = usable.filter { it.kind == StepKind.SHRINK }.sumOf(PlanCandidate::bytes),
            learned = learner.learned,
        )
    }

    fun reasonFor(c: PlanCandidate, learner: PreferenceSnapshot): StepReason = when (c.kind) {
        StepKind.NO_LOSS -> StepReason.NO_LOSS
        StepKind.SHRINK -> StepReason.WITHOUT_DELETING
        StepKind.SIMILAR -> StepReason.BEST_STAYS
        StepKind.ASK -> when (learner.preselect(c.key)) {
            true -> StepReason.YOU_DELETE_THESE
            false -> StepReason.YOU_KEEP_THESE
            null -> if (c.ageDays >= 365) StepReason.OLD_FILES else StepReason.YOUR_CALL
        }
    }
}
