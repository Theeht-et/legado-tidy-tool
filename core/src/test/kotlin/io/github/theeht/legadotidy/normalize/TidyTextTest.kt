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
    fun `广告清理与 v3 原型的前后缀规则一致`() {
        assertEquals("星河书源", TidyText.removeAdvertisingWords("星河书源（官方推荐） - 广告"))
        assertEquals("站点", TidyText.removeAdvertisingWords("站点 - 福利"))
        assertEquals("书源", TidyText.removeAdvertisingWords("推荐书源"))
        assertEquals("小说", TidyText.removeAdvertisingWords("小说推荐"))
        assertEquals("星河书源", TidyText.removeAdvertisingWords("星河书源 VIP"))
        assertEquals("星河书源", TidyText.removeAdvertisingWords("星河书源 AD"))
        assertEquals("星河书源", TidyText.removeAdvertisingWords("星河书源（广告推广·官方推荐）"))
        assertEquals("星河书源", TidyText.removeAdvertisingWords("星河书源 [VIP会员专享]"))
        assertEquals("星河书源", TidyText.removeAdvertisingWords("VIP 星河书源 官方推荐"))
        assertEquals("", TidyText.removeAdvertisingWords("【VIP广告】"))
    }

    @Test
    fun `装饰清理不截断长名称且不会回退已清空的图标名`() {
        val longName = "星河小说书源".repeat(12)
        assertEquals(longName, TidyText.cleanDecorations("📚 $longName ✨"))
        assertEquals("", TidyText.cleanDecorations("📚 ★ ✨"))
    }

    @Test
    fun `名称标点整理遵循原型的重复标点规则`() {
        assertEquals("星河书源 || 官方", TidyText.normalizeNamePunctuation(" 星河书源  ||   官方 "))
        assertEquals("星河书源 官方", TidyText.normalizeNamePunctuation("星河书源——官方"))
    }

}
