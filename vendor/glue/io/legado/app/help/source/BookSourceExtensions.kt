package io.legado.app.help.source

import io.legado.app.constant.BookSourceType
import io.legado.app.constant.BookType
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.help.CacheManager
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.isJsonArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 取自 upstream 的 help/source/BookSourceExtensions.kt。
 *
 * 与上游的差异（均为 headless 取舍，逐处注明）：
 *  1. 上游的发现分类缓存用 `ACache.get("explore")`，headless 没有 ACache；
 *     换成语义等价的 [CacheManager]（内存 + 磁盘，key 仍是同一个 md5）。
 *  2. 上游的 `exploreKinds()`（解析发现分类，会 eval JS、依赖 UI 的 exploreInfoMapList/InfoMap）
 *     headless 的对比流程用不到，本文件不实现；只保留引擎实际引用的 exploreKindsJson /
 *     clearExploreKindsCache / getBookType。
 */

/**
 * 采用md5作为key可以在分类修改后自动重新计算,不需要手动刷新
 */
private fun BookSource.getExploreKindsKey(): String {
    return MD5Utils.md5Encode(bookSourceUrl + exploreUrl)
}

private fun BookSourcePart.getExploreKindsKey(): String {
    return getBookSource()!!.getExploreKindsKey()
}

suspend fun BookSourcePart.clearExploreKindsCache() {
    withContext(Dispatchers.IO) {
        CacheManager.delete(getExploreKindsKey())
    }
}

suspend fun BookSource.clearExploreKindsCache() {
    withContext(Dispatchers.IO) {
        CacheManager.delete(getExploreKindsKey())
    }
}

fun BookSource.exploreKindsJson(): String {
    val exploreKindsKey = getExploreKindsKey()
    return CacheManager.get(exploreKindsKey)?.takeIf { it.isJsonArray() }
        ?: exploreUrl.takeIf { it.isJsonArray() }
        ?: ""
}

fun BookSource.getBookType(): Int {
    return when (bookSourceType) {
        BookSourceType.file -> BookType.text or BookType.webFile
        BookSourceType.image -> BookType.image
        BookSourceType.audio -> BookType.audio
        BookSourceType.video -> BookType.video
        else -> BookType.text
    }
}
