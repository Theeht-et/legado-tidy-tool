package io.github.theeht.legadotidy.engine

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.model.SourceIO
import io.github.theeht.legadotidy.probe.ProbeOptions
import io.github.theeht.legadotidy.probe.ProbeStage
import io.github.theeht.legadotidy.probe.ProbeStageStatus
import io.github.theeht.legadotidy.probe.ProbeStatus
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 用本地假站点验证真实上游 WebBook 调用以及 CheckSourceService 的单轮编排。
 * 不访问公网，不依赖可变的第三方书源。
 */
class MockSiteProbeTest {

    private lateinit var server: HttpServer
    private var port: Int = 0
    @Volatile private var searchEmpty = false
    private val searchQueries = Collections.synchronizedList(mutableListOf<String>())
    private val discoveryPages = Collections.synchronizedList(mutableListOf<Int>())
    private val contentRequests = AtomicInteger()

    @BeforeTest
    fun start() {
        searchEmpty = false
        searchQueries.clear()
        discoveryPages.clear()

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        port = server.address.port

        server.createContext("/search") { exchange ->
            searchQueries += exchange.requestURI.rawQuery.orEmpty()
            when {
                searchEmpty -> respond(exchange, "<html><body><ul class='list'></ul></body></html>")
                else -> respond(
                    exchange,
                    """
                    <!DOCTYPE html>
                    <html><head><meta charset="utf-8"></head>
                    <body>
                      <ul class="list">
                        <li class="book-item">
                          <a href="/book/1/">测试之书</a>
                          <span class="au">某作者</span>
                        </li>
                      </ul>
                    </body></html>
                    """.trimIndent(),
                )
            }
        }

        server.createContext("/explore") { exchange ->
            val page = exchange.requestURI.rawQuery.orEmpty()
                .substringAfter("page=", "1")
                .toIntOrNull() ?: 1
            discoveryPages += page
            respond(
                exchange,
                """
                <html><body>
                  <a href="/book/1/">发现之书</a>
                </body></html>
                """.trimIndent(),
            )
        }

        server.createContext("/book/1/") { exchange ->
            respond(
                exchange,
                """
                <!DOCTYPE html>
                <html><head><meta charset="utf-8"><title>测试之书</title></head>
                <body>
                  <h1>测试之书</h1>
                  <span class="au">某作者</span>
                  <ul class="ch">
                    <li><a href="/book/1/c1">第一章</a></li>
                    <li><a href="/book/1/c2">第二章</a></li>
                  </ul>
                </body></html>
                """.trimIndent(),
            )
        }

        server.createContext("/book/1/c1") { exchange ->
            contentRequests.incrementAndGet()
            respond(
                exchange,
                """
                <!DOCTYPE html>
                <html><head><meta charset="utf-8"></head>
                <body><div class="content">这是第一章的正文内容。</div></body></html>
                """.trimIndent(),
            )
        }

        server.start()
    }

    @AfterTest
    fun stop() {
        server.stop(0)
    }

    private fun respond(exchange: HttpExchange, html: String, code: Int = 200) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun sourceJson(
        includeDiscovery: Boolean = true,
        checkKeyWord: String = "",
    ): String {
        val discovery = if (includeDiscovery) {
            """,
              "exploreUrl": "本地分类::/explore?page={{page}}",
              "ruleExplore": {
                "bookList": "tag.a",
                "name": "text",
                "bookUrl": "href"
              }"""
        } else {
            ""
        }
        val json = """
            {
              "bookSourceName": "本地模拟站",
              "bookSourceGroup": "测试",
              "bookSourceUrl": "http://127.0.0.1:PORT",
              "bookSourceType": 0,
              "enabled": true,
              "searchUrl": "/search?q={{key}}",
              "ruleSearch": {
                "bookList": "class.list@tag.li",
                "name": "tag.a@text",
                "author": "class.au@text",
                "bookUrl": "tag.a@href",
                "checkKeyWord": "SOURCE_KEYWORD"
              },
              "ruleBookInfo": { "name": "tag.h1@text", "author": "class.au@text" },
              "ruleToc": {
                "chapterList": "class.ch@tag.li",
                "chapterName": "tag.a@text",
                "chapterUrl": "tag.a@href"
              },
              "ruleContent": { "content": "class.content@text" }DISCOVERY
            }
        """.trimIndent()
        return json
            .replace("PORT", port.toString())
            .replace("SOURCE_KEYWORD", checkKeyWord)
            .replace("DISCOVERY", discovery)
    }

