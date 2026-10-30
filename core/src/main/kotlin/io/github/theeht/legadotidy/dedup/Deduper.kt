package io.github.theeht.legadotidy.dedup

import io.github.theeht.legadotidy.model.BookSourceDoc

/** HTML 原型使用的 URL 完全重复分组。 */
enum class DedupKind(val label: String) {
    EXACT("相同 URL"),
    FINGERPRINT("规则指纹相同"),
    SIMILAR("规则高度相似"),
}

/**
 * 保留流水线旧配置入口的兼容类型。
 * URL-only 去重与 HTML 原型一致，不再使用相似度阈值或 shingle 候选参数。
 */
data class DedupConfig(
    @Deprecated("去重现在只按规范化 URL 分组") val similarityThreshold: Double = 0.92,
    @Deprecated("去重现在只按规范化 URL 分组") val minSharedShingles: Int = 3,
    @Deprecated("去重现在只按规范化 URL 分组") val maxShingleDocFreq: Int = 60,
)

data class DedupGroup(
    val kind: DedupKind,
    /** 组内成员在输入列表里的下标。 */
    val members: List<Int>,
    val keep: Int,
    val similarity: Double,
) {
    val size: Int get() = members.size
}

data class DedupReport(
    val groups: List<DedupGroup>,
    val total: Int,
) {
    /** 每组建议保留一项，其余均为候选删除项。 */
    val keepCount: Int = total - groups.sumOf { it.size - 1 }
    val exactGroups: Int get() = groups.count { it.kind == DedupKind.EXACT }
    val fingerprintGroups: Int get() = 0
    val similarGroups: Int get() = 0
}

/**
 * 去重规则与 HTML v3 原型保持一致：
 * 1. 仅按规范化后的 bookSourceUrl 分组；
 * 2. 不因规则字段相同或内容相似而把不同 URL 的书源误判为重复；
 * 3. 组内按原型 sourceScore 选择默认保留项，平分时保持输入顺序。
 *
 * 本阶段只产生建议，不修改原始书源。
 */
object Deduper {

    private val URL_SCHEME = Regex("^https?://", RegexOption.IGNORE_CASE)

    @Suppress("UNUSED_PARAMETER")
    fun run(sources: List<BookSourceDoc>, config: DedupConfig = DedupConfig()): DedupReport {
        if (sources.size <= 1) return DedupReport(emptyList(), sources.size)

        val buckets = LinkedHashMap<String, MutableList<Int>>()
        sources.forEachIndexed { index, source ->
            val key = canonicalSourceUrl(source.url)
            if (key.isNotEmpty()) buckets.getOrPut(key) { ArrayList(2) }.add(index)
        }

        val groups = buckets.values
            .asSequence()
            .filter { it.size >= 2 }
            .map { members ->
                val keep = members.maxWithOrNull(
                    compareBy<Int> { keepScore(sources[it]) }.thenBy { -it },
                ) ?: members.first()
                DedupGroup(
                    kind = DedupKind.EXACT,
                    members = members.toList(),
                    keep = keep,
                    similarity = 1.0,
                )
            }
            .toList()

        return DedupReport(groups, sources.size)
    }

    /** 与 HTML 原型 canonicalUrl 完全一致：去协议、www、查询、片段及尾斜杠。 */
    fun canonicalSourceUrl(raw: String): String = raw.trim()
        .lowercase()
        .replace(URL_SCHEME, "")
        .removePrefix("www.")
        .substringBefore('?')
        .substringBefore('#')
        .trimEnd('/')

    /** 与 HTML 原型 sourceScore 一致，用于组内默认保留推荐和对比面板显示。 */
    fun keepScore(source: BookSourceDoc): Double {
        val raw = source.raw
        var score = 0.0
        if (!explicitlyFalse(raw, "enabled") && !explicitlyFalse(raw, "bookSourceEnabled")) score += 10.0
        if (firstTruthy(raw, "searchUrl", "searchUrlRule", "ruleSearch", "ruleSearchUrl")) score += 8.0
        if (firstTruthy(raw, "exploreUrl", "exploreUrlRule", "bookSourceExploreUrl")) score += 4.0
        if (firstTruthy(raw, "ruleBookInfo", "bookSourceInfoRule")) score += 2.0
        if (firstTruthy(raw, "ruleBookContent", "bookSourceContentRule")) score += 2.0
        if (firstTruthy(raw, "bookSourceComment")) score += 1.0
        val weight = when {
            jsTruthy(raw.get("weight")) -> rawNumber(raw, "weight") ?: Double.NaN
            jsTruthy(raw.get("bookSourceWeight")) -> rawNumber(raw, "bookSourceWeight") ?: Double.NaN
            else -> 0.0
        }
        score += weight * 0.1
        score += raw.size() * 0.01
        return score
    }

    private fun firstTruthy(raw: com.google.gson.JsonObject, vararg keys: String): Boolean =
        keys.any { key -> jsTruthy(raw.get(key)) }

    private fun jsTruthy(value: com.google.gson.JsonElement?): Boolean {
        if (value == null || value.isJsonNull) return false
        if (value.isJsonObject || value.isJsonArray) return true
        if (!value.isJsonPrimitive) return false
        val primitive = value.asJsonPrimitive
        return when {
            primitive.isBoolean -> primitive.asBoolean
            primitive.isNumber -> primitive.asDouble != 0.0 && !primitive.asDouble.isNaN()
            primitive.isString -> primitive.asString.isNotEmpty()
            else -> false
        }
    }

    /** HTML checks strict boolean false; strings such as "false" are not false. */
    private fun explicitlyFalse(raw: com.google.gson.JsonObject, key: String): Boolean {
        val value = raw.get(key) ?: return false
        return value.isJsonPrimitive && value.asJsonPrimitive.isBoolean && !value.asBoolean
    }

    private fun rawNumber(raw: com.google.gson.JsonObject, key: String): Double? {
        val value = raw.get(key) ?: return null
        if (value.isJsonNull || !value.isJsonPrimitive) return null
        return runCatching { value.asDouble }.getOrNull()
    }
}
