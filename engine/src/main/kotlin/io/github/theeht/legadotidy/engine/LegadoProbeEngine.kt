package io.github.theeht.legadotidy.engine

import com.google.gson.Gson
import com.script.ScriptException
import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.probe.ProbeEngine
import io.github.theeht.legadotidy.probe.ProbeOptions
import io.github.theeht.legadotidy.probe.ProbeResult
import io.github.theeht.legadotidy.probe.ProbeStage
import io.github.theeht.legadotidy.probe.ProbeStageResult
import io.github.theeht.legadotidy.probe.ProbeStageStatus
import io.github.theeht.legadotidy.probe.ProbeStatus
import io.legado.app.constant.BookSourceType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.exception.ContentEmptyException
import io.legado.app.exception.NoStackTraceException
import io.legado.app.exception.TocEmptyException
import io.legado.app.help.http.ProbeProxyContext
import io.legado.app.help.http.parseProxyConfig
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.GSON
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.htmlunit.corejs.javascript.WrappedException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 校验编排严格跟随 Legado CheckSourceService.checkSource/doCheckSource/checkBook。
 *
 * WebBook 负责搜索、发现、详情、目录和正文的实际解析；本类只负责按上游顺序
 * 调用这些入口、记录六项检查耗时，并计算与 BookSource.respondTime 一致的总响应耗时。
 *
 * 上游行为基准：
 * - checkDomain -> checkSearch + checkBook(search) -> checkDiscovery + checkBook(discovery)
 * - checkInfo / checkCategory / checkContent 分别控制 checkBook 内部阶段
 * - 每个书源只有一个 CheckSource.timeout 总超时；域名 Socket 检测单独 2 秒超时
 * - 发现校验取 exploreKinds() 中第一个 url 非空的分类，只请求第一页
 */
