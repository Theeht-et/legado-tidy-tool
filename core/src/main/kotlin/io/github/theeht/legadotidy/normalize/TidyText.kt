package io.github.theeht.legadotidy.normalize

object TidyText {
    private val EMOJI = Regex("""[\p{So}\p{Sk}\p{Cs}\uFE0F\u200D\u20E3]""")
    private val ZERO_WIDTH = Regex("""[\u200B-\u200F\uFEFF]""")
    private val CIRCLED_NUMBERS = Regex("""[\u2460-\u2473\u24EA]""")
    private val WHITESPACE = Regex("""[\s\u00A0\u3000]+""")
    private val BRACKET_PAIRS = mapOf(
        '(' to ')', '（' to '）', '[' to ']', '［' to '］', '【' to '】',
        '{' to '}', '｛' to '｝', '<' to '>', '＜' to '＞',
        '〔' to '〕', '〖' to '〗', '〘' to '〙', '〚' to '〛',
        '❨' to '❩', '❪' to '❫', '❬' to '❭', '❮' to '❯',
        '❰' to '❱', '❲' to '❳', '❴' to '❵', '⟨' to '⟩',
        '⟪' to '⟫', '⦃' to '⦄', '⦅' to '⦆', '⦇' to '⦈',
        '⦉' to '⦊', '⦋' to '⦌', '⦍' to '⦐', '⦑' to '⦒',
    )
    private val BRACKET_CLOSERS = BRACKET_PAIRS.values.toSet()
    private val URL_PREFIX = Regex(
        """(?i)^\s*(https?://)?(?:www\.)?(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+(?:com|cn|net|org|edu|gov|io|co|me|tv|cc|top|xyz|vip|info|biz|site|online|live|app|club|work|pro|fun|store|tech|mobi)(?::\d{1,5})?(/[^\s<>"'，。；、]*)?\s*""",
    )
    private val URL_SUFFIX = Regex(
        """(?i)\s*(?:https?://)?(?:www\.)?(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+(?:com|cn|net|org|edu|gov|io|co|me|tv|cc|top|xyz|vip|info|biz|site|online|live|app|club|work|pro|fun|store|tech|mobi)(?::\d{1,5})?(?:/[^\s<>"'，。；、]*)?\s*$""",
    )
    private val TRAILING_SEP = Regex("""\s*[-_|｜·・—–]\s*[^-_|｜·・—–]{1,12}$""")
    private val REPEATED_WS = Regex("""\s{2,}""")
    private val AD_TERMS = "免广告|广告推广|官方推荐|精品源|高速源|优质源|稳定源|优[+＋]+|推荐|精品|高速|优质|稳定|广告|VIP|福利|会员专享|免费"
    private val AD_BRACKETED = Regex("""[【［\[（(][^】］\]）)]*(?:$AD_TERMS)[^】］\]）)]*[】］\]）)]""", RegexOption.IGNORE_CASE)
    private val AD_PREFIX = Regex("""^(?:$AD_TERMS)[：:_ -]*""", RegexOption.IGNORE_CASE)
    private val AD_SUFFIX = Regex("""[：:_ -]*(?:$AD_TERMS)$""", RegexOption.IGNORE_CASE)
    private val AD_STANDALONE = Regex("""\b(?:VIP|AD|ADS)\b""", RegexOption.IGNORE_CASE)
    private val DECORATIVE_MD_PREFIX = Regex("""(?i)^md(?=[\p{So}\p{Sk}\uFE0F\u200D]+[+＋])""")
    private val COOKIE_AFTER_DECORATION = Regex("""(?i)([\p{So}\p{Sk}\uFE0F\u200D]+)\s*cookie$""")
    private val HASH_SUFFIX = Regex("""[#＃][\s\S]*$""")
    private val DECORATIVE_SYMBOLS = Regex("""[★☆◆◇■□●○✦✧✪✿❖※♛♚♔♕♡♥❤⚡🔥✨🌟☀️☘️]+""")
    private val DECORATION_PREFIX = Regex("""^[\s|｜·•!！:：_=~～—–-]+""")
    private val DECORATION_SUFFIX = Regex("""[\s|｜·•!！:：_=~～—–-]+$""")
    private val REPEATED_NAME_PUNCTUATION = Regex("""[!！。．·•]{2,}""")
    private val REPEATED_DASHES = Regex("""[_~～—–-]{2,}""")
    private val EMPTY_BRACKETS = Regex("""[【［\[（(「『]+\s*[】］\]）)」』]+""")
    private val EDGE_NAME_PUNCTUATION = Regex("""^[\s,，。:：;；|｜]+|[\s,，。:：;；|｜]+$""")

