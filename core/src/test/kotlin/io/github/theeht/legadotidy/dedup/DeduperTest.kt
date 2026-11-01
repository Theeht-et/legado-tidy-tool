package io.github.theeht.legadotidy.dedup

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.probe.ProbeResult
import io.github.theeht.legadotidy.probe.ProbeStage
import io.github.theeht.legadotidy.probe.ProbeStageResult
import io.github.theeht.legadotidy.probe.ProbeStageStatus
import io.github.theeht.legadotidy.probe.ProbeStatus
import kotlin.test.Test
import kotlin.test.assertEquals

object Docs {
    fun doc(
        name: String,
        url: String,
        searchUrl: String = "https://example.com/search?q=%s",
        bookList: String = "//div[@class='book']",
        content: String = "//div[@id='content']@html",
        extra: Map<String, String> = emptyMap(),
    ): BookSourceDoc {
        val obj = JsonObject()
        obj.addProperty("bookSourceName", name)
        obj.addProperty("bookSourceUrl", url)
        if (searchUrl.isNotEmpty()) obj.addProperty("searchUrl", searchUrl)
        val ruleSearch = JsonObject()
        if (bookList.isNotEmpty()) ruleSearch.addProperty("bookList", bookList)
        ruleSearch.addProperty("name", "class.name.0@text")
        obj.add("ruleSearch", ruleSearch)
        val ruleContent = JsonObject()
        ruleContent.addProperty("content", content)
        obj.add("ruleContent", ruleContent)
        extra.forEach { (k, v) -> obj.addProperty(k, v) }
        return BookSourceDoc(obj)
    }

    fun docFromJson(json: String): BookSourceDoc =
        BookSourceDoc(JsonParser.parseString(json).asJsonObject)
}

class DeduperTest {

    @Test
    fun `规范化 URL 相同的书源会被归为一组`() {
        val sources = listOf(
            Docs.doc("🔥顶点小说", "https://a.com"),
            Docs.doc("顶点小说（备用）", "https://a.com/"),
            Docs.doc("别的站", "https://b.com", bookList = "//li"),
        )
        val report = Deduper.run(sources)
        assertEquals(1, report.groups.size)
        assertEquals(DedupKind.EXACT, report.groups.single().kind)
        assertEquals(listOf(0, 1), report.groups.single().members)
        assertEquals(2, report.groups.single().size)
        assertEquals(2, report.keepCount)
    }

    @Test
    fun `忽略协议 www 查询参数片段与尾斜杠`() {
        val first = Docs.doc(
            "站点甲",
            "https://www.example.com/Book/?from=one",
            bookList = "//div[@id='first']",
        )
        val second = Docs.doc(
            "站点乙",
            "http://example.com/book?from=two#chapter",
            bookList = "//section[@id='second']",
        )

        val report = Deduper.run(listOf(first, second))

        assertEquals(1, report.groups.size)
        assertEquals(DedupKind.EXACT, report.groups.single().kind)
        assertEquals(2, report.groups.single().size)
    }

    @Test
    fun `不同 URL 不会因为规则指纹相同而误判重复`() {
        val sources = listOf(
            Docs.doc("甲", "https://a.com"),
            Docs.doc("乙", "https://b.com"),
        )
        val report = Deduper.run(sources)
        assertEquals(0, report.groups.size)
        assertEquals(2, report.keepCount)
    }

    @Test
    fun `不同 URL 不会因为规则高度相似而误判重复`() {
        val longRule = (1..30).joinToString("/") { "div[@class='seg$it']@text" }
        val changed = longRule.replace("seg7", "seg-seven")
        val sources = listOf(
            Docs.doc("甲", "https://a.com", bookList = longRule),
            Docs.doc("乙", "https://b.com", bookList = changed),
        )
        val report = Deduper.run(sources)
        assertEquals(0, report.groups.size)
    }

