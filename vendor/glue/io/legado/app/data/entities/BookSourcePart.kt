package io.legado.app.data.entities

import io.legado.app.constant.AppPattern
import io.legado.app.utils.splitNotBlank

data class BookSourcePart(
    // 地址，包括 http/https
    var bookSourceUrl: String = "",
    // 名称
    var bookSourceName: String = "",
    // 分组
    var bookSourceGroup: String? = null,
    // 手动排序编号
    var customOrder: Int = 0,
    // 是否启用
    var enabled: Boolean = true,
    // 启用发现
    var enabledExplore: Boolean = true,
    // 是否有登录地址或 JS 表单登录
    var hasLoginUrl: Boolean = false,
    // 最后更新时间，用于排序
    var lastUpdateTime: Long = 0,
    // 响应时间，用于排序
    var respondTime: Long = 180000L,
    // 智能排序的权重
    var weight: Int = 0,
    // 是否有发现url
    var hasExploreUrl: Boolean = false,
    // 是否启用事件监听
    var eventListener: Boolean = false,
    // 书源类型
    var bookSourceType: Int = 0,
    // 是否为纯 JS 单文件源
    var hasJs: Boolean = false,
    var checkStatus: String = BookSourceCheckState.NEEDS_CHECK,
    var checkRevision: String = "",
    var sourceRevision: String = "",
    var checkedAt: Long = 0,
    var checkDetail: String = "",
) {

    override fun hashCode(): Int {
        return bookSourceUrl.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return if (other is BookSourcePart) other.bookSourceUrl == bookSourceUrl else false
    }

    fun getDisPlayNameGroup(): String {
        return if (bookSourceGroup.isNullOrBlank()) {
            bookSourceName
        } else {
            String.format("%s (%s)", bookSourceName, bookSourceGroup)
        }
    }

    fun getBookSource(): BookSource? {
        // 上游按 bookSourceUrl 查数据库返回完整书源。headless 没有书源数据库，
        // 引擎实际走的是「直接传入 BookSource」的入口（见 SourceRunner），
        // 这条按 URL 回查的路径不会被命中，固定返回 null。
        return null
    }

    fun addGroup(groups: String) {
        bookSourceGroup?.splitNotBlank(AppPattern.splitGroupRegex)
            ?.toCollection(linkedSetOf())?.let {
            it.addAll(groups.splitNotBlank(AppPattern.splitGroupRegex))
            bookSourceGroup = it.joinToString(",")
        }
        if (bookSourceGroup.isNullOrBlank()) bookSourceGroup = groups
    }

    fun removeGroup(groups: String) {
        bookSourceGroup?.splitNotBlank(AppPattern.splitGroupRegex)
            ?.toCollection(linkedSetOf())?.let {
            it.removeAll(groups.splitNotBlank(AppPattern.splitGroupRegex).toSet())
            bookSourceGroup = it.joinToString(",")
        }
    }

}

// 上游还有一个 List<BookSourcePart>.toBookSource()，以及配套的 bookSourceKeyChunks /
// orderResolvedBookSources 和 SQL 视图常量 BOOK_SOURCE_PART_VIEW —— 它们全都是
// 「把书源列表从数据库里批量查出来」的 App 专有代码（依赖书源表 DAO 与 Room）。
// headless 的书源由服务层直接提供（见 io.legado.headless.store.SourceStore），
// 这些声明在引擎与保留实体中都没有引用，故整段删除。