class LegadoProbeEngine(
    /** 使用上游 GSON，兼容 Legado 自己的 JSON 字段反序列化规则。 */
    private val gson: Gson = GSON,
) : ProbeEngine {

    private data class ChapterSelection(
        val chapter: BookChapter,
        val nextChapterUrl: String,
        val chapterCount: Int,
    )

    override suspend fun probe(
        index: Int,
        source: BookSourceDoc,
        options: ProbeOptions,
    ): ProbeResult {
        val proxy = options.proxy?.trim()?.takeIf { it.isNotEmpty() }
        val effectiveOptions = options.copy(proxy = proxy)
        val timeoutMs = options.timeoutMs.coerceAtLeast(1L)

        val bookSource = try {
            gson.fromJson(source.raw, BookSource::class.java)
                ?: return preflightFailure(index, effectiveOptions, "书源反序列化失败")
        } catch (error: Throwable) {
            currentCoroutineContext().ensureActive()
            return preflightFailure(
                index,
                effectiveOptions,
                error.message ?: "书源反序列化失败",
            )
        }

        if (proxy != null) {
            val invalidProxy = runCatching { parseProxyConfig(proxy) }.exceptionOrNull()
            if (invalidProxy != null) {
                return preflightFailure(
                    index,
                    effectiveOptions,
                    "代理配置无效：请检查协议、主机、端口和认证信息。",
                )
            }
        }

        val stageResults = ArrayList<ProbeStageResult>(12)
        var activeStage: ProbeStage? = null
        var httpCode: Int? = null
        var bookCount = 0
        var totalStartedAt: Long? = null
        var domainUnchecked = false
        var failure: Throwable? = null

        fun hasStage(stage: ProbeStage, context: String? = null): Boolean =
            stageResults.any { it.stage == stage && it.context == context }

        fun addNotRun(stage: ProbeStage, detail: String, context: String? = null) {
            if (!hasStage(stage, context)) {
                stageResults += ProbeStageResult(
                    stage = stage,
                    status = ProbeStageStatus.NOT_RUN,
                    detail = detail,
                    context = context,
                )
            }
        }

        fun addSkippedBookStages(context: String, reason: String) {
            addNotRun(ProbeStage.BOOK_INFO, reason, context)
            addNotRun(ProbeStage.TOC, reason, context)
            addNotRun(ProbeStage.CONTENT, reason, context)
        }

        suspend fun <T> measureStage(
            stage: ProbeStage,
            context: String? = null,
            emptyDetail: String? = null,
            itemCount: (T) -> Int = { 1 },
            action: suspend () -> T,
        ): T {
            activeStage = stage
            val startedAt = System.currentTimeMillis()
            try {
                val value = action()
                val count = itemCount(value).coerceAtLeast(0)
                stageResults += ProbeStageResult(
                    stage = stage,
                    status = if (emptyDetail != null && count == 0) {
                        ProbeStageStatus.EMPTY
                    } else {
                        ProbeStageStatus.SUCCESS
                    },
                    itemCount = count,
                    elapsedMs = System.currentTimeMillis() - startedAt,
                    detail = if (emptyDetail != null && count == 0) emptyDetail else null,
                    context = context,
                )
                return value
            } catch (error: Throwable) {
                val (failureStatus, code) = classify(error)
                if (httpCode == null) httpCode = code
                stageResults += ProbeStageResult(
                    stage = stage,
                    status = ProbeStageStatus.FAILED,
                    elapsedMs = System.currentTimeMillis() - startedAt,
                    detail = shortReason(error),
                    failureStatus = failureStatus,
                    httpCode = code,
                    context = context,
                )
                throw error
            }
        }

        suspend fun checkBook(book: Book, isSearchBook: Boolean) {
            val context = if (isSearchBook) "搜索结果" else "发现结果"
            val bookType = if (isSearchBook) "搜索" else "发现"

            if (!options.checkInfo) {
                addSkippedBookStages(context, "上游 checkInfo 已关闭")
                return
            }

            try {
                var currentBook = book

                // 与上游一致：搜索结果已经携带 tocUrl 时不再请求详情页。
                if (currentBook.tocUrl.isBlank()) {
                    currentBook = measureStage(
                        stage = ProbeStage.BOOK_INFO,
                        context = context,
                    ) {
                        WebBook.getBookInfoAwait(
                            bookSource = bookSource,
                            book = currentBook,
                            canReName = true,
                        )
                    }
                } else {
                    addNotRun(
                        ProbeStage.BOOK_INFO,
                        "书籍已有 tocUrl，上游跳过 getBookInfoAwait",
                        context,
                    )
                }

                // 与上游一致：文件类型书源不校验目录；checkCategory 关闭也直接返回。
                if (!options.checkCategory || bookSource.bookSourceType == BookSourceType.file) {
                    val reason = if (!options.checkCategory) {
                        "上游 checkCategory 已关闭"
                    } else {
                        "上游对文件类型书源跳过目录和正文校验"
                    }
                    addNotRun(ProbeStage.TOC, reason, context)
                    addNotRun(ProbeStage.CONTENT, reason, context)
                    return
                }

                val selection = measureStage(
                    stage = ProbeStage.TOC,
                    context = context,
                    itemCount = { it.chapterCount },
                ) {
                    // 故意使用上游默认 isFromBookInfo=false，不传入工具旧版使用的 true。
                    val chapters = WebBook.getChapterListAwait(
                        bookSource = bookSource,
                        book = currentBook,
                    ).getOrThrow()
                    selectCheckSourceChapter(chapters)
                }

                if (!options.checkContent) {
                    addNotRun(ProbeStage.CONTENT, "上游 checkContent 已关闭", context)
                    return
                }

                measureStage(
                    stage = ProbeStage.CONTENT,
                    context = context,
                ) {
                    // 与上游一致：正文探测不写入阅读缓存。
                    WebBook.getContentAwait(
                        bookSource = bookSource,
                        book = currentBook,
                        bookChapter = selection.chapter,
                        nextChapterUrl = selection.nextChapterUrl,
                        needSave = false,
                    )
                }

                // 对应上游 checkBook(...).onSuccess：清除该入口之前记录的目录/正文失效组。
                bookSource.removeGroup(bookType + "目录失效")
                bookSource.removeGroup(bookType + "正文失效")
            } catch (error: Throwable) {
                // 上游只吞掉这两类业务异常；其他异常必须冒泡并终止本轮后续检查。
                when (error) {
                    is ContentEmptyException -> {
                        bookSource.addGroup(bookType + "正文失效")
                        if (activeStage != ProbeStage.CONTENT) {
                            addNotRun(
                                ProbeStage.CONTENT,
                                "上游 checkBook 已因正文异常提前返回",
                                context,
                            )
                        }
                    }

                    is TocEmptyException -> {
                        bookSource.addGroup(bookType + "目录失效")
                        if (activeStage != ProbeStage.TOC) {
                            addNotRun(
                                ProbeStage.TOC,
                                "上游 checkBook 已因目录异常提前返回",
                                context,
                            )
                        }
                        addNotRun(
                            ProbeStage.CONTENT,
                            "上游 checkBook 已因目录异常提前返回",
                            context,
                        )
                    }

                    else -> {
                        when (activeStage) {
                            ProbeStage.BOOK_INFO -> {
                                addNotRun(ProbeStage.TOC, "详情阶段异常，按上游流程中止", context)
                                addNotRun(ProbeStage.CONTENT, "详情阶段异常，按上游流程中止", context)
                            }

                            ProbeStage.TOC -> {
                                addNotRun(ProbeStage.CONTENT, "目录阶段异常，按上游流程中止", context)
                            }

                            else -> Unit
                        }
                        throw error
                    }
                }
            }
        }

        try {
            withContext(ProbeProxyContext(proxy)) {
                // 上游 CheckSourceService.checkSource 的单源 withTimeout 包住整个 doCheckSource。
                withTimeout(timeoutMs) {
                    // 对齐 Debug.startChecking：在进入 doCheckSource 时开始总计时，
                    // 书源 JSON 反序列化与工具代理预检不计入 respondTime。
                    totalStartedAt = System.currentTimeMillis()

                    bookSource.removeInvalidGroups()
                    if (options.writeErrorComment) {
                        bookSource.removeErrorComment()
                    }

                    // 1. 域名检测（默认关闭）。非 HTTP 地址标记 domainUnchecked，但不阻断后续检查。
                    if (options.checkDomain) {
                        val endpoint = parseCheckSourceEndpoint(bookSource.bookSourceUrl)
                        if (endpoint == null) {
                            domainUnchecked = true
                            addNotRun(
                                ProbeStage.DOMAIN,
                                "源地址不是可检测的 HTTP(S) 地址，上游继续执行其他校验",
                            )
                        } else {
                            measureStage(
                                stage = ProbeStage.DOMAIN,
                                itemCount = { if (it) 1 else 0 },
                            ) {
                                val reachable = isDomainReachable(endpoint)
                                if (reachable) {
                                    bookSource.removeGroup("域名失效")
                                    true
                                } else {
                                    bookSource.addGroup("域名失效")
                                    throw NoStackTraceException("源地址不可访问")
                                }
                            }
                        }
                    } else {
                        addNotRun(ProbeStage.DOMAIN, "上游 checkDomain 已关闭")
                    }

                    // 2. 搜索。搜索为空只记录“搜索失效”，继续发现；请求异常则按上游冒泡终止。
                    if (options.checkSearch) {
                        val searchBooks = measureStage(
                            stage = ProbeStage.SEARCH,
                            emptyDetail = "上游搜索流程未解析出书籍",
                            itemCount = { it.size },
                        ) {
                            val searchWord = bookSource.getCheckKeyword(options.keyword)
                            if (bookSource.isJsSource() || !bookSource.searchUrl.isNullOrBlank()) {
                                bookSource.removeGroup("搜索链接规则为空")
                                WebBook.searchBookAwait(
                                    bookSource = bookSource,
                                    key = searchWord,
                                )
                            } else {
                                throw NoStackTraceException("搜索链接规则为空")
                            }
                        }
                        bookCount += searchBooks.size
                        if (searchBooks.isEmpty()) {
                            bookSource.addGroup("搜索失效")
                            addSkippedBookStages(
                                "搜索结果",
                                "搜索结果为空，上游不会对搜索结果执行详情、目录和正文校验",
                            )
                        } else {
                            bookSource.removeGroup("搜索失效")
                            checkBook(searchBooks.first().toBook(), isSearchBook = true)
                        }
                    } else {
                        addNotRun(ProbeStage.SEARCH, "上游 checkSearch 已关闭")
                        addSkippedBookStages("搜索结果", "上游 checkSearch 已关闭")
                    }

                    // 3. 发现。上游只在 exploreUrl 非空时检查，并取第一个 url 非空分类。
                    // 不检查分类 type，也不排除上游返回的 ERROR 分类；保持 exploreKinds() 原行为。
                    if (options.checkDiscovery && !bookSource.exploreUrl.isNullOrBlank()) {
                        val exploreBooks = measureStage(
                            stage = ProbeStage.DISCOVERY,
                            emptyDetail = "上游发现第一页未解析出书籍",
                            itemCount = { it.size },
                        ) {
                            val url = bookSource.exploreKinds()
                                .firstOrNull { !it.url.isNullOrBlank() }
                                ?.url
                            if (url.isNullOrBlank()) {
                                throw NoStackTraceException("发现规则为空")
                            }
                            bookSource.removeGroup("发现规则为空")
                            // 与上游 CheckSourceService 相同：分类解析结束后直接请求第一页。
                            WebBook.exploreBookAwait(
                                bookSource = bookSource,
                                url = url,
                            )
                        }
                        bookCount += exploreBooks.size
                        if (exploreBooks.isEmpty()) {
                            bookSource.addGroup("发现失效")
                        } else {
                            bookSource.removeGroup("发现失效")
                            checkBook(exploreBooks.first().toBook(), isSearchBook = false)
                        }
                    } else {
                        val reason = when {
                            !options.checkDiscovery -> "上游 checkDiscovery 已关闭"
                            bookSource.exploreUrl.isNullOrBlank() -> "书源未配置 exploreUrl，上游会跳过发现校验"
                            else -> "上游未执行发现校验"
                        }
                        addNotRun(ProbeStage.DISCOVERY, reason)
                        addSkippedBookStages("发现结果", "发现阶段未执行，没有发现结果可供后续校验")
                    }

                    // 与上游一致：只检查包含“失效”或“校验超时”的分组。
                    val finalCheckMessage = bookSource.getInvalidGroupNames()
                    if (finalCheckMessage.isNotBlank()) {
                        throw NoStackTraceException(finalCheckMessage)
                    }
                }
            }
        } catch (error: Throwable) {
            // 不吞掉用户取消/上层取消；只把本书源的超时或业务异常转换为结果。
            currentCoroutineContext().ensureActive()
            failure = error
            if (error is TimeoutCancellationException) {
                bookSource.addGroup("校验超时")
            }
            if (options.writeErrorComment) {
                bookSource.addErrorComment(error)
            }
        }

        // 上游 Debug.getRespondTime 在 checkSource 的错误处理完成后立即取值；
        // 不把后续仅用于 UI 的 NOT_RUN 状态补齐工作计入总响应耗时。
        val actualElapsedMs = totalStartedAt?.let {
            (System.currentTimeMillis() - it).coerceAtLeast(0L)
        } ?: 0L

        // 保证详情页六个阶段都有明确状态；已执行项不会被覆盖。
        if (!hasStage(ProbeStage.DOMAIN)) {
            addNotRun(
                ProbeStage.DOMAIN,
                if (options.checkDomain) "前序阶段中止，域名检测未执行" else "上游 checkDomain 已关闭",
            )
        }
        if (!hasStage(ProbeStage.SEARCH)) {
            addNotRun(
                ProbeStage.SEARCH,
                if (options.checkSearch) "前序阶段中止，搜索未执行" else "上游 checkSearch 已关闭",
            )
        }
        if (!hasStage(ProbeStage.BOOK_INFO, "搜索结果")) {
            addNotRun(ProbeStage.BOOK_INFO, "搜索结果未进入后续校验", "搜索结果")
        }
        if (!hasStage(ProbeStage.TOC, "搜索结果")) {
            addNotRun(ProbeStage.TOC, "搜索结果未进入后续校验", "搜索结果")
        }
        if (!hasStage(ProbeStage.CONTENT, "搜索结果")) {
            addNotRun(ProbeStage.CONTENT, "搜索结果未进入后续校验", "搜索结果")
        }
        if (!hasStage(ProbeStage.DISCOVERY)) {
            addNotRun(
                ProbeStage.DISCOVERY,
                if (!options.checkDiscovery) "上游 checkDiscovery 已关闭"
                else if (bookSource.exploreUrl.isNullOrBlank()) "书源未配置 exploreUrl，上游会跳过发现校验"
                else "前序阶段中止，发现未执行",
            )
        }
        if (!hasStage(ProbeStage.BOOK_INFO, "发现结果")) {
            addNotRun(ProbeStage.BOOK_INFO, "发现阶段未产生可校验书籍", "发现结果")
        }
        if (!hasStage(ProbeStage.TOC, "发现结果")) {
            addNotRun(ProbeStage.TOC, "发现阶段未产生可校验书籍", "发现结果")
        }
        if (!hasStage(ProbeStage.CONTENT, "发现结果")) {
            addNotRun(ProbeStage.CONTENT, "发现阶段未产生可校验书籍", "发现结果")
        }

        val status = when {
            failure != null -> classifyFinal(failure!!)
            domainUnchecked -> ProbeStatus.UNCHECKABLE
            else -> ProbeStatus.OK
        }
        // Debug.getRespondTime: failed checks add CheckSource.timeout to the actual elapsed time.
        val respondTime = if (failure != null) {
            if (actualElapsedMs > Long.MAX_VALUE - timeoutMs) Long.MAX_VALUE
            else timeoutMs + actualElapsedMs
        } else {
            actualElapsedMs
        }

        return ProbeResult(
            index = index,
            status = status,
            httpCode = httpCode ?: failure?.let(::httpStatus),
            bookCount = bookCount,
            elapsedMs = respondTime,
            actualElapsedMs = actualElapsedMs,
            detail = when {
                failure != null -> shortReason(failure!!)
                domainUnchecked -> "无法校验：源地址不是 HTTP(S) 链接；其余已启用项目已按上游流程执行"
                else -> null
            },
            stageResults = stageResults.toList(),
            keyword = options.keyword,
            timeoutMs = timeoutMs,
            proxy = proxy,
            options = effectiveOptions.copy(timeoutMs = timeoutMs),
        )
    }

    private fun preflightFailure(
        index: Int,
        options: ProbeOptions,
        detail: String,
    ) = ProbeResult(
        index = index,
        status = ProbeStatus.ERROR,
        detail = detail,
        keyword = options.keyword,
        timeoutMs = options.timeoutMs,
        proxy = options.proxy,
        actualElapsedMs = 0,
        options = options,
    )

    /**
     * 与上游 CheckSourceChapterSelection.selectCheckSourceChapter 完全相同：
     * 排除 isVolume 且 url 以 title 开头的卷标题，取前两个可读章节。
     */
    private fun selectCheckSourceChapter(chapters: List<BookChapter>): ChapterSelection {
        val readableChapters = chapters.asSequence()
            .filterNot { it.isVolume && it.url.startsWith(it.title) }
            .take(2)
            .toList()
        val chapter = readableChapters.firstOrNull()
            ?: throw TocEmptyException("目录为空")
        return ChapterSelection(
            chapter = chapter,
            nextChapterUrl = readableChapters.getOrNull(1)?.url ?: chapter.url,
            chapterCount = chapters.size,
        )
    }

    private fun parseCheckSourceEndpoint(domain: String): Pair<String, Int>? {
        val rawUrl = domain.substringBefore('#')
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return null
        if (uri.rawAuthority.isNullOrBlank()) return null
        val url = rawUrl.toHttpUrlOrNull() ?: return null
        return url.host to url.port
    }

    /** 与 CheckSourceService.isDomainReachable 一致：2 秒总超时、Socket.connect 1.6 秒。 */
    private suspend fun isDomainReachable(endpoint: Pair<String, Int>): Boolean =
        runCatching {
            withTimeout(2_000L) {
                val (host, port) = endpoint
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 1_600)
                    true
                }
            }
        }.getOrDefault(false)

    private fun classify(error: Throwable): Pair<ProbeStatus, Int?> {
        val message = error.message.orEmpty()
        when {
            error is TimeoutCancellationException || error is SocketTimeoutException ->
                return ProbeStatus.TIMEOUT to null
            error is ScriptException || error is WrappedException ->
                return ProbeStatus.NEED_JS to null
            error is TocEmptyException || error is ContentEmptyException ->
                return ProbeStatus.PARSE_EMPTY to null
            message.contains("源地址不可访问") ->
                return ProbeStatus.NETWORK to null
            message.contains("搜索链接规则为空") ->
                return ProbeStatus.NO_SEARCH_URL to null
            message.contains("发现规则为空") ->
                return ProbeStatus.NO_EXPLORE_CATEGORY to null
        }
        val code = httpStatus(error)
        if (code != null) return ProbeStatus.HTTP_ERROR to code
        if (error is UnknownHostException || error is IOException) {
            return ProbeStatus.NETWORK to null
        }
        return ProbeStatus.ERROR to null
    }

    private fun classifyFinal(error: Throwable): ProbeStatus {
        val message = error.message.orEmpty()
        return when {
            error is TimeoutCancellationException -> ProbeStatus.TIMEOUT
            error is ScriptException || error is WrappedException -> ProbeStatus.NEED_JS
            message.contains("js失效") -> ProbeStatus.NEED_JS
            message.contains("源地址不可访问") -> ProbeStatus.NETWORK
            message.contains("搜索链接规则为空") -> ProbeStatus.NO_SEARCH_URL
            message.contains("发现规则为空") -> ProbeStatus.NO_EXPLORE_CATEGORY
            message.contains("失效") || message.contains("校验超时") -> ProbeStatus.PARSE_EMPTY
            else -> classify(error).first
        }
    }

    private fun httpStatus(error: Throwable): Int? {
        val text = error.message ?: return null
        return Regex("""\b(4\d{2}|5\d{2})\b""")
            .find(text)
            ?.value
            ?.toIntOrNull()
    }

    private fun shortReason(error: Throwable): String {
        val message = error.message?.takeIf { it.isNotBlank() }
            ?: error::class.java.simpleName
        return message.lineSequence().first().take(160)
    }
}
