package io.github.theeht.legadotidy.model

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import java.nio.file.Path
import kotlin.io.path.readText

/** 解析 / 序列化书源 JSON。入参容错：数组、单个对象、或 `{ sources: [...] }` 包装。 */
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
        return array.mapNotNull { e -> e.takeIf { it.isJsonObject }?.asJsonObject }
    }

    fun load(path: Path): List<JsonObject> = parse(path.readText())

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
