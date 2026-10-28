package io.legado.app.help.http

import okhttp3.Interceptor

/**
 * headless 版 Cronet 空壳。
 *
 * upstream 用 Cronet（Chromium 网络栈）加速请求，依赖 Android + 编译好的 native 库，
 * headless 下**不实现**：所有请求都走 okhttp。
 *
 * 保留引擎实际引用的 [interceptor]（AnalyzeUrl / AnalyzeUrlNetworkOptions 会读取它，
 * 用来在「当前请求超时/重定向/dnsIp 被覆盖」时把它从 client 里摘掉）。
 * headless 下恒为 null —— 读到的行为等同于「没装 Cronet」，与未开启该选项的 App 一致。
 */
object Cronet {

    interface LoaderInterface {

        fun install(): Boolean

        fun preDownload()

    }

    val loader: LoaderInterface? = null

    fun preDownload() {
        // headless 无 Cronet，无需预下载
    }

    val interceptor: Interceptor? = null

}
