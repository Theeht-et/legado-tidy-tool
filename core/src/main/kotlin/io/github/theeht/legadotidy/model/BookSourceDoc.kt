package io.github.theeht.legadotidy.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Legado 上游 BookSource 的顶层字段名。
 *
 * 字段清单对应仓库固定的上游引擎版本；读取和导出仍以原始 JSON 为准，
 * 因为不同版本及不同书源可能只使用其中一部分，也可能带有更新版本的字段。
 */
object SourceField {
    const val URL = "bookSourceUrl"
    const val NAME = "bookSourceName"
    const val GROUP = "bookSourceGroup"
    const val TYPE = "bookSourceType"
    const val BOOK_URL_PATTERN = "bookUrlPattern"
    const val CUSTOM_ORDER = "customOrder"
    const val ENABLED = "enabled"
    const val ENABLED_EXPLORE = "enabledExplore"
    const val JS_LIB = "jsLib"
    const val ENABLED_COOKIE_JAR = "enabledCookieJar"
    const val CONCURRENT_RATE = "concurrentRate"
    const val HEADER = "header"
    const val LOGIN_URL = "loginUrl"
    const val LOGIN_UI = "loginUi"
    const val LOGIN_CHECK_JS = "loginCheckJs"
    const val COVER_DECODE_JS = "coverDecodeJs"
    const val COMMENT = "bookSourceComment"
    const val VARIABLE_COMMENT = "variableComment"
    const val LAST_UPDATE_TIME = "lastUpdateTime"
    const val RESPOND_TIME = "respondTime"
    const val WEIGHT = "weight"
    const val EXPLORE_URL = "exploreUrl"
    const val EXPLORE_SCREEN = "exploreScreen"
    const val SEARCH_URL = "searchUrl"
    const val RULE_SEARCH = "ruleSearch"
    const val RULE_EXPLORE = "ruleExplore"
    const val RULE_BOOK_INFO = "ruleBookInfo"
    const val RULE_TOC = "ruleToc"
    const val RULE_CONTENT = "ruleContent"
    const val RULE_REVIEW = "ruleReview"
    const val MAIN_JS = "mainJs"
    const val EVENT_LISTENER = "eventListener"
    const val CUSTOM_BUTTON = "customButton"

    /** 上游定义的规则组；每组内部字段按实际 JSON 动态读取，不假设字段齐全。 */
    val RULE_GROUPS: List<String> = listOf(
        RULE_SEARCH, RULE_EXPLORE, RULE_BOOK_INFO, RULE_TOC, RULE_CONTENT, RULE_REVIEW,
    )
}

/** 一个规则块（如 `ruleSearch`）的只读视图。 */
class RuleGroup internal constructor(private val raw: JsonObject?) {
    operator fun get(field: String): String {
        val value = raw?.get(field) ?: return ""
        if (value.isJsonNull) return ""
        return if (value.isJsonPrimitive) value.asString else value.toString()
    }

    /** 返回实际存在的字段，而不是假设每个书源都填满上游实体的所有属性。 */
    fun fields(): Map<String, String> = raw?.entrySet()
        ?.associateTo(LinkedHashMap()) { (key, value) ->
            key to if (value.isJsonNull) "" else if (value.isJsonPrimitive) value.asString else value.toString()
        } ?: emptyMap()

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
    val mainJs: String = raw.str(SourceField.MAIN_JS)

    val ruleSearch: RuleGroup = ruleGroup(SourceField.RULE_SEARCH)
    val ruleExplore: RuleGroup = ruleGroup(SourceField.RULE_EXPLORE)
    val ruleBookInfo: RuleGroup = ruleGroup(SourceField.RULE_BOOK_INFO)
    val ruleToc: RuleGroup = ruleGroup(SourceField.RULE_TOC)
    val ruleContent: RuleGroup = ruleGroup(SourceField.RULE_CONTENT)
    val ruleReview: RuleGroup = ruleGroup(SourceField.RULE_REVIEW)

    private fun ruleGroup(key: String): RuleGroup {
        val element = raw.get(key) ?: return RuleGroup(null)
        val objectValue = when {
            element.isJsonObject -> element.asJsonObject
            // Upstream rule deserializers accept either an object or a JSON-object string.
            element.isJsonPrimitive && element.asJsonPrimitive.isString -> runCatching {
                JsonParser.parseString(element.asString).takeIf { it.isJsonObject }?.asJsonObject
            }.getOrNull()
            else -> null
        }
        return RuleGroup(objectValue)
    }

    /** 能否按常规规则发起搜索；JS 书源由上游 mainJs 流程处理。 */
    val canSearch: Boolean
        get() = mainJs.isNotEmpty() || (searchUrl.isNotEmpty() && ruleSearch["bookList"].isNotEmpty())

    /** 是否包含上游规则组或 JS 主脚本。可选规则组缺失不视为格式错误。 */
    val hasAnyRule: Boolean
        get() = mainJs.isNotEmpty() || SourceField.RULE_GROUPS.any { !ruleGroup(it).isEmpty() }

    /**
     * 行为字段用于去重指纹与相似度。
     *
     * 规则组字段按 JSON 中实际出现的键动态收集，覆盖上游新增字段和 ruleReview；
     * 同时纳入会改变抓取行为的顶层配置。展示属性、启用状态、排序和统计时间不参与。
     */
    fun behaviorFields(): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        listOf(
            SourceField.SEARCH_URL,
            SourceField.EXPLORE_URL,
            SourceField.LOGIN_URL,
            SourceField.HEADER,
            SourceField.BOOK_URL_PATTERN,
            SourceField.JS_LIB,
            SourceField.LOGIN_CHECK_JS,
            SourceField.COVER_DECODE_JS,
            SourceField.MAIN_JS,
        ).forEach { key ->
            val value = raw.str(key)
            if (value.isNotEmpty()) m[key] = value
        }

        for (groupName in SourceField.RULE_GROUPS) {
            ruleGroup(groupName).fields().forEach { (field, value) ->
                if (value.isNotEmpty()) m["$groupName.$field"] = value
            }
        }
        return m
    }

    /** 规则完整度：实际存在的非空行为字段数，不依赖规则组是否填满。 */
    val ruleCompleteness: Int get() = behaviorFields().size

    override fun toString(): String = "BookSourceDoc(${name.ifEmpty { "<无名>" }} @ ${url})"
}
