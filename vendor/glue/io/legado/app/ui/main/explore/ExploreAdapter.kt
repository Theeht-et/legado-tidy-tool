package io.legado.app.ui.main.explore

import androidx.collection.LruCache
import io.legado.app.utils.InfoMap

/**
 * 平台桩。上游 `ExploreAdapter` 是发现页的 RecyclerView 适配器（大量 Android/UI 依赖，
 * headless 不需要），但它的 companion 里的 [exploreInfoMapList] **有实际作用** ——
 * `WebBook.exploreBookAwait()` 会读它，并把命中的 `InfoMap` 作为 JS 变量 `infoMap`
 * 注入 `AnalyzeUrl` 的规则执行环境（缓存发现页的解析结果）。
 *
 * 所以这里保真：同类型 `LruCache<String, InfoMap>`、同容量 `99`、同 key（书源 URL）。
 * `WebBook` 只引用这个 companion 字段，适配器本体 headless 不会实例化，故留空类。
 *
 * 上游文件：`upstream/legado/app/src/main/java/io/legado/app/ui/main/explore/ExploreAdapter.kt`
 */
class ExploreAdapter {

    companion object {
        val exploreInfoMapList = LruCache<String, InfoMap>(99)
    }

}
