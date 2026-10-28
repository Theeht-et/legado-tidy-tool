package io.legado.app.help.book

import android.os.Build
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.AppPattern.spaceRegex
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.exception.RegexTimeoutException
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.utils.ChineseUtils
import io.legado.app.utils.escapeRegex
import io.legado.app.utils.replace
import io.legado.app.utils.stackTraceStr
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import splitties.init.appCtx
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Pattern

/**
 * 正文净化后处理（照抄 upstream `help/book/ContentProcessor.kt` 的真实逻辑）。
 *
 * 这一层直接决定最终对比结果：去重复标题、重新分段、简繁转换、`<usehtml>` 特殊样式占位、
 * 用户替换规则、重加标题、段落缩进。所以**除下面列出的依赖替换外，算法逐字保留**。
 *
 * ## 与 upstream 的差异（逐个说明）
 *  1. `appDb.replaceRuleDao`（用户净化规则）→ [ReplaceRuleProvider]（恒为空）。
 *     原因见 `ReplaceRuleProvider` 的类注释：用户私有净化配置不该默认参与对比。
 *     影响：默认不套用任何用户规则；书源自带的 `contentRule.replaceRegex` 不受影响。
 *  2. `upRemoveSameTitle()` 上游会从 `appDb.bookDao.getBookByOrigin(...)` 取书、
 *     再列缓存目录里以 `nr` 结尾的文件，得到「本章单独禁用了去重标题」的集合。
 *     headless 无数据库、也没有 App 内的章节级设置，集合保持为空
 *     （等价于「所有章节都做去重」，这也是未配置时的默认行为）。
 *  3. `RegexTimeoutException` 分支里禁用规则的落库动作换成 [ReplaceRuleProvider.disable]（no-op）。
 *  4. 图片下载/测量部分：上游本文件**没有**图片逻辑（那在 `BookHelp`），无需删。
 *  5. `Build.VERSION.SDK_INT` 与 `androidx(Android8 的 NBSP 归一化)` 沿用平台桩，行为不变。
 *
 * 依赖的外部符号 `escapeRegex` / `replace` / `ChineseUtils` / `RegexTimeoutException` /
 * `ReadBookConfig.paragraphIndent` / `AppConfig.chineseConverterType` 由其它 worker 提供（见汇报）。
 */
internal fun sameTitleLineMatcher(
    content: String,
    namePattern: String,
    titlePattern: String
) = Pattern.compile(
    "^(\\s|\\p{P}|$namePattern)*$titlePattern" +
        "[\\t\\x0B\\f\\p{Zs}]*(?:(?:\\r\\n|\\r|\\n)\\s*|$)"
).matcher(content)

