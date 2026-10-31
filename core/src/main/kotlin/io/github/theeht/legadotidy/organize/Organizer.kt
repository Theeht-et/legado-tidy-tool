package io.github.theeht.legadotidy.organize

import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.normalize.TidyText
import io.github.theeht.legadotidy.probe.ProbeResult
import io.github.theeht.legadotidy.probe.ProbeStatus
import java.text.Collator
import java.util.Locale

/** UI 可显式选择的整理规则。 */
enum class OrganizeRule(val label: String) {
    CLEAN_DECORATIONS("清理各类emoji/符号"),
    REMOVE_ADVERTISING_WORDS("清理多余文字"),
    NORMALIZE_PUNCTUATION("规范标点与空格"),
    KEEP_ORIGINAL_GROUPS("保留原有分组"),
    DROP_ORIGINAL_GROUPS("不保留原有分组"),
    NO_SMART_GROUPING("不启用智能分组"),
    REGROUP_BY_TYPE("按书源类型分组"),
    REGROUP_BY_QUALITY("按书源质量分组"),
    SORT_BY_RESPONSE_TIME("按响应时间升序"),
}

enum class GroupingMode {
    KEEP_ORIGINAL,
    NO_SMART_GROUPING,
    BY_TYPE,
    BY_QUALITY,
}

data class OrganizeConfig(
    val maxNameLength: Int = 40,
    val unknownGroup: String = "未分组",
    /** Legado bookSourceType: 0 文本、1 音频、2 图片、3 文件、4 视频。 */
    val typeNames: Map<Int, String> = mapOf(0 to "文本", 1 to "音频", 2 to "图片", 3 to "文件", 4 to "视频"),
    /**
     * null 表示旧版固定整理行为，保证 CLI 兼容；
     * 非 null 表示严格只执行集合内显式选择的规则。
     */
    val selectedRules: Set<OrganizeRule>? = null,
    /** 非 null 时使用新的独立分组策略；null 时兼容旧规则集合。 */
    val groupingMode: GroupingMode? = null,
)

data class QualityAssessment(
    val score: Int,
    val grade: String,
    val reasons: List<String>,
)

data class OrganizedSource(
    val index: Int,
    val name: String,
    val group: String,
    val status: ProbeStatus,
    /** 分类内排名（从 0 开始），写回 customOrder 时 +1。 */
    val rank: Int,
    val quality: QualityAssessment? = null,
)

data class Pool(val name: String, val members: List<Int>)

data class OrganizeReport(
    val sources: List<OrganizedSource>,
    val pools: List<Pool>,
)

