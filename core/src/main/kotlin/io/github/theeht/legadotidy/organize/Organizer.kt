package io.github.theeht.legadotidy.organize

import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.normalize.TidyText
import io.github.theeht.legadotidy.probe.ProbeResult
import io.github.theeht.legadotidy.probe.ProbeStatus
import java.text.Collator
import java.util.Locale

data class OrganizeConfig(
    val maxNameLength: Int = 40,
    val unknownGroup: String = "未分组",
    /** 根据 [BookSourceDoc.type] 生成分类名前缀。 */
    val typeNames: Map<Int, String> = mapOf(0 to "文本", 1 to "音频", 2 to "图片", 3 to "文件"),
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
 * S3 整理：清洗名称、分类、排序。
 *
 * 只重排不删除；名称清洗也不会拿掉书名主体，只是去污染。
 */
object Organizer {

    fun run(
        sources: List<BookSourceDoc>,
        results: Map<Int, ProbeResult>,
        config: OrganizeConfig = OrganizeConfig(),
    ): OrganizeReport {
        val collator = Collator.getInstance(Locale.CHINA)

        val statusOf = sources.indices.associateWith { results[it]?.status ?: ProbeStatus.SKIPPED }
        val nameOf = sources.map { TidyText.display(it.name, config.maxNameLength) }
        val groupOf = sources.map { resolveGroup(it, config) }

        // 先按分类分组，组内按（状态, 名称, URL）排
        val byGroup = LinkedHashMap<String, MutableList<Int>>()
        sources.indices.forEach { i -> byGroup.getOrPut(groupOf[i]) { ArrayList() }.add(i) }

        val organized = ArrayList<OrganizedSource>(sources.size)
        val pools = ArrayList<Pool>(byGroup.size)
        for ((group, members) in byGroup) {
            members.sortWith { a, b ->
                val byStatus = statusOf.getValue(a).severity.compareTo(statusOf.getValue(b).severity)
                if (byStatus != 0) byStatus
                else {
                    val byName = collator.compare(nameOf[a], nameOf[b])
                    if (byName != 0) byName else sources[a].url.compareTo(sources[b].url)
                }
            }
            members.forEachIndexed { rank, i ->
                organized.add(OrganizedSource(i, nameOf[i], group, statusOf.getValue(i), rank))
            }
            pools.add(Pool(group, members.toList()))
        }
        // 返回顺序就是写入 customOrder 的顺序：按分类聚在一起、组内按 rank。
        // 不要再 sortBy(index)——那会把刚排好的顺序打回原形。
        return OrganizeReport(organized, pools)
    }

    /** 有用户分组就保留，没有就按类型给个中性分组名。 */
    fun resolveGroup(doc: BookSourceDoc, config: OrganizeConfig): String {
        val existing = doc.group.trim()
        if (existing.isNotEmpty()) return existing
        val typeName = config.typeNames[doc.type] ?: config.unknownGroup
        return typeName
    }
}
