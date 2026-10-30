package io.github.theeht.legadotidy.desktop.ui

import com.google.gson.JsonParser
import io.github.theeht.legadotidy.model.BookSourceDoc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceDetailFieldsTest {

    @Test
    fun `详情数据覆盖上游书源顶层字段与各规则组字段`() {
        val raw = JsonParser.parseString(
            """
            {
              "bookSourceName": "测试书源",
              "bookSourceUrl": "https://example.test",
              "bookSourceGroup": "测试",
              "bookSourceType": 0,
              "bookUrlPattern": "https://example.test/book/.*",
              "customOrder": 7,
              "enabled": true,
              "enabledExplore": false,
              "header": "{\"User-Agent\":\"test\"}",
              "concurrentRate": "1",
              "enabledCookieJar": true,
              "loginUrl": "/login",
              "loginUi": "[]",
              "loginCheckJs": "true",
              "searchUrl": "/search",
              "ruleSearch": {"bookList": ".book", "name": "a", "futureSearchRule": "custom"},
              "exploreUrl": "/explore",
              "exploreScreen": "分类",
              "ruleExplore": {"bookList": ".item"},
              "ruleBookInfo": {"name": "h1", "tocUrl": "/toc"},
              "ruleToc": {"chapterList": ".chapter"},
              "ruleContent": {"content": "#content"},
              "ruleReview": {"reviewUrl": "/review", "enabled": false},
              "jsLib": "lib",
              "coverDecodeJs": "decode()",
              "mainJs": "function searchBook() {}",
              "eventListener": true,
              "customButton": true,
              "bookSourceComment": "备注",
              "variableComment": "变量说明",
              "lastUpdateTime": 123,
              "respondTime": 456,
              "weight": 9,
              "futureTopLevelField": {"nested": "kept"}
            }
            """.trimIndent()
        ).asJsonObject
        val fields = sourceDetailSections(BookSourceDoc(raw))
            .flatMap { it.fields }
            .associateBy { it.key }

        val expected = setOf(
            "bookSourceName", "bookSourceUrl", "bookSourceGroup", "bookSourceType",
            "bookUrlPattern", "customOrder", "enabled", "enabledExplore",
            "header", "concurrentRate", "enabledCookieJar", "loginUrl", "loginUi", "loginCheckJs",
            "searchUrl", "ruleSearch.bookList", "ruleSearch.checkKeyWord",
            "ruleExplore.bookList", "exploreUrl", "exploreScreen",
            "ruleBookInfo.name", "ruleBookInfo.tocUrl", "ruleToc.chapterList",
            "ruleContent.content", "ruleReview.reviewUrl", "ruleReview.enabled",
            "jsLib", "coverDecodeJs", "mainJs", "eventListener", "customButton",
            "bookSourceComment", "variableComment", "lastUpdateTime", "respondTime", "weight",
            "futureTopLevelField", "ruleSearch.futureSearchRule",
        )
        assertTrue(fields.keys.containsAll(expected), "缺少字段：${expected - fields.keys}")
        assertEquals("测试书源", fields.getValue("bookSourceName").value)
        assertEquals("custom", fields.getValue("ruleSearch.futureSearchRule").value)
        assertEquals("{\n  \"nested\": \"kept\"\n}", fields.getValue("futureTopLevelField").value)
        assertTrue(fields.getValue("enabled").configured)
        assertFalse(fields.getValue("ruleSearch.checkKeyWord").configured)
    }

    @Test
    fun `兼容以 JSON 字符串保存的上游规则组`() {
        val raw = JsonParser.parseString(
            """{"ruleSearch":"{\"bookList\":\".book\",\"customField\":\"x\"}"}"""
        ).asJsonObject
        val fields = sourceDetailSections(BookSourceDoc(raw)).flatMap { it.fields }.associateBy { it.key }

        assertEquals(".book", fields.getValue("ruleSearch.bookList").value)
        assertEquals("x", fields.getValue("ruleSearch.customField").value)
    }
}
