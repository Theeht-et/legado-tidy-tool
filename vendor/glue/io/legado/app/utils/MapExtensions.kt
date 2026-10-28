package io.legado.app.utils

/**
 * headless 版：只保留实际被引用的三个函数。
 *  - `has` / 大小写不敏感 `get`：AnalyzeUrl 读 header、BaseSource 填 header 时用
 *  - `getOrPutLimit`：AnalyzeRule 用它缓存规则解析结果
 */

fun HashMap<String, *>.has(key: String, ignoreCase: Boolean = false): Boolean {
    for (item in this) {
        if (key.equals(item.key, ignoreCase)) {
            return true
        }
    }
    return false
}

fun <T> HashMap<String, T>.get(key: String, ignoreCase: Boolean = false): T? {
    for (item in this) {
        if (key.equals(item.key, ignoreCase)) {
            return item.value
        }
    }
    return null
}

inline fun <K, V> MutableMap<K, V>.getOrPutLimit(key: K, maxSize: Int, defaultValue: () -> V): V {
    var value = get(key)
    if (containsKey(key)) {
        @Suppress("UNCHECKED_CAST")
        return value as V
    }
    value = defaultValue()
    if (size < maxSize) {
        put(key, value)
    }
    return value
}
