package io.github.theeht.legadotidy.model

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SourceIOTest {

    @Test
    fun `解析数组`() {
        val list = SourceIO.parse("""[{"bookSourceName":"a"},{"bookSourceName":"b"}]""")
        assertEquals(2, list.size)
    }

    @Test
    fun `解析单个对象`() {
        val list = SourceIO.parse("""{"bookSourceName":"a"}""")
        assertEquals(1, list.size)
    }

    @Test
    fun `解析 sources 包装`() {
        val list = SourceIO.parse("""{"sources":[{"bookSourceName":"a"}]}""")
        assertEquals(1, list.size)
    }

    @Test
    fun `数组含非对象元素时拒绝导入而不是静默丢弃`() {
        val error = assertFailsWith<IllegalArgumentException> {
            SourceIO.parse("""[{"bookSourceName":"a"},null,3]""")
        }
        assertTrue(error.message!!.contains("非对象元素"))
    }


    @Test
    fun `非法 JSON 报中文错`() {
        val e = assertFailsWith<IllegalArgumentException> { SourceIO.parse("not json") }
        assertTrue(e.message!!.contains("不是合法 JSON"))
    }

    @Test
    fun `导出再解析数量一致`() {
        val raw = JsonParser.parseString("""[{"a":1},{"b":2}]""").asJsonArray
        val text = SourceIO.toJson(raw.map { it.asJsonObject })
        assertEquals(2, SourceIO.parse(text).size)
    }
}
