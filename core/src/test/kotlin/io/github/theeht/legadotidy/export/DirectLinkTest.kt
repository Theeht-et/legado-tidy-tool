package io.github.theeht.legadotidy.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DirectLinkTest {

    @Test
    fun `拼出上游认的一键导入链接`() {
        val link = DirectLink.forSourceUrl("https://example.com/sources.json")

        assertEquals(
            "legado://import/auto?src=https%3A%2F%2Fexample.com%2Fsources.json",
            link,
        )
    }

    @Test
    fun `带查询串的地址不会被截断`() {
        // 这是这个类存在的理由：不 encode 的话 & 会把参数劈开，App 只拿到半截地址。
        val link = DirectLink.forSourceUrl("https://example.com/a.json?v=2&x=1")

        assertTrue(link.contains("%3F"), "问号应被编码：$link")
        assertTrue(link.contains("%26"), "与号应被编码：$link")
        assertFalse(link.substringAfter("?src=").contains("&x=1"), "不许出现裸参数：$link")
    }

    @Test
    fun `本地路径不能当直链`() {
        assertFailsWith<IllegalArgumentException> {
            DirectLink.forSourceUrl("D:\\书源.json")
        }
        assertFalse(DirectLink.isUsable("D:\\书源.json"))
        assertFalse(DirectLink.isUsable("file:///C:/sources.json"))
        assertFalse(DirectLink.isUsable(null))
        assertFalse(DirectLink.isUsable("   "))
    }

    @Test
    fun `http 地址可用`() {
        assertTrue(DirectLink.isUsable("http://192.168.1.5:8080/sources.json"))
        assertTrue(DirectLink.isUsable("https://pan.example.com/d/abc"))
        assertFalse(DirectLink.isUsable("https://"))
    }

    @Test
    fun `可以指定导入类型`() {
        val link = DirectLink.forSourceUrl("https://example.com/s.json", kind = "bookSource")

        assertTrue(link.startsWith("legado://import/bookSource?src="), link)
    }
}
