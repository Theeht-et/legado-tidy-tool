package io.legado.app.help.book

import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.utils.StringUtils
import io.legado.headless.Paths
import org.apache.commons.text.similarity.JaccardSimilarity
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 正文缓存 + 章节定位（headless 版 `help/book/BookHelp.kt`）。
 *
 * ## 保留的真实逻辑
 *  - **正文缓存**：`contentSaveToken` → `saveContent` → `getContent` 这条链路逐字保留
 *    （含 [ContentSaveFence] 的版本栅栏），引擎的 `WebBook.getContentAwait`、JS 源的
 *    批量回存、`BatchContentContext` 都依赖它。区别只是缓存根目录从 Android 的
 *    `appCtx.externalFiles/book_cache` 换成工程内的 `run/cache/book_cache`
 *    （见 [Paths]），文件名仍是 `book.getFolderName()/<index>-<titleMD5>.nb`。
 *  - **去重标题标记**：`getChapterFiles` 列出某本书缓存目录下的文件名，供
 *    `ContentProcessor` 判断「本章是否单独关闭了去重标题」。headless 没有 App 内的
 *    章节级开关，目录里通常也没有 `nr` 文件，效果等价于「所有章节都去重」。
 *  - **章节定位**：`getDurChapter` 及其纯算法依赖（章节号提取、Jaccard 纯标题相似度）
 *    与上游逐字一致 —— 它决定「换源后落回哪一章」，影响对比结果，不能降级。
 *
 * ## 降级了什么（及原因）
 *  1. 图片缓存（`saveImage/getImage/flowImages/hasImageContent` 等）与
 *     `fetchImage`、`getEpubFile`、`getBookPFD`：依赖 Glide/Bitmap/SAF/ZipFile，
 *     属于阅读器展示与本地书解压；对比流程不用，**整体删除**。
 *  2. 依赖 `appDb` 的清理/落库（`clearInvalidCache/clearComicCache/updateCacheFolder`、
 *     `saveChapterMetadata` 里的字数统计、`readContent` 的本地/epub 分支）：
 *     headless 无数据库、无本地书，跳过。
 *  3. `saveText/isContentReversed/reverseContent` 等反转缓存：依赖磁盘原子写 + `appDb`，
 *     未被任何已编译代码引用，**删除**。
 *
 * 依赖方若发现缺方法，应按需从上游补回，而不是在这里塞空实现。
 */
internal data class ContentSaveKey(val bookUrl: String, val chapterIndex: Int)

internal data class ContentSaveState(val version: Long = 0L, val fileName: String? = null)

data class ContentSaveToken internal constructor(
    internal val key: ContentSaveKey,
    internal val folderName: String,
    val version: Long,
)

/**
 * 正文缓存版本栅栏，逐字照抄 upstream：保证「请求开始时的版本」与「写回时的版本」一致，
 * 避免旧的响应覆盖新刷新的正文。纯 Kotlin，无 Android 依赖。
 */
internal class ContentSaveFence {
    private val states = ConcurrentHashMap<ContentSaveKey, ContentSaveState>()

    @Synchronized
    fun state(key: ContentSaveKey): ContentSaveState = states[key] ?: ContentSaveState()

    fun <T> exclusive(block: () -> T): T = synchronized(this, block)

    @Synchronized
    fun writeIfCurrent(
        key: ContentSaveKey,
        expectedVersion: Long,
        fileName: String,
        write: () -> Unit,
    ): Boolean {
        var written = false
        states.compute(key) { _, current ->
            if ((current?.version ?: 0L) == expectedVersion) {
                write()
                written = true
                current?.copy(fileName = fileName)
            } else {
                current
            }
        }
        return written
    }

    @Synchronized
    fun replace(key: ContentSaveKey, fileName: String, write: () -> Unit) {
        var failure: Throwable? = null
        states.compute(key) { _, current ->
            val nextVersion = (current?.version ?: 0L) + 1L
            try {
                write()
                ContentSaveState(nextVersion, fileName)
            } catch (error: Throwable) {
                failure = error
                ContentSaveState(nextVersion, current?.fileName)
            }
        }
        failure?.let { throw it }
    }
}

@Suppress("unused", "ConstPropertyName")
object BookHelp {
    private const val cacheFolderName = "book_cache"

    /** 缓存根目录：工程内 `run/cache/book_cache`（替代上游的 `appCtx.externalFiles`）。 */
    private val cacheRoot: File by lazy { File(Paths.cacheDir, cacheFolderName) }

    private val contentSaveFence = ContentSaveFence()

    private fun bookDir(folderName: String): File = File(cacheRoot, folderName)