    @Test
    fun `同组内按 HTML 原型配置评分优先保留完整书源`() {
        val rich = Docs.doc("顶点小说", "https://a.com")
        val poor = Docs.docFromJson(
            """{"bookSourceName":"顶点小说（备用）","bookSourceUrl":"https://a.com/",
               "ruleSearch":{"bookList":"//div"}}"""
        )
        val report = Deduper.run(listOf(poor, rich))
        assertEquals(1, report.groups.size)
        assertEquals(1, report.groups.single().keep)
    }


    @Test
    fun `实际校验成功优先于配置字段更多但校验失败的候选`() {
        val richButFailed = Docs.doc("规则较多但失败", "https://same.example")
        val leanButWorking = Docs.docFromJson(
            """{"bookSourceName":"实际可用","bookSourceUrl":"https://same.example","searchUrl":"https://example.com/search?q=%s","ruleSearch":{"bookList":"//div"}}"""
        )

        val report = Deduper.run(
            listOf(richButFailed, leanButWorking),
            probeResults = mapOf(
                0 to ProbeResult(0, ProbeStatus.NETWORK, elapsedMs = 100),
                1 to ProbeResult(1, ProbeStatus.OK, elapsedMs = 500),
            ),
        )

        assertEquals(1, report.groups.single().keep)
    }

    @Test
    fun `启用状态不影响重复书源保留推荐`() {
        val enabled = Docs.doc("启用书源", "https://same.example")
        val disabled = Docs.doc("关闭书源", "https://same.example").also {
            it.raw.addProperty("enabled", false)
        }

        val report = Deduper.run(listOf(enabled, disabled))

        assertEquals(0, report.groups.single().keep)
    }

    @Test
    fun `无法自动校验的书源不按明确失效处理`() {
        val unsupported = Docs.docFromJson(
            """{"bookSourceName":"工具无法校验","bookSourceUrl":"https://same.example","searchUrl":"https://example.com/search?q=%s","ruleSearch":{"bookList":"//div"}}"""
        )
        val richerButUnverified = Docs.doc("配置完整但跳过", "https://same.example")

        val report = Deduper.run(
            listOf(unsupported, richerButUnverified),
            probeResults = mapOf(
                0 to ProbeResult(0, ProbeStatus.UNSUPPORTED),
                1 to ProbeResult(1, ProbeStatus.SKIPPED),
            ),
        )

        assertEquals(1, report.groups.single().keep)
    }

    @Test
    fun `工具不支持的阶段不按书源规则失败计入惩罚`() {
        val unverified = Docs.docFromJson(
            """{"bookSourceName":"当前环境不支持","bookSourceUrl":"https://same.example","searchUrl":"https://example.com/search?q=%s","ruleSearch":{"bookList":"//div"}}"""
        )
        val configured = Docs.doc("配置更完整", "https://same.example")
        val probe = ProbeResult(
            index = 0,
            status = ProbeStatus.UNSUPPORTED,
            stageResults = listOf(
                ProbeStageResult(
                    ProbeStage.SEARCH,
                    ProbeStageStatus.FAILED,
                    detail = "当前环境不支持 WebView",
                    failureStatus = ProbeStatus.UNSUPPORTED,
                ),
                ProbeStageResult(ProbeStage.BOOK_INFO, ProbeStageStatus.NOT_RUN),
                ProbeStageResult(ProbeStage.TOC, ProbeStageStatus.NOT_RUN),
                ProbeStageResult(ProbeStage.CONTENT, ProbeStageStatus.NOT_RUN),
            ),
        )

        val group = Deduper.run(
            listOf(unverified, configured),
            probeResults = mapOf(0 to probe),
        ).groups.single()

        assertEquals(1, group.keep)
    }

