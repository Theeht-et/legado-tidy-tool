package io.legado.app.utils

import android.text.TextUtils
import org.jsoup.Jsoup
import java.io.File
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlin.math.abs

/**
 * 自动获取文本/文件的编码。
 *
 * ## 为什么这里必须真实实现
 * 本机实测有大量书源是 **HTTP 声明编码与实际字节不符** 的站点（GBK / GB18030 / BIG5）。
 * 编码猜错不会抛异常，只会让正文整段变成乱码，最终表现为“这个源质量差”的错误结论。
 * 所以这里的策略、顺序、回退值都必须可解释。
 *
 * ## 与上游（upstream/io/legado/app/utils/EncodingDetect.kt）的差异
 * - 上游 `getEncode(bytes)` 依赖内置的 ICU4J 移植版 `io.legado.app.lib.icu4j.CharsetDetector`
 *   （统计式 n-gram 识别器，覆盖 20+ 种单/多字节编码）。那批 java 文件位于 Android 工程内、
 *   不在引擎 sourceSet 里，且属于第三方 ICU 代码，headless 不引入。
 * - 本实现改用 JDK 自带 `CharsetDecoder` + `MalformedInput`（严格模式）计数：
 *   对候选编码逐个试解码，统计“成功解码字符数 / 非法字节数”得到 `validRatio`，
 *   再用“解码结果中汉字占比”得到 `hanRatio`，取 `validRatio * hanRatio` 最高者。
 * - 因此**差异点**（保真度边界）：
 *   1. UTF-8 / BOM 判定与上游等价（UTF-8 严格解码通过即 UTF-8；带 BOM 直接命名）。
 *   2. GBK / GB2312 与上游一样统一命名 **GB18030**（上游的 gb_18030 识别器即覆盖 GBK/GB2312）。
 *   3. 向上游靠拢的候选优先级：GB18030 > Big5 > EUC-JP > Shift_JIS > EUC-KR > windows-1251 > ISO-8859-1；
 *      当两个候选分数相差不超过 [PRIORITY_EPSILON] 时取优先级更高者（用于 Big5 与 Shift_JIS 的近似误判）。
 *   4. **非中文单字节编码**（ISO-8859-x / windows-125x / KOI8 等）本实现不做区分：
 *      若所有候选都解不出任何汉字特征（`hanRatio == 0`），直接回退 `UTF-8`。
 *      上游会给出具体名字。本工具的输入是中文书源，此处按“宁可回退 UTF-8 也不乱标”处理。
 *   5. 极短文本 / 大量标记的文本，统计信息不足，Big5 与 GB18030 可能互相误判（上游同样不保证）。
 * - `getHtmlEncode` 的 meta 解析顺序与上游逐字一致；上游在没有 `<head>` 时用 `!!` 触发 NPE 走回退，
 *   这里改为判空后同样回退到 `getEncode`，结果等价。
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
object EncodingDetect {

    private val headTagRegex = "(?i)<head>[\\s\\S]*?</head>".toRegex()
    private val headOpenBytes = "<head>".toByteArray()
    private val headCloseBytes = "</head>".toByteArray()

    /** 候选编码，顺序即优先级（越小越优先）。 */
    private val candidateCharsets = arrayOf(
        "GB18030", "Big5", "EUC-JP", "Shift_JIS", "EUC-KR", "windows-1251", "ISO-8859-1"
    )

    /** 分数差在此范围内视为“并列”，取优先级更高者。用于压住 Big5 被 Shift_JIS 抢判。 */
    private const val PRIORITY_EPSILON = 0.08

    fun getHtmlEncode(bytes: ByteArray): String {
        try {
            var head: String? = null
            val startIndex = bytes.indexOfPattern(headOpenBytes)
            if (startIndex > -1) {
                val endIndex = bytes.indexOfPattern(headCloseBytes, startIndex)
                if (endIndex > -1) {
                    head = String(bytes.copyOfRange(startIndex, endIndex + headCloseBytes.size))
                }
            }
            val headHtml = head ?: headTagRegex.find(String(bytes))?.value
            if (headHtml != null) {
                val doc = Jsoup.parseBodyFragment(headHtml)
                val metaTags = doc.getElementsByTag("meta")
                for (metaTag in metaTags) {
                    var charsetStr = metaTag.attr("charset")
                    if (!TextUtils.isEmpty(charsetStr)) {
                        return charsetStr
                    }
                    val httpEquiv = metaTag.attr("http-equiv")
                    if (httpEquiv.equals("content-type", true)) {
                        val content = metaTag.attr("content")
                        val idx = content.indexOf("charset=", ignoreCase = true)
                        charsetStr = if (idx > -1) {
                            content.substring(idx + "charset=".length)
                        } else {
                            content.substringAfter(";")
                        }
                        if (!TextUtils.isEmpty(charsetStr)) {
                            return charsetStr
                        }
                    }
                }
            }
        } catch (ignored: Exception) {
        }
        return getEncode(bytes)
    }

    fun getEncode(bytes: ByteArray): String {
        if (bytes.isEmpty()) {
            return "UTF-8"
        }
        bomCharset(bytes)?.let { return it }
        // 绝大多数现代站点：严格 UTF-8 能过就直接判定，绝不进入下面的启发式。
        if (isStrictlyDecodable(bytes, "UTF-8")) {
            return "UTF-8"
        }
        var bestScore = -1.0
        var bestIndex = -1
        for (i in candidateCharsets.indices) {
            val score = score(bytes, candidateCharsets[i])
            when {
                bestIndex == -1 -> {
                    bestScore = score; bestIndex = i
                }
                score > bestScore + PRIORITY_EPSILON -> {
                    bestScore = score; bestIndex = i
                }
                abs(score - bestScore) <= PRIORITY_EPSILON && i < bestIndex -> {
                    bestScore = maxOf(bestScore, score); bestIndex = i
                }
            }
        }
        // 没有任何中文特征：与上游 detect() 返回 null 时一致，回退 UTF-8。
        return if (bestIndex == -1 || bestScore <= 0.0) "UTF-8" else candidateCharsets[bestIndex]
    }

    /**
     * 得到文件的编码。
     */
    fun getEncode(filePath: String): String {
        return getEncode(File(filePath))
    }

    /**
     * 得到文件的编码。
     */
    fun getEncode(file: File): String {
        val tempByte = getFileBytes(file)
        if (tempByte.isEmpty()) {
            return "UTF-8"
        }
        return getEncode(tempByte)
    }

    /**
     * 与上游逐字一致的取样：只保留高字节（把 ASCII 覆盖掉），最多 8000 个。
     * 汉字/全角符号的判定几乎不受 ASCII 影响，这样能提高统计信噪比。
     */
    private fun getFileBytes(file: File): ByteArray {
        val byteArray = ByteArray(8000)
        var pos = 0
        try {
            file.inputStream().buffered().use {
                while (pos < byteArray.size) {
                    val n = it.read(byteArray, pos, 1)
                    if (n == -1) {
                        break
                    }
                    if (byteArray[pos] < 0) {
                        pos++
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("Error: $e")
        }
        return byteArray.copyOf(pos)
    }

    private fun bomCharset(bytes: ByteArray): String? = when {
        bytes.size >= 4 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() &&
            bytes[2] == 0x00.toByte() && bytes[3] == 0x00.toByte() -> "UTF-32LE"

        bytes.size >= 4 && bytes[0] == 0x00.toByte() && bytes[1] == 0x00.toByte() &&
            bytes[2] == 0xFE.toByte() && bytes[3] == 0xFF.toByte() -> "UTF-32BE"

        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte() -> "UTF-8"

        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> "UTF-16LE"

        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> "UTF-16BE"

        else -> null
    }

    private fun isStrictlyDecodable(bytes: ByteArray, charsetName: String): Boolean {
        return try {
            Charset.forName(charsetName).newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 严格解码并对非法字节计数，返回 `validRatio * hanRatio`。
     * 非法字节不会中断统计：跳过该字节后继续，允许页面上存在零星的坏字节。
     */
    private fun score(bytes: ByteArray, charsetName: String): Double {
        val charset = runCatching { Charset.forName(charsetName) }.getOrNull() ?: return -1.0
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val input = ByteBuffer.wrap(bytes)
        val output = CharBuffer.allocate(8192)
        val decoded = StringBuilder()
        var malformed = 0
        while (true) {
            val result = decoder.decode(input, output, true)
            output.flip()
            decoded.append(output)
            output.clear()
            when {
                result.isOverflow -> continue
                result.isUnderflow -> break
                result.isError -> {
                    val step = if (result.length() > 0) result.length() else 1
                    malformed += step
                    decoder.reset()
                    input.position(input.position() + step)
                }
                else -> break
            }
        }
        var nonAscii = 0
        var han = 0
        for (c in decoded) {
            if (c.code >= 0x80) {
                nonAscii++
                if (isHan(c)) {
                    han++
                }
            }
        }
        val validChars = decoded.length
        if (validChars == 0 && malformed == 0) {
            return 0.0
        }
        val validRatio = validChars.toDouble() / (validChars + malformed)
        val hanRatio = if (nonAscii == 0) 0.0 else han.toDouble() / nonAscii
        return validRatio * hanRatio
    }

    /** 汉字以及中文正文里常见的符号/全角区。刻意排除假名与谚文，避免 CJK 误判互相“刷分”。 */
    private fun isHan(c: Char): Boolean {
        val code = c.code
        return code in 0x4E00..0x9FFF ||
            code in 0x3400..0x4DBF ||
            code in 0xF900..0xFAFF ||
            code in 0x3000..0x303F ||
            code in 0xFF00..0xFFEF
    }

    /** 上游 `ByteArrayExtensions.indexOf(pattern, start, stop)`；这里内联，避免新增公共符号。 */
    private fun ByteArray.indexOfPattern(pattern: ByteArray, start: Int = 0): Int {
        if (pattern.isEmpty()) {
            return -1
        }
        for (i in start..this.size - pattern.size) {
            var matched = true
            for (j in pattern.indices) {
                if (this[i + j] != pattern[j]) {
                    matched = false
                    break
                }
            }
            if (matched) {
                return i
            }
        }
        return -1
    }
}
