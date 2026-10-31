package io.github.theeht.legadotidy.organize

import com.google.gson.JsonParser
import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.probe.ProbeResult
import io.github.theeht.legadotidy.probe.ProbeStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        assertEquals(listOf("音频", "音频"), report.sources.map { it.group })
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
        assertEquals(listOf("图片", "文本", "音频"), report.sources.map { it.group })
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

    @Test
    fun `名称整理完整清除装饰宣传编号尾缀并保留正常英文站名`() {
        val sources = listOf(
            doc("""{"bookSourceName":"得奇小说(优+++)","bookSourceUrl":"https://one.test"}"""),
            doc("""{"bookSourceName":"小小阅读/书香之家app[免费]","bookSourceUrl":"https://two.test"}"""),
            doc("""{"bookSourceName":"阅友小说#一程","bookSourceUrl":"https://three.test"}"""),
            doc("""{"bookSourceName":"md🏷+新笔趣阁🎉🎉cookie","bookSourceUrl":"https://four.test"}"""),
            doc("""{"bookSourceName":"⑧PO18文学⑧","bookSourceUrl":"https://five.test"}"""),
            doc("""{"bookSourceName":"PO18文学cookie","bookSourceUrl":"https://six.test"}"""),
        )

        val report = Organizer.run(
            sources = sources,
            results = emptyMap(),
            config = OrganizeConfig(
                selectedRules = setOf(
                    OrganizeRule.CLEAN_DECORATIONS,
                    OrganizeRule.REMOVE_ADVERTISING_WORDS,
                    OrganizeRule.NORMALIZE_PUNCTUATION,
                ),
            ),
        )

        assertEquals(
            listOf("得奇小说", "小小阅读/书香之家app", "阅友小说", "新笔趣阁", "PO18文学", "PO18文学cookie"),
            report.sources.map { it.name },
        )
    }

    @Test
    fun `分组策略支持保留原组统一未分组及按类型`() {
        val sources = listOf(
            doc("""{"bookSourceName":"文本源","bookSourceUrl":"https://text.test","bookSourceType":0,"bookSourceGroup":"旧组"}"""),
            doc("""{"bookSourceName":"音频源","bookSourceUrl":"https://audio.test","bookSourceType":1,"bookSourceGroup":"旧组"}"""),
            doc("""{"bookSourceName":"图片源","bookSourceUrl":"https://image.test","bookSourceType":2,"bookSourceGroup":"其他旧组"}"""),
            doc("""{"bookSourceName":"文件源","bookSourceUrl":"https://file.test","bookSourceType":3}"""),
            doc("""{"bookSourceName":"视频源","bookSourceUrl":"https://video.test","bookSourceType":4}"""),
        )
        fun groups(mode: GroupingMode) = Organizer.run(
            sources, emptyMap(), OrganizeConfig(selectedRules = emptySet(), groupingMode = mode),
        ).sources.associate { it.index to it.group }

        assertEquals(listOf("旧组", "旧组", "其他旧组", "", ""), groups(GroupingMode.KEEP_ORIGINAL).values.toList())
        assertEquals(listOf("未分组", "未分组", "未分组", "未分组", "未分组"), groups(GroupingMode.NO_SMART_GROUPING).values.toList())
        assertEquals(listOf("文本", "音频", "图片", "文件", "视频"), groups(GroupingMode.BY_TYPE).values.toList())
    }

    @Test
    fun `质量分组使用四档中文名称且不把未校验当成失败`() {
        val sources = listOf(
            doc("""{"bookSourceName":"已验证","bookSourceUrl":"https://ok.test","bookSourceType":0,"searchUrl":"https://ok.test/search","ruleSearch":{"bookList":"x"}}"""),
            doc("""{"bookSourceName":"未校验","bookSourceUrl":"https://skip.test","bookSourceType":0}"""),
        )
        val results = mapOf(0 to ProbeResult(0, ProbeStatus.OK, elapsedMs = 300))
        val report = Organizer.run(
            sources, results,
            OrganizeConfig(selectedRules = emptySet(), groupingMode = GroupingMode.BY_QUALITY),
        )
        assertEquals(listOf("优质", "合格"), report.sources.map { it.group })
        assertTrue(report.sources.all { it.quality != null })
        val skipped = Organizer.assessQuality(sources[1], ProbeResult(1, ProbeStatus.SKIPPED))
        val notMeasured = Organizer.assessQuality(sources[1], null)
        assertEquals(notMeasured, skipped)
    }

    @Test
    fun `质量四档分数边界正确`() {
        val fields = listOf(
            "searchUrl", "exploreUrl", "loginUrl", "loginUi", "header",
            "bookUrlPattern", "enabledCookieJar", "exploreScreen", "eventListener",
        )
        fun sourceWithConfiguredFieldCount(count: Int): BookSourceDoc {
            val raw = JsonParser.parseString("""{"bookSourceType":0}""").asJsonObject
            fields.take(count - 1).forEach { raw.addProperty(it, "configured") }
            return BookSourceDoc(raw)
        }

        val assessments = listOf(9, 7, 5, 4).map {
            Organizer.assessQuality(sourceWithConfiguredFieldCount(it), null)
        }
        assertEquals(listOf(90, 70, 50, 40), assessments.map { it.score })
        assertEquals(listOf("优质", "良好", "普通", "合格"), assessments.map { it.grade })
    }

    private fun doc(json: String): BookSourceDoc =
        BookSourceDoc(JsonParser.parseString(json).asJsonObject)
}
