package io.github.theeht.legadotidy.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** 读取 JSON 字符串字段：缺失 / null / 非标量一律给空串。 */
internal fun JsonObject?.str(key: String): String {
    val e: JsonElement = this?.get(key) ?: return ""
    if (e.isJsonNull || !e.isJsonPrimitive) return ""
    return e.asString.trim()
}

/** 读取布尔字段：缺失按 [fallback]；字符串 "true"/"false" 也认。 */
internal fun JsonObject?.bool(key: String, fallback: Boolean = false): Boolean {
    val e: JsonElement = this?.get(key) ?: return fallback
    if (e.isJsonNull || !e.isJsonPrimitive) return fallback
    return e.asJsonPrimitive.let { if (it.isBoolean) it.asBoolean else it.asString.equals("true", true) }
}

/** 读取整数字段：缺失 / 非数字按 [fallback]。 */
internal fun JsonObject?.int(key: String, fallback: Int = 0): Int {
    val e: JsonElement = this?.get(key) ?: return fallback
    if (e.isJsonNull || !e.isJsonPrimitive) return fallback
    return e.asJsonPrimitive.let { p -> if (p.isNumber) p.asInt else p.asString.toIntOrNull() ?: fallback }
}

internal fun JsonObject?.obj(key: String): JsonObject? {
    val e: JsonElement = this?.get(key) ?: return null
    return if (e.isJsonObject) e.asJsonObject else null
}
