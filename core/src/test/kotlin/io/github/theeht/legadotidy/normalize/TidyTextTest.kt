package io.github.theeht.legadotidy.normalize

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TidyTextTest {

    @Test
    fun `匹配键会去掉 emoji 括号注释与营销尾巴`() {
        val a = TidyText.key("🔥顶点小说（备用） - 顶点中文网")
        val b = TidyText.key("顶点小说")
        assertEquals(b, a)
    }

    @Test
    fun `匹配键忽略大小写与空白`() {
        assertEquals(TidyText.key("abc"), TidyText.key(" A B C "))
    }

    @Test
    fun `显示名只做保守清洗`() {
        assertEquals("顶点小说（备用）", TidyText.display("  顶点小说（备用）  "))
        assertEquals("顶点小说", TidyText.display("🔥顶点小说"))
        assertTrue(TidyText.display("a".repeat(80)).length <= 40)
    }

    @Test
    fun `规则文本保留标点只去空白`() {
        assertEquals("class.name.0@text", TidyText.ruleText(" class.name.0@text\n"))
        assertEquals("@css:#content@html", TidyText.ruleText("@css:\n#content@html"))
    }

    @Test
    fun `URL 归一化只小写协议与主机`() {
        assertEquals("https://a.com/Path", TidyText.url("HTTPS://A.com/Path/"))
        assertEquals("https://a.com", TidyText.url("https://a.com"))
        assertEquals("", TidyText.url("  "))
    }

    @Test
    fun `全角转半角`() {
        assertEquals("abc123", TidyText.halfWidth("ａｂｃ１２３"))
    }

    @Test
    fun `主机名提取`() {
        assertEquals("a.com", TidyText.host("https://a.com:8080/x?y=1"))
        assertFalse(TidyText.host("").isNotEmpty())
    }

    @Test
    fun `广告清理只移除常见宣传尾缀和括号内容`() {
        assertEquals("星河书源", TidyText.removeAdvertisingWords("星河书源（官方推荐） - 广告"))
        assertEquals("站点", TidyText.removeAdvertisingWords("站点 · 福利"))
        assertEquals("推荐书源", TidyText.removeAdvertisingWords("推荐书源"))
    }

    @Test
    fun `名称标点整理合并多余分隔符并规范空格`() {
        assertEquals("星河书源 | 官方", TidyText.normalizeNamePunctuation(" 星河书源  ||   官方 "))
    }

}