    private fun doc(
        includeDiscovery: Boolean = true,
        checkKeyWord: String = "",
    ): BookSourceDoc = BookSourceDoc(
        SourceIO.parse(sourceJson(includeDiscovery, checkKeyWord)).single(),
    )

    private fun options(
        checkDomain: Boolean = false,
        checkSearch: Boolean = true,
        checkDiscovery: Boolean = true,
        checkInfo: Boolean = true,
        checkCategory: Boolean = true,
        checkContent: Boolean = true,
        keyword: String = "test",
        timeoutMs: Long = 10_000,
    ) = ProbeOptions(
        timeoutMs = timeoutMs,
        keyword = keyword,
        checkDomain = checkDomain,
        checkSearch = checkSearch,
        checkDiscovery = checkDiscovery,
        checkInfo = checkInfo,
        checkCategory = checkCategory,
        checkContent = checkContent,
    )

    @Test
    fun singleRoundRunsDomainThenSearchThenDiscoveryAndOnlyFirstDiscoveryPage() = runBlocking {
        val result = LegadoProbeEngine().probe(
            0,
            doc(),
            options(checkDomain = true),
        )

        assertEquals(ProbeStatus.OK, result.status, "详情：" + result.detail)
        assertEquals(listOf(1), discoveryPages.toList())
        assertTrue(contentRequests.get() >= 1)

        val domainIndex = result.stageResults.indexOfFirst { it.stage == ProbeStage.DOMAIN }
        val searchIndex = result.stageResults.indexOfFirst { it.stage == ProbeStage.SEARCH }
        val discoveryIndex = result.stageResults.indexOfFirst { it.stage == ProbeStage.DISCOVERY }
        assertTrue(domainIndex >= 0 && searchIndex > domainIndex && discoveryIndex > searchIndex)
        assertEquals(result.actualElapsedMs, result.elapsedMs)
    }

    @Test
    fun emptySearchStillRunsDiscoveryAndKeepsSearchFailure() = runBlocking {
        searchEmpty = true
        val result = LegadoProbeEngine().probe(0, doc(), options())

        assertEquals(ProbeStatus.PARSE_EMPTY, result.status, "详情：" + result.detail)
        assertEquals(listOf(1), discoveryPages.toList())
        assertEquals(
            ProbeStageStatus.EMPTY,
            result.stageResults.first { it.stage == ProbeStage.SEARCH }.status,
        )
        assertTrue(result.stageResults.any {
            it.stage == ProbeStage.DISCOVERY && it.status == ProbeStageStatus.SUCCESS
        })
        assertTrue(result.detail.orEmpty().contains("搜索失效"))
    }

    @Test
    fun searchRequestExceptionStopsBeforeDiscovery() = runBlocking {
        val unusedPort = ServerSocket(0).use { it.localPort }
        val json = sourceJson().replace(
            "\"searchUrl\": \"/search?q={{key}}\"",
            "\"searchUrl\": \"http://127.0.0.1:" + unusedPort + "/search?q={{key}}\"",
        )
        val source = BookSourceDoc(SourceIO.parse(json).single())
        val timeoutMs = 10_000L
        val result = LegadoProbeEngine().probe(0, source, options(timeoutMs = timeoutMs))

        assertEquals(ProbeStatus.NETWORK, result.status, "详情：" + result.detail)
        assertTrue(discoveryPages.isEmpty())
        assertEquals(timeoutMs, result.elapsedMs - result.actualElapsedMs)
        assertEquals(
            ProbeStageStatus.FAILED,
            result.stageResults.first { it.stage == ProbeStage.SEARCH }.status,
        )
        assertEquals(
            ProbeStageStatus.NOT_RUN,
            result.stageResults.first { it.stage == ProbeStage.DISCOVERY }.status,
        )
    }

    @Test
    fun sourceCheckKeywordOverridesToolDefaultKeyword() = runBlocking {
        val result = LegadoProbeEngine().probe(
            0,
            doc(includeDiscovery = false, checkKeyWord = "source-keyword"),
            options(checkDiscovery = false, keyword = "tool-keyword"),
        )

        assertEquals(ProbeStatus.OK, result.status, "详情：" + result.detail)
        assertTrue(searchQueries.single().contains("source-keyword"), searchQueries.single())
        assertFalse(searchQueries.single().contains("tool-keyword"))
    }

