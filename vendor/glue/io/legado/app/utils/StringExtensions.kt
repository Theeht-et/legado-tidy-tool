@file:Suppress("unused")

package io.legado.app.utils

import io.legado.app.constant.AppPattern
import io.legado.app.constant.AppPattern.dataUriRegex
// 上游 `CharSequence.toStringArray()` 逐字使用了这两个 java.lang.Character 静态方法，
// 追加该函数所需的 import；文件里已有的实现未做任何改动。
import java.lang.Character.codePointCount
import java.lang.Character.offsetByCodePoints

/**
 * headless 版：只保留引擎实际引用的字符串判定/切分函数。
 * 其余 upstream 实现依赖 android.net.Uri / android.text.Editable / android.icu.Collator 等，
 * headless 用不到，未搬。
 */

fun String?.isDataUrl() =
    this?.let {
        dataUriRegex.matches(it)
    } ?: false

fun String?.isAbsUrl() =
    this?.let {
        it.startsWith("http://", true) || it.startsWith("https://", true)
    } ?: false

fun String?.isJson(): Boolean =
    this?.run {
        val str = this.trim()
        when {
            str.startsWith("{") && str.endsWith("}") -> true
            str.startsWith("[") && str.endsWith("]") -> true
            else -> false
        }
    } ?: false

fun String?.isJsonObject(): Boolean =
    this?.run {
        val str = this.trim()
        str.startsWith("{") && str.endsWith("}")
    } ?: false

fun String?.isJsonArray(): Boolean =
    this?.run {
        val str = this.trim()
        str.startsWith("[") && str.endsWith("]")
    } ?: false

fun String?.isXml(): Boolean =
    this?.run {
        val str = this.trim()
        str.startsWith("<") && str.endsWith(">")
    } ?: false

fun String?.isTrue(nullIsTrue: Boolean = false): Boolean {
    if (this.isNullOrBlank() || this == "null") {
        return nullIsTrue
    }
    return !this.trim().matches("(?i)^(?:false|no|not|0|0.0)$".toRegex())
}

fun String.splitNotBlank(vararg delimiter: String, limit: Int = 0): Array<String> = run {
    this.split(*delimiter, limit = limit).map { it.trim() }.filterNot { it.isBlank() }
        .toTypedArray()
}

fun String.splitNotBlank(regex: Regex, limit: Int = 0): Array<String> = run {
    this.split(regex, limit).map { it.trim() }.filterNot { it.isBlank() }.toTypedArray()
}

/**
 * 是否全部由十六进制字符组成。
 *
 * 加密层 `SymmetricCryptoAndroid` 用它判断「传进来的是十六进制串还是原始字节」，
 * 判定错了会导致解密结果完全不同 —— 而且是**静默**的（不抛异常，只是内容变成乱码）。
 * 与 upstream `utils/StringExtensions.kt` 逐字一致。
 */
fun String.isHex(): Boolean {
    return all { c ->
        c in '0'..'9' || c in 'A'..'F' || c in 'a'..'f'
    }
}

/**
 * 将字符串拆分为单个字符,包含emoji
 */
fun CharSequence.toStringArray(): Array<String> {
    var codePointIndex = 0
    return try {
        Array(codePointCount(this, 0, length)) {
            val start = codePointIndex
            codePointIndex = offsetByCodePoints(this, start, 1)
            substring(start, codePointIndex)
        }
    } catch (e: Exception) {
        split("").toTypedArray()
    }
}

/**
 * 转义正则元字符。
 *
 * `ContentProcessor` 把章节标题拼进正则时用它防止标题里的元字符改变匹配语义，
 * 与 upstream `utils/StringExtensions.kt` 逐字一致。
 */
fun String.escapeRegex(): String {
    return replace(AppPattern.regexCharRegex, "\\\\$0")
}

/**
 * 把 JS 替换结果里出现的 `\` 翻倍。
 *
 * `Matcher.appendReplacement` 会把替换串里的 `\` 当成转义符，JS 结果可能带反斜杠，
 * 不翻倍会被吞掉。与 upstream `utils/StringExtensions.kt` 逐字一致。
 */
fun String.quoteReplacementJs(): String {
    if (!this.contains('\\')) {
        return this
    }
    val sb = StringBuilder()
    for (c in this) {
        if (c == '\\') {
            sb.append("\\\\")
        } else {
            sb.append(c)
        }
    }
    return sb.toString()
}
