package com.bumptech.glide.load.model

/**
 * 平台桩。引擎里只有 `AnalyzeUrl.getGlideUrl()` 用到，那是给封面图加载器用的，
 * headless 下不会被调用。保留签名只为让引擎文件零修改通过编译。
 */
class GlideUrl(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
) {
    /** 上游 `GlideUrl(url, headers: Headers)` 的形态，`AnalyzeUrl.getGlideUrl()` 走这条。 */
    constructor(url: String, headers: Headers) : this(url, headers.getHeaders())
}