    @Test
    fun nonHttpSourceIsMarkedUncheckableButOtherChecksContinue() = runBlocking {
        val origin = "file:///tmp/non-http-source"
        val httpBase = "http://127.0.0.1:" + port
        val json = sourceJson()
            .replace(httpBase, origin)
            .replace(
                "\"searchUrl\": \"/search?q={{key}}\"",
                "\"searchUrl\": \"" + httpBase + "/search?q={{key}}\"",
            )
            .replace(
                "\"exploreUrl\": \"本地分类::/explore?page={{page}}\"",
                "\"exploreUrl\": \"本地分类::" + httpBase + "/explore?page={{page}}\"",
            )
        val source = BookSourceDoc(SourceIO.parse(json).single())
        val result = LegadoProbeEngine().probe(0, source, options(checkDomain = true))

        assertEquals(ProbeStatus.UNCHECKABLE, result.status, "详情：" + result.detail)
        assertEquals(listOf(1), discoveryPages.toList())
        assertTrue(searchQueries.isNotEmpty())
        assertEquals(result.actualElapsedMs, result.elapsedMs)
        assertEquals(
            ProbeStageStatus.NOT_RUN,
            result.stageResults.first { it.stage == ProbeStage.DOMAIN }.status,
        )
    }

    @Test
    fun disablingSearchStillAllowsDiscovery() = runBlocking {
        val result = LegadoProbeEngine().probe(
            0,
            doc(),
            options(checkSearch = false, checkDiscovery = true),
        )

        assertEquals(ProbeStatus.OK, result.status, "详情：" + result.detail)
        assertTrue(searchQueries.isEmpty())
        assertEquals(listOf(1), discoveryPages.toList())
        assertEquals(
            ProbeStageStatus.NOT_RUN,
            result.stageResults.first { it.stage == ProbeStage.SEARCH }.status,
        )
        assertTrue(result.stageResults.any {
            it.stage == ProbeStage.DISCOVERY && it.status == ProbeStageStatus.SUCCESS
        })
    }

    @Test
    fun disablingBookInfoSkipsAllDownstreamBookChecksButStillSearchesAndDiscovers() = runBlocking {
        val result = LegadoProbeEngine().probe(
            0,
            doc(),
            options(checkInfo = false),
        )

        assertEquals(ProbeStatus.OK, result.status, "详情：" + result.detail)
        assertEquals(listOf(1), discoveryPages.toList())
        assertTrue(result.stageResults.filter {
            it.stage == ProbeStage.BOOK_INFO || it.stage == ProbeStage.TOC || it.stage == ProbeStage.CONTENT
        }.all { it.status == ProbeStageStatus.NOT_RUN })
        assertEquals(0, contentRequests.get())
    }

    @Test
    fun categoryAndContentTogglesRespectUpstreamDependencies() = runBlocking {
        val noCategory = LegadoProbeEngine().probe(
            0,
            doc(includeDiscovery = false),
            options(checkDiscovery = false, checkCategory = false),
        )
        assertEquals(ProbeStatus.OK, noCategory.status, "详情：" + noCategory.detail)
        assertTrue(noCategory.stageResults.filter {
            it.stage == ProbeStage.TOC || it.stage == ProbeStage.CONTENT
        }.all { it.status == ProbeStageStatus.NOT_RUN })

        val noContent = LegadoProbeEngine().probe(
            1,
            doc(includeDiscovery = false),
            options(checkDiscovery = false, checkContent = false),
        )
        assertEquals(ProbeStatus.OK, noContent.status, "详情：" + noContent.detail)
        assertTrue(noContent.stageResults.filter {
            it.stage == ProbeStage.CONTENT
        }.all { it.status == ProbeStageStatus.NOT_RUN })
    }

    @Test
    fun domainFailureStopsSearchAndDiscoveryAndUsesUpstreamFailurePenalty() = runBlocking {
        val unusedPort = ServerSocket(0).use { it.localPort }
        val json = sourceJson().replace("127.0.0.1:" + port, "127.0.0.1:" + unusedPort)
        val source = BookSourceDoc(SourceIO.parse(json).single())
        val timeoutMs = 10_000L
        val result = LegadoProbeEngine().probe(
            0,
            source,
            options(checkDomain = true, timeoutMs = timeoutMs),
        )

        assertEquals(ProbeStatus.NETWORK, result.status, "详情：" + result.detail)
        assertTrue(searchQueries.isEmpty())
        assertTrue(discoveryPages.isEmpty())
        assertEquals(
            ProbeStageStatus.FAILED,
            result.stageResults.first { it.stage == ProbeStage.DOMAIN }.status,
        )
        assertEquals(timeoutMs, result.elapsedMs - result.actualElapsedMs)
    }
}
