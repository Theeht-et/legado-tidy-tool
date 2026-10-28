package io.legado.app.help.glide

import com.bumptech.glide.load.model.Headers

/**
 * 平台桩，逐字照抄上游同名文件（不含 Android 依赖，逻辑原样保留）。
 *
 * 上游 `help/glide/` 不在编译范围内，必须在此提供；否则
 * `AnalyzeUrl.getGlideUrl()` 里 `GlideUrl(url, GlideHeaders(headerMap))` 无法解析。
 * headless 下这条路径是给封面图加载器用的，永不调用。
 */
class GlideHeaders(private val headers: MutableMap<String, String>) : Headers {

    override fun getHeaders(): MutableMap<String, String> {
        return headers
    }

}
