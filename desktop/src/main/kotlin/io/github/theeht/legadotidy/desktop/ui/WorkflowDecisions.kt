package io.github.theeht.legadotidy.desktop.ui

/**
 * Pure decision result for committing a stage's delete marks.
 * It deliberately works on stable-ID entries rather than list indexes.
 */
internal data class DeletionPlan<T>(
    val canAdvance: Boolean,
    val retained: List<T>,
    val removedCount: Int,
    val allItemsMarked: Boolean,
)

internal fun <T> planMarkedDeletions(
    items: List<T>,
    isMarked: (T) -> Boolean,
): DeletionPlan<T> {
    val markedCount = items.count(isMarked)
    val allMarked = items.isNotEmpty() && markedCount == items.size
    if (allMarked) {
        return DeletionPlan(
            canAdvance = false,
            retained = items,
            removedCount = 0,
            allItemsMarked = true,
        )
    }
    return DeletionPlan(
        canAdvance = items.isNotEmpty(),
        retained = items.filterNot(isMarked),
        removedCount = markedCount,
        allItemsMarked = false,
    )
}

internal fun <T> resolveSelectedId(
    selectedId: Int?,
    items: List<T>,
    idOf: (T) -> Int,
): Int? {
    if (selectedId != null && items.any { idOf(it) == selectedId }) return selectedId
    return items.firstOrNull()?.let(idOf)
}

/** Map engine/core result indexes to stable working-set IDs before deleting or reordering anything. */
internal fun <T> stableIdsAtIndexes(
    items: List<T>,
    indexes: List<Int>,
    idOf: (T) -> Int,
): List<Int> = indexes.mapNotNull { index -> items.getOrNull(index)?.let(idOf) }.distinct()
