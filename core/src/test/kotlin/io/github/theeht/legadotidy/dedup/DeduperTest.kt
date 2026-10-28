package io.github.theeht.legadotidy.dedup

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.theeht.legadotidy.model.BookSourceDoc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun `完全重复的源会被归为一组`() {
        val sources = listOf(
            Docs.doc("🔥顶点小说", "https://a.com"),
            Docs.doc("顶点小说（备用）", "https://a.com/"),
            Docs.doc("别的站", "https://b.com", bookList = "//li"),
        )
        val report = Deduper.run(sources)
        assertEquals(1, report.groups.size)
        assertEquals(DedupKind.EXACT, report.groups[0].kind)
        assertEquals(2, report.groups[0].size)
        assertEquals(2, report.keepCount)
    }

    @Test
    fun `名字不同但规则完全相同拾为指纹重复`() {
        val sources = listOf(
            Docs.doc("甲", "https://a.com"),
            Docs.doc("乙", "https://b.com"),
        )
        val report = Deduper.run(sources)
        assertEquals(1, report.groups.size)
        assertEquals(DedupKind.FINGERPRINT, report.groups[0].kind)
    }

    @Test
    fun `长规则里改一个字符仍归为相似`() {
        val longRule = (1..30).joinToString("/") { "div[@class='seg$it']@text" }
        val changed = longRule.replace("seg7", "seg-seven")
        val sources = listOf(
            Docs.doc("甲", "https://a.com", bookList = longRule),
            Docs.doc("乙", "https://b.com", bookList = changed),
        )
        val report = Deduper.run(sources)
        assertEquals(1, report.groups.size)
        assertTrue(report.groups[0].kind <= DedupKind.SIMILAR)
    }

    @Test
    fun `规则无关且域名不同的源不会归组`() {
        val sources = listOf(
            Docs.doc("甲", "https://a.com"),
            Docs.doc("乙", "https://b.com", bookList = "//section[@id='list']/article"),
            Docs.doc("丙", "https://c.com", bookList = "json.$.data[*]"),
        )
        val report = Deduper.run(sources)
        assertEquals(0, report.groups.size)
        assertEquals(3, report.keepCount)
    }

    @Test
    fun `同组内优先保留规则更完整的那个`() {
        // 同一个站的马甲：名称匹配键与 URL 相同，但一个规则填得全、一个几乎空。
        // 这类源很常见（有人把同一个站重发一遍，却把正文规则删了）。
        val rich = Docs.doc("顶点小说", "https://a.com")
        val poor = Docs.docFromJson(
            """{"bookSourceName":"顶点小说（备用）","bookSourceUrl":"https://a.com/",
               "ruleSearch":{"bookList":"//div"}}"""
        )
        val report = Deduper.run(listOf(poor, rich))
        assertEquals(1, report.groups.size)
        assertEquals(DedupKind.EXACT, report.groups[0].kind)
        assertEquals(1, report.groups[0].keep)
    }
}
