package com.mrzekai.depoakilli.data

import com.mrzekai.depoakilli.model.CleanableItem

/**
 * The user's own decisions applied to a scan (V10, V11): files kept in the
 * swipe review and folders the user does not want suggestions from are
 * removed from review groups only. Auto groups are never affected.
 */
object UserChoiceFilter {
    data class Result(val items: List<CleanableItem>, val hiddenKept: Int, val hiddenExcluded: Int)

    fun apply(items: List<CleanableItem>, kept: Set<String>, excluded: List<ExcludedFolder>): Result {
        if (kept.isEmpty() && excluded.isEmpty()) return Result(items, 0, 0)
        var hiddenKept = 0
        var hiddenExcluded = 0
        val visible = items.filter { item ->
            if (item.assessment.group.auto) return@filter true
            when {
                excluded.any { it.contains(item.volumeRoot, item.relativePath) } -> {
                    hiddenExcluded++
                    false
                }
                KeptItemsStore.keyOf(item) in kept -> {
                    hiddenKept++
                    false
                }
                else -> true
            }
        }
        return Result(visible, hiddenKept, hiddenExcluded)
    }
}