    /** Removes complete matched bracket expressions, including nested and mixed pairs. */
    private fun removePairedBracketContents(raw: String): String {
        val output = StringBuilder(raw.length)
        var index = 0
        while (index < raw.length) {
            val opener = raw[index]
            if (opener !in BRACKET_PAIRS) {
                output.append(opener)
                index++
                continue
            }
            val stack = ArrayDeque<Char>()
            stack.addLast(BRACKET_PAIRS.getValue(opener))
            var cursor = index + 1
            var matchedEnd = -1
            while (cursor < raw.length && stack.isNotEmpty()) {
                val current = raw[cursor]
                if (current in BRACKET_PAIRS) stack.addLast(BRACKET_PAIRS.getValue(current))
                else if (current in BRACKET_CLOSERS && current == stack.last()) {
                    stack.removeLast()
                    if (stack.isEmpty()) matchedEnd = cursor
                }
                cursor++
            }
            if (matchedEnd >= 0) {
                output.append(' ')
                index = matchedEnd + 1
            } else {
                output.append(opener)
                index++
            }
        }
        return output.toString()
    }

    private fun removeLeadingUrl(raw: String): String {
        var value = raw
        while (true) {
            val match = URL_PREFIX.find(value) ?: return value
            val path = match.groups[2]?.value.orEmpty()
            val retained = path.trim('/').substringAfterLast('/').substringBefore('?').substringBefore('#')
            value = if (retained.isNotBlank()) "\${retained} " + value.substring(match.range.last + 1)
            else value.substring(match.range.last + 1)
            value = value.trimStart()
        }
    }

    private fun removeTrailingUrl(raw: String): String {
        var value = raw
        while (true) {
            val match = URL_SUFFIX.find(value) ?: return value
            value = value.substring(0, match.range.first).trimEnd()
        }
    }

    fun halfWidth(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) sb.append(when {
            ch == '\u3000' -> ' '
            ch in '\uFF01'..'\uFF5E' -> (ch.code - 0xFEE0).toChar()
            else -> ch
        })
        return sb.toString()
    }

    fun key(raw: String): String {
        var s = halfWidth(raw)
        s = EMOJI.replace(s, "")
        s = ZERO_WIDTH.replace(s, "")
        s = removePairedBracketContents(s)
        s = TRAILING_SEP.replace(s, "")
        s = WHITESPACE.replace(s, "")
        return s.lowercase()
    }

    fun display(raw: String, maxLength: Int = 40): String {
        var s = ZERO_WIDTH.replace(raw, "")
        s = REPEATED_WS.replace(s.trim(), " ")
        s = EMOJI.replace(s, "").trim()
        s = s.trim(' ', '-', '|', '·', '_')
        if (s.length > maxLength) s = s.take(maxLength).trim()
        return s.ifEmpty { raw.trim() }
    }

    fun cleanDecorations(raw: String): String {
        var s = ZERO_WIDTH.replace(raw, "")
        s = EMOJI.replace(s, " ")
        s = CIRCLED_NUMBERS.replace(s, "")
        s = DECORATIVE_SYMBOLS.replace(s, " ")
        s = DECORATION_PREFIX.replace(s, "")
        s = DECORATION_SUFFIX.replace(s, "")
        s = REPEATED_WS.replace(s, " ")
        return s.trim()
    }

    fun removeAdvertisingWords(raw: String): String {
        var s = ZERO_WIDTH.replace(raw, "").trim()
        s = removePairedBracketContents(s)
        s = removeLeadingUrl(s)
        s = removeTrailingUrl(s)
        s = HASH_SUFFIX.replace(s, "")
        s = DECORATIVE_MD_PREFIX.replace(s, "")
        while (true) {
            val previous = s
            s = AD_BRACKETED.replace(s, " ")
            s = AD_STANDALONE.replace(s, " ")
            s = COOKIE_AFTER_DECORATION.replace(s, "$1")
            s = AD_PREFIX.replace(s, "")
            s = AD_SUFFIX.replace(s, "")
            if (s == previous) break
        }
        s = EMPTY_BRACKETS.replace(s, " ")
        s = WHITESPACE.replace(s, " ")
        s = EDGE_NAME_PUNCTUATION.replace(s, "")
        return s.trim(' ', '-', '_', '+', '＋', '|', '｜', '·', '・', '—', '–', ':', '：')
    }

    fun normalizeNamePunctuation(raw: String): String {
        var s = ZERO_WIDTH.replace(raw, "")
        s = REPEATED_NAME_PUNCTUATION.replace(s, "")
        s = REPEATED_DASHES.replace(s, " ")
        s = EMPTY_BRACKETS.replace(s, "")
        s = WHITESPACE.replace(s, " ")
        s = EDGE_NAME_PUNCTUATION.replace(s, "").trim()
        return s.ifEmpty { raw.trim() }
    }

    fun ruleText(raw: String): String {
        val s = ZERO_WIDTH.replace(halfWidth(raw), "")
        return WHITESPACE.replace(s, "")
    }

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
        return "\${scheme}://\${host.lowercase()}\${path}".trimEnd('/')
    }

    fun host(raw: String): String {
        val s = url(raw)
        if (s.isEmpty()) return ""
        val start = s.indexOf("://").let { if (it < 0) 0 else it + 3 }
        var end = start
        while (end < s.length && s[end] != '/' && s[end] != '?') end++
        return s.substring(start, end).substringBefore(':')
    }
}
