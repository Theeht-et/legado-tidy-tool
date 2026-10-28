package io.legado.app.help.http

import io.legado.app.constant.AppConst
import io.legado.app.help.CacheManager
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.CookieManager.cookieJarHeader
import io.legado.app.utils.NetworkUtils
import okhttp3.ConnectionSpec
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Credentials
import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * headless 版 OkHttpClient 工厂。
 *
 * ★ 与 upstream 的差异（务必逐条确认，任何一条都会改变书源的抓取结果）：
 *  1. **无 Cronet**：`Cronet.interceptor` 恒为 null，客户端不挂 Cronet。
 *     （等价于 App 未开 Cronet 选项。）
 *  2. **无 addressCache 自定义 DNS**：upstream 会读 `AppConfig.addressCache` 覆盖 DNS。
 *     headless 的 AppConfig 没这个偏好项；书源的 `dnsIp` 由 AnalyzeUrl 自己通过
 *     `buildRequestClient` 注入作用域 DNS 解决，所以这里不再需要。
 *  3. **无 `okHttpClientManga`**：那是漫画/音频进度条（ProgressResponseBody）+ 限速用的，
 *     依赖 Android Glide/ExoPlayer，headless 直接不提供。引擎不引用它。
 *  4. SSL 证书策略不变：信任所有证书（[SSLHelper]）。
 *
 * 其余全部逐字照搬：超时、连接池（默认）、重定向、UA 注入、cookie 网络拦截器、解压、日志。
 */

private val proxyClientCache: ConcurrentHashMap<ProxyConfig, OkHttpClient> = ConcurrentHashMap()

val cookieJar by lazy {
    object : CookieJar {

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return emptyList()
        }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            if (cookies.isEmpty()) return
            //临时保存 书源启用cookie选项再添加到数据库
            val cookieBuilder = StringBuilder()
            cookies.forEachIndexed { index, cookie ->
                if (index > 0) cookieBuilder.append(";")
                cookieBuilder.append(cookie.name).append('=').append(cookie.value)
            }
            val domain = NetworkUtils.getSubDomain(url.toString())
            CacheManager.putMemory("${domain}_cookieJar", cookieBuilder.toString())
        }

    }
}

/**
 * 全局出网代理。
 *
 * ## 为什么需要它
 * 引擎的代理入口是**每个书源 header 里的 `proxy` 键**（见 `AnalyzeUrl`），
 * 而 headless 里没人往那里写 —— 所以默认是**全直连**。实测在"只有代理能通"的
 * 站点上，直连一律超时，占了失败数的相当一部分（本机抽样：15% 的站点属于这类）。
 *
 * 不往书源里注入的原因：`header` 常常是 **JS 脚本**（`@js:` 返回 map），
 * 往字符串里塞 JSON 会把脚本弄坏。所以改在 OkHttp 层面挂一个全局代理，一处生效。
 *
 * ## 取值源（按优先级）
 *  1. `-Dlegado.proxy=...`
 *  2. 环境变量 `LEGADO_PROXY`
 *  3. 没设 → 不挂代理（保持上游直连行为）
 *
 * 格式：`http://host:port` / `socks5://host:port`（协议缺省按 http 算）。
 * **故意不读 `http_proxy`/`https_proxy`** —— 那两个是 shell 的约定，
 * Java 的 ProxySelector 不理会它们；把它们接进来会让「谁在生效」变得难以预测。
 */
private val globalProxy: Proxy? by lazy {
    val spec = System.getProperty("legado.proxy")?.trim()
        ?: System.getenv("LEGADO_PROXY")?.trim()
    if (spec.isNullOrEmpty()) return@lazy null
    runCatching {
        val withScheme = if ("://" in spec) spec else "http://$spec"
        val uri = java.net.URI(withScheme)
        val host = uri.host ?: error("代理地址缺少主机名：$spec")
        val port = if (uri.port > 0) uri.port else when (uri.scheme?.lowercase()) {
            "socks", "socks5" -> 1080
            else -> 80
        }
        val type = if (uri.scheme?.lowercase()?.startsWith("socks") == true) {
            Proxy.Type.SOCKS
        } else {
            Proxy.Type.HTTP
        }
        Proxy(type, InetSocketAddress(host, port))
    }.getOrElse {
        System.err.println("[legado] 代理配置无法解析，已按直连处理：$spec （${it.message}）")
        null
    }
}

