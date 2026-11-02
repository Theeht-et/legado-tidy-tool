package io.github.theeht.legadotidy.dedup

import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.probe.ProbeResult
import io.github.theeht.legadotidy.probe.ProbeStatus

/** 按规范化 URL 判定的完全重复分组。 */
enum class DedupKind(val label: String) {
    EXACT("相同 URL"),
}

data class DedupGroup(
    val kind: DedupKind,
    /** 组内成员在输入列表里的下标。 */
    val members: List<Int>,
    val keep: Int,
) {
    val size: Int get() = members.size
}

data class DedupReport(
    val groups: List<DedupGroup>,
    val total: Int,
) {
    /** 每组建议保留一项，其余均为候选删除项。 */
    val keepCount: Int = total - groups.sumOf { it.size - 1 }
}

/**
 * 去重规则：
 * 1. 仅按规范化后的 bookSourceUrl 分组，不因规则相似而误判不同 URL；
 * 2. 先比较实际校验结果，再比较核心/辅助配置与规则完整度；
 * 3. JSON 顶层业务字段数量只作为最后一个质量比较因素；
 * 4. 所有因素仍相同时稳定保留输入顺序靠前的候选。
 *
 * 本阶段只产生建议，不修改原始书源。
 */
object Deduper {

    private val URL_SCHEME = Regex("^https?://", RegexOption.IGNORE_CASE)

    private val NON_QUALITY_TOP_LEVEL_FIELDS = setOf(
        "bookSourceUrl",
        "bookSourceName",
        "bookSourceGroup",
        "bookSourceType",
        "enabled",
        "enabledExplore",
        "bookSourceEnabled",
        "weight",
        "bookSourceComment",
        "variableComment",
        "customOrder",
        "lastUpdateTime",
        "respondTime",
    )

    fun run(
        sources: List<BookSourceDoc>,
        probeResults: Map<Int, ProbeResult> = emptyMap(),
    ): DedupReport {
        if (sources.size <= 1) return DedupReport(emptyList(), sources.size)

        val buckets = LinkedHashMap<String, MutableList<Int>>()
        sources.forEachIndexed { index, source ->
            val key = canonicalSourceUrl(source.url)
            if (key.isNotEmpty()) buckets.getOrPut(key) { ArrayList(2) }.add(index)
        }

        val duplicateBuckets = buckets.values.filter { it.size >= 2 }

        // 评分项涉及规则遍历和 JSON 字段统计；每个重复候选只计算一次。
        val scoresByIndex = HashMap<Int, CandidateScore>()
        duplicateBuckets.forEach { members ->
            members.forEach { index ->
                scoresByIndex[index] = scoreCandidate(sources[index], probeResults[index])
            }
        }

        val keepComparator = compareBy<Int> { scoresByIndex.getValue(it).probeQuality }
            .thenByDescending { scoresByIndex.getValue(it).coreCapabilities }
            .thenByDescending { scoresByIndex.getValue(it).auxiliaryCapabilities }
            .thenByDescending { scoresByIndex.getValue(it).ruleCompleteness }
            .thenBy { scoresByIndex.getValue(it).successfulElapsedMs }
            .thenByDescending { scoresByIndex.getValue(it).topLevelFieldCount }
            .thenBy { it }

        val groups = duplicateBuckets.map { members ->
            val keep = members.minWithOrNull(keepComparator) ?: members.first()
            DedupGroup(
                kind = DedupKind.EXACT,
                members = members.toList(),
                keep = keep,
            )
        }

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

    private data class CandidateScore(
        val probeQuality: Int,
        val coreCapabilities: Int,
        val auxiliaryCapabilities: Int,
        val ruleCompleteness: Int,
        val successfulElapsedMs: Long,
        val topLevelFieldCount: Int,
    )

    private fun scoreCandidate(source: BookSourceDoc, result: ProbeResult?): CandidateScore =
        CandidateScore(
            probeQuality = probeQuality(result),
            coreCapabilities = coreCapabilityCount(source),
            auxiliaryCapabilities = auxiliaryCapabilityCount(source),
            ruleCompleteness = source.ruleCompleteness,
            successfulElapsedMs = successfulProbeElapsedMs(result),
            topLevelFieldCount = qualityTopLevelFieldCount(source),
        )

    /**
     * 只有实际搜索成功或明确缺少搜索入口/解析为空，才形成确定的质量差异。
     * 网络、超时、HTTP、JS 和运行环境不支持等结果不能证明书源本身不可用，
     * 与跳过/缺失结果一样继续比较配置能力。
     */
    private fun probeQuality(result: ProbeResult?): Int = when (result?.status) {
        ProbeStatus.OK, ProbeStatus.UNCHECKABLE -> 0
        ProbeStatus.NO_SEARCH_URL, ProbeStatus.PARSE_EMPTY -> 2
        else -> 1
    }

    /** 只在成功校验时比较实测耗时；失败、跳过和缺失结果不提供速度依据。 */
    private fun successfulProbeElapsedMs(result: ProbeResult?): Long =
        result?.takeIf { it.ok && it.elapsedMs > 0 }?.elapsedMs ?: Long.MAX_VALUE

    private fun coreCapabilityCount(source: BookSourceDoc): Int = listOf(
        source.canSearch,
        !source.ruleBookInfo.isEmpty(),
        !source.ruleToc.isEmpty(),
        !source.ruleContent.isEmpty(),
    ).count { it }

    private fun auxiliaryCapabilityCount(source: BookSourceDoc): Int = listOf(
        source.exploreUrl.isNotBlank() || !source.ruleExplore.isEmpty(),
        !source.ruleReview.isEmpty(),
    ).count { it }

    /** 用户偏好、展示元数据和统计字段不参与最后的 JSON 字段数量比较。 */
    private fun qualityTopLevelFieldCount(source: BookSourceDoc): Int =
        source.raw.entrySet().count { (key, value) ->
            key !in NON_QUALITY_TOP_LEVEL_FIELDS && !value.isJsonNull
        }
}