    fun saveContent(
        bookSource: BookSource,
        book: Book,
        bookChapter: BookChapter,
        content: String,
        token: ContentSaveToken = contentSaveToken(book, bookChapter),
        saveChapterMetadata: Boolean = false,
    ): Boolean {
        return try {
            if (token.key.bookUrl != book.bookUrl ||
                token.key.chapterIndex != bookChapter.index ||
                token.folderName != book.getFolderName()
            ) {
                return false
            }
            val fileName = bookChapter.getFileName()
            contentSaveFence.writeIfCurrent(
                token.key,
                token.version,
                fileName,
            ) {
                if (content.isNotEmpty()) {
                    writeText(book, bookChapter, token.folderName, fileName, content)
                }
                // 上游在这里还会 appDb.bookChapterDao.updateContentMetadata(...)；
                // headless 无数据库，章节元数据（标题/图片）不落库。
            }
        } catch (e: Exception) {
            e.printStackTrace()
            AppLog.put("保存正文失败 ${book.name} ${bookChapter.title}", e)
            false
        }
    }

    internal fun contentSaveToken(book: Book, bookChapter: BookChapter): ContentSaveToken {
        val key = contentSaveKey(book, bookChapter)
        return ContentSaveToken(
            key,
            book.getFolderName(),
            contentSaveFence.state(key).version,
        )
    }

    internal fun isContentSaveCurrent(token: ContentSaveToken): Boolean =
        contentSaveFence.state(token.key).version == token.version

    private fun contentSaveFileName(book: Book, bookChapter: BookChapter): String? {
        return contentSaveFence.state(contentSaveKey(book, bookChapter)).fileName
    }

    private fun contentSaveKey(book: Book, bookChapter: BookChapter) =
        ContentSaveKey(book.bookUrl, bookChapter.index)

    private fun writeText(
        book: Book,
        bookChapter: BookChapter,
        folderName: String,
        fileName: String,
        content: String,
    ) {
        val file = File(bookDir(folderName), fileName)
        file.parentFile?.mkdirs()
        file.writeText(content)
        // 手动/重新下载的正文会替换掉旧的「反转缓存」，删掉标记文件。
        File(file.path + ".reversed").delete()
        // 上游还会在 isOnLineTxt && tocCountWords 时用 appDb 更新字数；headless 跳过。
    }

    /**
     * 读取章节内容。
     */
    fun getContent(book: Book, bookChapter: BookChapter): String? {
        val fileName = contentSaveFileName(book, bookChapter)
            ?: bookChapter.getFileName()
        return readContent(book, bookChapter, book.getFolderName(), fileName)
    }

    internal fun getContent(
        book: Book,
        bookChapter: BookChapter,
        token: ContentSaveToken,
    ): String? {
        if (token.key.bookUrl != book.bookUrl ||
            token.key.chapterIndex != bookChapter.index ||
            token.folderName != book.getFolderName()
        ) {
            return null
        }
        val fileName = contentSaveFence.state(token.key).fileName
            ?: bookChapter.getFileName()
        return readContent(book, bookChapter, token.folderName, fileName)
    }

    private fun readContent(
        book: Book,
        bookChapter: BookChapter,
        folderName: String,
        fileName: String,
    ): String? = contentSaveFence.exclusive {
        val file = File(bookDir(folderName), fileName)
        if (file.exists()) {
            val string = file.readText()
            if (string.isEmpty()) {
                return@exclusive null
            }
            return@exclusive string
        }
        // 上游这里会读本地书（LocalBook）与修复 epub 缓存；headless 只处理在线书源。
        null
    }

    fun getChapterFiles(book: Book): HashSet<String> {
        val fileNames = hashSetOf<String>()
        if (book.isLocalTxt) {
            return fileNames
        }
        bookDir(book.getFolderName()).list()?.let {
            fileNames.addAll(it)
        }
        return fileNames
    }

    /**
     * 格式化书名
     */
    fun formatBookName(name: String): String {
        return name
            .replace(AppPattern.nameRegex, "")
            .trim { it <= ' ' }
    }

    /**
     * 格式化作者
     */
    fun formatBookAuthor(author: String): String {
        return author
            .replace(AppPattern.authorRegex, "")
            .trim { it <= ' ' }
    }

