package io.legado.app.help.source

import io.legado.app.constant.SourceType
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart

/**
 * 取自 upstream 的 help/source/SourceHelp.kt，这里是 headless 的最小实现。
 *
 * 上游这一层几乎全是「数据库 + 全局播放/阅读状态」操作（appDb / ReadBook / AudioPlay /
 * VideoPlay / 播放器 Activity），headless 没有这些，也不在对比流程的路径上。
 * 因 JsSourceUpsert 会调用 [deleteBookSource]，必须保证相关签名存在；依赖 UI/数据库的部分
 * 一律不实现（返回空 / 不做事），需要人工介入的 [openVideoPlayer] 直接快速失败。
 *
 * 与上游的差异：
 *  - 不提供任何 RssSource 相关方法：headless 只有书源，没有 RssSource 实体，
 *    engineSources 也不引用它们。
 *  - [getSource] 直接返回 null：上游会去查当前阅读/播放中的源和数据库，headless 无此全局状态。
 */
object SourceHelp {

    fun getSource(key: String?): BaseSource? {
        // headless 无「当前正在阅读/播放的源」这类全局状态，也无数据库查询入口，固定返回 null
        return null
    }

    fun getSource(key: String?, @SourceType.Type type: Int): BaseSource? {
        // 同上：headless 没有可查询的源库，固定返回 null
        return null
    }

    fun deleteSource(key: String, @SourceType.Type type: Int) {
        if (type == SourceType.book) {
            deleteBookSource(key)
        }
        // rss 源在 headless 不存在，忽略
    }

    fun deleteBookSourceParts(sources: List<BookSourcePart>) {
        deleteBookSources(sources.mapNotNull { it.getBookSource() })
    }

    fun deleteBookSources(sources: List<BookSource>) {
        sources.forEach { deleteBookSource(it.bookSourceUrl) }
    }

    /**
     * 只清理 headless 真正持有的状态：书源的 JS 共享全局作用域。
     * upstream 还会删数据库记录与缓存变量，headless 的源库由服务层负责，不在这里动。
     */
    fun deleteBookSource(key: String) {
        clearSharedGlobalStateBySourceKey(BookSource::class.java, key)
    }

    fun enableSource(key: String, @SourceType.Type type: Int, enable: Boolean) {
        // headless 的源库由服务层管理，这里不改动。保留签名以对齐 upstream。
    }

    fun insertBookSource(vararg bookSources: BookSource) {
        // 同上：源库写入由服务层负责，这里不做事。
    }

    /**
     * 调整排序序号 —— 上游是数据库操作，headless 无此概念，空实现。
     */
    fun adjustSortNumber() {
    }

    fun openVideoPlayer(source: BaseSource?, url: String, title: String, isFloat: Boolean) {
        // headless 无内置播放器；书源一旦调到这里就需要人工介入，快速失败而不是挂起。
        throw UnsupportedOperationException("headless 不支持 java.openVideoPlayer: 依赖 App 内置视频播放器")
    }
}
