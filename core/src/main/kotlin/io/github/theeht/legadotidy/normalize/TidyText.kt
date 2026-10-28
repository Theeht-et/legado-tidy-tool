package io.github.theeht.legadotidy.normalize

/**
 * 文本归一化。
 *
 * 这里有三种口径，用途不同，不要混用：
 * - [key]   ：用于「两个书源是不是同一个」的 **匹配键**，会把营销后缀、括号注释、emoji 都去掉；
 * - [display]：用于导出的 **显示名**，只做保守清洗，没有归一化；
 * - [ruleText]：用于规则指纹/相似度，去掉所有空白与不可见字符。
 */
object TidyText {

    /**
     * emoji 走 Unicode 类别，而不是手写代理对区间：Java 的 regex 按码点匹配，
     * `[\uD800-\uDBFF][\uDC00-\uDFFF]` 这种写法永远匹配不到——它只认「孤立代理」。
     * 这类 bug 编译期看不出来，只有拿真数据跑才会暴露。
     */
    private val EMOJI = Regex("[\\p{So}\\p{Sk}\\p{Cs}\\uFE0F\\u200D\\u20E3]")
    private val ZERO_WIDTH = Regex("[\\u200B-\\u200F\\uFEFF]")
    private val WHITESPACE = Regex("[\\s\\u00A0\\u3000]+")
    private val BRACKETED = Regex("[（(【\\[][^）)】\\]]*[）)】\\]]")
    private val TRAILING_SEP = Regex("\\s*[-_|｜·・—–]\\s*[^-_|｜·・—–]{1,12}$")
    private val REPEATED_WS = Regex("\\s{2,}")

    /** 全角 → 半角（仅 ASCII 区 + 全角空格）。 */
    fun halfWidth(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            sb.append(
                when {
                    ch == '\u3000' -> ' '
                    ch in '\uFF01'..'\uFF5E' -> (ch.code - 0xFEE0).toChar()
                    else -> ch
                }
            )
        }
        return sb.toString()
    }

    /** 匹配键：去 emoji / 零宽 / 括号注释 / 营销后缀 / 大小写 / 空白。 */
    fun key(raw: String): String {
        var s = halfWidth(raw)
        s = EMOJI.replace(s, "")
        s = ZERO_WIDTH.replace(s, "")
        s = BRACKETED.replace(s, "")
        s = TRAILING_SEP.replace(s, "")
        s = WHITESPACE.replace(s, "")
        return s.lowercase()
    }

    /** 显示名：去首尾空白、去零宽与 emoji 尾巴、限长。不做语义裁剪。 */
    fun display(raw: String, maxLength: Int = 40): String {
        var s = ZERO_WIDTH.replace(raw, "")
        s = REPEATED_WS.replace(s.trim(), " ")
        s = EMOJI.replace(s, "").trim()
        s = s.trim(' ', '-', '|', '·', '_')
        if (s.length > maxLength) s = s.take(maxLength).trim()
        return s.ifEmpty { raw.trim() }
    }

    /** 规则文本：仅去空白与不可见字符，保留标点（标点是规则语法的一部分）。 */
    fun ruleText(raw: String): String {
        val s = ZERO_WIDTH.replace(halfWidth(raw), "")
        return WHITESPACE.replace(s, "")
    }

    /** URL 归一化：协议与主机小写、去尾斜杠，路径大小写保留。 */
    fun url(raw: String): String {
        val s = raw.trim()
        if (s.isEmpty()) return ""
        val schemeEnd = s.indexOf("://")
        if (schemeEnd < 0) return s.trimEnd('/').lowercase()
        val scheme = s.substring(0, schemeEnd).lowercase()
        val rest = s.substring(schemeEnd + 3)
        val slash = rest.indexOf('/')
        val host = if (slash < 0) rest else rest.substring(0, slash)
        val path = if (slash < 0) "" else rest.substring(slash)
        return "$scheme://${host.lowercase()}$path".trimEnd('/')
    }

    /** 从 URL 取主机名（去端口）。 */
    fun host(raw: String): String {
        val s = url(raw)
        if (s.isEmpty()) return ""
        val start = s.indexOf("://").let { if (it < 0) 0 else it + 3 }
        var end = start
        while (end < s.length && s[end] != '/' && s[end] != '?') end++
        return s.substring(start, end).substringBefore(':')
    }
}
