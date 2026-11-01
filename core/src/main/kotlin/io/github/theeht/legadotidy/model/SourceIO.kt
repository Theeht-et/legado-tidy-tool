package io.github.theeht.legadotidy.model

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import java.nio.file.Path
import kotlin.io.path.readText

enum class ImportIssueSeverity { ERROR, WARNING }

data class SourceImportIssue(
    val field: String,
    val severity: ImportIssueSeverity,
    val message: String,
)

/** 解析 / 序列化书源 JSON。入参容错：数组、单个对象、或包装对象。 */
object SourceIO {

    private val prettyGson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val compactGson = GsonBuilder().disableHtmlEscaping().create()

    fun parse(text: String): List<JsonObject> {
        val root: JsonElement = try {
            JsonParser.parseString(text)
        } catch (e: JsonSyntaxException) {
            throw IllegalArgumentException("不是合法 JSON：${e.message}", e)
        }
        val array = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject -> {
                val obj = root.asJsonObject
                when {
                    obj.get("sources")?.isJsonArray == true -> obj.getAsJsonArray("sources")
                    obj.get("bookSources")?.isJsonArray == true -> obj.getAsJsonArray("bookSources")
                    else -> JsonArray().apply { add(obj) }
                }
            }
            else -> throw IllegalArgumentException("顶层必须是数组或对象")
        }
        require(array.all { it.isJsonObject }) {
            "书源数组中包含非对象元素；为避免静默丢弃数据，已拒绝导入"
        }
        return array.map { it.asJsonObject }
    }

    fun load(path: Path): List<JsonObject> = parse(path.readText())

    /**
     * Inspect one imported record without silently discarding it.
     * bookSourceUrl is required for source identity and request base URL.
     * A blank name is only a warning because the upstream entity defaults it to empty.
     */
    fun validateSource(source: JsonObject): List<SourceImportIssue> = buildList {
        val url = source.get(SourceField.URL)
        if (url == null || url.isJsonNull || !url.isJsonPrimitive ||
            !url.asJsonPrimitive.isString || url.asString.isBlank()
        ) {
            add(
                SourceImportIssue(
                    field = SourceField.URL,
                    severity = ImportIssueSeverity.ERROR,
                    message = "缺少有效的书源地址；该记录不会发起网络校验，也不能参与 URL 去重。",
                ),
            )
        }

        val name = source.get(SourceField.NAME)
        if (name == null || name.isJsonNull || !name.isJsonPrimitive ||
            !name.asJsonPrimitive.isString || name.asString.isBlank()
        ) {
            add(
                SourceImportIssue(
                    field = SourceField.NAME,
                    severity = ImportIssueSeverity.WARNING,
                    message = "书源名称为空，建议在整理前补充。",
                ),
            )
        }

        SourceField.RULE_GROUPS.forEach { field ->
            val rule = source.get(field) ?: return@forEach
            if (rule.isJsonNull) return@forEach
            val validObject = rule.isJsonObject || (
                rule.isJsonPrimitive && rule.asJsonPrimitive.isString &&
                    runCatching { JsonParser.parseString(rule.asString).isJsonObject }.getOrDefault(false)
                )
            if (!validObject) {
                add(
                    SourceImportIssue(
                        field = field,
                        severity = ImportIssueSeverity.ERROR,
                        message = "规则组必须是 JSON 对象或 JSON 对象字符串。",
                    ),
                )
            }
        }
    }

    fun toJson(sources: List<JsonObject>, pretty: Boolean = true): String {
        val array = JsonArray()
        sources.forEach(array::add)
        return (if (pretty) prettyGson else compactGson).toJson(array)
    }
}

/** JSON 对象工具：按路径取字符串（支持 `a.b.c`）。 */
internal fun JsonObject.path(vararg keys: String): String {
    var cur: JsonElement = this
    for (k in keys) {
        if (!cur.isJsonObject) return ""
        cur = cur.asJsonObject.get(k) ?: return ""
    }
    return if (cur.isJsonPrimitive) cur.asString else ""
}
