package com.bumptech.glide.load.model

/**
 * 平台桩：Glide 的 `Headers` 接口。
 *
 * 引擎里只有 `io.legado.app.help.glide.GlideHeaders` 实现它，用途是把请求头交给
 * 封面图加载器（`AnalyzeUrl.getGlideUrl()`），headless 下永不调用。
 * 只保留上游 `GlideHeaders` 实际覆写的那一个方法。
 */
interface Headers {
    fun getHeaders(): Map<String, String>
}
