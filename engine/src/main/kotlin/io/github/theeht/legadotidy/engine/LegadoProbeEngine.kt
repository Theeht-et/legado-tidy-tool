package io.github.theeht.legadotidy.engine

import com.google.gson.Gson
import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.probe.ProbeDepth
import io.github.theeht.legadotidy.probe.ProbeEngine
import io.github.theeht.legadotidy.probe.ProbeOptions
import io.github.theeht.legadotidy.probe.ProbeResult
import io.github.theeht.legadotidy.probe.ProbeStage
import io.github.theeht.legadotidy.probe.ProbeStageResult
import io.github.theeht.legadotidy.probe.ProbeStageStatus
import io.github.theeht.legadotidy.probe.ProbeStatus
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.BookSource
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.GSON
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 使用固定版本的 Legado WebBook 流程校验书源。
 *
 * SEARCH: searchBookAwait
 * WITH_TOC: searchBookAwait -> getBookInfoAwait -> getChapterListAwait
 * FULL_CHAIN: 上述步骤后再执行 getContentAwait（needSave=false）
 *
 * 阶段顺序和解析行为交给上游；本类只记录每一步的结果，不复制规则解析逻辑。
 */
class LegadoProbeEngine(
    /**
     * 必须用上游那套 GSON，不能自行创建 Gson。
     * 上游 StringJsonDeserializer 会兼容非原始值字段（例如旧源中的数组形式 replaceRegex）。
     */
    private val gson: Gson = GSON,
) : ProbeEngine {

    internal data class StaticVerdict(val status: ProbeStatus, val detail: String)

    override suspend fun probe(index: Int, source: BookSourceDoc, options: ProbeOptions): ProbeResult {
        val started = System.currentTimeMillis()

        staticVerdict(source)?.let { verdict ->
            return ProbeResult(
                index = index,
                status = verdict.status,
                elapsedMs = System.currentTimeMillis() - started,
                detail = verdict.detail,
                stageResults = listOf(
                    ProbeStageResult(
                        stage = ProbeStage.SEARCH,
                        status = ProbeStageStatus.NOT_RUN,
                        detail = verdict.detail,
                        failureStatus = verdict.status,
                    ),
                ),
            )
        }

        val stageResults = ArrayList<ProbeStageResult>(4)
        var activeStage = ProbeStage.SEARCH
        var hits: List<SearchBook> = emptyList()
        var bookCount = 0
        var httpCode: Int? = null

        val perStageTimeoutMs = options.timeoutMs.coerceAtLeast(1L)
        val chainTimeoutMs = if (perStageTimeoutMs > Long.MAX_VALUE / 4) {
            Long.MAX_VALUE
        } else {
            perStageTimeoutMs * 4
        }

        suspend fun runStage(
            stage: ProbeStage,
            emptyDetail: String,
            action: suspend () -> Int,
        ): ProbeStageResult {
            activeStage = stage
            val stageStarted = System.currentTimeMillis()
            return try {
                val count = withTimeout(perStageTimeoutMs) { action() }
                ProbeStageResult(
                    stage = stage,
                    status = if (count > 0) ProbeStageStatus.SUCCESS else ProbeStageStatus.EMPTY,
                    itemCount = count.coerceAtLeast(0),
                    elapsedMs = System.currentTimeMillis() - stageStarted,
                    detail = if (count > 0) null else emptyDetail,
                )
            } catch (e: TimeoutCancellationException) {
                // An individual stage timeout is recoverable; a parent timeout must propagate.
                currentCoroutineContext().ensureActive()
                ProbeStageResult(
                    stage = stage,
                    status = ProbeStageStatus.FAILED,
                    elapsedMs = System.currentTimeMillis() - stageStarted,
                    detail = "阶段超过 " + perStageTimeoutMs + "ms",
                    failureStatus = ProbeStatus.TIMEOUT,
                )
            } catch (e: Throwable) {
                // Do not turn parent cancellation into a failed source result.
                currentCoroutineContext().ensureActive()
                val (failureStatus, code) = classify(e)
                if (httpCode == null) httpCode = code
                ProbeStageResult(
                    stage = stage,
                    status = ProbeStageStatus.FAILED,
                    elapsedMs = System.currentTimeMillis() - stageStarted,
                    detail = shortReason(e),
                    failureStatus = failureStatus,
                    httpCode = code,
                )
            }
        }

        fun addNotRun(stage: ProbeStage, reason: String) {
            stageResults += ProbeStageResult(
                stage = stage,
                status = ProbeStageStatus.NOT_RUN,
                detail = reason,
            )
        }

        fun result(status: ProbeStatus, detail: String? = null): ProbeResult =
            ProbeResult(
                index = index,
                status = status,
                httpCode = httpCode,
                bookCount = bookCount,
                elapsedMs = System.currentTimeMillis() - started,
                detail = detail,
                stageResults = stageResults.toList(),
            )

        return try {
            withTimeout(chainTimeoutMs) {
                val bookSource = gson.fromJson(source.raw, BookSource::class.java)
                    ?: return@withTimeout result(ProbeStatus.ERROR, "书源反序列化失败")

                val search = runStage(
                    stage = ProbeStage.SEARCH,
                    emptyDetail = "上游搜索流程未解析出书籍",
                ) {
                    hits = WebBook.searchBookAwait(
                        bookSource = bookSource,
                        key = options.keyword,
                        page = 1,
                    )
                    hits.size
                }
                stageResults += search
                bookCount = hits.size

                when (search.status) {
                    ProbeStageStatus.FAILED -> {
                        return@withTimeout result(
                            search.failureStatus ?: ProbeStatus.ERROR,
                            search.detail,
                        )
                    }
                    ProbeStageStatus.EMPTY -> {
                        return@withTimeout result(
                            ProbeStatus.PARSE_EMPTY,
                            "搜索页已请求，但上游未解析出书籍结果",
                        )
                    }
                    else -> Unit
                }

                if (options.depth == ProbeDepth.SEARCH) {
                    return@withTimeout result(ProbeStatus.OK)
                }

                var book: Book? = null
                val bookInfo = runStage(
                    stage = ProbeStage.BOOK_INFO,
                    emptyDetail = "上游未返回书籍详情",
                ) {
                    val candidate = hits.first().toBook()
                    book = candidate
                    book = WebBook.getBookInfoAwait(
                        bookSource = bookSource,
                        book = candidate,
                        canReName = false,
                    )
                    1
                }
                stageResults += bookInfo

                val selectedBook = book
                if (bookInfo.status != ProbeStageStatus.SUCCESS || selectedBook == null) {
                    addNotRun(ProbeStage.TOC, "书籍详情阶段未成功，目录阶段未执行")
                    if (options.depth == ProbeDepth.FULL_CHAIN) {
                        addNotRun(ProbeStage.CONTENT, "书籍详情或目录阶段未成功，正文阶段未执行")
                    }
                    return@withTimeout result(
                        ProbeStatus.PARTIAL,
                        summarizeStages(stageResults, options.depth),
                    )
                }

                var chapters = emptyList<io.legado.app.data.entities.BookChapter>()
                val toc = runStage(
                    stage = ProbeStage.TOC,
                    emptyDetail = "上游目录解析结果为空",
                ) {
                    chapters = WebBook.getChapterListAwait(bookSource, selectedBook).getOrThrow()
                    chapters.size
                }
                stageResults += toc

                if (options.depth == ProbeDepth.FULL_CHAIN) {
                    val chapterIndex = chapters.indexOfFirst { !it.isVolume && it.url.isNotBlank() }
                    if (toc.status != ProbeStageStatus.SUCCESS) {
                        addNotRun(ProbeStage.CONTENT, "目录阶段未成功，正文阶段未执行")
                    } else if (chapterIndex < 0) {
                        stageResults += ProbeStageResult(
                            stage = ProbeStage.CONTENT,
                            status = ProbeStageStatus.EMPTY,
                            detail = "目录中没有可读取的非卷章节",
                        )
                    } else {
                        val chapter = chapters[chapterIndex]
                        val nextChapterUrl = chapters
                            .drop(chapterIndex + 1)
                            .firstOrNull { !it.isVolume && it.url.isNotBlank() }
                            ?.url
                        val content = runStage(
                            stage = ProbeStage.CONTENT,
                            emptyDetail = "上游正文解析结果为空",
                        ) {
                            val text = WebBook.getContentAwait(
                                bookSource = bookSource,
                                book = selectedBook,
                                bookChapter = chapter,
                                nextChapterUrl = nextChapterUrl,
                                needSave = false,
                            )
                            if (text.isBlank()) 0 else 1
                        }
                        stageResults += content
                    }
                }

                val allPassed = stageResults.all { it.status == ProbeStageStatus.SUCCESS }
                result(
                    status = if (allPassed) ProbeStatus.OK else ProbeStatus.PARTIAL,
                    detail = summarizeStages(stageResults, options.depth),
                )
            }
        } catch (e: TimeoutCancellationException) {
            if (stageResults.none { it.stage == activeStage }) {
                stageResults += ProbeStageResult(
                    stage = activeStage,
                    status = ProbeStageStatus.FAILED,
                    elapsedMs = System.currentTimeMillis() - started,
                    detail = "超过 " + options.timeoutMs + "ms",
                    failureStatus = ProbeStatus.TIMEOUT,
                )
            }
            val searchPassed = stageResults.any {
                it.stage == ProbeStage.SEARCH && it.status == ProbeStageStatus.SUCCESS
            }
            result(
                status = if (searchPassed) ProbeStatus.PARTIAL else ProbeStatus.TIMEOUT,
                detail = summarizeStages(stageResults, options.depth)
                    ?: "单源校验超过 " + options.timeoutMs + "ms",
            )
        } catch (e: Throwable) {
            currentCoroutineContext().ensureActive()
            val (failureStatus, code) = classify(e)
            if (httpCode == null) httpCode = code
            if (stageResults.none { it.stage == activeStage }) {
                stageResults += ProbeStageResult(
                    stage = activeStage,
                    status = ProbeStageStatus.FAILED,
                    elapsedMs = System.currentTimeMillis() - started,
                    detail = shortReason(e),
                    failureStatus = failureStatus,
                    httpCode = code,
                )
            }
            val searchPassed = stageResults.any {
                it.stage == ProbeStage.SEARCH && it.status == ProbeStageStatus.SUCCESS
            }
            result(
                status = if (searchPassed) ProbeStatus.PARTIAL else failureStatus,
                detail = summarizeStages(stageResults, options.depth) ?: shortReason(e),
            )
        }
    }

    private fun summarizeStages(
        stages: List<ProbeStageResult>,
        depth: ProbeDepth = ProbeDepth.WITH_TOC,
    ): String? {
        if (depth == ProbeDepth.SEARCH) return null
        val problems = stages.filter { it.status != ProbeStageStatus.SUCCESS }
        if (problems.isNotEmpty()) {
            return problems.joinToString("；") { stage ->
                val reason = stage.detail
                    ?: stage.failureStatus?.label
                    ?: stage.status.label
                stage.stage.label + stage.status.label + "：" + reason
            }
        }
        val chapterCount = stages.firstOrNull { it.stage == ProbeStage.TOC }?.itemCount ?: 0
        return if (depth == ProbeDepth.FULL_CHAIN) {
            "目录 $chapterCount 章；正文校验成功"
        } else {
            "目录 $chapterCount 章"
        }
    }

    /**
     * 静态拦截只识别当前 headless 环境明确无法执行的能力。
     * 不在这里复刻上游的规则可执行性判断，实际搜索与解析仍由 WebBook 完成。
     */
    internal fun staticVerdict(source: BookSourceDoc): StaticVerdict? {
        if (!source.canSearch) {
            return StaticVerdict(
                ProbeStatus.NO_SEARCH_URL,
                "缺少上游搜索入口：普通源需要 searchUrl，JS 源需要 mainJs",
            )
        }

        // 只检查实际参与请求/规则执行的字段；header、展示字段与 ruleContent.webJs
        // 不能单凭出现 "webView" 字样就判定需要浏览器。
        val activeRules = source.behaviorFields()
            .filter { (key, _) ->
                (key == "searchUrl" || key == "exploreUrl" || key == "mainJs" ||
                    key == "loginCheckJs" || key.startsWith("rule")) &&
                    !key.endsWith(".webJs")
            }
            .values
            .joinToString("\n") { it }

        val webViewOption = Regex(
            """(?i)["']?webView["']?\s*:\s*(?:true|1|"true"|'true')""",
        ).containsMatchIn(activeRules)
        val javaWebViewCall = Regex(
            """(?i)\bjava\s*\.\s*webView\s*\(""",
        ).containsMatchIn(activeRules)
        val webViewGetSourceCall = Regex(
            """(?i)\bwebViewGetSource\s*\(""",
        ).containsMatchIn(activeRules)
        if (webViewOption || javaWebViewCall || webViewGetSourceCall) {
            return StaticVerdict(ProbeStatus.UNSUPPORTED, "规则明确要求 WebView，当前无浏览器环境")
        }

        fun calls(name: String): Boolean =
            Regex("""(?i)\b${Regex.escape(name)}\s*\(""").containsMatchIn(activeRules)

        if (calls("getVerificationCode")) {
            return StaticVerdict(ProbeStatus.UNSUPPORTED, "getVerificationCode 需要人工完成验证码")
        }
        if (calls("startBrowser") || calls("startBrowserAwait")) {
            return StaticVerdict(ProbeStatus.UNSUPPORTED, "startBrowser 需要人工介入浏览器")
        }
        if (calls("showBrowser")) {
            return StaticVerdict(ProbeStatus.UNSUPPORTED, "showBrowser 需要交互式浏览器")
        }
        if (calls("openVideoPlayer")) {
            return StaticVerdict(ProbeStatus.UNSUPPORTED, "openVideoPlayer 需要播放器支持")
        }
        if (calls("openUrl")) {
            return StaticVerdict(ProbeStatus.UNSUPPORTED, "openUrl 需要外部应用或页面跳转")
        }
        return null
    }

    private fun classify(e: Throwable): Pair<ProbeStatus, Int?> {
        val msg = e.message?.lowercase() ?: ""
        if (
            msg.contains("webview") ||
            msg.contains("getverificationcode") || msg.contains("startbrowser") ||
            msg.contains("showbrowser") || msg.contains("openvideoplayer") ||
            msg.contains("openurl")
        ) {
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

    private fun httpStatus(e: Throwable): Int? {
        val text = e.message ?: return null
        val match = Regex("""\b(4\d{2}|5\d{2})\b""").find(text) ?: return null
        return match.value.toIntOrNull()
    }

    private fun shortReason(e: Throwable): String {
        val message = e.message?.takeIf { it.isNotBlank() } ?: e::class.java.simpleName
        return message.lineSequence().first().take(120)
    }
}
