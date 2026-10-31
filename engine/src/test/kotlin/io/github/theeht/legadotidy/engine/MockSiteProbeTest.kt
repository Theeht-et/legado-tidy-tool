package io.github.theeht.legadotidy.engine

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.model.SourceIO
import io.github.theeht.legadotidy.probe.ProbeDepth
import io.github.theeht.legadotidy.probe.ProbeOptions
import io.github.theeht.legadotidy.probe.ProbeStatus
import io.legado.app.utils.GSON
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 用本地假站点跑通「真引擎」。
 *
 * ## 为什么必须有这个测试
 * 这个模块的价值在于「引擎真的能跑」，而引擎是由上游源码 + 平台桩拼出来的。
 * 桩只要有一个方法签名不对，编译照样能过、运行期才炸。所以必须有一个真的 HTTP 往返：
 * 起一个本地服务器，写一个最普通的书源，让上游 `WebBook.searchBookAwait` 去搜。
 *
 * 用本地假站点而不是真站点的理由：
 * - 结果确定。真站点会改版、会限流，CI 里抖动会让人以为引擎坏了。
 * - 不碰第三方内容，也不需要外网。
 *
 * 它挡住的是「引擎接线错了」这类错误；挡不住「规则写得不好」，那不归静态校验管。
 */
class MockSiteProbeTest {

    private lateinit var server: HttpServer
    private var port: Int = 0

    @BeforeTest
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        port = server.address.port

