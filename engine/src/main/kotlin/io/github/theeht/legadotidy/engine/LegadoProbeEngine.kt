package io.github.theeht.legadotidy.engine

import com.google.gson.Gson
import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.probe.ProbeDepth
import io.github.theeht.legadotidy.probe.ProbeEntry
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
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.help.source.exploreKinds
import io.legado.app.help.source.flushExploreInfoMapIfNeeded
import io.legado.app.help.source.initializeExploreInfoMap
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

        staticVerdict(source, options.entry)?.let { verdict ->
            val stage = if (options.entry == ProbeEntry.EXPLORE) {
                ProbeStage.EXPLORE_CATEGORIES
            } else {
                ProbeStage.SEARCH
            }
            return ProbeResult(
                index = index,
                status = verdict.status,
                elapsedMs = System.currentTimeMillis() - started,
                detail = verdict.detail,
                entry = options.entry,
                depth = options.depth,
                keyword = options.keyword,
                timeoutMs = options.timeoutMs,
                maxExplorePages = options.maxExplorePages,
                stageResults = listOf(
                    ProbeStageResult(
                        stage = stage,
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
        var exploreLimitReached = false

        val perStageTimeoutMs = options.timeoutMs.coerceAtLeast(1L)
        // 搜索链最多四个阶段；发现链还包含分类解析和最多 N 次分页请求。
        val stageAllowance = if (options.entry == ProbeEntry.EXPLORE) {
            options.maxExplorePages.coerceIn(1, 10).toLong() + 4L
        } else {
            4L
        }
        val chainTimeoutMs = if (perStageTimeoutMs > Long.MAX_VALUE / stageAllowance) {
            Long.MAX_VALUE
        } else {
            perStageTimeoutMs * stageAllowance
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
                entry = options.entry,
                depth = options.depth,
                keyword = options.keyword,
                timeoutMs = options.timeoutMs,
                maxExplorePages = options.maxExplorePages,
            )

        return try {
            withTimeout(chainTimeoutMs) {
                val bookSource = gson.fromJson(source.raw, BookSource::class.java)
                    ?: return@withTimeout result(ProbeStatus.ERROR, "书源反序列化失败")

                when (options.entry) {
                    ProbeEntry.SEARCH -> {
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
                    }

                    ProbeEntry.EXPLORE -> {
                        var categories = emptyList<ExploreKind>()
                        val categoryStage = runStage(
                            stage = ProbeStage.EXPLORE_CATEGORIES,
                            emptyDetail = "没有可用于自动校验的 URL 类型发现分类",
                        ) {
                            categories = bookSource.exploreKinds()
                            bookSource.initializeExploreInfoMap(categories)
                            bookSource.flushExploreInfoMapIfNeeded()
                            categories.count {
                                it.type == ExploreKind.Type.url &&
                                    !it.url.isNullOrBlank() &&
                                    !it.title.startsWith("ERROR:")
                            }
                        }
                        val usableCategories = categories.filter {
                            it.type == ExploreKind.Type.url &&
                                !it.url.isNullOrBlank() &&
                                !it.title.startsWith("ERROR:")
                        }
                        if (categoryStage.status == ProbeStageStatus.FAILED) {
                            stageResults += categoryStage
                            return@withTimeout result(
                                categoryStage.failureStatus ?: ProbeStatus.ERROR,
                                categoryStage.detail,
                            )
                        }
                        if (usableCategories.isEmpty()) {
                            val parseError = categories.firstOrNull {
                                it.title.startsWith("ERROR:")
                            }
                            val failedCategoryStage = if (parseError != null) {
                                categoryStage.copy(
                                    status = ProbeStageStatus.FAILED,
                                    detail = parseError.title.removePrefix("ERROR:"),
                                    failureStatus = ProbeStatus.ERROR,
                                )
                            } else {
                                categoryStage
                            }
                            stageResults += failedCategoryStage
                            return@withTimeout result(
                                if (parseError != null) ProbeStatus.ERROR else ProbeStatus.NO_EXPLORE_CATEGORY,
                                failedCategoryStage.detail
                                    ?: "发现分类中没有可请求的 URL 类型分类",
                            )
                        }
                        stageResults += categoryStage

                        // The upstream UI opens one category at a time. Batch validation
                        // deterministically selects the first usable URL category.
                        val category = usableCategories.first()
                        val categoryUrl = checkNotNull(category.url)
                        val uniqueBooks = linkedSetOf<SearchBook>()
                        val maxPages = options.maxExplorePages.coerceIn(1, 10)

                        for (page in 1..maxPages) {
                            var pageBooks = emptyList<SearchBook>()
                            val previousUniqueCount = uniqueBooks.size
                            val pageStage = runStage(
                                stage = ProbeStage.EXPLORE,
                                emptyDetail = "第 ${page} 页没有解析出书籍",
                            ) {
                                pageBooks = WebBook.exploreBookAwait(
                                    bookSource = bookSource,
                                    url = categoryUrl,
                                    page = page,
                                )
                                bookSource.flushExploreInfoMapIfNeeded()
                                pageBooks.size
                            }

                            if (pageStage.status == ProbeStageStatus.FAILED) {
                                stageResults += pageStage.copy(
                                    detail = "分类「${category.title}」第 ${page} 页：${pageStage.detail.orEmpty()}",
                                )
                                if (uniqueBooks.isEmpty()) {
                                    return@withTimeout result(
                                        pageStage.failureStatus ?: ProbeStatus.ERROR,
                                        pageStage.detail,
                                    )
                                }
                                // A later-page error does not discard books from earlier pages,
                                // but the overall result remains PARTIAL after downstream checks.
                                break
                            }

                            uniqueBooks.addAll(pageBooks)
                            val addedCount = uniqueBooks.size - previousUniqueCount
                            if (addedCount == 0) {
                                if (page == 1) {
                                    stageResults += pageStage.copy(
                                        detail = "分类「${category.title}」第一页没有解析出书籍",
                                    )
                                    return@withTimeout result(
                                        ProbeStatus.PARSE_EMPTY,
                                        "发现分类「${category.title}」第一页没有解析出书籍",
                                    )
                                }
                                // This is the same stop condition used by the upstream
                                // ExploreShowViewModel: no increase in the distinct book set.
                                stageResults += pageStage.copy(
                                    status = ProbeStageStatus.SUCCESS,
                                    detail = "分类「${category.title}」第 ${page} 页没有新增书籍，按上游分页规则停止",
                                )
                                break
                            }

                            stageResults += pageStage.copy(
                                detail = buildString {
                                    append("分类「${category.title}」第 ${page} 页返回 ${pageBooks.size} 条，新增 ${addedCount} 条")
                                    if (page == maxPages && addedCount > 0) {
                                        append("；达到本次配置的 ${maxPages} 页上限，未确认是否还有后续结果")
                                    }
                                },
                            )
                            if (page == maxPages && addedCount > 0) {
                                exploreLimitReached = true
                            }
                        }

                        hits = uniqueBooks.toList()
                        bookCount = hits.size
                    }
                }

                if (options.depth == ProbeDepth.SEARCH) {
                    val hasExplorePageFailure = options.entry == ProbeEntry.EXPLORE &&
                        stageResults.any {
                            it.stage == ProbeStage.EXPLORE && it.status == ProbeStageStatus.FAILED
                        }
                    return@withTimeout result(
                        if (hasExplorePageFailure || exploreLimitReached) ProbeStatus.PARTIAL else ProbeStatus.OK,
                        if (options.entry == ProbeEntry.EXPLORE) {
                            summarizeStages(stageResults, options.depth)
                        } else {
                            null
                        },
                    )
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
                        canReName = true,
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
                    chapters = WebBook.getChapterListAwait(
                        bookSource = bookSource,
                        book = selectedBook,
                        isFromBookInfo = true,
                    ).getOrThrow()
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
                            // Upstream's BookContent falls back to the first chapter when no next
                            // chapter exists. Pass that boundary explicitly so the headless engine
                            // never has to consult the shared in-memory appDb DAO.
                            ?: chapters.firstOrNull { !it.isVolume && it.url.isNotBlank() }?.url
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

                val allPassed = stageResults.all { it.status == ProbeStageStatus.SUCCESS } &&
                    !exploreLimitReached
                result(
                    status = if (allPassed) ProbeStatus.OK else ProbeStatus.PARTIAL,
                    detail = summarizeStages(stageResults, options.depth),
                )
            }
        } catch (e: TimeoutCancellationException) {
            if (stageResults.lastOrNull()?.let {
                    it.stage == activeStage && it.status == ProbeStageStatus.FAILED
                } != true) {
                stageResults += ProbeStageResult(
                    stage = activeStage,
                    status = ProbeStageStatus.FAILED,
                    elapsedMs = System.currentTimeMillis() - started,
                    detail = "超过 " + options.timeoutMs + "ms",
                    failureStatus = ProbeStatus.TIMEOUT,
                )
            }
            val entryPassed = stageResults.any {
                (it.stage == ProbeStage.SEARCH || it.stage == ProbeStage.EXPLORE) &&
                    it.status == ProbeStageStatus.SUCCESS
            }
            result(
                status = if (entryPassed) ProbeStatus.PARTIAL else ProbeStatus.TIMEOUT,
                detail = summarizeStages(stageResults, options.depth)
                    ?: "单源校验超过 " + options.timeoutMs + "ms",
            )
        } catch (e: Throwable) {
            currentCoroutineContext().ensureActive()
            val (failureStatus, code) = classify(e)
            if (httpCode == null) httpCode = code
            if (stageResults.lastOrNull()?.let {
                    it.stage == activeStage && it.status == ProbeStageStatus.FAILED
                } != true) {
                stageResults += ProbeStageResult(
                    stage = activeStage,
                    status = ProbeStageStatus.FAILED,
                    elapsedMs = System.currentTimeMillis() - started,
                    detail = shortReason(e),
                    failureStatus = failureStatus,
                    httpCode = code,
                )
            }
            val entryPassed = stageResults.any {
                (it.stage == ProbeStage.SEARCH || it.stage == ProbeStage.EXPLORE) &&
                    it.status == ProbeStageStatus.SUCCESS
            }
            result(
                status = if (entryPassed) ProbeStatus.PARTIAL else failureStatus,
                detail = summarizeStages(stageResults, options.depth) ?: shortReason(e),
            )
        }
    }

    private fun summarizeStages(
        stages: List<ProbeStageResult>,
        depth: ProbeDepth = ProbeDepth.WITH_TOC,
    ): String? {
        val hasExplore = stages.any {
            it.stage == ProbeStage.EXPLORE_CATEGORIES || it.stage == ProbeStage.EXPLORE
        }
        if (depth == ProbeDepth.SEARCH && !hasExplore) return null

        val problems = stages.filter { it.status != ProbeStageStatus.SUCCESS }
        if (problems.isNotEmpty()) {
            return problems.joinToString("；") { stage ->
                val reason = stage.detail
                    ?: stage.failureStatus?.label
                    ?: stage.status.label
                stage.stage.label + stage.status.label + "：" + reason
            }
        }

        val exploreSummary = if (hasExplore) {
            val categoryCount = stages.firstOrNull {
                it.stage == ProbeStage.EXPLORE_CATEGORIES
            }?.itemCount ?: 0
            val pageCount = stages.count { it.stage == ProbeStage.EXPLORE }
            val limitReached = stages.any {
                it.stage == ProbeStage.EXPLORE && it.detail?.contains("达到本次配置的") == true
            }
            val suffix = if (limitReached) "（达到配置页数上限，结果可能不完整）" else ""
            "发现分类 $categoryCount 个；已按上游规则请求 $pageCount 页$suffix"
        } else {
            null
        }

        if (depth == ProbeDepth.SEARCH) return exploreSummary

        val chapterCount = stages.firstOrNull { it.stage == ProbeStage.TOC }?.itemCount ?: 0
        val chainSummary = if (depth == ProbeDepth.FULL_CHAIN) {
            "目录 $chapterCount 章；正文校验成功"
        } else {
            "目录 $chapterCount 章"
        }
        return listOfNotNull(exploreSummary, chainSummary).joinToString("；")
    }

    /**
     * 静态拦截只识别当前 headless 环境明确无法执行的能力。
     * 不在这里复刻上游的规则可执行性判断，实际搜索与解析仍由 WebBook 完成。
     */
    internal fun staticVerdict(
        source: BookSourceDoc,
        entry: ProbeEntry = ProbeEntry.SEARCH,
    ): StaticVerdict? {
        if (entry == ProbeEntry.SEARCH && !source.canSearch) {
            return StaticVerdict(
                ProbeStatus.NO_SEARCH_URL,
                "缺少上游搜索入口：普通源需要 searchUrl，JS 源需要 mainJs",
            )
        }
        if (entry == ProbeEntry.EXPLORE && !source.canExplore) {
            return StaticVerdict(
                ProbeStatus.NO_EXPLORE_CATEGORY,
                "缺少上游发现入口：需要配置 exploreUrl",
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
