package io.github.theeht.legadotidy.engine

import com.google.gson.Gson
import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.model.SourceField
import io.github.theeht.legadotidy.probe.ProbeDepth
import io.github.theeht.legadotidy.probe.ProbeEngine
import io.github.theeht.legadotidy.probe.ProbeOptions
import io.github.theeht.legadotidy.probe.ProbeResult
import io.github.theeht.legadotidy.probe.ProbeStatus
import io.legado.app.data.entities.BookSource
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.GSON
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
    /**
     * 必须用上游那套 `GSON`，不能自己 `GsonBuilder().create()`。
     *
     * 上游给它挂了几个关键适配器，其中 [io.legado.app.utils.StringJsonDeserializer]
     * 会把「不是原始值」的字段整体 `toString()` 成字面量——真实源里
     * `contentRule.replaceRegex` 有时是数组（老版本导出），自建 Gson 会直接抛
     * `Expected STRING but was BEGIN_ARRAY`，于是**能用的源被判成解析失败**。
     * 这类源在参考项目的 2407 个样本里占比不小，属于「一上来就全红」的原因之一。
     */
    private val gson: Gson = GSON,
) : ProbeEngine {

    override suspend fun probe(index: Int, source: BookSourceDoc, options: ProbeOptions): ProbeResult {
        val started = System.currentTimeMillis()

        // 1. 静态可判的，别浪费一次网络请求。
        //    这些不是「源坏了」，是「当前 headless 环境跑不通」，统一标记 UNSUPPORTED。
        staticVerdict(source)?.let { status ->
            return ProbeResult(
                index = index,
                status = status,
                elapsedMs = System.currentTimeMillis() - started,
            )
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
     * 静态判定：不花网络就能确定「当前 headless 环境跑不通」的情况。
     *
     * 判据参考 legado-tool-kotlin 的 `UnsupportedDetector` 实测口径（2407 个真实源里，
     * 8% 需要浏览器渲染、0.75% 需要人工介入）。这里只认最硬的信号，
     * 宁可放过（让它去跑一次、然后失败），也不误杀。
     *
     * 所有命中统一归类为 `UNSUPPORTED`，`detail` 记录具体原因，供导出/人工处理参考。
     */
    private fun staticVerdict(source: BookSourceDoc): ProbeStatus? {
        // 无搜索入口：不是「不支持」，是「没法校验」
        if (!source.canSearch) {
            return ProbeStatus.NO_SEARCH_URL
        }

        // 收集所有可能声明不支持能力的规则文本
        // 使用 behaviorFields() 动态获取所有非空行为字段（含 ruleToc.chapterUrl、ruleBookInfo.tocUrl、ruleContent.webJs 等）
        val rules = source.behaviorFields()
            .values
            .joinToString("\n") { it }
            .lowercase()

        // --- 需浏览器渲染类（参考 UnsupportedDetector.BROWSER_PATTERNS）---
        // webView 以各种形式出现：URL 选项 webView、java.webView、webViewGetSource 等
        if (rules.contains("webview") || rules.contains("webjs")) {
            return ProbeStatus.UNSUPPORTED
        }

        // --- 需人工介入类（参考 UnsupportedDetector.NEEDS_HUMAN）---
        // getVerificationCode / startBrowser* / showBrowser
        if (rules.contains("getverificationcode")
            || rules.contains("startbrowser")
            || rules.contains("showbrowser")) {
            return ProbeStatus.UNSUPPORTED
        }

        // --- 播放器 / 外部跳转类（headless 无对应实现）---
        if (rules.contains("openvideoplayer") || rules.contains("openurl")) {
            return ProbeStatus.UNSUPPORTED
        }

        // --- 登录需求 ---
        // loginUrl 非空且 loginCheckJs 非空 → 需登录，headless 无法完成
        if (source.loginUrl.isNotBlank() && source.loginCheckJs.isNotBlank()) {
            return ProbeStatus.UNSUPPORTED
        }

        return null // 静态看不出问题，交给运行时跑一次
    }

    private fun classify(e: Throwable): Pair<ProbeStatus, Int?> {
        val msg = e.message?.lowercase() ?: ""

        // 运行时也可能抛出这些（Rhino 抛 UnsupportedOperationException，或 JS 里显式 throw）
        // 顺序很重要：Timeout 是 IOException 的子类，必须先判。
        if (msg.contains("webview") || msg.contains("webjs")
            || msg.contains("getverificationcode") || msg.contains("startbrowser")
            || msg.contains("showbrowser") || msg.contains("openvideoplayer")
            || msg.contains("openurl")) {
            return ProbeStatus.UNSUPPORTED to null
        }

        return when (e) {
            is TimeoutCancellationException -> ProbeStatus.TIMEOUT to null
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