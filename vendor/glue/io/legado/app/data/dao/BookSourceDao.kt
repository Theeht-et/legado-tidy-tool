package io.legado.app.data.dao

import io.legado.app.data.entities.BookSource
import java.util.concurrent.ConcurrentHashMap

/**
 * 书源表 DAO —— headless 版，内存实现。
 *
 * ## 用途
 * 上游 `JsSourceUpsert`（「JS 书源调试/保存」流程）会查库判重、再插/删：
 * `getBookSource` / `insert` / `delete`。本工具的**导入流程不走这里**
 * （`Orchestrator` 从 JSON 文件直接把 `BookSource` 交给 `SourceRunner`），
 * 所以正常情况下这张表是空的，引擎那几个调用点不会被碰到。
 *
 * 之所以仍给真实现而不是 no-op：`getBookSource(key)` 在上游是「这本书源是否已存在」
 * 的判据。返回 null 会让 `JsSourceUpsert` 认为「源不存在」而走新增分支 ——
 * 那是**静默的错误行为**。给一张真表，至少语义是诚实的。
 *
 * ## 降级说明
 * 上游是 Room 表 + `BOOK_SOURCE_PART_VIEW` 视图（书源与分组的关联查询）。
 * 这里只存书源本体，视图相关的 `getBookSourcePart` 等由调用方按需扩展；
 * 引擎当前不调用它们，所以不实现 —— **不写猜的实现**，缺了就是缺了，
 * 编译器会在有人真需要时报错，而不是运行期返回错数据。
 */
class BookSourceDao {

    /** bookSourceUrl -> 书源。 */
    private val sources = ConcurrentHashMap<String, BookSource>()

    fun getBookSource(key: String): BookSource? = sources[key]

    fun getBookSources(keys: List<String>): List<BookSource> =
        keys.mapNotNull { sources[it] }

    fun getAll(): List<BookSource> = sources.values.toList()

    fun count(): Int = sources.size

    fun clear() = sources.clear()

    fun insertSources(vararg bookSource: BookSource) = insert(*bookSource)

    /** Room `@Insert(onConflict = REPLACE)`：同 URL 覆盖。 */
    fun insert(vararg bookSource: BookSource) {
        for (s in bookSource) {
            if (s.bookSourceUrl.isNotEmpty()) sources[s.bookSourceUrl] = s
        }
    }

    fun delete(vararg bookSource: BookSource) {
        for (s in bookSource) sources.remove(s.bookSourceUrl)
    }

    fun delete(key: String) {
        sources.remove(key)
    }
}
