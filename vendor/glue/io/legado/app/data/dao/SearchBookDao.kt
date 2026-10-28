package io.legado.app.data.dao

import io.legado.app.data.entities.SearchBook
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 搜索结果表 DAO —— headless 版，内存实现。
 *
 * ## 用途
 * 上游 `SearchModel` 边搜边把结果插库（`:127 appDb.searchBookDao.insert(*items)`），
 * 作用有两个：搜索中显示增量结果、以及「搜索结果页」离开后仍能看到上次结果。
 *
 * 本工具**不走 `SearchModel`**（`Orchestrator` 直接调 `WebBook.searchBookAwait`），
 * 所以这里的写入只发生在编译期依赖层面。之所以不做成 no-op，是因为
 * `SearchModel` 是引擎里真实存在的搜索路径 —— 万一将来有人从那里走，
 * 内存表能给出正确结果而不是静默丢数据。
 *
 * ## 降级说明
 * 上游是 Room 表（带 `precision`、`bookType` 等索引与去重约束）。
 * 这里按 `name + author` 做键去重（与上游搜索结果的「同一本书」认定一致），
 * 不实现 Room 的索引/Flow/事务。
 */
class SearchBookDao {

    /** "name|author" -> 最新一条。 */
    private val rows = ConcurrentHashMap<String, SearchBook>()

    private val inserted = AtomicLong(0)

    /** 累计写入条数。用于报告里说明「搜索阶段确实有结果流过」。 */
    val insertedCount: Long get() = inserted.get()

    fun clear() {
        rows.clear()
        inserted.set(0)
    }

    /** Room `@Insert` 返回 rowId 列表。本实现不产生 rowId，按 Room 冲突语义返回 -1。 */
    fun insert(vararg searchBook: SearchBook): List<Long> {
        val ids = ArrayList<Long>(searchBook.size)
        for (b in searchBook) {
            rows[key(b)] = b
            inserted.incrementAndGet()
            ids.add(-1L)
        }
        return ids
    }

    fun delete(vararg searchBook: SearchBook) {
        for (b in searchBook) rows.remove(key(b))
    }

    fun deleteAll() = rows.clear()

    fun getAll(): List<SearchBook> = rows.values.toList()

    fun size(): Int = rows.size

    private fun key(b: SearchBook) = "${b.name}|${b.author}"
}
