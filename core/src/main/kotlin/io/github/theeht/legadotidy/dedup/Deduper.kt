package io.github.theeht.legadotidy.dedup

import io.github.theeht.legadotidy.model.BookSourceDoc

/** HTML 原型使用的 URL 完全重复分组。 */
enum class DedupKind(val label: String) {
    EXACT("相同 URL"),
    FINGERPRINT("规则指纹相同"),
    SIMILAR("规则高度相似"),
}

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

    fun run(sources: List<BookSourceDoc>): DedupReport {
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
        if (rawBoolean(raw, "enabled", true) && rawBoolean(raw, "bookSourceEnabled", true)) score += 10.0
        if (firstPresent(raw, "searchUrl", "searchUrlRule", "ruleSearch", "ruleSearchUrl")) score += 8.0
        if (firstPresent(raw, "exploreUrl", "exploreUrlRule", "bookSourceExploreUrl")) score += 4.0
        if (firstPresent(raw, "ruleBookInfo", "bookSourceInfoRule")) score += 2.0
        if (firstPresent(raw, "ruleBookContent", "bookSourceContentRule")) score += 2.0
        if (firstPresent(raw, "bookSourceComment")) score += 1.0
        score += (rawNumber(raw, "weight") ?: rawNumber(raw, "bookSourceWeight") ?: 0.0) * 0.1
        score += raw.size() * 0.01
        return score
    }

    private fun firstPresent(raw: com.google.gson.JsonObject, vararg keys: String): Boolean =
        keys.any { key ->
            val value = raw.get(key) ?: return@any false
            !value.isJsonNull && when {
                value.isJsonPrimitive -> value.asString.isNotBlank()
                value.isJsonObject -> value.asJsonObject.size() > 0
                value.isJsonArray -> value.asJsonArray.size() > 0
                else -> false
            }
        }

    private fun rawBoolean(raw: com.google.gson.JsonObject, key: String, fallback: Boolean): Boolean {
        val value = raw.get(key) ?: return fallback
        if (value.isJsonNull || !value.isJsonPrimitive) return fallback
        return runCatching { value.asBoolean }.getOrDefault(fallback)
    }

    private fun rawNumber(raw: com.google.gson.JsonObject, key: String): Double? {
        val value = raw.get(key) ?: return null
        if (value.isJsonNull || !value.isJsonPrimitive) return null
        return runCatching { value.asDouble }.getOrNull()
    }
}
