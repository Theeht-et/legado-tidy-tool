package io.github.theeht.legadotidy.organize

import com.google.gson.JsonParser
import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.probe.ProbeResult
import io.github.theeht.legadotidy.probe.ProbeStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class SelectedOrganizerTest {

    @Test
    fun `只执行用户选中的名称规则且保留未选择的字段`() {
        val source = doc(
            """{"bookSourceName":"📚 星河书源 - 官方推荐","bookSourceUrl":"https://example.test","bookSourceType":1,"bookSourceGroup":"原分组"}"""
        )

        val report = Organizer.run(
            sources = listOf(source),
            results = emptyMap(),
            config = OrganizeConfig(selectedRules = setOf(OrganizeRule.CLEAN_DECORATIONS)),
        )

        assertEquals("星河书源 - 官方推荐", report.sources.single().name)
        assertEquals("原分组", report.sources.single().group)
    }

    @Test
    fun `广告清理类型重分组和响应时间排序可组合`() {
        val slower = doc(
            """{"bookSourceName":"星河书源 - 官方推荐","bookSourceUrl":"https://slow.test","bookSourceType":1,"bookSourceGroup":"旧分组"}"""
        )
        val faster = doc(
            """{"bookSourceName":"银河书源 - 福利","bookSourceUrl":"https://fast.test","bookSourceType":1,"bookSourceGroup":"另一个旧分组"}"""
        )
        val results = mapOf(
            0 to ProbeResult(0, ProbeStatus.OK, elapsedMs = 300),
            1 to ProbeResult(1, ProbeStatus.OK, elapsedMs = 100),
        )

        val report = Organizer.run(
            sources = listOf(slower, faster),
            results = results,
            config = OrganizeConfig(
                selectedRules = setOf(
                    OrganizeRule.REMOVE_ADVERTISING_WORDS,
                    OrganizeRule.REGROUP_BY_TYPE,
                    OrganizeRule.SORT_BY_RESPONSE_TIME,
                ),
            ),
        )

        assertEquals(listOf(1, 0), report.sources.map { it.index })
        assertEquals(listOf("银河书源", "星河书源"), report.sources.map { it.name })
        assertEquals(listOf("有声", "有声"), report.sources.map { it.group })
        assertEquals(listOf(0, 1), report.sources.map { it.rank })
    }

    @Test
    fun `响应耗时排序跨分组全局生效且重分组匹配原型规则`() {
        val slowAudio = doc(
            """{"bookSourceName":"星河书源","bookSourceUrl":"https://slow.test","bookSourceType":1,"bookSourceGroup":"旧音频"}"""
        )
        val fastComic = doc(
            """{"bookSourceName":"漫画站","bookSourceUrl":"https://fast.test","bookSourceType":2,"bookSourceGroup":"旧漫画"}"""
        )
        val middleLibrary = doc(
            """{"bookSourceName":"古籍文库","bookSourceUrl":"https://middle.test","bookSourceType":0,"bookSourceGroup":"旧文本"}"""
        )
        val results = mapOf(
            0 to ProbeResult(0, ProbeStatus.OK, elapsedMs = 500),
            1 to ProbeResult(1, ProbeStatus.OK, elapsedMs = 100),
            2 to ProbeResult(2, ProbeStatus.OK, elapsedMs = 300),
        )

        val report = Organizer.run(
            sources = listOf(slowAudio, fastComic, middleLibrary),
            results = results,
            config = OrganizeConfig(
                selectedRules = setOf(
                    OrganizeRule.REGROUP_BY_TYPE,
                    OrganizeRule.SORT_BY_RESPONSE_TIME,
                ),
            ),
        )

        assertEquals(listOf(1, 2, 0), report.sources.map { it.index })
        assertEquals(listOf("漫画", "出版物", "有声"), report.sources.map { it.group })
        assertEquals(listOf(0, 0, 0), report.sources.map { it.rank })
    }

    @Test
    fun `显式空规则不清洗名称也不自动补分组`() {
        val source = doc(
            """{"bookSourceName":"📚 原始 名称","bookSourceUrl":"https://example.test","bookSourceType":0,"bookSourceGroup":""}"""
        )

        val report = Organizer.run(
            sources = listOf(source),
            results = emptyMap(),
            config = OrganizeConfig(selectedRules = emptySet()),
        )

        assertEquals("📚 原始 名称", report.sources.single().name)
        assertEquals("", report.sources.single().group)
    }

    @Test
    fun `响应时间排序将未实际测得耗时的书源稳定放在末尾`() {
        val sources = listOf(
            doc("""{"bookSourceName":"无搜索规则","bookSourceUrl":"https://no-search.test"}"""),
            doc("""{"bookSourceName":"较慢","bookSourceUrl":"https://slow.test"}"""),
            doc("""{"bookSourceName":"跳过校验","bookSourceUrl":"https://skipped.test"}"""),
            doc("""{"bookSourceName":"较快","bookSourceUrl":"https://fast.test"}"""),
            doc("""{"bookSourceName":"未返回耗时","bookSourceUrl":"https://error.test"}"""),
        )
        val results = mapOf(
            0 to ProbeResult(0, ProbeStatus.NO_SEARCH_URL),
            1 to ProbeResult(1, ProbeStatus.OK, elapsedMs = 300),
            2 to ProbeResult(2, ProbeStatus.SKIPPED, elapsedMs = 10),
            3 to ProbeResult(3, ProbeStatus.OK, elapsedMs = 100),
            4 to ProbeResult(4, ProbeStatus.ERROR),
        )

        val report = Organizer.run(
            sources = sources,
            results = results,
            config = OrganizeConfig(selectedRules = setOf(OrganizeRule.SORT_BY_RESPONSE_TIME)),
        )

        assertEquals(listOf(3, 1, 0, 2, 4), report.sources.map { it.index })
        assertEquals(listOf(0, 1, 2, 3, 4), report.sources.map { it.rank })
    }

    @Test
    fun `先清理末尾装饰再清除广告词避免组合规则漏项`() {
        val source = doc(
            """{"bookSourceName":"📚 星河书源 - 官方推荐 ✨","bookSourceUrl":"https://example.test","bookSourceType":0,"bookSourceGroup":"原分组"}"""
        )

        val report = Organizer.run(
            sources = listOf(source),
            results = emptyMap(),
            config = OrganizeConfig(
                selectedRules = setOf(
                    OrganizeRule.CLEAN_DECORATIONS,
                    OrganizeRule.REMOVE_ADVERTISING_WORDS,
                    OrganizeRule.NORMALIZE_PUNCTUATION,
                    OrganizeRule.KEEP_ORIGINAL_GROUPS,
                ),
            ),
        )

        assertEquals("星河书源", report.sources.single().name)
        assertEquals("原分组", report.sources.single().group)
    }

    private fun doc(json: String): BookSourceDoc =
        BookSourceDoc(JsonParser.parseString(json).asJsonObject)
}
