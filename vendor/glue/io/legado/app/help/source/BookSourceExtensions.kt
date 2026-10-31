package io.legado.app.help.source

import com.script.rhino.runScriptWithContext
import io.legado.app.constant.BookSourceType
import io.legado.app.constant.BookType
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.help.CacheManager
import io.legado.app.ui.main.explore.ExploreAdapter.Companion.exploreInfoMapList
import io.legado.app.utils.GSON
import io.legado.app.utils.InfoMap
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.printOnDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Upstream uses an in-memory map plus ACache for evaluated JS category definitions.
 * Headless replaces ACache with CacheManager, retaining the same MD5 key and permanent
 * cache semantics. The UI adapter itself is a platform stub, but its shared InfoMap
 * cache is retained because WebBook.exploreBookAwait injects it into AnalyzeUrl.
 */
private val exploreKindsMutexes = ConcurrentHashMap<String, Mutex>()
private val exploreKindsMap = ConcurrentHashMap<String, List<ExploreKind>>()

/**
 * 采用 md5 作为 key，分类内容变化后会自动重新计算。
 */
private fun BookSource.getExploreKindsKey(): String {
    return MD5Utils.md5Encode(bookSourceUrl + exploreUrl)
}

/**
 * 与 Legado 3.26101819 的 BookSource.exploreKinds() 保持相同解析语义：
 * - @js: 与 <js>...</js> 先执行脚本，并将同一份 InfoMap 注入脚本上下文；
 * - JSON 数组反序列化为 ExploreKind；
 * - 其他格式按 && / 换行拆分，并按 title::url 取分类名称和链接；
 * - 解析结果按 MD5(bookSourceUrl + exploreUrl) 缓存。
 *
 * 仅缓存锁的实现改为 ConcurrentHashMap，避免多个校验协程首次访问同一来源时
 * 因上游 lazy HashMap 初始化竞争而拿到不同 Mutex。
 */
suspend fun BookSource.exploreKinds(): List<ExploreKind> {
    val exploreKindsKey = getExploreKindsKey()
    exploreKindsMap[exploreKindsKey]?.let { return it }

    val categoryRule = exploreUrl
    if (categoryRule.isNullOrBlank()) return emptyList()

    val mutex = exploreKindsMutexes.computeIfAbsent(bookSourceUrl) { Mutex() }
    return mutex.withLock {
        exploreKindsMap[exploreKindsKey]?.let { return@withLock it }

        val kinds = arrayListOf<ExploreKind>()
        withContext(Dispatchers.IO) {
            runCatching {
                val ruleStr = when {
                    categoryRule.startsWith("@js:", ignoreCase = true) -> {
                        CacheManager.get(exploreKindsKey)?.takeIf { it.isNotBlank() } ?: run {
                            val exploreInfoMap = exploreInfoMapList[bookSourceUrl]
                                ?: InfoMap(bookSourceUrl).also {
                                    exploreInfoMapList.put(bookSourceUrl, it)
                                }
                            runScriptWithContext {
                                evalJS(categoryRule.substring(4)) {
                                    put("infoMap", exploreInfoMap)
                                }.toString().trim()
                            }.also {
                                CacheManager.put(exploreKindsKey, it)
                            }
                        }
                    }

                    categoryRule.startsWith("<js>", ignoreCase = true) -> {
                        CacheManager.get(exploreKindsKey)?.takeIf { it.isNotBlank() } ?: run {
                            val exploreInfoMap = exploreInfoMapList[bookSourceUrl]
                                ?: InfoMap(bookSourceUrl).also {
                                    exploreInfoMapList.put(bookSourceUrl, it)
                                }
                            runScriptWithContext {
                                evalJS(categoryRule.substring(4, categoryRule.lastIndexOf("<"))) {
                                    put("infoMap", exploreInfoMap)
                                }.toString().trim()
                            }.also {
                                CacheManager.put(exploreKindsKey, it)
                            }
                        }
                    }

                    else -> categoryRule
                }

                if (ruleStr.isJsonArray()) {
                    GSON.fromJsonArray<ExploreKind>(ruleStr).getOrThrow().let(kinds::addAll)
                } else {
                    ruleStr.split("(&&|\\n)+".toRegex()).forEach { kindStr ->
                        val kindCfg = kindStr.split("::")
                        kinds.add(ExploreKind(kindCfg.first(), kindCfg.getOrNull(1)))
                    }
                }
            }.onFailure { error ->
                // Upstream exposes category parsing errors as an ERROR: category row.
                kinds.add(
                    ExploreKind(
                        title = "ERROR:${error.localizedMessage}",
                        url = error.stackTraceToString(),
                    ),
                )
                error.printOnDebug()
            }
        }

        exploreKindsMap[exploreKindsKey] = kinds
        kinds
    }
}

/**
 * ExploreAdapter normally persists an InfoMap marked by infoMap.save() when its
 * screen pauses. A headless validation has no UI lifecycle, so flush that same
 * upstream InfoMap after each category/page request that may have modified it.
 */
fun BookSource.flushExploreInfoMapIfNeeded() {
    exploreInfoMapList[bookSourceUrl]?.takeIf { it.needSave }?.saveNow()
}

suspend fun BookSourcePart.clearExploreKindsCache() {
    withContext(Dispatchers.IO) {
        val source = getBookSource() ?: return@withContext
        val key = source.getExploreKindsKey()
        CacheManager.delete(key)
        exploreKindsMap.remove(key)
    }
}

suspend fun BookSource.clearExploreKindsCache() {
    withContext(Dispatchers.IO) {
        val key = getExploreKindsKey()
        CacheManager.delete(key)
        exploreKindsMap.remove(key)
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
