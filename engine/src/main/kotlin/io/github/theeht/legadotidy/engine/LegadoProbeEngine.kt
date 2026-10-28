package io.github.theeht.legadotidy.engine

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.probe.ProbeDepth
import io.github.theeht.legadotidy.probe.ProbeEngine
import io.github.theeht.legadotidy.probe.ProbeOptions
import io.github.theeht.legadotidy.probe.ProbeResult
import io.github.theeht.legadotidy.probe.ProbeStatus
import io.legado.app.data.entities.BookSource
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 用 Legado 引擎本体做校验（S2）。
 *
 * ## 为什么不逐条规则判「好不好用」
 * 只有真跑一遍才知道。规则写得再完整，站点改版、加了反爬、域名挂了，
 * 静态检查一个都看不出来。反过来，一个看起来「缺字段」的源可能恰好能用。
 * 所以这里的判定标准只有一个：**引擎按它自己的逻辑去搜一本书，能不能搜到**。
 *
 * ## 引擎是死的，调度是活的
 * 引擎内部有大量单例状态（`appDb`、`CacheManager`、`SharedJsScope`），
 * 所以「并发多少」必须由调用方控制，本类只管单个源的 probe，不做重试、不做排队。
 * 也不在循环里清 `appDb`：那是引擎的全局章节缓存，我们跑搜索根本不写它。
 *
 * ## 超时是两层的
 * `withTimeout` 只能打断协程挂起点，而引擎里的 HTTP 是阻塞调用，
 * 真掐断靠 OkHttp 的 read/call timeout（见 glue 的 HttpHelper，60s）。
 * 所以 `options.timeoutMs` 只保证「这一源不会无限期挂住调用方」，
 * 顶不到 60s 的源仍然会先被我们的 withTimeout 记成 TIMEOUT。
 */
class LegadoProbeEngine(
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create(),
) : ProbeEngine {

    override suspend fun probe(index: Int, source: BookSourceDoc, options: ProbeOptions): ProbeResult {
        val started = System.currentTimeMillis()

        // 1. 静态可判的，别浪费一次网络请求。
        //    这些不是「源坏了」，是「本工具不可能用」，所以要单独分类：
        //    用户看到「需要浏览器渲染」会去别处处理，看到「网络不通」只会白折腾。
        staticVerdict(source)?.let { status ->
            return ProbeResult(index, status, elapsedMs = System.currentTimeMillis() - started)
        }

        // 2. 真跑。
        return try {
            val bookSource = gson.fromJson(source.raw, BookSource::class.java)
                ?: return ProbeResult(index, ProbeStatus.ERROR, detail = "书源反序列化失败")

            val hits = withTimeout(options.timeoutMs) {
                WebBook.searchBookAwait(
                    bookSource = bookSource,
                    key = options.keyword,
                    page = 1,
                )
            }

            if (hits.isEmpty()) {
                return ProbeResult(
                    index = index,
                    status = ProbeStatus.PARSE_EMPTY,
                    elapsedMs = System.currentTimeMillis() - started,
                    detail = "搜索页打开了，但没解析出任何结果（规则与站点不匹配）",
                )
            }

            var chapters = 0
            var detail: String? = null

            if (options.depth == ProbeDepth.WITH_TOC) {
                // 取第一条结果继续往下走：能找到目录才算「真能用」。
                // 走不通就退回 OK 但附注——搜索能出结果已经说明域名和规则主体是对的。
                val book = hits.first().toBook()
                val toc = runCatching {
                    withTimeout(options.timeoutMs) {
                        WebBook.getChapterListAwait(bookSource, book).getOrThrow()
                    }
                }
                chapters = toc.getOrNull()?.size ?: 0
                if (toc.isFailure) {
                    detail = "搜索可用，但取目录失败：" + (toc.exceptionOrNull()?.let { shortReason(it) } ?: "未知原因")
                }
            }

            ProbeResult(
                index = index,
                status = ProbeStatus.OK,
                bookCount = hits.size,
                elapsedMs = System.currentTimeMillis() - started,
                detail = detail ?: if (options.depth == ProbeDepth.WITH_TOC) "目录 $chapters 章" else null,
            )
        } catch (e: TimeoutCancellationException) {
            ProbeResult(
                index, ProbeStatus.TIMEOUT,
                elapsedMs = System.currentTimeMillis() - started,
                detail = "超过 ${options.timeoutMs}ms",
            )
        } catch (e: Throwable) {
            val (status, httpCode) = classify(e)
            ProbeResult(
                index = index,
                status = status,
                httpCode = httpCode,
                elapsedMs = System.currentTimeMillis() - started,
                detail = shortReason(e),
            )
        }
    }

    /**
     * 静态判定：不花网络就能确定「本工具跑不了」的情况。
     *
     * 判据来自参考项目的实测口径（2407 个真实源里，8% 需要浏览器、0.75% 需要人工）。
     * 这里只认最硬的信号，宁可放过（让它去跑一次、然后失败），也不误杀。
     */
    private fun staticVerdict(source: BookSourceDoc): ProbeStatus? {
        if (!source.canSearch) {
            // 没有 searchUrl 的源，本次校验根本没有入口。
            // 它不是「坏源」——有的源只提供发现/探索，不提供搜索。
            return ProbeStatus.NO_SEARCH_URL
        }

        val rules = buildString {
            append(source.searchUrl)
            for (k in listOf("bookList", "name", "author", "kind", "bookUrl", "coverUrl", "lastChapter")) {
                append('\n').append(source.ruleSearch[k])
            }
        }

        // webView / webJs：要求真实浏览器内核渲染，headless 下必然拿不到内容。
        if (rules.contains("webView", ignoreCase = true) || rules.contains("webJs", ignoreCase = true)) {
            return ProbeStatus.NEED_BROWSER
        }

        // 其余已知的「引擎有、但我们这套补全层没有」的 JS 能力。
        if (rules.contains("@js:", ignoreCase = true) || rules.contains("<js>", ignoreCase = true)) {
            return ProbeStatus.NEED_JS
        }

        return null
    }

    private fun classify(e: Throwable): Pair<ProbeStatus, Int?> {
        // 顺序很重要：Timeout 是 IOException 的子类，必须先判。
        return when (e) {
            is SocketTimeoutException -> ProbeStatus.TIMEOUT to null
            is UnknownHostException -> ProbeStatus.NETWORK to null
            is IOException -> ProbeStatus.NETWORK to null
            else -> {
                val code = httpStatus(e)
                if (code != null) ProbeStatus.HTTP_ERROR to code else ProbeStatus.ERROR to null
            }
        }
    }

    /** 引擎抛的是 `HttpException`，状态码只能从 message 里捞（上游没给字段）。 */
    private fun httpStatus(e: Throwable): Int? {
        val text = e.message ?: return null
        val m = Regex("""\b(4\d{2}|5\d{2})\b""").find(text) ?: return null
        return m.value.toIntOrNull()
    }

    private fun shortReason(e: Throwable): String {
        val msg = e.message?.takeIf { it.isNotBlank() } ?: e::class.java.simpleName
        return msg.lineSequence().first().take(120)
    }
}
