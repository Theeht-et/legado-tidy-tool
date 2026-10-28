package io.legado.app.help.http

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.source
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * 透明解压。
 *
 * 与 upstream 的差异：**不支持 br（Brotli）**。
 * upstream 会主动声明 `Accept-Encoding: gzip, deflate, br` 并引入 `org.brotli:dec` 解压。
 * headless 不允许新增依赖，所以：
 *   · 不声明 br → 服务器不会返回 br，不会出现「拿到 br 却解不开」；
 *   · 只手动处理 gzip / deflate。
 * 代价：少数**无视 Accept-Encoding、强行回 br** 的站点会拿到未解压的二进制。
 * 这类站点极少，且真出现时错误是显式的（正文解析失败），不会静默给出错误结论。
 */
internal const val TRANSPARENT_ACCEPT_ENCODING = "gzip, deflate"

internal fun Request.canUseTransparentDecompression(): Boolean {
    return header("Accept-Encoding") == null && header("Range") == null
}

internal fun decompressResponse(response: Response): Response {
    val body = response.body
    if (body === ResponseBody.EMPTY) {
        return response
    }

    // 我们自己设置了 Accept-Encoding，okhttp 的透明 gzip 就被关掉了，必须在这里手动解。
    val source = try {
        when (response.header("Content-Encoding")?.lowercase()) {
            "gzip" -> GZIPInputStream(body.byteStream()).source().buffer()
            "deflate" -> InflaterInputStream(body.byteStream(), Inflater(true)).source().buffer()
            else -> return response
        }
    } catch (throwable: Throwable) {
        try {
            body.close()
        } catch (closeThrowable: Throwable) {
            throwable.addSuppressed(closeThrowable)
        }
        throw throwable
    }

    return response.newBuilder()
        .removeHeader("Content-Encoding")
        .removeHeader("Content-Length")
        .body(source.asResponseBody(body.contentType(), -1))
        .build()
}

object DecompressInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestBuilder = request.newBuilder()

        val transparentDecompress = request.canUseTransparentDecompression()
        if (transparentDecompress) {
            requestBuilder.header("Accept-Encoding", TRANSPARENT_ACCEPT_ENCODING)
        }

        val response = chain.proceed(requestBuilder.build())
        return if (transparentDecompress) decompressResponse(response) else response
    }
}