/** S3 整理：名称、分组与排序。整理阶段不删除源。 */
object Organizer {
    fun run(
        sources: List<BookSourceDoc>,
        results: Map<Int, ProbeResult>,
        config: OrganizeConfig = OrganizeConfig(),
    ): OrganizeReport {
        val collator = Collator.getInstance(Locale.CHINA)
        val selected = config.selectedRules
        val rules = selected.orEmpty()
        val explicitRules = selected != null

        val statusOf = sources.indices.associateWith { results[it]?.status ?: ProbeStatus.SKIPPED }
        val qualityOf = sources.indices.associateWith { index ->
            assessQuality(sources[index], results[index])
        }
        val nameOf = sources.map { doc ->
            if (!explicitRules) {
                TidyText.display(doc.name, config.maxNameLength)
            } else {
                var name = doc.name
                if (OrganizeRule.CLEAN_DECORATIONS in rules &&
                    OrganizeRule.REMOVE_ADVERTISING_WORDS in rules
                ) name = TidyText.removeAdvertisingWords(name)
                if (OrganizeRule.CLEAN_DECORATIONS in rules) name = TidyText.cleanDecorations(name)
                if (OrganizeRule.REMOVE_ADVERTISING_WORDS in rules) name = TidyText.removeAdvertisingWords(name)
                if (OrganizeRule.NORMALIZE_PUNCTUATION in rules) name = TidyText.normalizeNamePunctuation(name)
                name
            }
        }

        val groupingMode = config.groupingMode ?: when {
            OrganizeRule.REGROUP_BY_QUALITY in rules -> GroupingMode.BY_QUALITY
            OrganizeRule.REGROUP_BY_TYPE in rules -> GroupingMode.BY_TYPE
            OrganizeRule.DROP_ORIGINAL_GROUPS in rules -> GroupingMode.NO_SMART_GROUPING
            else -> GroupingMode.KEEP_ORIGINAL
        }
        val groupOf = sources.mapIndexed { index, doc ->
            if (!explicitRules) resolveGroup(doc, config)
            else when (groupingMode) {
                GroupingMode.KEEP_ORIGINAL -> doc.group
                GroupingMode.NO_SMART_GROUPING -> config.unknownGroup
                GroupingMode.BY_TYPE -> config.typeNames[doc.type] ?: config.unknownGroup
                GroupingMode.BY_QUALITY -> qualityOf.getValue(index).grade
            }
        }

        val byGroup = LinkedHashMap<String, MutableList<Int>>()
        sources.indices.forEach { i -> byGroup.getOrPut(groupOf[i]) { ArrayList() }.add(i) }

        if (explicitRules && OrganizeRule.SORT_BY_RESPONSE_TIME in rules) {
            val byElapsed = compareBy<Int> { index ->
                results[index]?.takeIf { it.elapsedMs > 0 }?.elapsedMs ?: Long.MAX_VALUE
            }.thenBy { it }
            val rankByIndex = HashMap<Int, Int>(sources.size)
            val pools = byGroup.map { (group, members) ->
                val orderedMembers = members.sortedWith(byElapsed)
                orderedMembers.forEachIndexed { rank, index -> rankByIndex[index] = rank }
                Pool(group, orderedMembers)
            }
            val orderedIndexes = sources.indices.sortedWith(byElapsed)
            val organized = orderedIndexes.map { index ->
                OrganizedSource(index, nameOf[index], groupOf[index], statusOf.getValue(index),
                    rankByIndex.getValue(index), qualityOf[index])
            }
            return OrganizeReport(organized, pools)
        }

        val organized = ArrayList<OrganizedSource>(sources.size)
        val pools = ArrayList<Pool>(byGroup.size)
        for ((group, members) in byGroup) {
            members.sortWith { a, b ->
                if (!explicitRules) {
                    val byStatus = statusOf.getValue(a).severity.compareTo(statusOf.getValue(b).severity)
                    if (byStatus != 0) byStatus else {
                        val byName = collator.compare(nameOf[a], nameOf[b])
                        if (byName != 0) byName else sources[a].url.compareTo(sources[b].url)
                    }
                } else a.compareTo(b)
            }
            members.forEachIndexed { rank, index ->
                organized.add(OrganizedSource(index, nameOf[index], group, statusOf.getValue(index),
                    rank, qualityOf[index]))
            }
            pools.add(Pool(group, members.toList()))
        }
        return OrganizeReport(organized, pools)
    }

    /**
     * 评分权重：校验可用性 50%、响应速度 30%、规则完整度 20%。
     * 不可测指标不记为失败，而是从分母剔除后按其余可用指标归一化。
     */
    fun assessQuality(doc: BookSourceDoc, probe: ProbeResult?): QualityAssessment {
        var weighted = 0.0
        var availableWeight = 0
        val reasons = mutableListOf<String>()

        val status = probe?.status
        if (status != null && status !in setOf(
                ProbeStatus.SKIPPED, ProbeStatus.NO_SEARCH_URL, ProbeStatus.UNSUPPORTED,
            )
        ) {
            val availability = if (status == ProbeStatus.OK) 100 else 0
            weighted += availability * 50
            availableWeight += 50
            reasons += if (availability == 100) "校验可用" else "校验未通过（${status.label}）"
        }

        val elapsed = probe?.elapsedMs?.takeIf { it > 0 }
        if (elapsed != null) {
            val speed = when {
                elapsed <= 1_000 -> 100
                elapsed <= 3_000 -> 80
                elapsed <= 5_000 -> 60
                elapsed <= 10_000 -> 40
                else -> 20
            }
            weighted += speed * 30
            availableWeight += 30
            reasons += "响应 ${elapsed}ms"
        }

        val completeness = (doc.ruleCompleteness * 10).coerceIn(0, 100)
        weighted += completeness * 20
        availableWeight += 20
        reasons += "已配置字段 ${doc.ruleCompleteness} 项"

        val score = if (availableWeight == 0) 0 else (weighted / availableWeight).toInt().coerceIn(0, 100)
        val grade = when {
            score >= 85 -> "优质"
            score >= 70 -> "良好"
            score >= 50 -> "普通"
            else -> "合格"
        }
        return QualityAssessment(score, grade, reasons)
    }

    /** 旧版 CLI 默认分组行为保持不变。 */
    fun resolveGroup(doc: BookSourceDoc, config: OrganizeConfig): String {
        val existing = doc.group.trim()
        if (existing.isNotEmpty()) return existing
        return config.typeNames[doc.type] ?: config.unknownGroup
    }
}