        server.createContext("/search") { exchange ->
            respond(
                exchange,
                """
                <!DOCTYPE html>
                <html><head><meta charset="utf-8"><title>搜索</title></head>
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

        // 详情页直接带目录：覆盖上游先取书籍详情、再取目录的真实调用顺序。
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

    private fun respond(exchange: HttpExchange, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun sourceJson(): String = """
        {
          "bookSourceName": "本地模拟站",
          "bookSourceGroup": "测试",
          "bookSourceUrl": "http://127.0.0.1:$port",
          "bookSourceType": 0,
          "enabled": true,
          "searchUrl": "/search?q={{key}}",
          "ruleSearch": {
            "bookList": "class.list@tag.li",
            "name": "tag.a@text",
            "author": "class.au@text",
            "bookUrl": "tag.a@href"
          },
          "ruleBookInfo": { "name": "tag.h1@text", "author": "class.au@text" },
          "ruleToc": {
            "chapterList": "class.ch@tag.li",
            "chapterName": "tag.a@text",
            "chapterUrl": "tag.a@href"
          },
          "ruleContent": {
            "content": "class.content@text"
          }
        }
    """.trimIndent()

    private fun doc(): BookSourceDoc = BookSourceDoc(SourceIO.parse(sourceJson()).single())

    private fun options(depth: ProbeDepth = ProbeDepth.SEARCH) = ProbeOptions(
        depth = depth,
        timeoutMs = 10_000,
        keyword = "test",
    )

    @Test
    fun `搜索能解析出一条结果`() = runBlocking {
        val result = LegadoProbeEngine().probe(0, doc(), options())

        assertEquals(ProbeStatus.OK, result.status, "详情：${result.detail}")
        assertTrue(result.bookCount >= 1, "应至少解析出 1 本书，实际 ${result.bookCount}")
    }

    @Test
    fun `连目录一起验能拿到章节`() = runBlocking {
        val result = LegadoProbeEngine().probe(0, doc(), options(ProbeDepth.WITH_TOC))

        assertEquals(ProbeStatus.OK, result.status, "详情：${result.detail}")
        assertEquals("目录 2 章", result.detail)
        assertTrue(result.stageResults.any {
            it.stage == io.github.theeht.legadotidy.probe.ProbeStage.BOOK_INFO &&
                it.status == io.github.theeht.legadotidy.probe.ProbeStageStatus.SUCCESS
        })
    }

    @Test
    fun `全链路校验会依次完成搜索详情目录和正文`() = runBlocking {
        val result = LegadoProbeEngine().probe(0, doc(), options(ProbeDepth.FULL_CHAIN))

        assertEquals(ProbeStatus.OK, result.status, "详情：${result.detail}")
        assertEquals(
            listOf(
                io.github.theeht.legadotidy.probe.ProbeStage.SEARCH,
                io.github.theeht.legadotidy.probe.ProbeStage.BOOK_INFO,
                io.github.theeht.legadotidy.probe.ProbeStage.TOC,
                io.github.theeht.legadotidy.probe.ProbeStage.CONTENT,
            ),
            result.stageResults.map { it.stage },
        )
        assertTrue(result.stageResults.all {
            it.status == io.github.theeht.legadotidy.probe.ProbeStageStatus.SUCCESS
        })
        assertTrue(result.detail.orEmpty().contains("正文校验成功"))
    }

    @Test
    fun `正文解析失败时保留前序成功阶段并返回部分通过`() = runBlocking {
        val brokenJson = sourceJson().replace(
            "\"class.content@text\"",
            "\"class.missing-content@text\"",
        )
        val brokenDoc = BookSourceDoc(SourceIO.parse(brokenJson).single())

        val result = LegadoProbeEngine().probe(0, brokenDoc, options(ProbeDepth.FULL_CHAIN))

        assertEquals(ProbeStatus.PARTIAL, result.status, "详情：${result.detail}")
        assertEquals(
            listOf(
                io.github.theeht.legadotidy.probe.ProbeStage.SEARCH,
                io.github.theeht.legadotidy.probe.ProbeStage.BOOK_INFO,
                io.github.theeht.legadotidy.probe.ProbeStage.TOC,
                io.github.theeht.legadotidy.probe.ProbeStage.CONTENT,
            ),
            result.stageResults.map { it.stage },
        )
        assertTrue(result.stageResults.take(3).all {
            it.status == io.github.theeht.legadotidy.probe.ProbeStageStatus.SUCCESS
        })
        assertEquals(
            io.github.theeht.legadotidy.probe.ProbeStageStatus.FAILED,
            result.stageResults.last().status,
        )
    }

    @Test
    fun `JS 书源按上游函数链完成搜索详情目录和正文`() = runBlocking {
        val mainJs = """
            function search(key, page) {
                return [{
                    name: "JS 书源书",
                    author: "JS 作者",
                    bookUrl: baseUrl + "/js-book/",
                    tocUrl: baseUrl + "/js-book/"
                }];
            }
            // getBookInfo 是上游可选函数；缺失时应沿用搜索阶段字段。
            function getChapters(book) {
                return [
                    { title: "JS 第一章", url: baseUrl + "/js-chapter/1" },
                    { title: "JS 第二章", url: baseUrl + "/js-chapter/2" }
                ];
            }
            function getContent(chapter, book, nextChapterUrl) {
                return "JS 正文：" + chapter.title;
            }
        """.trimIndent()
        val json = """
            {
              "bookSourceName": "本地 JS 书源",
              "bookSourceUrl": "http://127.0.0.1:$port",
              "bookSourceType": 0,
              "mainJs": ${GSON.toJson(mainJs)}
            }
        """.trimIndent()
        val jsDoc = BookSourceDoc(SourceIO.parse(json).single())

        val result = LegadoProbeEngine().probe(0, jsDoc, options(ProbeDepth.FULL_CHAIN))

        assertEquals(ProbeStatus.OK, result.status, "详情：${result.detail}")
        // bookCount is the number of search hits (one book); chapter count belongs to TOC.
        assertEquals(1, result.bookCount)
        assertEquals(
            1,
            result.stageResults.first {
                it.stage == io.github.theeht.legadotidy.probe.ProbeStage.SEARCH
            }.itemCount,
        )
        assertEquals(
            2,
            result.stageResults.first {
                it.stage == io.github.theeht.legadotidy.probe.ProbeStage.TOC
            }.itemCount,
        )
        assertEquals(
            listOf(
                io.github.theeht.legadotidy.probe.ProbeStage.SEARCH,
                io.github.theeht.legadotidy.probe.ProbeStage.BOOK_INFO,
                io.github.theeht.legadotidy.probe.ProbeStage.TOC,
                io.github.theeht.legadotidy.probe.ProbeStage.CONTENT,
            ),
            result.stageResults.map { it.stage },
        )
        assertTrue(result.stageResults.all {
            it.status == io.github.theeht.legadotidy.probe.ProbeStageStatus.SUCCESS
        })
    }

    @Test
    fun `没有 searchUrl 的源不浪费网络请求`() = runBlocking {
        val json = """
            {
              "bookSourceName": "只有发现页",
              "bookSourceUrl": "http://127.0.0.1:$port",
              "bookSourceType": 0,
              "exploreUrl": "/explore"
            }
        """.trimIndent()
        val doc = BookSourceDoc(SourceIO.parse(json).single())

        val result = LegadoProbeEngine().probe(0, doc, options())

        assertEquals(ProbeStatus.NO_SEARCH_URL, result.status)
    }

    @Test
    fun `需要 webView 的源被静态识别出来`() = runBlocking {
        val json = """
            {
              "bookSourceName": "要浏览器",
              "bookSourceUrl": "http://127.0.0.1:$port",
              "bookSourceType": 0,
              "searchUrl": "/search?q={{key}}",
              "ruleSearch": {
                "bookList": "class.list@tag.li@js:webView(\"xxx\")",
                "name": "tag.a@text",
                "bookUrl": "tag.a@href"
              }
            }
        """.trimIndent()
        val doc = BookSourceDoc(SourceIO.parse(json).single())

        val result = LegadoProbeEngine().probe(0, doc, options())

        assertEquals(ProbeStatus.UNSUPPORTED, result.status, "详情：${result.detail}")
    }
}
