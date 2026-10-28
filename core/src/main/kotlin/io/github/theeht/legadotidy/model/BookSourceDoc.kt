package io.github.theeht.legadotidy.model

import com.google.gson.JsonObject

/** Legado 的书源字段名（与上游 `BookSource` 实体一致）。 */
object SourceField {
    const val URL = "bookSourceUrl"
    const val NAME = "bookSourceName"
    const val GROUP = "bookSourceGroup"
    const val TYPE = "bookSourceType"
    const val COMMENT = "bookSourceComment"
    const val ENABLED = "enabled"

    const val SEARCH_URL = "searchUrl"
    const val LOGIN_URL = "loginUrl"
    const val HEADER = "header"
    const val EXPLORE_URL = "exploreUrl"

    const val RULE_SEARCH = "ruleSearch"
    const val RULE_EXPLORE = "ruleExplore"
    const val RULE_BOOK_INFO = "ruleBookInfo"
    const val RULE_TOC = "ruleToc"
    const val RULE_CONTENT = "ruleContent"

    const val WEIGHT = "weight"
    const val CUSTOM_ORDER = "customOrder"
}

/** 一个规则块（如 `ruleSearch`）的只读视图。 */
class RuleGroup internal constructor(private val raw: JsonObject?) {
    operator fun get(field: String): String = raw.str(field)
    fun isEmpty(): Boolean = raw == null || raw.size() == 0
}

/**
 * 书源文档：包住原始 [JsonObject]，不改动未知字段。
 *
 * 刻意不做实体反序列化：书源字段随上游版本增减，任何未知字段都必须原样回到 JSON 里。
 */
class BookSourceDoc(val raw: JsonObject) {
    val url: String = raw.str(SourceField.URL)
    val name: String = raw.str(SourceField.NAME)
    val group: String = raw.str(SourceField.GROUP)
    val type: Int = raw.int(SourceField.TYPE)
    val enabled: Boolean = raw.bool(SourceField.ENABLED, fallback = true)
    val comment: String = raw.str(SourceField.COMMENT)

    val searchUrl: String = raw.str(SourceField.SEARCH_URL)
    val loginUrl: String = raw.str(SourceField.LOGIN_URL)
    val header: String = raw.str(SourceField.HEADER)
    val exploreUrl: String = raw.str(SourceField.EXPLORE_URL)

    val ruleSearch: RuleGroup = RuleGroup(raw.obj(SourceField.RULE_SEARCH))
    val ruleExplore: RuleGroup = RuleGroup(raw.obj(SourceField.RULE_EXPLORE))
    val ruleBookInfo: RuleGroup = RuleGroup(raw.obj(SourceField.RULE_BOOK_INFO))
    val ruleToc: RuleGroup = RuleGroup(raw.obj(SourceField.RULE_TOC))
    val ruleContent: RuleGroup = RuleGroup(raw.obj(SourceField.RULE_CONTENT))

    /** 能否发起搜索：`searchUrl` 非空且 `ruleSearch.bookList` 非空。 */
    val canSearch: Boolean get() = searchUrl.isNotEmpty() && ruleSearch["bookList"].isNotEmpty()

    /** 没有任何规则块的书源，基本是坏数据。 */
    val hasAnyRule: Boolean
        get() = !(ruleSearch.isEmpty() && ruleExplore.isEmpty() && ruleBookInfo.isEmpty() &&
                ruleToc.isEmpty() && ruleContent.isEmpty())

    /**
     * 行为字段：决定「这个书源能不能爬到同一本书」的字段集合，不含名称/分组/注释等展示属性。
     * 指纹与相似度都以此为准，保证「同一份定义」只有一个口径。
     */
    fun behaviorFields(): Map<String, String> {
        val m = LinkedHashMap<String, String>(32)
        m["searchUrl"] = searchUrl
        m["ruleSearch.bookList"] = ruleSearch["bookList"]
        m["ruleSearch.name"] = ruleSearch["name"]
        m["ruleSearch.author"] = ruleSearch["author"]
        m["ruleSearch.kind"] = ruleSearch["kind"]
        m["ruleSearch.lastChapter"] = ruleSearch["lastChapter"]
        m["ruleSearch.coverUrl"] = ruleSearch["coverUrl"]
        m["ruleSearch.bookUrl"] = ruleSearch["bookUrl"]
        m["ruleBookInfo.init"] = ruleBookInfo["init"]
        m["ruleBookInfo.name"] = ruleBookInfo["name"]
        m["ruleBookInfo.author"] = ruleBookInfo["author"]
        m["ruleBookInfo.tocUrl"] = ruleBookInfo["tocUrl"]
        m["ruleToc.chapterList"] = ruleToc["chapterList"]
        m["ruleToc.chapterName"] = ruleToc["chapterName"]
        m["ruleToc.chapterUrl"] = ruleToc["chapterUrl"]
        m["ruleToc.nextTocUrl"] = ruleToc["nextTocUrl"]
        m["ruleContent.content"] = ruleContent["content"]
        m["ruleContent.nextContentUrl"] = ruleContent["nextContentUrl"]
        m["ruleContent.webJs"] = ruleContent["webJs"]
        m["ruleContent.sourceRegex"] = ruleContent["sourceRegex"]
        m["ruleContent.replaceRegex"] = ruleContent["replaceRegex"]
        return m.filterValues { it.isNotEmpty() }
    }

    /** 规则完整度：非空行为字段个数，用于同组内选「保留哪个」。 */
    val ruleCompleteness: Int get() = behaviorFields().size

    override fun toString(): String = "BookSourceDoc(${name.ifEmpty { "<无名>" }} @ ${url})"
}
