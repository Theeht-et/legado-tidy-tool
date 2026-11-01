package io.github.theeht.legadotidy.dedup

import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.probe.ProbeDepth
import io.github.theeht.legadotidy.probe.ProbeEntry
import io.github.theeht.legadotidy.probe.ProbeResult
import io.github.theeht.legadotidy.probe.ProbeStage
import io.github.theeht.legadotidy.probe.ProbeStageResult
import io.github.theeht.legadotidy.probe.ProbeStageStatus
import io.github.theeht.legadotidy.probe.ProbeStatus

/** HTML 原型使用的 URL 完全重复分组。 */
enum class DedupKind(val label: String) {
    EXACT("相同 URL"),
    FINGERPRINT("规则指纹相同"),
    SIMILAR("规则高度相似"),
}

/** 保留流水线旧配置入口的兼容类型。 */
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
    val recommendationReason: String = "",
) {
    val size: Int get() = members.size
}

data class DedupReport(
    val groups: List<DedupGroup>,
    val total: Int,
) {
    val keepCount: Int = total - groups.sumOf { it.size - 1 }
    val exactGroups: Int get() = groups.count { it.kind == DedupKind.EXACT }
    val fingerprintGroups: Int get() = 0
    val similarGroups: Int get() = 0
}

private data class CandidateQuality(
    val successfulCoreStages: Int,
    val coreStageRanks: List<Int>,
    val failurePenalty: Int,
    val averageSuccessfulStageMs: Long,
    val coreCapabilityCount: Int,
    val auxiliaryCapabilityCount: Int,
    val ruleCompleteness: Int,
    val topLevelFieldCount: Int,
)

