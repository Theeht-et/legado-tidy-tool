package io.legado.app.utils

import io.legado.app.constant.AppLog
import io.legado.app.model.analyzeRule.CustomUrl

/**
 * URL 工具。**只保留引擎/glue 实际用到的方法**：
 * `JsExtensions.downloadFile` 里的 `UrlUtil.getSuffix(url)`。
 *
 * 与上游的差异：
 * - 上游 `getFileName(...)` 依赖 `android.net.Uri` / `HttpURLConnection` HEAD 请求 /
 *   `AppConfig.recordLog` / `BuildConfig.DEBUG`；headless 没有这些调用点，故不搬。
 * - 上游的 `replaceReservedChar` 引擎未引用，也不搬。
 * - `getSuffix` 逻辑与上游逐行一致（`CustomUrl` 先剥掉 `,{attr}` 的 url option）。
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
object UrlUtil {

    private val fileSuffixRegex = Regex("^[a-z\\d]+$", RegexOption.IGNORE_CASE)

    /* 获取合法的文件后缀 */
    fun getSuffix(str: String, default: String? = null): String {
        val suffix = CustomUrl(str).getUrl()
            .substringAfterLast("/")
            .substringBefore("?")
            .substringBefore("#")
            .substringAfterLast(".", "")
        //检查截取的后缀字符是否合法 [a-zA-Z0-9]
        return if (suffix.length > 5 || !suffix.matches(fileSuffixRegex)) {
            if (default == null) {
                AppLog.put("Cannot find legal suffix:\n target: $str\n suffix: $suffix")
            }
            default ?: "ext"
        } else {
            suffix
        }
    }
}
