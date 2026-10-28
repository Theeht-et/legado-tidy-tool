package io.legado.app.help.config

import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.headless.Paths
import java.io.File

/**
 * headless 版 SourceConfig。
 *
 * 上游用 SharedPreferences 保存「按书源 origin 维度的小数据」（书源评分等）。headless 没有
 * Android SharedPreferences，改成内存 Map，并在每次修改后**可选**落盘到
 * [Paths.runDir] 下的 `sourceConfig.json`（Gson）。内存是真实来源，落盘失败只忽略
 * （评分不是对比结果的一部分）。
 *
 * 签名与上游保持一致：`setBookScore` / `getBookScore` / `getSourceScore` / `removeSource` /
 * `removeSources`，以及 internal 的 `belongsToSource` / `sourceConfigKeysToRemove`。
 *
 * ⚠️ 与上游的差异：上游 `removeSources` 先查书源数据库得到 protectedOrigins（仍存在的书源
 * 不能被误删）；headless 没有书源数据库，protectedOrigins 取空集，只按「key 的 origin 归属
 * 是否在待删集合里」清理。headless 本就没有评分数据，这个差异不影响引擎行为。
 *
 * 说明：任务里提到的 `extract` / `stampLastUpdateTime` / `declaresReviewFunctions` /
 * `declaresReviewRepliesFunction` 不在本类 —— 它们在引擎目录的
 * `io.legado.app.model.jsSource.JsSourceConfig`（随 upstream 一起编译）。本类被
 * `JsSourceUpsert`（`removeSource`）与 headless 的 `SourceHelp`（`removeSources`）调用。
 */
object SourceConfig {

    private const val CONFIG_FILE_NAME = "sourceConfig.json"

    /** key 与上游 SharedPreferences 同构：`origin` 或 `${origin}_${name}_${author}`。 */
    private val values = LinkedHashMap<String, Int>()

    private val file: File by lazy { File(Paths.runDir, CONFIG_FILE_NAME) }

    init {
        load()
    }

    @Synchronized
    fun setBookScore(origin: String, name: String, author: String, score: Int) {
        val preScore = getBookScore(origin, name, author)
        var newScore = score
        if (preScore != 0) {
            newScore = score - preScore
        }
        values[origin] = getSourceScore(origin) + newScore
        values["${origin}_${name}_${author}"] = score
        persist()
    }

    @Synchronized
    fun getBookScore(origin: String, name: String, author: String): Int {
        return values["${origin}_${name}_${author}"] ?: 0
    }

    @Synchronized
    fun getSourceScore(origin: String): Int {
        return values[origin] ?: 0
    }

    @Synchronized
    fun removeSource(origin: String) {
        removeSources(listOf(origin))
    }

    @Synchronized
    fun removeSources(origins: Collection<String>) {
        if (origins.isEmpty()) return
        // 上游这里用书源数据库查 protectedOrigins；headless 无书源数据库，取空集。
        val protectedOrigins = emptyList<String>()
        sourceConfigKeysToRemove(values.keys, origins, protectedOrigins).forEach(values::remove)
        persist()
    }

    private fun load() {
        val text = runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull() ?: return
        val loaded = GSON.fromJsonObject<Map<String, Int>>(text).getOrNull() ?: return
        values.putAll(loaded)
    }

    private fun persist() {
        runCatching { file.writeText(GSON.toJson(values)) }
    }
}

internal fun belongsToSource(
    key: String,
    origin: String,
    protectedOrigins: Collection<String>,
): Boolean = key in sourceConfigKeysToRemove(
    listOf(key),
    listOf(origin),
    protectedOrigins,
)

internal fun sourceConfigKeysToRemove(
    keys: Collection<String>,
    removedOrigins: Collection<String>,
    protectedOrigins: Collection<String>,
): Set<String> {
    val removed = removedOrigins.toHashSet()
    val knownOrigins = protectedOrigins.toHashSet().apply { addAll(removed) }
    return keys.filterTo(HashSet()) { sourceOwner(it, knownOrigins) in removed }
}

private fun sourceOwner(key: String, origins: Set<String>): String? {
    if (key in origins) return key
    var separator = key.lastIndexOf('_')
    while (separator >= 0) {
        key.substring(0, separator).takeIf { it in origins }?.let { return it }
        separator = key.lastIndexOf('_', separator - 1)
    }
    return null
}