/**
 * 排序优先级：
 * 1. 搜索、书籍信息、目录、正文的实际校验结果；
 * 2. 校验错误情况与成功阶段平均响应时间；
 * 3. 核心/辅助功能配置和规则完整度；
 * 4. JSON 顶层业务字段数量；
 * 5. 所有质量因素完全相同时，稳定保留输入顺序靠前的候选。
 *
 * 每组始终产生一个确定推荐项；这里只生成建议，不修改原始书源。
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

    private val CORE_STAGE_LABELS = listOf("搜索", "书籍信息", "目录", "正文")

    @Suppress("UNUSED_PARAMETER")
    fun run(
        sources: List<BookSourceDoc>,
        config: DedupConfig = DedupConfig(),
        probeResults: Map<Int, ProbeResult> = emptyMap(),
    ): DedupReport {
        if (sources.size <= 1) return DedupReport(emptyList(), sources.size)

        val buckets = LinkedHashMap<String, MutableList<Int>>()
        sources.forEachIndexed { index, source ->
            val key = canonicalSourceUrl(source.url)
            if (key.isNotEmpty()) buckets.getOrPut(key) { ArrayList(2) }.add(index)
        }

        val qualities = sources.indices.associateWith { index ->
            assessQuality(sources[index], probeResults[index])
        }
        val groups = buckets.values
            .asSequence()
            .filter { it.size >= 2 }
            .map { members ->
                val ranked = members.sortedWith { left, right ->
                    compareQuality(qualities.getValue(left), qualities.getValue(right))
                        .takeIf { it != 0 } ?: left.compareTo(right)
                }
                val keep = ranked.first()
                val runnerUp = ranked.getOrNull(1)
                DedupGroup(
                    kind = DedupKind.EXACT,
                    members = members.toList(),
                    keep = keep,
                    similarity = 1.0,
                    recommendationReason = if (runnerUp == null) {
                        "重复组只有一个候选，无需比较。"
                    } else {
                        explainRecommendation(
                            qualities.getValue(keep),
                            qualities.getValue(runnerUp),
                            probeResults[keep],
                            probeResults[runnerUp],
                        )
                    },
                )
            }
            .toList()

        return DedupReport(groups, sources.size)
    }

    /** URL 归一化保持现有规则不变。 */
    fun canonicalSourceUrl(raw: String): String = raw.trim()
        .lowercase()
        .replace(URL_SCHEME, "")
        .removePrefix("www.")
        .substringBefore('?')
        .substringBefore('#')
        .trimEnd('/')

    private fun assessQuality(source: BookSourceDoc, probe: ProbeResult?): CandidateQuality {
        val entryStage = if (probe?.entry == ProbeEntry.EXPLORE) ProbeStage.EXPLORE else ProbeStage.SEARCH
        val expectedStages = when (probe?.depth ?: ProbeDepth.FULL_CHAIN) {
            ProbeDepth.SEARCH -> listOf(entryStage)
            ProbeDepth.WITH_TOC -> listOf(entryStage, ProbeStage.BOOK_INFO, ProbeStage.TOC)
            ProbeDepth.FULL_CHAIN -> listOf(entryStage, ProbeStage.BOOK_INFO, ProbeStage.TOC, ProbeStage.CONTENT)
        }
        val actualStages = probe?.stageResults.orEmpty().associateBy { it.stage }

        fun fallbackEntryRank(): Int = when (probe?.status) {
            ProbeStatus.OK -> 4
            ProbeStatus.PARTIAL -> 0
            ProbeStatus.PARSE_EMPTY -> 2
            ProbeStatus.NETWORK, ProbeStatus.TIMEOUT, ProbeStatus.HTTP_ERROR -> 1
            ProbeStatus.SKIPPED, ProbeStatus.UNSUPPORTED, ProbeStatus.NEED_JS -> -1
            null -> -2
            else -> 0
        }

        fun rank(stage: ProbeStage): Int {
            if (probe == null) return -2
            val actual = actualStages[stage]
            if (actual != null) return stageRank(actual)
            if (probe.stageResults.isEmpty() && stage == entryStage) return fallbackEntryRank()
            if (probe.stageResults.isEmpty() && probe.status == ProbeStatus.OK && stage in expectedStages) return 4
            return -2
        }

        val stageRanks = expectedStages.map(::rank)
        val successfulCount = when {
            probe == null -> 0
            probe.stageResults.isEmpty() && probe.status == ProbeStatus.OK -> expectedStages.size
            else -> expectedStages.count { actualStages[it]?.status == ProbeStageStatus.SUCCESS }
        }

        val relevantResults = probe?.stageResults.orEmpty().filter { it.stage in expectedStages }
        val failurePenalty = if (probe == null || relevantResults.isEmpty()) {
            fallbackFailurePenalty(probe?.status)
        } else {
            relevantResults.sumOf { stage ->
                when (stage.status) {
                    ProbeStageStatus.SUCCESS -> 0
                    ProbeStageStatus.NOT_RUN -> when (stage.failureStatus) {
                        ProbeStatus.NO_SEARCH_URL, ProbeStatus.NO_EXPLORE_CATEGORY, ProbeStatus.ERROR -> 3
                        else -> 0
                    }
                    ProbeStageStatus.EMPTY -> 2
                    ProbeStageStatus.FAILED -> when (stage.failureStatus) {
                        ProbeStatus.UNSUPPORTED, ProbeStatus.NEED_JS -> 0
                        ProbeStatus.NETWORK, ProbeStatus.TIMEOUT -> 1
                        ProbeStatus.HTTP_ERROR -> 2
                        else -> 3
                    }
                }
            }
        }

        val successfulTimes = relevantResults
            .filter { it.status == ProbeStageStatus.SUCCESS && it.elapsedMs > 0 }
            .map { it.elapsedMs }
        val averageMs = when {
            successfulTimes.isNotEmpty() -> successfulTimes.average().toLong()
            probe != null && probe.stageResults.isEmpty() && probe.status == ProbeStatus.OK && probe.elapsedMs > 0 ->
                probe.elapsedMs / expectedStages.size.coerceAtLeast(1)
            else -> Long.MAX_VALUE
        }

        return CandidateQuality(
            successfulCoreStages = successfulCount,
            coreStageRanks = stageRanks,
            failurePenalty = failurePenalty,
            averageSuccessfulStageMs = averageMs,
            coreCapabilityCount = coreCapabilityCount(source),
            auxiliaryCapabilityCount = auxiliaryCapabilityCount(source),
            ruleCompleteness = source.ruleCompleteness,
            topLevelFieldCount = qualityTopLevelFieldCount(source),
        )
    }

    private fun stageRank(result: ProbeStageResult): Int = when (result.status) {
        ProbeStageStatus.SUCCESS -> 4
        ProbeStageStatus.EMPTY -> 2
        ProbeStageStatus.FAILED -> when (result.failureStatus) {
            ProbeStatus.NETWORK, ProbeStatus.TIMEOUT, ProbeStatus.HTTP_ERROR -> 1
            ProbeStatus.UNSUPPORTED, ProbeStatus.NEED_JS -> -1
            else -> 0
        }
        ProbeStageStatus.NOT_RUN -> when (result.failureStatus) {
            ProbeStatus.NO_SEARCH_URL, ProbeStatus.NO_EXPLORE_CATEGORY, ProbeStatus.ERROR -> 0
            else -> -1
        }
    }

    private fun fallbackFailurePenalty(status: ProbeStatus?): Int = when (status) {
        ProbeStatus.NETWORK, ProbeStatus.TIMEOUT -> 1
        ProbeStatus.HTTP_ERROR -> 2
        ProbeStatus.PARSE_EMPTY -> 2
        ProbeStatus.SKIPPED, ProbeStatus.UNSUPPORTED, ProbeStatus.NEED_JS, null, ProbeStatus.OK -> 0
        else -> 3
    }

    /** Negative means left is preferred; JSON field count and input order are deliberately last. */
    private fun compareQuality(left: CandidateQuality, right: CandidateQuality): Int {
        fun descending(a: Int, b: Int): Int = b.compareTo(a)
        descending(left.successfulCoreStages, right.successfulCoreStages).takeIf { it != 0 }?.let { return it }
        for (index in 0 until minOf(left.coreStageRanks.size, right.coreStageRanks.size)) {
            descending(left.coreStageRanks[index], right.coreStageRanks[index]).takeIf { it != 0 }?.let { return it }
        }
        left.failurePenalty.compareTo(right.failurePenalty).takeIf { it != 0 }?.let { return it }
        left.averageSuccessfulStageMs.compareTo(right.averageSuccessfulStageMs).takeIf { it != 0 }?.let { return it }
        descending(left.coreCapabilityCount, right.coreCapabilityCount).takeIf { it != 0 }?.let { return it }
        descending(left.auxiliaryCapabilityCount, right.auxiliaryCapabilityCount).takeIf { it != 0 }?.let { return it }
        descending(left.ruleCompleteness, right.ruleCompleteness).takeIf { it != 0 }?.let { return it }
        descending(left.topLevelFieldCount, right.topLevelFieldCount).takeIf { it != 0 }?.let { return it }
        return 0
    }

    private fun explainRecommendation(
        winner: CandidateQuality,
        runnerUp: CandidateQuality,
        winnerProbe: ProbeResult?,
        runnerUpProbe: ProbeResult?,
    ): String {
        if (winner.successfulCoreStages != runnerUp.successfulCoreStages) {
            return "核心功能成功阶段更多（" + winner.successfulCoreStages + "/4，对比 " +
                runnerUp.successfulCoreStages + "/4）。"
        }
        for (index in 0 until minOf(winner.coreStageRanks.size, runnerUp.coreStageRanks.size)) {
            if (winner.coreStageRanks[index] != runnerUp.coreStageRanks[index]) {
                val label = if (winnerProbe?.entry == ProbeEntry.EXPLORE && index == 0) {
                    "发现"
                } else {
                    CORE_STAGE_LABELS.getOrElse(index) { "核心功能" }
                }
                return label + "阶段的实际校验结果更好。"
            }
        }
        if (winner.failurePenalty != runnerUp.failurePenalty) {
            return "本次校验中的错误和失败情况较轻。"
        }
        if (winner.averageSuccessfulStageMs != runnerUp.averageSuccessfulStageMs) {
            return "核心阶段平均响应更快（" + latencyLabel(winner.averageSuccessfulStageMs) +
                "，对比 " + latencyLabel(runnerUp.averageSuccessfulStageMs) + "）。"
        }
        if (winner.coreCapabilityCount != runnerUp.coreCapabilityCount) {
            return "核心功能配置更完整。"
        }
        if (winner.auxiliaryCapabilityCount != runnerUp.auxiliaryCapabilityCount) {
            return "发现、段评等辅助能力配置更完整。"
        }
        if (winner.ruleCompleteness != runnerUp.ruleCompleteness) {
            return "非空行为配置项更多。"
        }
        if (winner.topLevelFieldCount != runnerUp.topLevelFieldCount) {
            return "前述因素相同，JSON 顶层业务字段数量更多（最后兜底因素）。"
        }
        if (winnerProbe?.status != runnerUpProbe?.status) {
            return "阶段结果相同，整体校验结论更好。"
        }
        return "各项质量因素相同，按原始导入顺序稳定保留。"
    }

    private fun latencyLabel(value: Long): String =
        if (value == Long.MAX_VALUE) "无可比较耗时" else value.toString() + "ms"

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
