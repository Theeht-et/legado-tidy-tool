package io.github.theeht.legadotidy.model

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookSourceDocTest {

    @Test
    fun `仅按书源实际提供的规则字段构建行为字段`() {
        val source = doc(
            """{
              "bookSourceName":"partial",
              "searchUrl":"https://example.test/search",
              "ruleSearch":{"bookList":"div.book","name":"h2","customFutureField":"x"},
              "ruleContent":{"content":"article"}
            }"""
        )

        assertEquals("div.book", source.behaviorFields()["ruleSearch.bookList"])
        assertEquals("x", source.behaviorFields()["ruleSearch.customFutureField"])
        assertEquals("article", source.behaviorFields()["ruleContent.content"])
        assertFalse(source.behaviorFields().containsKey("ruleSearch.wordCount"))
        assertTrue(source.hasAnyRule)
    }

    @Test
    fun `识别上游段评规则和 JS 书源`() {
        val review = doc("""{"ruleReview":{"reviewUrl":"/reviews","enabled":true}}""")
        assertEquals("/reviews", review.behaviorFields()["ruleReview.reviewUrl"])
        assertEquals("true", review.behaviorFields()["ruleReview.enabled"])
        assertTrue(review.hasAnyRule)

        val js = doc("""{"mainJs":"function searchBook(){ return []; }"}""")
        assertEquals("function searchBook(){ return []; }", js.behaviorFields()["mainJs"])
        assertTrue(js.hasAnyRule)
        assertTrue(js.canSearch)
    }

    @Test
    fun `兼容以 JSON 字符串存储的规则组`() {
        val source = doc(
            """{"ruleSearch":"{\\"bookList\\":\\"div.book\\",\\"name\\":\\"a\\",\\"wordCount\\":\\".count\\"}"}"""
        )

        assertEquals("div.book", source.ruleSearch["bookList"])
        assertEquals(".count", source.behaviorFields()["ruleSearch.wordCount"])
        assertTrue(source.canSearch.not())
    }

    @Test
    fun `缺失可选规则组不报错`() {
        val source = doc("""{"bookSourceName":"metadata-only"}""")

        assertTrue(source.ruleSearch.isEmpty())
        assertTrue(source.ruleReview.isEmpty())
        assertFalse(source.hasAnyRule)
        assertTrue(source.behaviorFields().isEmpty())
    }

    private fun doc(json: String) = BookSourceDoc(JsonParser.parseString(json).asJsonObject)
}
