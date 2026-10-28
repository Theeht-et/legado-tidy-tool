package io.legado.app.help.http

/**
 * 后台 WebView 的最小占位。
 *
 * headless **不实现** webView：upstream 用它去跑页面 JS、过 Cloudflare / 滑块这类
 * 需要真实浏览器环境的验证。JVM 进程里没有 WebView，硬做要么引入整条 Chromium，
 * 要么无法通过验证 —— 所以这里只保证**签名能编译**，调用即抛。
 *
 * 引擎里只有两处会走到：`AnalyzeUrl`（书源开了 webView 选项）和 `AnalyzeRule.getWebJsResult`
 * （结果里直接写 `@webjs`）。这些书源本就需要人工过验证，headless 下明确失败比静默给错结果好。
 *
 * 构造参数与 upstream 逐个对齐（引擎全部用具名参数调用），这样将来真要接入
 * 无头浏览器时，替换本文件即可，调用方零改动。
 */
@Suppress("unused", "UNUSED_PARAMETER")
class BackstageWebView(
    private val url: String? = null,
    private val html: String? = null,
    private val encode: String? = null,
    private val tag: String? = null,
    private val headerMap: HashMap<String, String>? = null,
    private val sourceRegex: String? = null,
    private val overrideUrlRegex: String? = null,
    private val javaScript: String? = null,
    private var delayTime: Long = 0,
    private val cacheFirst: Boolean = false,
    private val timeout: Long? = null,
    private val result: String? = null,
    private val isRule: Boolean = false
) {

    suspend fun getStrResponse(): StrResponse {
        throw UnsupportedOperationException(
            "headless 不支持 webView，无法执行该请求（url=$url, tag=$tag）"
        )
    }

}