    @Test
    fun `核心阶段校验通过数量优先于规则配置完整度`() {
        val leanButWorking = Docs.docFromJson(
            """{"bookSourceName":"实际可用","bookSourceUrl":"https://same.example","searchUrl":"https://example.com/search?q=%s","ruleSearch":{"bookList":"//div"}}"""
        )
        val richButFailing = Docs.doc("配置完整但详情失败", "https://same.example")
        val richInfo = com.google.gson.JsonObject().apply { addProperty("name", "//h1") }
        richButFailing.raw.add("ruleBookInfo", richInfo)

        val workingResult = ProbeResult(
            index = 0,
            status = ProbeStatus.OK,
            stageResults = listOf(
                ProbeStageResult(ProbeStage.SEARCH, ProbeStageStatus.SUCCESS, elapsedMs = 100),
                ProbeStageResult(ProbeStage.BOOK_INFO, ProbeStageStatus.SUCCESS, elapsedMs = 100),
                ProbeStageResult(ProbeStage.TOC, ProbeStageStatus.SUCCESS, elapsedMs = 100),
                ProbeStageResult(ProbeStage.CONTENT, ProbeStageStatus.SUCCESS, elapsedMs = 100),
            ),
        )
        val failingResult = ProbeResult(
            index = 1,
            status = ProbeStatus.PARTIAL,
            stageResults = listOf(
                ProbeStageResult(ProbeStage.SEARCH, ProbeStageStatus.SUCCESS, elapsedMs = 50),
                ProbeStageResult(ProbeStage.BOOK_INFO, ProbeStageStatus.FAILED, detail = "详情解析失败", failureStatus = ProbeStatus.ERROR),
                ProbeStageResult(ProbeStage.TOC, ProbeStageStatus.NOT_RUN, detail = "详情阶段未通过"),
                ProbeStageResult(ProbeStage.CONTENT, ProbeStageStatus.NOT_RUN, detail = "详情阶段未通过"),
            ),
        )

        val group = Deduper.run(
            listOf(leanButWorking, richButFailing),
            probeResults = mapOf(0 to workingResult, 1 to failingResult),
        ).groups.single()

        assertEquals(0, group.keep)
        assertTrue(group.recommendationReason.contains("核心功能成功阶段更多"))
    }

    @Test
    fun `实际响应速度优先于配置完整度`() {
        val complete = Docs.doc("规则较完整", "https://same.example")
        val lean = Docs.docFromJson(
            """{"bookSourceName":"配置较少","bookSourceUrl":"https://same.example","searchUrl":"https://example.com/search?q=%s","ruleSearch":{"bookList":"//div"}}"""
        )

        val report = Deduper.run(
            listOf(complete, lean),
            probeResults = mapOf(
                0 to ProbeResult(0, ProbeStatus.OK, elapsedMs = 1_200),
                1 to ProbeResult(1, ProbeStatus.OK, elapsedMs = 100),
            ),
        )

        assertEquals(1, report.groups.single().keep)
    }

    @Test
    fun `JSON顶层业务字段数量仅在其他因素相同后参与比较`() {
        val ordinary = Docs.doc("普通字段", "https://same.example")
        val extraTopLevelField = Docs.doc("额外字段", "https://same.example").also {
            it.raw.addProperty("futureBookSourceField", "preserved")
        }

        val report = Deduper.run(listOf(ordinary, extraTopLevelField))

        assertEquals(1, report.groups.single().keep)
    }

    @Test
    fun `评分相同时稳定保留原列表靠前项且仍有推荐理由`() {
        val sources = listOf(
            Docs.doc("甲", "https://a.com"),
            Docs.doc("乙", "https://a.com"),
        )
        val group = Deduper.run(sources).groups.single()
        assertEquals(0, group.keep)
        assertTrue(group.recommendationReason.isNotBlank())
    }

    @Test
    fun `空 URL 不会被聚合成重复组`() {
        val report = Deduper.run(
            listOf(Docs.doc("甲", ""), Docs.doc("乙", "   "))
        )
        assertEquals(0, report.groups.size)
    }
}
