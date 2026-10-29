package io.github.theeht.legadotidy.engine

import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.probe.ProbeOptions
import io.github.theeht.legadotidy.probe.ProbeStatus
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LegadoProbeEngineTest {

    private val engine = LegadoProbeEngine()
    private val options = ProbeOptions(timeoutMs = 5000)

    private fun doc(json: String) = BookSourceDoc(JsonParser.parseString(json).asJsonObject)

    private suspend fun probe(source: BookSourceDoc): ProbeResult =
        engine.probe(0, source, options)

    @Test
    fun `无 searchUrl 的源归类为 NO_SEARCH_URL`() = runBlocking {
        val source = doc("""{"bookSourceName": "no-search"}""")
        val result = probe(source)
        assertEquals(ProbeStatus.NO_SEARCH_URL, result.status)
    }

    @Test
    fun `ruleSearch.webView 在 URL 选项中出现 → UNSUPPORTED`() = runBlocking {
        val source = doc("""
            {
              "bookSourceName": "webView-url-option",
              "searchUrl": "https://example.com/search",
              "ruleSearch": {"bookList": "div.book", "chapterUrl": "href##$##,{'webView': true}"}
            }
        """)
        val result = probe(source)
        assertEquals(ProbeStatus.UNSUPPORTED, result.status)
        assertTrue(result.detail!!.contains("webview", ignoreCase = true))
    }

    @Test
    fun `ruleToc.chapterUrl 带 webView 选项 → UNSUPPORTED`() = runBlocking {
        val source = doc("""
            {
              "bookSourceName": "webView-toc-chapterUrl",
              "searchUrl": "https://example.com/search",
              "ruleSearch": {"bookList": "div.book"},
              "ruleToc": {"chapterUrl": "href##$##,{'webView': true}"}
            }
        """)
        val result = probe(source)
        assertEquals(ProbeStatus.UNSUPPORTED, result.status)
        assertTrue(result.detail!!.contains("webview", ignoreCase = true))
    }

    @Test
    fun `ruleBookInfo.tocUrl 带 webView 选项 → UNSUPPORTED`() = runBlocking {
        val source = doc("""
            {
              "bookSourceName": "webView-bookInfo-tocUrl",
              "searchUrl": "https://example.com/search",
              "ruleSearch": {"bookList": "div.book"},
              "ruleBookInfo": {"tocUrl": "@get:{t}##$##,{'webView': true}"}
            }
        """)
        val result = probe(source)
        assertEquals(ProbeStatus.UNSUPPORTED, result.status)
        assertTrue(result.detail!!.contains("webview", ignoreCase = true))
    }

    @Test
    fun `ruleContent.webJs 声明 → UNSUPPORTED`() = runBlocking {
        val source = doc("""
            {
              "bookSourceName": "webJs-content",
              "searchUrl": "https://example.com/search",
              "ruleSearch": {"bookList": "div.book"},
              "ruleContent": {"webJs": "{'webView': true}"}
            }
        """)
        val result = probe(source)
        assertEquals(ProbeStatus.UNSUPPORTED, result.status)
        assertTrue(result.detail!!.contains("webview", ignoreCase = true))
    }

    @Test
    fun `header 中嵌入 webView 块 → UNSUPPORTED`() = runBlocking {
        val source = doc("""
            {
              "bookSourceName": "webView-header",
              "searchUrl": "https://example.com/search",
              "ruleSearch": {"bookList": "div.book"},
              "header": "{\"webView\": true}"
            }
        """)
        val result = probe(source)
        assertEquals(ProbeStatus.UNSUPPORTED, result.status)
        assertTrue(result.detail!!.contains("webview", ignoreCase = true))
    }

    @Test
    fun `ruleSearch 结果 URL 带 webView → UNSUPPORTED`() = runBlocking {
        val source = doc("""
            {
              "bookSourceName": "webView-search-result",
              "searchUrl": "https://example.com/search",
              "ruleSearch": {"bookList": "div.book", "bookUrl": "href##$##,{'webView': true}"}
            }
        """)
        val result = probe(source)
        assertEquals(ProbeStatus.UNSUPPORTED, result.status)
        assertTrue(result.detail!!.contains("webview", ignoreCase = true))
    }

    @Test
    fun `java.webView 调用 → UNSUPPORTED`() = runBlocking {
        val source = doc("""
            {
              "bookSourceName": "java-webView",
              "searchUrl": "https://example.com/search",
              "ruleSearch": {"bookList": "div.book"},
              "ruleContent": {"content": "java.webView(null, url, 'document.body.innerHTML')"}
            }
        """)
        val result = probe(source)
        assertEquals(ProbeStatus.UNSUPPORTED, result.status)
        assertTrue(result.detail!!.contains("webview", ignoreCase = true))
    }

    @Test
    fun `getVerificationCode 验证码 → UNSUPPORTED`() = runBlocking {
        val source = doc("""
            {
              "bookSourceName": "verification-code",
              "searchUrl": "https://example.com/search",
              "ruleSearch": {"bookList": "div.book"},
              "ruleContent": {"content": "getVerificationCode(imgUrl)"}
            }
        """)
        val result = probe(source)
        assertEquals(ProbeStatus.UNSUPPORTED, result.status)
        assertTrue(result.detail!!.contains("verificationcode", ignoreCase = true))
    }

    @Test
    fun `startBrowserAwait → UNSUPPORTED`() = runBlocking {
        val source = doc("""
            {
              "bookSourceName": "startBrowserAwait",
              "searchUrl": "https://example.com/search",
              "ruleSearch": {"bookList": "div.book"},
              "ruleContent": {"content": "startBrowserAwait('https://example.com/login', '登录')"}
            }
        """)
        val result = probe(source)
        assertEquals(ProbeStatus.UNSUPPORTED, result.status)
        assertTrue(result.detail!!.contains("startbrowser", ignoreCase = true))
    }

    @Test
    fun `startBrowser → UNSUPPORTED`() = runBlocking {
        val source = doc("""
            {
              "bookSourceName": "startBrowser",
              "searchUrl": "https://example.com/search",
              "ruleSearch": {"bookList": "div.book"},
              "ruleContent": {"content": "startBrowser('https://example.com/login', '登录')"}
            }
        """)
        val result = probe(source)
        assertEquals(ProbeStatus.UNSUPPORTED, result.status)
        assertTrue(result.detail!!.contains("startbrowser", ignoreCase = true))
    }

    @Test
    fun `showBrowser → UNSUPPORTED`() = runBlocking {
        val source = doc("""
            {
              "bookSourceName": "showBrowser",
              "searchUrl": "https://example.com/search",
              "ruleSearch": {"bookList": "div.book"},
              "ruleContent": {"content": "showBrowser('https://example.com/login')"}
            }
        """)
        val result = probe(source)
        assertEquals(ProbeStatus.UNSUPPORTED, result.status)
        assertTrue(result.detail!!.contains("showbrowser", ignoreCase = true))
    }

    @Test
    fun `openVideoPlayer 播放器 → UNSUPPORTED`() = runBlocking {
        val source = doc("""
            {
              "bookSourceName": "openVideoPlayer",
              "searchUrl": "https://example.com/search",
              "ruleSearch": {"bookList": "div.book"},
              "ruleContent": {"content": "openVideoPlayer('https://example.com/video.mp4', '视频')"}
            }
        """)
        val result = probe(source)
        assertEquals(ProbeStatus.UNSUPPORTED, result.status)
        assertTrue(result.detail!!.contains("openvideoplayer", ignoreCase = true))
    }

    @Test
    fun `openUrl 外部跳转 → UNSUPPORTED`() = runBlocking {
        val source = doc("""
            {
              "bookSourceName": "openUrl",
              "searchUrl": "https://example.com/search",
              "ruleSearch": {"bookList": "div.book"},
              "ruleContent": {"content": "openUrl('https://example.com/app')"}
            }
        """)
        val result = probe(source)
        assertEquals(ProbeStatus.UNSUPPORTED, result.status)
        assertTrue(result.detail!!.contains("openurl", ignoreCase = true))
    }

    @Test
    fun `loginUrl + loginCheckJs 登录需求 → UNSUPPORTED`() = runBlocking {
        val source = doc("""
            {
              "bookSourceName": "need-login",
              "searchUrl": "https://example.com/search",
              "ruleSearch": {"bookList": "div.book"},
              "loginUrl": "https://example.com/login",
              "loginCheckJs": "return document.cookie.includes('token')"
            }
        """)
        val result = probe(source)
        assertEquals(ProbeStatus.UNSUPPORTED, result.status)
        assertTrue(result.detail!!.contains("login", ignoreCase = true))
    }

    @Test
    fun `loginUrl 为空但有 loginCheckJs → 不拦截（交给运行时）`() = runBlocking {
        val source = doc("""
            {
              "bookSourceName": "loginCheckJs-only",
              "searchUrl": "https://example.com/search",
              "ruleSearch": {"bookList": "div.book"},
              "loginCheckJs": "return document.cookie.includes('token')"
            }
        """)
        val result = probe(source)
        // 静态阶段不拦截，运行时跑一次
        assertNull(result.status?.let { if (it == ProbeStatus.UNSUPPORTED) it else null })
    }

    @Test
    fun `只有 @js: 但无 webView/验证码 → 不拦截（Rhino 能跑）`() = runBlocking {
        val source = doc("""
            {
              "bookSourceName": "js-only",
              "searchUrl": "https://example.com/search",
              "ruleSearch": {"bookList": "div.book"},
              "ruleContent": {"content": "@js: return document.body.innerText"}
            }
        """)
        val result = probe(source)
        // 静态阶段不拦截 @js:，交给 Rhino 跑
        assertNull(result.status?.let { if (it == ProbeStatus.UNSUPPORTED) it else null })
    }

    @Test
    fun `正常源（无不支持信号） → 静态通过，交给运行时`() = runBlocking {
        val source = doc("""
            {
              "bookSourceName": "normal",
              "searchUrl": "https://example.com/search",
              "ruleSearch": {"bookList": "div.book", "name": "h2"},
              "ruleContent": {"content": "article.content"}
            }
        """)
        val result = probe(source)
        // 静态阶段返回 null，进入运行时（这里会因为网络失败归 NETWORK/ERROR，但不是 UNSUPPORTED）
        assertTrue(result.status != ProbeStatus.UNSUPPORTED)
    }

    @Test
    fun `JS 书源（mainJs 非空）且无不支持信号 → 静态通过`() = runBlocking {
        val source = doc("""
            {
              "bookSourceName": "js-source",
              "mainJs": "function searchBook(key) { return [{name: key, author: '', url: '', cover: '', kind: '', lastChapter: '', intro: ''}]; }",
              "ruleSearch": {"bookList": "div.book"}
            }
        """)
        val result = probe(source)
        assertTrue(result.status != ProbeStatus.UNSUPPORTED)
        assertTrue(source.canSearch) // mainJs 非空即可搜索
    }
}