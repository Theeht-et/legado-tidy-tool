package io.legado.app.data.dao

import io.legado.app.data.entities.BookChapter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 章节表 DAO —— headless 版，内存实现。
 *
 * ## 为什么这里需要真实现，而不是一个返回 null 的空壳
 *
 * 上游 `BookContent.analyzeContent` 用 `appDb.bookChapterDao.getChapter(bookUrl, index + 1)`
 * 来**确定多页正文拼接的停止边界**：
 *
 * ```kotlin
 * val mNextChapterUrl = if (nextChapterUrl.isNullOrEmpty()) {
 *     getChapter(book.bookUrl, bookChapter.index + 1)?.url
 *         ?: getChapter(book.bookUrl, 0)?.url      // 末章回绕到首章，靠它兜底
 * } else nextChapterUrl
 * // ...
 * if (mNextChapterUrl 非空 && 下一页地址 == mNextChapterUrl) break
 * ```
 *
 * 很多站点把一章正文拆成多页（「下一页」链接）。**没有这个边界，循环就会一直跟下去**，
 * 把后面几章的内容粘到本章里 —— 两个源一对比，结论完全是错的。
 *
 * 所以我们有两条防线：
 * 1. `SourceRunner` **显式传** [nextChapterUrl]（这是主路径，也是 App 的正常行为）。
 *    只要有它，本 DAO 根本不会被查询。
 * 2. 本 DAO 作为兜底真的存得住数据 —— 万一哪条路径漏传了，结果仍然正确，
 *    而不是静默产生一份「粘了几章」的正文。
 *
 * [fallbackHits] 用来观测第 2 条防线是否被触发过：**只要它非 0，就说明有源漏传了
 * nextChapterUrl**，这是个应该修的 bug，不是正常状态。它会被带进报告的 warnings。
 *
 * ## 降级说明
 * 上游是 Room（`@Query` + Flow + 事务）。这里用 [ConcurrentHashMap]，
 * 因为抓取池是几百个虚拟线程并发访问；锁粒度按 bookUrl 分，不设全局锁。
 * 不提供 Flow/事务语义 —— 引擎用不到。
 */
class BookChapterDao {

    /** bookUrl -> 按 [BookChapter.index] 有序的章节表。 */
    private val tables = ConcurrentHashMap<String, List<BookChapter>>()

    private val fallback = AtomicLong(0)

    /**
     * 第 2 条防线被触发的次数。非 0 表示有源没拿到 `nextChapterUrl`，
     * 正走了 DAO 回退 —— 结果可能仍然正确，但值得排查。
     */
    val fallbackHits: Long get() = fallback.get()

    fun resetFallbackHits() = fallback.set(0)

    // ---------- 写入（供 SourceRunner 预置章节表）----------

    /** 预置某本书的完整章节表（按 index 排序后存）。 */
    fun put(bookUrl: String, chapters: List<BookChapter>) {
        if (bookUrl.isEmpty() || chapters.isEmpty()) return
        tables[bookUrl] = chapters.sortedBy { it.index }
    }

    /** 释放某本书的章节表。抓完一章就调，避免几百个源的章节表常驻内存。 */
    fun release(bookUrl: String) {
        tables.remove(bookUrl)
    }

    /** 清空全部（一次对比跑完时调用）。 */
    fun clear() = tables.clear()

    // ---------- 引擎使用的查询接口（签名对齐上游）----------

    fun getChapterList(bookUrl: String): List<BookChapter> =
        tables[bookUrl] ?: emptyList()

    fun getChapterList(bookUrl: String, start: Int, end: Int): List<BookChapter> =
        getChapterList(bookUrl).filter { it.index in start until end }

    fun getChapter(bookUrl: String, index: Int): BookChapter? {
        fallback.incrementAndGet()
        return tables[bookUrl]?.firstOrNull { it.index == index }
    }

    fun getChapter(bookUrl: String, title: String): BookChapter? =
        tables[bookUrl]?.firstOrNull { it.title == title }

    fun getChapterCount(bookUrl: String): Int = tables[bookUrl]?.size ?: 0

    /** Room 的 `@Insert`。按 [BookChapter.bookUrl] 分表合并 —— 保留已有表里没被覆盖的项。 */
    fun insert(vararg bookChapter: BookChapter) {
        for (c in bookChapter) {
            if (c.bookUrl.isEmpty()) continue
            val merged = (tables[c.bookUrl].orEmpty().filter { it.index != c.index } + c)
                .sortedBy { it.index }
            tables[c.bookUrl] = merged
        }
    }
}
