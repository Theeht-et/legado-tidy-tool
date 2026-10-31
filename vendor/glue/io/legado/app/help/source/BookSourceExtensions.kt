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
 * Serializes the complete discovery session for a source URL, not only category parsing.
 * Explore rules share one upstream InfoMap, so initialization, pagination and persistence
 * must not overlap for duplicate source records.
 */
suspend fun <T> withExploreSession(sourceUrl: String, block: suspend () -> T): T {
    val mutex = exploreKindsMutexes.computeIfAbsent(sourceUrl) { Mutex() }
    return mutex.withLock { block() }
}

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
 * 每个书源 URL 使用 ConcurrentHashMap 中的同一把 Mutex 保护完整发现会话，
 * 避免重复书源并发读写上游共享 InfoMap。
 */
private fun BookSource.getOrCreateExploreInfoMap(): InfoMap =
    synchronized(exploreInfoMapList) {
        exploreInfoMapList[bookSourceUrl] ?: InfoMap(bookSourceUrl).also {
            exploreInfoMapList.put(bookSourceUrl, it)
        }
    }

/**
 * ExploreAdapter initializes toggle/select defaults while building its controls.
 * Those defaults can be read by @js category URLs, so a headless probe must apply
 * the same first-render state before calling WebBook.exploreBookAwait.
 */
fun BookSource.initializeExploreInfoMap(kinds: List<ExploreKind>) {
    val infoMap = getOrCreateExploreInfoMap()
    kinds.forEach { kind ->
        if (kind.type == ExploreKind.Type.toggle || kind.type == ExploreKind.Type.select) {
            val chars = kind.chars?.filterNotNull() ?: listOf("chars", "is null")
            if (infoMap[kind.title].isNullOrEmpty()) {
                val initial = kind.default ?: chars.firstOrNull()
                    ?: throw IllegalArgumentException(
                        "发现分类「${kind.title}」的 ${kind.type} 控件没有可用选项",
                    )
                infoMap[kind.title] = initial
            }
        }
    }
}

suspend fun BookSourcePart.exploreKinds(): List<ExploreKind> {
    return getBookSource()?.exploreKinds() ?: emptyList()
}

suspend fun BookSource.exploreKinds(): List<ExploreKind> =
    withExploreSession(bookSourceUrl) { exploreKindsInSession() }

/**
 * Caller must already hold [withExploreSession] for this bookSourceUrl.
 * The engine uses this entry point so the lock remains held through all pages.
 */
suspend fun BookSource.exploreKindsInSession(): List<ExploreKind> {
    val exploreKindsKey = getExploreKindsKey()
    exploreKindsMap[exploreKindsKey]?.let { return it }

    val categoryRule = exploreUrl
    if (categoryRule.isNullOrBlank()) return emptyList()

    val kinds = arrayListOf<ExploreKind>()
    withContext(Dispatchers.IO) {
        runCatching {
            val ruleStr = when {
                categoryRule.startsWith("@js:", ignoreCase = true) -> {
                    CacheManager.get(exploreKindsKey)?.takeIf { it.isNotBlank() } ?: run {
                        val exploreInfoMap = getOrCreateExploreInfoMap()
                        runScriptWithContext {
                            evalJS(categoryRule.substring(4)) {
                                put("infoMap", exploreInfoMap)
                            }.toString().trim()
                        }.also {
                            CacheManager.put(exploreKindsKey, it)
                            CacheManager.flush()
                        }
                    }
                }

                categoryRule.startsWith("<js>", ignoreCase = true) -> {
                    CacheManager.get(exploreKindsKey)?.takeIf { it.isNotBlank() } ?: run {
                        val exploreInfoMap = getOrCreateExploreInfoMap()
                        runScriptWithContext {
                            evalJS(categoryRule.substring(4, categoryRule.lastIndexOf("<"))) {
                                put("infoMap", exploreInfoMap)
                            }.toString().trim()
                        }.also {
                            CacheManager.put(exploreKindsKey, it)
                            CacheManager.flush()
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
    return kinds
}

/**
 * ExploreAdapter normally persists an InfoMap marked by infoMap.save() when its
 * screen pauses. A headless validation has no UI lifecycle, so flush that same
 * upstream InfoMap after each category/page request that may have modified it.
 */
fun BookSource.flushExploreInfoMapIfNeeded() {
    val infoMap = synchronized(exploreInfoMapList) { exploreInfoMapList[bookSourceUrl] }
    if (infoMap?.needSave == true) {
        infoMap.saveNow()
        CacheManager.flush()
    }
}

suspend fun BookSourcePart.clearExploreKindsCache() {
    val source = getBookSource() ?: return
    source.clearExploreKindsCache()
}

suspend fun BookSource.clearExploreKindsCache() {
    withExploreSession(bookSourceUrl) {
        withContext(Dispatchers.IO) {
            val key = getExploreKindsKey()
            CacheManager.delete(key)
            CacheManager.flush()
            exploreKindsMap.remove(key)
        }
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
