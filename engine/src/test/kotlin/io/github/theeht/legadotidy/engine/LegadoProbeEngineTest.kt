package io.github.theeht.legadotidy.engine

import com.google.gson.JsonParser
import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.probe.ProbeStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LegadoProbeEngineTest {

    private val engine = LegadoProbeEngine()

    private fun doc(json: String) = BookSourceDoc(JsonParser.parseString(json).asJsonObject)

    private fun assertUnsupported(json: String, expectedDetail: String) {
        val verdict = assertNotNull(engine.staticVerdict(doc(json)))
        assertEquals(ProbeStatus.UNSUPPORTED, verdict.status)
        assertTrue(verdict.detail.contains(expectedDetail, ignoreCase = true), verdict.detail)
    }

    @Test
    fun `无 searchUrl 的源归类为 NO_SEARCH_URL`() {
        val verdict = assertNotNull(engine.staticVerdict(doc("""{"bookSourceName": "no-search"}""")))
        assertEquals(ProbeStatus.NO_SEARCH_URL, verdict.status)
    }

    @Test
    fun `ruleSearch 中的 webView URL 选项归类为 UNSUPPORTED`() = assertUnsupported(
        """
        {
          "bookSourceName": "webView-url-option",
          "searchUrl": "https://example.com/search",
          "ruleSearch": {"bookList": "div.book", "chapterUrl": "href##$##,{'webView': true}"}
        }
        """,
        "webview",
    )

    @Test
    fun `ruleToc.chapterUrl 带 webView 选项归类为 UNSUPPORTED`() = assertUnsupported(
        """
        {
          "bookSourceName": "webView-toc-chapterUrl",
          "searchUrl": "https://example.com/search",
          "ruleSearch": {"bookList": "div.book"},
          "ruleToc": {"chapterUrl": "href##$##,{'webView': true}"}
        }
        """,
        "webview",
    )

    @Test
    fun `ruleBookInfo.tocUrl 带 webView 选项归类为 UNSUPPORTED`() = assertUnsupported(
        """
        {
          "bookSourceName": "webView-bookInfo-tocUrl",
          "searchUrl": "https://example.com/search",
          "ruleSearch": {"bookList": "div.book"},
          "ruleBookInfo": {"tocUrl": "@get:{t}##$##,{'webView': true}"}
        }
        """,
        "webview",
    )

    @Test
    fun `ruleContent.webJs 声明归类为 UNSUPPORTED`() = assertUnsupported(
        """
        {
          "bookSourceName": "webJs-content",
          "searchUrl": "https://example.com/search",
          "ruleSearch": {"bookList": "div.book"},
          "ruleContent": {"webJs": "{'webView': true}"}
        }
        """,
        "webview",
    )

    @Test
    fun `header 中嵌入 webView 配置归类为 UNSUPPORTED`() = assertUnsupported(
        """
        {
          "bookSourceName": "webView-header",
          "searchUrl": "https://example.com/search",
          "ruleSearch": {"bookList": "div.book"},
          "header": "{\"webView\": true}"
        }
        """,
        "webview",
    )

    @Test
    fun `搜索结果 bookUrl 使用 webView 归类为 UNSUPPORTED`() = assertUnsupported(
        """
        {
          "bookSourceName": "webView-search-result",
          "searchUrl": "https://example.com/search",
          "ruleSearch": {"bookList": "div.book", "bookUrl": "href##$##,{'webView': true}"}
        }
        """,
        "webview",
    )

    @Test
    fun `java.webView 调用归类为 UNSUPPORTED`() = assertUnsupported(
        """
        {
          "bookSourceName": "java-webView",
          "searchUrl": "https://example.com/search",
          "ruleSearch": {"bookList": "div.book"},
          "ruleContent": {"content": "java.webView(null, url, 'document.body.innerHTML')"}
        }
        """,
        "webview",
    )

    @Test
    fun `getVerificationCode 验证码归类为 UNSUPPORTED`() = assertUnsupported(
        """
        {
          "bookSourceName": "verification-code",
          "searchUrl": "https://example.com/search",
          "ruleSearch": {"bookList": "div.book"},
          "ruleContent": {"content": "getVerificationCode(imgUrl)"}
        }
        """,
        "verificationcode",
    )

    @Test
    fun `startBrowserAwait 归类为 UNSUPPORTED`() = assertUnsupported(
        """
        {
          "bookSourceName": "startBrowserAwait",
          "searchUrl": "https://example.com/search",
          "ruleSearch": {"bookList": "div.book"},
          "ruleContent": {"content": "startBrowserAwait('https://example.com/login', '登录')"}
        }
        """,
        "startbrowser",
    )

    @Test
    fun `startBrowser 归类为 UNSUPPORTED`() = assertUnsupported(
        """
        {
          "bookSourceName": "startBrowser",
          "searchUrl": "https://example.com/search",
          "ruleSearch": {"bookList": "div.book"},
          "ruleContent": {"content": "startBrowser('https://example.com/login', '登录')"}
        }
        """,
        "startbrowser",
    )

    @Test
    fun `showBrowser 归类为 UNSUPPORTED`() = assertUnsupported(
        """
        {
          "bookSourceName": "showBrowser",
          "searchUrl": "https://example.com/search",
          "ruleSearch": {"bookList": "div.book"},
          "ruleContent": {"content": "showBrowser('https://example.com/login')"}
        }
        """,
        "showbrowser",
    )

    @Test
    fun `openVideoPlayer 播放器归类为 UNSUPPORTED`() = assertUnsupported(
        """
        {
          "bookSourceName": "openVideoPlayer",
          "searchUrl": "https://example.com/search",
          "ruleSearch": {"bookList": "div.book"},
          "ruleContent": {"content": "openVideoPlayer('https://example.com/video.mp4', '视频')"}
        }
        """,
        "openvideoplayer",
    )

    @Test
    fun `openUrl 外部跳转归类为 UNSUPPORTED`() = assertUnsupported(
        """
        {
          "bookSourceName": "openUrl",
          "searchUrl": "https://example.com/search",
          "ruleSearch": {"bookList": "div.book"},
          "ruleContent": {"content": "openUrl('https://example.com/app')"}
        }
        """,
        "openurl",
    )

    @Test
    fun `loginUrl 与 loginCheckJs 同时存在时归类为 UNSUPPORTED`() = assertUnsupported(
        """
        {
          "bookSourceName": "need-login",
          "searchUrl": "https://example.com/search",
          "ruleSearch": {"bookList": "div.book"},
          "loginUrl": "https://example.com/login",
          "loginCheckJs": "return document.cookie.includes('token')"
        }
        """,
        "login",
    )

    @Test
    fun `只有 loginCheckJs 没有 loginUrl 时不静态拦截`() {
        val source = doc(
            """
            {
              "bookSourceName": "loginCheckJs-only",
              "searchUrl": "https://example.com/search",
              "ruleSearch": {"bookList": "div.book"},
              "loginCheckJs": "return document.cookie.includes('token')"
            }
            """,
        )
        assertNull(engine.staticVerdict(source))
    }

    @Test
    fun `只有 @js 且没有不支持信号时不静态拦截`() {
        val source = doc(
            """
            {
              "bookSourceName": "js-only",
              "searchUrl": "https://example.com/search",
              "ruleSearch": {"bookList": "div.book"},
              "ruleContent": {"content": "@js: return document.body.innerText"}
            }
            """,
        )
        assertNull(engine.staticVerdict(source))
    }

    @Test
    fun `普通源没有静态不支持信号`() {
        val source = doc(
            """
            {
              "bookSourceName": "normal",
              "searchUrl": "https://example.com/search",
              "ruleSearch": {"bookList": "div.book", "name": "h2"},
              "ruleContent": {"content": "article.content"}
            }
            """,
        )
        assertNull(engine.staticVerdict(source))
    }

    @Test
    fun `mainJs 书源允许进入运行时校验`() {
        val source = doc(
            """
            {
              "bookSourceName": "js-source",
              "mainJs": "function searchBook(key) { return [{name: key, author: '', url: '', cover: '', kind: '', lastChapter: '', intro: ''}]; }",
              "ruleSearch": {"bookList": "div.book"}
            }
            """,
        )
        assertTrue(source.canSearch)
        assertNull(engine.staticVerdict(source))
    }
}
