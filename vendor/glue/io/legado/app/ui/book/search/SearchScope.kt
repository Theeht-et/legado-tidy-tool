package io.legado.app.ui.book.search

import io.legado.app.data.entities.BookSourcePart

/**
 * 平台桩。上游 `SearchScope`（搜索范围：按分组 / 按书源）里塞满了 `AppConfig` 持久化、
 * `MutableLiveData` 与 `appDb` 查询；headless 的搜索编排**不走 `SearchModel`**
 * （直接调 `WebBook.searchBookAwait`），所以这里只补 `SearchModel` 实际引用到的成员。
 *
 * [getBookSourceParts] 返回空列表：headless 不用它取书源，空列表即「启用书源为空」的
 * 语义，不会撒谎成「有书源但没搜」。
 *
 * 上游文件：`upstream/legado/app/src/main/java/io/legado/app/ui/book/search/SearchScope.kt`
 */
class SearchScope {

    fun getBookSourceParts(): List<BookSourcePart> = emptyList()

}
