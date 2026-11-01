package io.github.theeht.legadotidy.desktop.ui

import com.google.gson.JsonParser
import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.organize.OrganizeRule
import io.github.theeht.legadotidy.probe.ProbeStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class WorkflowDecisionsTest {

    private fun doc(json: String) = BookSourceDoc(JsonParser.parseString(json).asJsonObject)

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

    @Test
    fun `空导出目录不会退化为程序当前目录`() {
        assertTrue(resolveExportDirectory("  ").isFailure)
    }

    @Test
    fun `有效导出目录会规范为绝对路径`() {
        assertTrue(resolveExportDirectory(".").getOrThrow().isAbsolute)
    }

    @Test
    fun `只有明确勾选响应时间排序才允许重排预览`() {
        assertEquals(false, usesOrganizerOutputOrder(setOf(OrganizeRule.REGROUP_BY_TYPE)))
        assertEquals(false, usesOrganizerOutputOrder(setOf(OrganizeRule.CLEAN_DECORATIONS)))
        assertEquals(true, usesOrganizerOutputOrder(setOf(OrganizeRule.SORT_BY_RESPONSE_TIME)))
        assertEquals(
            true,
            usesOrganizerOutputOrder(setOf(OrganizeRule.REGROUP_BY_TYPE, OrganizeRule.SORT_BY_RESPONSE_TIME)),
        )
    }

    @Test
    fun `修改地址或类型会使旧校验结果失效而修改名称分组不会`() {
        val original = doc("""{"bookSourceName":"原名","bookSourceUrl":"https://one.test","bookSourceType":0,"bookSourceGroup":"旧组"}""")
        val renamed = doc("""{"bookSourceName":"新名","bookSourceUrl":"https://one.test","bookSourceType":0,"bookSourceGroup":"新组"}""")
        val changedUrl = doc("""{"bookSourceName":"原名","bookSourceUrl":"https://two.test","bookSourceType":0,"bookSourceGroup":"旧组"}""")
        val changedType = doc("""{"bookSourceName":"原名","bookSourceUrl":"https://one.test","bookSourceType":1,"bookSourceGroup":"旧组"}""")
        val changedExplore = doc("""{"bookSourceName":"原名","bookSourceUrl":"https://one.test","bookSourceType":0,"bookSourceGroup":"旧组","exploreUrl":"分类::/new-explore"}""")
        val changedRule = doc("""{"bookSourceName":"原名","bookSourceUrl":"https://one.test","bookSourceType":0,"bookSourceGroup":"旧组","ruleExplore":{"bookList":"div.new"}}""")

        assertFalse(probeInputsChanged(original, renamed))
        assertTrue(probeInputsChanged(original, changedUrl))
        assertTrue(probeInputsChanged(original, changedType))
        assertTrue(probeInputsChanged(original, changedExplore))
        assertTrue(probeInputsChanged(original, changedRule))
    }

    @Test
    fun `旧版启用字段编辑时同步写入新旧字段`() {
        val raw = JsonParser.parseString(
            """{"bookSourceName":"旧源","bookSourceUrl":"https://legacy.test","bookSourceEnabled":false}"""
        ).asJsonObject

        val legacyDoc = BookSourceDoc(raw)
        assertFalse(isDocumentEnabled(legacyDoc))
        setDocumentEnabled(raw, true)

        assertTrue(raw.get("enabled").asBoolean)
        assertTrue(raw.get("bookSourceEnabled").asBoolean)
        assertTrue(isDocumentEnabled(BookSourceDoc(raw)))
    }

    @Test
    fun `没有启用字段时默认启用，关闭后写入标准字段`() {
        val raw = JsonParser.parseString("""{"bookSourceName":"源","bookSourceUrl":"https://example.test"}""").asJsonObject
        assertTrue(isDocumentEnabled(BookSourceDoc(raw)))

        setDocumentEnabled(raw, false)

        assertFalse(raw.get("enabled").asBoolean)
        assertFalse(isDocumentEnabled(BookSourceDoc(raw)))
    }

    @Test
    fun `任一校验未通过都默认标记待删除`() {
        assertFalse(shouldMarkForDeletionByDefault(ProbeStatus.OK))
        ProbeStatus.values()
            .filter { it != ProbeStatus.OK && it != ProbeStatus.UNCHECKABLE }
            .forEach { status ->
                assertTrue(shouldMarkForDeletionByDefault(status), status.name)
            }
        assertFalse(shouldMarkForDeletionByDefault(ProbeStatus.UNCHECKABLE))
        assertFalse(shouldShowInExceptionReview(ProbeStatus.UNCHECKABLE, deleteMarked = false))
        assertTrue(shouldShowInExceptionReview(ProbeStatus.PARTIAL, deleteMarked = false))
        assertFalse(shouldShowInExceptionReview(ProbeStatus.PARTIAL, deleteMarked = true))
    }

    @Test
    fun `重复组重新计算时保留用户明确作出的删除决定`() {
        assertFalse(resolveDeleteMark(defaultMarked = true, currentMarked = false, hasUserDecision = true))
        assertTrue(resolveDeleteMark(defaultMarked = false, currentMarked = true, hasUserDecision = true))
        assertTrue(resolveDeleteMark(defaultMarked = true, currentMarked = false, hasUserDecision = false))
        assertFalse(resolveDeleteMark(defaultMarked = false, currentMarked = true, hasUserDecision = false))
    }

    @Test
    fun `书源列表筛选与计数共用同一分类判断`() {
        fun matches(
            filter: String,
            duplicate: Boolean = false,
            pending: Boolean = false,
            status: ProbeStatus? = null,
            deleted: Boolean = false,
        ) = isSourceIncludedInFilter(filter, duplicate, pending, status, deleted)

        assertTrue(matches("全部"))
        assertTrue(matches("重复候选", duplicate = true))
        assertFalse(matches("重复候选"))
        assertTrue(matches("待校验", pending = true))
        assertFalse(matches("待校验", status = ProbeStatus.OK))
        assertTrue(matches("校验通过", status = ProbeStatus.OK))
        assertTrue(matches("校验通过", status = ProbeStatus.UNCHECKABLE))
        assertFalse(matches("校验通过", status = ProbeStatus.NETWORK))
        assertFalse(matches("校验通过", status = ProbeStatus.PARTIAL))
        assertTrue(matches("异常 / 复核", status = ProbeStatus.NETWORK))
        assertTrue(matches("异常 / 复核", status = ProbeStatus.PARTIAL))
        assertFalse(matches("异常 / 复核", status = ProbeStatus.NETWORK, deleted = true))
        assertFalse(matches("异常 / 复核", status = ProbeStatus.OK))
        assertFalse(matches("异常 / 复核", status = ProbeStatus.SKIPPED))
        assertTrue(matches("待删除", deleted = true))
        assertFalse(matches("待删除"))
        assertTrue(matches("未知筛选条件"))
    }

    @Test
    fun `待删除标记优先异常与待删除分组互斥`() {
        assertFalse(shouldShowInExceptionReview(ProbeStatus.NO_SEARCH_URL, deleteMarked = true))
        assertFalse(shouldShowInExceptionReview(ProbeStatus.UNSUPPORTED, deleteMarked = true))
        assertFalse(shouldShowInExceptionReview(ProbeStatus.PARSE_EMPTY, deleteMarked = true))
        assertTrue(shouldShowInExceptionReview(ProbeStatus.NO_SEARCH_URL, deleteMarked = false))
        assertTrue(shouldShowInExceptionReview(ProbeStatus.NETWORK, deleteMarked = false))
        assertFalse(shouldShowInExceptionReview(ProbeStatus.SKIPPED, deleteMarked = false))
    }

    @Test
    fun `保留原组不触发无意义写回而重分组和手动编辑会写回`() {
        assertFalse(shouldApplyGroups(setOf(OrganizeRule.KEEP_ORIGINAL_GROUPS), false))
        assertTrue(shouldApplyGroups(setOf(OrganizeRule.REGROUP_BY_TYPE), false))
        assertTrue(shouldApplyGroups(setOf(OrganizeRule.REGROUP_BY_QUALITY), false))
        assertTrue(shouldApplyGroups(setOf(OrganizeRule.DROP_ORIGINAL_GROUPS, OrganizeRule.NO_SMART_GROUPING), false))
        assertTrue(shouldApplyGroups(setOf(OrganizeRule.KEEP_ORIGINAL_GROUPS), true))
        assertFalse(shouldApplyGroups(emptySet(), false))
    }

    @Test
    fun `导出文件名不会覆盖输入或之前的导出`(@TempDir dir: Path) {
        val input = File(dir.toFile(), "sources.json")
        input.writeText("original")

        val first = nextSafeExportFileName(input, dir.toFile(), "sources.json")
        assertEquals("sources-cleaned.json", first)
        File(dir.toFile(), first).writeText("first export")

        val second = nextSafeExportFileName(input, dir.toFile(), "sources.json")
        assertEquals("sources-cleaned-2.json", second)
        assertEquals("original", input.readText())
    }

}
