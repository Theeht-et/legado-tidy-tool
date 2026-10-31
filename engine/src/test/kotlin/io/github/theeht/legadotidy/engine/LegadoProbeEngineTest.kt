package io.github.theeht.legadotidy.engine

import com.google.gson.JsonParser
import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.probe.ProbeDepth
import io.github.theeht.legadotidy.probe.ProbeEntry
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
    fun `只有发现入口的书源可以进入发现校验但不能进入搜索校验`() {
        val source = doc(
            """{
              "bookSourceUrl":"https://example.test",
              "exploreUrl":"分类::/explore?page={{page}}"
            }""",
        )

        assertEquals(ProbeStatus.NO_SEARCH_URL, engine.staticVerdict(source)?.status)
        assertNull(engine.staticVerdict(source, ProbeEntry.EXPLORE))
    }

    @Test
    fun `发现校验缺少 exploreUrl 时归类为 NO_EXPLORE_CATEGORY`() {
        val source = doc(
            """{
              "bookSourceUrl":"https://example.test",
              "searchUrl":"/search"
            }""",
        )

        val verdict = assertNotNull(engine.staticVerdict(source, ProbeEntry.EXPLORE))
        assertEquals(ProbeStatus.NO_EXPLORE_CATEGORY, verdict.status)
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
    fun `目录章节 URL 带 webView 选项归类为 UNSUPPORTED`() = assertUnsupported(
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
    fun `书籍信息中的目录 URL 带 webView 选项归类为 UNSUPPORTED`() = assertUnsupported(
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
    fun `ruleContent 中的 webJs 文本本身不等于 WebView 请求`() {
        val source = doc(
            """
            {
              "bookSourceName": "webJs-content",
              "searchUrl": "https://example.com/search",
              "ruleSearch": {"bookList": "div.book"},
              "ruleContent": {"webJs": "return response"}
            }
            """,
        )
        assertNull(engine.staticVerdict(source))
    }

    @Test
    fun `header 中出现 webView 字样不代表请求要使用浏览器`() {
        val source = doc(
            """
            {
              "bookSourceName": "webView-header",
              "searchUrl": "https://example.com/search",
              "ruleSearch": {"bookList": "div.book"},
              "header": "{\"webView\": true}"
            }
            """,
        )
        assertNull(engine.staticVerdict(source))
    }

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
    fun `Java webView 调用归类为 UNSUPPORTED`() = assertUnsupported(
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
    fun `仅配置登录入口不预判登录必需而交给上游检查脚本`() {
        val source = doc(
            """
            {
              "bookSourceName": "need-login",
              "searchUrl": "https://example.com/search",
              "ruleSearch": {"bookList": "div.book"},
              "loginUrl": "https://example.com/login",
              "loginCheckJs": "return document.cookie.includes('token')"
            }
            """,
        )
        assertNull(engine.staticVerdict(source))
    }

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
    fun `搜索链不扫描未执行的发现和段评规则`() {
        val source = doc(
            """
            {
              "bookSourceName": "unrelated-rules",
              "searchUrl": "https://example.com/search",
              "ruleSearch": {"bookList": "div.book"},
              "exploreUrl": "@js: java.webView(null, url, 'document.body.innerHTML')",
              "ruleExplore": {"bookList": "java.webView(null, url, 'document.body.innerHTML')"},
              "ruleReview": {"content": "getVerificationCode(img)"}
            }
            """,
        )
        assertNull(engine.staticVerdict(source, ProbeEntry.SEARCH, ProbeDepth.FULL_CHAIN))
    }

    @Test
    fun `轻量搜索深度不会扫描未执行的正文规则`() {
        val source = doc(
            """
            {
              "bookSourceName": "search-only-depth",
              "searchUrl": "https://example.com/search",
              "ruleSearch": {"bookList": "div.book"},
              "ruleContent": {"content": "java.webView(null, url, 'document.body.innerHTML')"}
            }
            """,
        )
        assertNull(engine.staticVerdict(source, ProbeEntry.SEARCH, ProbeDepth.SEARCH))
        assertEquals(ProbeStatus.UNSUPPORTED, engine.staticVerdict(source)?.status)
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
