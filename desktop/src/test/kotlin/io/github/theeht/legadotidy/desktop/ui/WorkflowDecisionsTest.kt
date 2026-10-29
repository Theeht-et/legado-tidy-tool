package io.github.theeht.legadotidy.desktop.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkflowDecisionsTest {

    private data class Row(val id: Int, val markedForDeletion: Boolean)

    @Test
    fun `删除前按稳定ID映射重复组且删除后选择不受列表下标变化影响`() {
        val before = listOf(
            Row(id = 101, markedForDeletion = true),
            Row(id = 202, markedForDeletion = false),
            Row(id = 303, markedForDeletion = false),
        )

        val duplicateIds = stableIdsAtIndexes(before, listOf(0, 2)) { it.id }
        assertEquals(listOf(101, 303), duplicateIds)

        val plan = planMarkedDeletions(before) { it.markedForDeletion }
        assertTrue(plan.canAdvance)
        assertEquals(1, plan.removedCount)
        assertEquals(listOf(202, 303), plan.retained.map { it.id })

        // 303 was at index 2 before commit and is index 1 afterwards.
        assertEquals(303, resolveSelectedId(303, plan.retained) { it.id })
        // A selected source that was removed falls back to the first remaining stable ID.
        assertEquals(202, resolveSelectedId(101, plan.retained) { it.id })
    }

    @Test
    fun `不允许把全部书源一次性标记删除`() {
        val original = listOf(
            Row(id = 7, markedForDeletion = true),
            Row(id = 8, markedForDeletion = true),
        )

        val plan = planMarkedDeletions(original) { it.markedForDeletion }

        assertFalse(plan.canAdvance)
        assertTrue(plan.allItemsMarked)
        assertEquals(0, plan.removedCount)
        assertEquals(listOf(7, 8), plan.retained.map { it.id })
    }

    @Test
    fun `没有删除标记时保留工作池且允许继续`() {
        val original = listOf(
            Row(id = 4, markedForDeletion = false),
            Row(id = 9, markedForDeletion = false),
        )

        val plan = planMarkedDeletions(original) { it.markedForDeletion }

        assertTrue(plan.canAdvance)
        assertFalse(plan.allItemsMarked)
        assertEquals(0, plan.removedCount)
        assertEquals(original, plan.retained)
    }
}
