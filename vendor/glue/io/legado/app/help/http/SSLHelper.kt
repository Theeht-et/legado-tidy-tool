package io.legado.app.help.http

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * headless 版 SSLHelper。
 *
 * upstream 里 `unsafeTrustManagerExtensions` 依赖 `android.net.http.X509TrustManagerExtensions`
 * （只有 Android 有）。这里只搬引擎真正用到的三个成员：
 * [unsafeSSLSocketFactory] / [unsafeTrustManager] / [unsafeHostnameVerifier]。
 *
 * 语义与 upstream 完全一致：**信任所有证书**。这不是偷懒 —— legado 书源里大量站点用
 * 自签名证书或证书链不完整，App 就是这么放行的；headless 若改成严格校验，
 * 会凭空多出一批「App 能抓、我们抓不到」的差异，让对比结论失真。
 *
 * 为什么用 "SSL" 而不是 "TLS"：与 upstream 保持一致（JVM 下 "SSL" 本身就会协商到
 * TLS，只是协议名不同，实际握手结果一样）。
 */
object SSLHelper {

    val unsafeTrustManager: X509TrustManager = object : X509TrustManager {

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            //do nothing，接受任意客户端证书
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            //do nothing，接受任意服务端证书
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> {
            return arrayOf()
        }
    }

    val unsafeSSLSocketFactory: SSLSocketFactory by lazy {
        try {
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, arrayOf(unsafeTrustManager), SecureRandom())
            sslContext.socketFactory
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    /**
     * 主机名验证全部放行 —— 与 upstream 一致（信任任意证书时主机名校验没有意义）。
     */
    val unsafeHostnameVerifier: HostnameVerifier = HostnameVerifier { _, _ -> true }
}
