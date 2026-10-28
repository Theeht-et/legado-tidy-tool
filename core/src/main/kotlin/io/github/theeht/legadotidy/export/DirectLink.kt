package io.github.theeht.legadotidy.export

import java.net.URLEncoder

/**
 * 生成 Legado 的「一键导入」直链。
 *
 * ## 格式从哪来的
 *
 * 上游 App 的 `OnLineImportActivity` 开头写着：
 *
 * ```
 * 网络一键导入
 * 格式: legado://import/{path}?src={url}
 * ```
 *
 * `path` 是导入类型，`auto` 表示让 App 自己认（书源 / 订阅 / 字典……都走它）。
 * 所以只要有一个**能公开访问到导出 JSON 的 URL**，就能生成一条手机扫码即可导入的链接。
 *
 * ## 为什么这个功能默认不开
 *
 * 本地文件导出与直链导入是两条互斥的路：
 * - 本地文件：把 JSON 交给用户，自己去 App 里「导入书源 ← 本地文件」。稳妥、无需网络。
 * - 直链：需要用户先把 JSON 放到一个手机能访问的地址（自己的网盘、图床、Gitee Pages……）。
 *   工具本身**不提供**这个地址（那需要公网服务或局域网监听，前者不在本项目能力范围，
 *   后者被“不做局域网 HTTP”这条硬约束排除了）。
 *
 * 所以直链是「你有地址，我帮你拼链接」而不是「我帮你发布」。界面上也只在你真的填了地址时
 * 才出现二维码——没地址时弹个二维码只会误导人。
 *
 * ## 编码细节
 * `src` 的值必须整段 URL-encode（包括 `:` 与 `/`），否则 `&`、`?`、`#` 会把参数截断。
 * 这里用 `URLEncoder.encode`，空格会变成 `+`；对 URL 参数来说合法，App 侧会正确还原。
 */
object DirectLink {

    /** 一键导入的 scheme 前缀。 */
    const val SCHEME_PREFIX = "legado://import/"

    /** 让 App 自动判断内容类型。 */
    const val KIND_AUTO = "auto"

    /**
     * 拼一条一键导入链接。
     *
     * @param sourceUrl 能被手机访问到的导出 JSON 地址（必须 http/https）
     * @param kind 导入类型，默认 `auto`
     * @return 例如 `legado://import/auto?src=https%3A%2F%2Fexample.com%2Fsources.json`
     * @throws IllegalArgumentException 当地址不是 http/https
     */
    fun forSourceUrl(sourceUrl: String, kind: String = KIND_AUTO): String {
        val trimmed = sourceUrl.trim()
        require(trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)) {
            "直链必须是 http(s) 地址，实际是：$sourceUrl"
        }
        require(trimmed.length > 8) { "直链地址太短：$sourceUrl" }
        val encoded = URLEncoder.encode(trimmed, Charsets.UTF_8)
        return "$SCHEME_PREFIX$kind?src=$encoded"
    }

    /**
     * 这个地址能不能用来做直链。
     *
     * 用户很容易把本地路径（`D:\书源.json`）或 `file://` 填进来——手机上根本访问不到，
     * 所以这里必须拦住，并且界面要把理由说清楚。
     */
    fun isUsable(sourceUrl: String?): Boolean {
        val v = sourceUrl?.trim().orEmpty()
        if (!v.startsWith("http://", ignoreCase = true) &&
            !v.startsWith("https://", ignoreCase = true)
        ) {
            return false
        }
        // 只要主机名或端口有一个能解析出东西，就算填了地址；具体能不能连通不归这里管。
        return v.substringAfter("://").substringBefore('/').isNotBlank()
    }
}