    /**
     * 根据目录名获取当前章节
     */
    fun getDurChapter(
        oldDurChapterIndex: Int,
        oldDurChapterName: String?,
        newChapterList: List<BookChapter>,
        oldChapterListSize: Int = 0,
        searchAllChapterNumbers: Boolean = false,
    ): Int {
        if (oldDurChapterIndex <= 0) return 0
        if (newChapterList.isEmpty()) return oldDurChapterIndex
        val oldChapterNum = getChapterNum(oldDurChapterName)
        val newChapterSize = newChapterList.size
        val durIndex =
            if (oldChapterListSize == 0) oldDurChapterIndex
            else (oldDurChapterIndex.toLong() * newChapterSize / oldChapterListSize).toInt()
        val min = max(0, min(oldDurChapterIndex, durIndex) - 10)
        val max = min(newChapterSize - 1, max(oldDurChapterIndex, durIndex) + 10)
        findNearestChapterTitleIndex(
            oldDurChapterName,
            newChapterList,
            min..max,
            durIndex,
        )?.let { return it }
        if (searchAllChapterNumbers && oldChapterNum > 0) {
            findNearestChapterNumberIndex(
                newChapterList.map { getChapterNum(it.title) },
                oldChapterNum,
                durIndex,
            )?.let { return it }
        }
        if (oldChapterNum > 0) {
            for (i in min..max) {
                if (getChapterNum(newChapterList[i].title) == oldChapterNum) return i
            }
        }
        return min(max(0, newChapterList.size - 1), oldDurChapterIndex)
    }

    fun getDurChapter(
        oldBook: Book,
        newChapterList: List<BookChapter>
    ): Int {
        return oldBook.run {
            getDurChapter(durChapterIndex, durChapterTitle, newChapterList, totalChapterNum)
        }
    }

}

private val chapterNamePattern1 by lazy {
    Pattern.compile(
        ".*?第([\\d零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+)[章节篇回集话]"
    )
}

@Suppress("RegExpSimplifiable")
private val chapterNamePattern2 by lazy {
    Pattern.compile(
        "^(?:[\\d零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+[,:、])*([\\d零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+)(?:[,:、]|\\.[^\\d])"
    )
}

private val regexA by lazy {
    "\\s".toRegex()
}

private fun getChapterNum(chapterName: String?): Int {
    chapterName ?: return -1
    val chapterName1 = StringUtils.fullToHalf(chapterName).replace(regexA, "")
    return StringUtils.stringToInt(
        (
                chapterNamePattern1.matcher(chapterName1).takeIf { it.find() }
                    ?: chapterNamePattern2.matcher(chapterName1).takeIf { it.find() }
                )?.group(1)
            ?: "-1"
    )
}

private val regexOther by lazy {
    // 所有非字母数字中日韩文字 CJK区+扩展A-F区
    @Suppress("RegExpDuplicateCharacterInClass")
    "[^\\w\\u4E00-\\u9FEF〇\\u3400-\\u4DBF\\u20000-\\u2A6DF\\u2A700-\\u2EBEF]".toRegex()
}

@Suppress("RegExpUnnecessaryNonCapturingGroup", "RegExpSimplifiable")
private val regexB by lazy {
    //章节序号，排除处于结尾的状况，避免将章节名替换为空字串
    "^.*?第(?:[\\d零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+)[章节篇回集话](?!$)|^(?:[\\d零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+[,:、])*(?:[\\d零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+)(?:[,:、](?!$)|\\.(?=[^\\d]))".toRegex()
}

private val regexC by lazy {
    //前后附加内容，整个章节名都在括号中时只剔除首尾括号，避免将章节名替换为空字串
    "(?!^)(?:[〖【《〔\\[{(][^〖【《〔\\[{()〕》】〗\\]}]+)?[)〕》】〗\\]}]$|^[〖【《〔\\[{(](?:[^〖【《〔\\[{()〕》】〗\\]}]+[〕》】〗\\]})])?(?!$)".toRegex()
}

private fun getPureChapterName(chapterName: String?): String {
    return if (chapterName == null) "" else StringUtils.fullToHalf(chapterName)
        .replace(regexA, "")
        .replace(regexB, "")
        .replace(regexC, "")
        .replace(regexOther, "")
}

private val jaccardSimilarity by lazy {
    JaccardSimilarity()
}

internal fun findNearestChapterTitleIndex(
    oldChapterName: String?,
    newChapterList: List<BookChapter>,
    range: IntRange,
    expectedIndex: Int,
): Int? {
    val oldName = getPureChapterName(oldChapterName)
    if (oldName.isEmpty()) return null
    var bestSimilarity = 0.0
    var bestIndex = 0
    for (i in range) {
        val similarity = jaccardSimilarity.apply(
            oldName,
            getPureChapterName(newChapterList[i].title),
        )
        if (similarity > bestSimilarity ||
            similarity == bestSimilarity && abs(i - expectedIndex) < abs(bestIndex - expectedIndex)
        ) {
            bestSimilarity = similarity
            bestIndex = i
        }
    }
    return bestIndex.takeIf { bestSimilarity > 0.96 }
}

internal fun findNearestChapterNumberIndex(
    chapterNumbers: List<Int>,
    chapterNumber: Int,
    expectedIndex: Int,
): Int? {
    return chapterNumbers.indices
        .filter { chapterNumbers[it] == chapterNumber }
        .minByOrNull { abs(it - expectedIndex) }
}