class ContentProcessor private constructor(
    private val bookName: String,
    private val bookOrigin: String
) {

    companion object {
        private val processors = hashMapOf<String, WeakReference<ContentProcessor>>()
        private val isAndroid8 = Build.VERSION.SDK_INT in 26..27

        fun get(book: Book) = get(book.name, book.origin)

        fun get(bookName: String, bookOrigin: String): ContentProcessor {
            val processorWr = processors[bookName + bookOrigin]
            var processor: ContentProcessor? = processorWr?.get()
            if (processor == null) {
                processor = ContentProcessor(bookName, bookOrigin)
                processors[bookName + bookOrigin] = WeakReference(processor)
            }
            return processor
        }

        fun upReplaceRules() {
            processors.forEach {
                it.value.get()?.upReplaceRules()
            }
        }

    }

    private val titleReplaceRules = CopyOnWriteArrayList<ReplaceRule>()
    private val contentReplaceRules = CopyOnWriteArrayList<ReplaceRule>()
    val removeSameTitleCache = hashSetOf<String>()

    init {
        upReplaceRules()
        upRemoveSameTitle()
    }

    fun upReplaceRules() {
        titleReplaceRules.run {
            clear()
            addAll(ReplaceRuleProvider.findEnabledByTitleScope(bookName, bookOrigin))
        }
        contentReplaceRules.run {
            clear()
            addAll(ReplaceRuleProvider.findEnabledByContentScope(bookName, bookOrigin))
        }
    }

    private fun upRemoveSameTitle() {
        // headless：无 appDb、无章节级「去重标题」用户设置，缓存保持为空 = 所有章节都去重。
        // 上游实现依赖 appDb.bookDao.getBookByOrigin(...) + BookHelp.getChapterFiles(book)。
    }

    fun getTitleReplaceRules(): List<ReplaceRule> {
        return titleReplaceRules
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun getContentReplaceRules(): List<ReplaceRule> {
        return contentReplaceRules
    }

    fun getContent(
        book: Book,
        chapter: BookChapter,
        content: String,
        includeTitle: Boolean = true,
        useReplace: Boolean = true,
        chineseConvert: Boolean = true,
        reSegment: Boolean = true,
        replaceEnabledOverride: Boolean? = null,
        titleReplaceRulesOverride: List<ReplaceRule>? = null,
        contentReplaceRulesOverride: List<ReplaceRule>? = null,
    ): BookContent {
        var mContent = content
        var sameTitleRemoved = false
        var effectiveReplaceRules: ArrayList<ReplaceRule>? = null
        val replaceEnabled = replaceEnabledOverride ?: (useReplace && book.getUseReplaceRule())
        val titleRules = titleReplaceRulesOverride ?: titleReplaceRules
        val contentRules = contentReplaceRulesOverride ?: contentReplaceRules
        val contentReplaceEnabled = replaceEnabled &&
            (contentReplaceRulesOverride == null || contentRules.isNotEmpty())
        val replaceBook by lazy { book.toReplaceBook() }
        if (content != "null") {
            //去除重复标题
            val fileName = chapter.getFileName("nr")
            if (!removeSameTitleCache.contains(fileName)) try {
                val name = Pattern.quote(book.name)
                var title = chapter.title.escapeRegex().replace(spaceRegex, "\\\\s*")
                var matcher = sameTitleLineMatcher(mContent, name, title)
                if (matcher.find()) {
                    mContent = mContent.substring(matcher.end())
                    sameTitleRemoved = true
                } else if (replaceEnabled) {
                    title = Pattern.quote(
                        chapter.getDisplayTitle(
                            titleRules,
                            chineseConvert = false,
                            replaceBook = replaceBook
                        )
                    )
                    matcher = sameTitleLineMatcher(mContent, name, title)
                    if (matcher.find()) {
                        mContent = mContent.substring(matcher.end())
                        sameTitleRemoved = true
                    }
                }
            } catch (e: Exception) {
                AppLog.put("去除重复标题出错\n${e.localizedMessage}", e)
            }
            if (reSegment && book.getReSegment()) {
                //重新分段
                mContent = ContentHelp.reSegment(mContent, chapter.title)
            }
            if (chineseConvert) {
                //简繁转换
                try {
                    when (AppConfig.chineseConverterType) {
                        1 -> mContent = ChineseUtils.t2s(mContent)
                        2 -> mContent = ChineseUtils.s2t(mContent)
                    }
                } catch (_: Exception) {
                    appCtx.toastOnUi("简繁转换出错")
                }
            }
            val useHtmlMap = mutableMapOf<String, String>()
            if (AppConfig.adaptSpecialStyle) { //html处理
                mContent = AppPattern.useHtmlRegex.replace(mContent) { matchResult ->
                    val placeholder = "特殊格式的占位不应该被看见${useHtmlMap.size}。"
                    useHtmlMap[placeholder] = "\n${matchResult.value.replace("\n", "")}\n"
                    placeholder
                }
            }
            if (contentReplaceEnabled) {
                //替换
                effectiveReplaceRules = arrayListOf()
                mContent = mContent.lines().joinToString("\n") { it.trim() }
                contentRules.forEach { item ->
                    if (item.pattern.isEmpty()) {
                        return@forEach
                    }
                    try {
                        val tmp = if (item.isRegex) {
                            mContent.replace(
                                item.name,
                                item.regex,
                                item.replacement,
                                item.getValidTimeoutMillisecond(),
                                chapter,
                                replaceBook
                            )
                        } else {
                            mContent.replace(item.pattern, item.replacement)
                        }
                        if (mContent != tmp) {
                            effectiveReplaceRules.add(item)
                            mContent = tmp
                        }
                    } catch (e: RegexTimeoutException) {
                        item.isEnabled = false
                        ReplaceRuleProvider.disable(item.id)
                        mContent = item.name + e.stackTraceStr
                    } catch (_: CancellationException) {
                    } catch (e: Exception) {
                        AppLog.put("替换净化: 规则 ${item.name}替换出错.\n${mContent}", e)
                        appCtx.toastOnUi("替换净化: 规则 ${item.name}替换出错")
                    }
                }
            }
            useHtmlMap.forEach { (placeholder, originalContent) ->
                mContent = mContent.replace(placeholder, originalContent)
            }
        }
        if (includeTitle) {
            //重新添加标题
            mContent = chapter.getDisplayTitle(
                titleRules,
                useReplace = replaceEnabled,
                replaceBook = replaceBook
            ) + "\n" + mContent
        }
        if (isAndroid8) {
            mContent = mContent.replace('\u00A0', ' ')
        }
        val contents = arrayListOf<String>()
        mContent.lineSequence().forEach { str ->
            val paragraph = str.trim {
                it.code <= 0x20 || it == '\u3000'
            }
            if (paragraph.isNotEmpty()) {
                if (contents.isEmpty() && includeTitle) {
                    contents.add(paragraph)
                } else {
                    contents.add("${ReadBookConfig.paragraphIndent}$paragraph")
                }
            }
        }
        return BookContent(sameTitleRemoved, contents, effectiveReplaceRules)
    }

}