val okHttpClient: OkHttpClient by lazy {
    val specs = arrayListOf(
        ConnectionSpec.MODERN_TLS,
        ConnectionSpec.COMPATIBLE_TLS,
        ConnectionSpec.CLEARTEXT
    )

    val builder = OkHttpClient.Builder()
        // ★★ 必须显式设置 Dispatcher —— 这是整个工具吞吐量的真正天花板。
        //
        // 引擎发请求走的是 `Call.await()`（见 OkHttpUtils.kt），它用的是 `enqueue()`
        // 即**异步**模式。异步模式下 OkHttp 的 `Dispatcher` 会限制同时在飞的请求数，
        // 默认 **maxRequests = 64、maxRequestsPerHost = 5**。
        //
        // 后果（实测）：不管把书源并发调到 64 / 500 / 1000 / 2000，
        // **同一时刻最多只有 64 个 HTTP 请求真正发出去**，其余全在 OkHttp 队列里等。
        // 实测证据：并发设 200 时 socket 只有 ~85，并发设 1000 时也只有 ~106。
        //
        // 更致命的是**排队时间算在调用方超时里** —— `SourceRunner` 的
        // `withTimeout(stepTimeoutMs)` 包着整个 `await()`。
        // 1000 并发、每源 3 步 = 3000 个请求挤 64 个槽，队尾要等几十秒，
        // 于是 5 秒超时下几乎全灭（实测 2916/3087 超时）。
        //
        // 取值 512：高到**不会成为瓶颈**（书源并发才是真正的旋钮），
        // 又保留一个安全阀 —— 有人把并发打到 2000 时不会真的同时开 2000 个连接。
        //
        // maxRequestsPerHost = 16：这个才是防打爆的地方。实测 2407 个源对应
        // 1369 个域名（平均 1.8 源/域名），所以 16 对正常站点绰绰有余；
        // 真到了「同一个域名被无数源指向」的批量镜像站，它会把节奏限住，
        // 那正是我们想要的行为（否则会被风控）。
        .dispatcher(
            Dispatcher().apply {
                maxRequests = 512
                maxRequestsPerHost = 16
            }
        )
        // 全局代理（未配置则不动，保持直连）
        .apply { globalProxy?.let { proxy(it) } }
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        //.cookieJar(cookieJar = cookieJar)
        .sslSocketFactory(SSLHelper.unsafeSSLSocketFactory, SSLHelper.unsafeTrustManager)
        .retryOnConnectionFailure(true)
        .hostnameVerifier(SSLHelper.unsafeHostnameVerifier)
        .connectionSpecs(specs)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(OkHttpExceptionInterceptor)
        .addInterceptor { chain ->
            val request = chain.request()
            val builder = request.newBuilder()
            if (request.header(AppConst.UA_NAME) == null) {
                builder.addHeader(AppConst.UA_NAME, AppConfig.userAgent)
            } else if (request.header(AppConst.UA_NAME) == "null") {
                builder.removeHeader(AppConst.UA_NAME)
            }
            builder.addHeader("Keep-Alive", "300")
            builder.addHeader("Connection", "Keep-Alive")
            builder.addHeader("Cache-Control", "no-cache")
            chain.proceed(builder.build())
        }
        .addNetworkInterceptor { chain ->
            var request = chain.request()
            val enableCookieJar = request.header(cookieJarHeader) != null

            if (enableCookieJar) {
                val requestBuilder = request.newBuilder()
                requestBuilder.removeHeader(cookieJarHeader)
                request = CookieManager.loadRequest(requestBuilder.build())
            }

            val networkResponse = chain.proceed(request)

            if (enableCookieJar) {
                CookieManager.saveResponse(networkResponse)
            }
            networkResponse
        }
    builder.addInterceptor(HttpLogInterceptor())
    // headless 无 Cronet，这里不插 Cronet 拦截器
    builder.addInterceptor(DecompressInterceptor)
    builder.build().apply {
        val okHttpName =
            OkHttpClient::class.java.name.removePrefix("okhttp3.").removeSuffix("Client")
        val executor = dispatcher.executorService as ThreadPoolExecutor
        val threadName = "$okHttpName Dispatcher"
        executor.threadFactory = ThreadFactory { runnable ->
            Thread(runnable, threadName).apply {
                isDaemon = false
                uncaughtExceptionHandler = OkhttpUncaughtExceptionHandler
            }
        }
    }
}

/**
 * 缓存代理okHttp
 */
fun getProxyClient(proxy: String? = null): OkHttpClient {
    if (proxy.isNullOrBlank()) {
        return okHttpClient
    }
    val proxyConfig = parseProxyConfig(proxy)
    proxyClientCache[proxyConfig]?.let {
        return it
    }
    val builder = okHttpClient.newBuilder()
    if (proxyConfig.protocol == ProxyProtocol.SOCKS5 && proxyConfig.credentials != null) {
        builder
            .proxy(Proxy.NO_PROXY)
            .dns(socks5ProxyDns)
            .socketFactory(
                Socks5SocketFactory(
                    proxyConfig.host,
                    proxyConfig.port,
                    proxyConfig.credentials,
                )
            )
    } else {
        builder.proxy(
            Proxy(
                proxyConfig.protocol.proxyType,
                InetSocketAddress(proxyConfig.host, proxyConfig.port),
            )
        )
    }
    proxyConfig.credentials?.takeIf { proxyConfig.protocol == ProxyProtocol.HTTP }
        ?.let { credentials ->
            builder.proxyAuthenticator { _, response ->
                val challengeCount = response.consecutiveProxyChallengeCount()
                val hasAuthorization = response.request.header("Proxy-Authorization") != null
                if (!shouldRetryProxyAuthentication(response.code, hasAuthorization, challengeCount)) {
                    return@proxyAuthenticator null
                }
                response.request.newBuilder()
                    .header(
                        "Proxy-Authorization",
                        Credentials.basic(credentials.username, credentials.password),
                    )
                    .build()
            }
        }
    val proxyClient = builder.build()
    return proxyClientCache.putIfAbsent(proxyConfig, proxyClient) ?: proxyClient
}

private fun okhttp3.Response.consecutiveProxyChallengeCount(): Int {
    var count = 0
    var current: okhttp3.Response? = this
    while (current?.code == 407) {
        count++
        current = current.priorResponse
    }
    return count
}
