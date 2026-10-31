package io.github.theeht.legadotidy.normalize

/**
 * 文本归一化。
 *
 * 这里有三种口径，用途不同，不要混用：
 * - [key]   ：用于「两个书源是不是同一个」的匹配键，会把营销后缀、括号注释、emoji 都去掉；
 * - [display]：传统流水线使用的显示名清洗；
 * - [ruleText]：用于规则指纹/相似度，去掉所有空白与不可见字符。
 */
object TidyText {

    private val EMOJI = Regex("[\\p{So}\\p{Sk}\\p{Cs}\\uFE0F\\u200D\\u20E3]")
    private val ZERO_WIDTH = Regex("[\\u200B-\\u200F\\uFEFF]")
    private val CIRCLED_NUMBERS = Regex("""[\u2460-\u2473\u24EA]""")
    private val WHITESPACE = Regex("[\\s\\u00A0\\u3000]+")
    private val BRACKETED = Regex("[（(【\\[][^）)】\\]]*[）)】\\]]")
    private val TRAILING_SEP = Regex("\\s*[-_|｜·・—–]\\s*[^-_|｜·・—–]{1,12}$")
    private val REPEATED_WS = Regex("\\s{2,}")
    private val AD_TERMS = "免广告|广告推广|官方推荐|精品源|高速源|优质源|稳定源|优[+＋]+|推荐|精品|高速|优质|稳定|广告|VIP|福利|会员专享|免费"
    private val AD_BRACKETED = Regex(
        "[【［\\[（(][^】］\\]）)]*(?:$AD_TERMS)[^】］\\]）)]*[】］\\]）)]",
        RegexOption.IGNORE_CASE,
    )
    private val AD_PREFIX = Regex("^(?:$AD_TERMS)[：:_ -]*", RegexOption.IGNORE_CASE)
    private val AD_SUFFIX = Regex("[：:_ -]*(?:$AD_TERMS)$", RegexOption.IGNORE_CASE)
    private val AD_STANDALONE = Regex("""\b(?:VIP|AD|ADS)\b""", RegexOption.IGNORE_CASE)
    // "MD" is only a marker when immediately followed by a decorative symbol.
    private val DECORATIVE_MD_PREFIX = Regex("""(?i)^md(?=[\p{So}\p{Sk}\uFE0F\u200D+＋])""")
    // Remove only a standalone cookie suffix; never strip arbitrary English substrings.
    private val COOKIE_SUFFIX = Regex("""(?i)(?<![A-Za-z0-9])cookie$""")
    private val HASH_SUFFIX = Regex("""[#＃][\s\S]*$""")
    private val DECORATIVE_SYMBOLS = Regex("[★☆◆◇■□●○✦✧✪✿❖※♛♚♔♕♡♥❤⚡🔥✨🌟☀️☘️]+")
    // Preserve paired brackets for the text-cleaning rule; trim only decorative edge separators.
    private val DECORATION_PREFIX = Regex("""^[\s|｜·•!！:：_=~～—–-]+""")
    private val DECORATION_SUFFIX = Regex("""[\s|｜·•!！:：_=~～—–-]+$""")
    private val REPEATED_NAME_PUNCTUATION = Regex("[!！。．·•]{2,}")
    private val REPEATED_DASHES = Regex("[_~～—–-]{2,}")
    private val EMPTY_BRACKETS = Regex("[【［\\[（(「『]+\\s*[】］\\]）)」』]+")
    private val EDGE_NAME_PUNCTUATION = Regex("^[\\s,，。:：;；|｜]+|[\\s,，。:：;；|｜]+$")

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

    /** 显示名：去首尾空白、去零宽与 emoji、限长。不做语义裁剪。 */
    fun display(raw: String, maxLength: Int = 40): String {
        var s = ZERO_WIDTH.replace(raw, "")
        s = REPEATED_WS.replace(s.trim(), " ")
        s = EMOJI.replace(s, "").trim()
        s = s.trim(' ', '-', '|', '·', '_')
        if (s.length > maxLength) s = s.take(maxLength).trim()
        return s.ifEmpty { raw.trim() }
    }

    /** 只移除 emoji、装饰符号和带圈序号；成对括号及文字留给“清理多余文字”处理。 */
    fun cleanDecorations(raw: String): String {
        var s = ZERO_WIDTH.replace(raw, "")
        s = EMOJI.replace(s, "")
        s = CIRCLED_NUMBERS.replace(s, "")
        s = DECORATIVE_SYMBOLS.replace(s, "")
        s = DECORATION_PREFIX.replace(s, "")
        s = DECORATION_SUFFIX.replace(s, "")
        s = REPEATED_WS.replace(s, " ")
        return s.trim()
    }

    fun removeAdvertisingWords(raw: String): String {
        // Trim edges before matching prefixes/suffixes; preserve internal spacing until all markers are removed.
        var s = ZERO_WIDTH.replace(raw, "").trim()
        // A hash suffix is metadata (author name, serial number, etc.), not part of the source name.
        s = HASH_SUFFIX.replace(s, "")
        // Remove "MD" only when it is immediately followed by a decorative marker.
        s = DECORATIVE_MD_PREFIX.replace(s, "")
        // A bracketed annotation is removed as one unit, including both brackets.
        while (true) {
            val previous = s
            s = AD_BRACKETED.replace(s, " ")
            s = AD_STANDALONE.replace(s, " ")
            s = COOKIE_SUFFIX.replace(s, " ")
            s = AD_PREFIX.replace(s, "")
            s = AD_SUFFIX.replace(s, "")
            if (s == previous) break
        }
        s = EMPTY_BRACKETS.replace(s, " ")
        s = WHITESPACE.replace(s, " ")
        s = EDGE_NAME_PUNCTUATION.replace(s, "")
        return s.trim(' ', '-', '_', '+', '＋', '|', '｜', '·', '・', '—', '–', ':', '：')
    }
    /** 清理已知宣传词、作者/编号井号尾缀和少量明确噪声，不删除普通英文站名。 */
    fun removeAdvertisingWords(raw: String): String {
        // Trim the edges before matching Chinese prefixes/suffixes; internal spacing is preserved.
        var s = ZERO_WIDTH.replace(raw, "").trim()
        // A bracketed advertising annotation is removed as one unit, including both brackets.
        while (true) {
            val previous = s
            s = AD_BRACKETED.replace(s, " ")
            s = AD_STANDALONE.replace(s, " ")
            s = AD_PREFIX.replace(s, "")
            s = AD_SUFFIX.replace(s, "")
            if (s == previous) break
        }
        s = EMPTY_BRACKETS.replace(s, " ")
        s = WHITESPACE.replace(s, " ")
        s = EDGE_NAME_PUNCTUATION.replace(s, "")
        return s.trim(' ', '-', '_', '|', '｜', '·', '・', '—', '–', ':', '：')
    }

    /** 按 v3 HTML 原型规范重复标点与空格，不改 URL 或书源规则。 */
    fun normalizeNamePunctuation(raw: String): String {
        var s = ZERO_WIDTH.replace(raw, "")
        s = REPEATED_NAME_PUNCTUATION.replace(s, "")
        s = REPEATED_DASHES.replace(s, " ")
        s = EMPTY_BRACKETS.replace(s, "")
        s = WHITESPACE.replace(s, " ")
        s = EDGE_NAME_PUNCTUATION.replace(s, "").trim()
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
