package io.legado.app.data

import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookSourceDao
import io.legado.app.data.dao.SearchBookDao

/**
 * 平台桩：`appDb`。
 *
 * ## 上游是什么
 * `upstream/.../data/AppDatabase.kt` 里是 Room 数据库：
 * ```kotlin
 * val appDb by lazy { Room.databaseBuilder(...).build() }
 * ```
 * headless 里**没有 Room、也不该有**（它要一个真实 SQLite + Android Context）。
 *
 * ## 为什么必须有这个桩
 * 引擎里三个**不可修改**的上游文件直接引用它：
 * - `model/webBook/BookContent.kt` —— 查下一章地址，作为多页拼接的**停止边界**
 * - `model/webBook/BookChapterList.kt` —— 回填章节字数（受 `AppConfig.tocCountWords` 开关控制）
 * - `model/webBook/SearchModel.kt` / `model/jsSource/JsSourceUpsert.kt`
 *
 * 所以它不是可有可无的方便之物，而是**编译与正确性的必需品** ——
 * 详见 [BookChapterDao] 的文档：`getChapter` 返回错值会导致正文里粘进后面几章的文本。
 *
 * ## 设计：真内存表，不是空壳
 * 三个 DAO 都是可用的内存实现（[BookChapterDao] / [SearchBookDao] / [BookSourceDao]）。
 * 空壳会静默产生错数据；内存表最坏情况也只是返回「空」，
 * 而「空」在引擎里的语义是明确的（走 `?:` 兜底或跳过回填）。
 *
 * ## 与上游的差异（一句话）
 * Room/SQLite/Flow/事务 → `ConcurrentHashMap`；进程退出即丢，不持久化。
 * 本工具是「一次性对比」，不需要跨次持久化。
 */
object appDb {
    val bookChapterDao = BookChapterDao()
    val searchBookDao = SearchBookDao()
    val bookSourceDao = BookSourceDao()

    /** 一次对比跑开始前清干净，避免上一轮的章节表干扰本轮边界判定。 */
    fun reset() {
        bookChapterDao.clear()
        searchBookDao.clear()
    }
}
