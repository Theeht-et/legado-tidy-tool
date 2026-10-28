package android.util

import java.util.Base64 as JBase64

/**
 * 平台桩：`android.util.Base64.decode(str, flags)`。
 *
 * ⚠️ 这是本项目最容易「静默出错」的桩之一，务必留意语义差异：
 *  - Android 的 DEFAULT 解码器**容忍换行与空白**（data URI 的 base64 常被折行），
 *    JDK 的 `getDecoder()` 会直接抛异常。所以这里先剥掉空白。
 *  - 同时兼容 URL-safe 变体（`-` `_`），Android 侧靠 URL_SAFE 标志区分，
 *    但书源里几乎没人传标志，容错比严格更接近实际表现。
 *  - 失败时**不抛异常**，返回空数组 —— 与 Android 行为一致，让上层走兜底分支。
 */
object Base64 {
    const val DEFAULT: Int = 0
    const val NO_PADDING: Int = 1
    const val NO_WRAP: Int = 2
    const val CRLF: Int = 4
    const val URL_SAFE: Int = 8

    @JvmStatic
    fun decode(str: String, flags: Int): ByteArray {
        val cleaned = str.filterNot { it.isWhitespace() }
        if (cleaned.isEmpty()) return ByteArray(0)
        return try {
            if (cleaned.any { it == '-' || it == '_' }) {
                JBase64.getUrlDecoder().decode(cleaned)
            } else {
                JBase64.getDecoder().decode(cleaned)
            }
        } catch (_: IllegalArgumentException) {
            ByteArray(0)
        }
    }

    @JvmStatic
    fun decode(str: String): ByteArray = decode(str, DEFAULT)

    @JvmStatic
    fun encodeToString(input: ByteArray, flags: Int): String =
        if (flags and URL_SAFE != 0) {
            JBase64.getUrlEncoder().withoutPadding().encodeToString(input)
        } else {
            JBase64.getEncoder().encodeToString(input)
        }
}
