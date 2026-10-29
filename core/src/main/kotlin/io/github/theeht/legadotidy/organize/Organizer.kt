package io.github.theeht.legadotidy.organize

import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.normalize.TidyText
import io.github.theeht.legadotidy.probe.ProbeResult
import io.github.theeht.legadotidy.probe.ProbeStatus
import java.text.Collator
import java.util.Locale

/** UI 可显式选择的整理规则。 */
enum class OrganizeRule(val label: String) {
    CLEAN_DECORATIONS("清理图标与装饰"),
    REMOVE_ADVERTISING_WORDS("清理广告宣传词"),
    NORMALIZE_PUNCTUATION("规范标点与空格"),
    KEEP_ORIGINAL_GROUPS("保留原有分组"),
    REGROUP_BY_TYPE("按书源类型重新分组"),
    SORT_BY_RESPONSE_TIME("按响应时间升序"),
}

data class OrganizeConfig(
    val maxNameLength: Int = 40,
    val unknownGroup: String = "未分组",
    /** 根据 [BookSourceDoc.type] 生成分类名前缀。 */
    val typeNames: Map<Int, String> = mapOf(0 to "文本", 1 to "音频", 2 to "图片", 3 to "文件"),
    /**
     * null 表示旧版固定整理行为，保证 CLI 兼容；
     * 非 null 表示严格只执行集合内显式选择的规则，空集合也代表不做整理变换。
     */
    val selectedRules: Set<OrganizeRule>? = null,
)

data class OrganizedSource(
    val index: Int,
    val name: String,
    val group: String,
    val status: ProbeStatus,
    /** 分类内排名（从 0 开始），写回 `customOrder` 时 +1。 */
    val rank: Int,
)

data class Pool(val name: String, val members: List<Int>)

data class OrganizeReport(
    val sources: List<OrganizedSource>,
    val pools: List<Pool>,
)

/**
 * S3 整理：名称、分组与排序。
 *
 * GUI 可以传显式规则集合；CLI 不传规则时继续使用原有默认行为。
 * 整理阶段不删除源。
 */
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
        val nameOf = sources.map { doc ->
            if (!explicitRules) {
                TidyText.display(doc.name, config.maxNameLength)
            } else {
                var name = doc.name
                if (OrganizeRule.CLEAN_DECORATIONS in rules) {
                    name = TidyText.display(name, config.maxNameLength)
                }
                if (OrganizeRule.REMOVE_ADVERTISING_WORDS in rules) {
                    name = TidyText.removeAdvertisingWords(name)
                }
                if (OrganizeRule.NORMALIZE_PUNCTUATION in rules) {
                    name = TidyText.normalizeNamePunctuation(name)
                }
                name
            }
        }
        val groupOf = sources.map { doc ->
            if (!explicitRules) resolveGroup(doc, config)
            else if (OrganizeRule.REGROUP_BY_TYPE in rules) {
                config.typeNames[doc.type] ?: config.unknownGroup
            } else {
                // GUI 未勾选重新分组时严格保留原分组，不为未分组源偷偷填默认组。
                doc.group
            }
        }

        val byGroup = LinkedHashMap<String, MutableList<Int>>()
        sources.indices.forEach { i -> byGroup.getOrPut(groupOf[i]) { ArrayList() }.add(i) }

        val organized = ArrayList<OrganizedSource>(sources.size)
        val pools = ArrayList<Pool>(byGroup.size)
        for ((group, members) in byGroup) {
            members.sortWith { a, b ->
                if (!explicitRules) {
                    val byStatus = statusOf.getValue(a).severity.compareTo(statusOf.getValue(b).severity)
                    if (byStatus != 0) byStatus
                    else {
                        val byName = collator.compare(nameOf[a], nameOf[b])
                        if (byName != 0) byName else sources[a].url.compareTo(sources[b].url)
                    }
                } else if (OrganizeRule.SORT_BY_RESPONSE_TIME in rules) {
                    val elapsedA = results[a]?.takeIf { it.status != ProbeStatus.SKIPPED }?.elapsedMs ?: Long.MAX_VALUE
                    val elapsedB = results[b]?.takeIf { it.status != ProbeStatus.SKIPPED }?.elapsedMs ?: Long.MAX_VALUE
                    val byElapsed = elapsedA.compareTo(elapsedB)
                    if (byElapsed != 0) byElapsed else a.compareTo(b)
                } else {
                    // 未选排序规则时，不按状态、名称或耗时重新排。
                    a.compareTo(b)
                }
            }
            members.forEachIndexed { rank, i ->
                organized.add(OrganizedSource(i, nameOf[i], group, statusOf.getValue(i), rank))
            }
            pools.add(Pool(group, members.toList()))
        }
        return OrganizeReport(organized, pools)
    }

    /** 有用户分组就保留，没有就按类型给个中性分组名（旧版默认行为）。 */
    fun resolveGroup(doc: BookSourceDoc, config: OrganizeConfig): String {
        val existing = doc.group.trim()
        if (existing.isNotEmpty()) return existing
        val typeName = config.typeNames[doc.type] ?: config.unknownGroup
        return typeName
    }
}
