package io.github.theeht.legadotidy.dedup

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.theeht.legadotidy.model.BookSourceDoc
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
    fun `评分相同时稳定保留原列表靠前项`() {
        val sources = listOf(
            Docs.doc("甲", "https://a.com"),
            Docs.doc("乙", "https://a.com"),
        )
        assertEquals(0, Deduper.run(sources).groups.single().keep)
    }

    @Test
    fun `空 URL 不会被聚合成重复组`() {
        val report = Deduper.run(
            listOf(Docs.doc("甲", ""), Docs.doc("乙", "   "))
        )
        assertEquals(0, report.groups.size)
    }
}
