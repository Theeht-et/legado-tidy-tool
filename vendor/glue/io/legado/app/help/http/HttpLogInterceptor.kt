package io.legado.app.help.http

import okhttp3.Interceptor
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer

/**
 * HTTP 日志拦截器（与 upstream 同构）。
 *
 * 与 upstream 的差异：开关默认值。
 * upstream 默认取 `AppConfig.recordHttpLog`（其自身默认也是 false），
 * headless 版没有这个偏好设置，所以直接默认关闭 —— 行为与「App 未开启 HTTP 日志」一致。
 * 需要排查请求时，构造 [HttpLogInterceptor] 时传入 `enabled = { true }` 即可。
 */
class HttpLogInterceptor(
    private val enabled: () -> Boolean = { false },
    private val onRecord: (HttpLogRecord) -> Unit = HttpLogStore::add,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!enabled()) return chain.proceed(chain.request())

        val request = chain.request()
        val startTime = System.currentTimeMillis()
        val id = HttpLogStore.nextId()
        val requestHeaders = HttpLogSanitizer.formatHeaders(request.headers)
        val requestBody = captureRequestBody(request.body)
        val safeUrl = HttpLogSanitizer.redactUrl(request.url)

        val response = try {
            chain.proceed(request)
        } catch (throwable: Throwable) {
            kotlin.runCatching {
                onRecord(
                    HttpLogRecord(
                        id = id,
                        time = startTime,
                        method = request.method,
                        path = HttpLogSanitizer.redactPath(request.url),
                        url = safeUrl,
                        statusCode = -1,
                        duration = System.currentTimeMillis() - startTime,
                        requestHeaders = requestHeaders,
                        requestBody = requestBody,
                        responseHeaders = "",
                        responseBody = "",
                        error = throwable.javaClass.simpleName,
                    )
                )
            }
            throw throwable
        }

        kotlin.runCatching {
            onRecord(
                HttpLogRecord(
                    id = id,
                    time = startTime,
                    method = request.method,
                    path = HttpLogSanitizer.redactPath(request.url),
                    url = safeUrl,
                    statusCode = response.code,
                    duration = System.currentTimeMillis() - startTime,
                    requestHeaders = requestHeaders,
                    requestBody = requestBody,
                    responseHeaders = HttpLogSanitizer.formatHeaders(response.headers),
                    responseBody = captureResponseBody(response),
                    error = null,
                )
            )
        }
        return response
    }

    private fun captureRequestBody(body: RequestBody?): String {
        body ?: return ""
        if (body.isDuplex() || body.isOneShot()) return "[streaming body omitted]"
        return kotlin.runCatching {
            val contentLength = body.contentLength()
            if (contentLength < 0 || contentLength > MAX_BODY_BYTES) {
                return@runCatching "[request body omitted: $contentLength bytes]"
            }
            val buffer = Buffer()
            body.writeTo(buffer)
            val size = buffer.size
            val bytes = buffer.readByteArray(minOf(size, MAX_BODY_BYTES))
            HttpLogSanitizer.body(bytes, body.contentType(), truncated = false)
        }.getOrElse { "[request body unavailable]" }
    }

    private fun captureResponseBody(response: Response): String {
        return kotlin.runCatching {
            val contentLength = response.body.contentLength()
            val bytes = response.peekBody(MAX_BODY_BYTES).bytes()
            val truncated = contentLength > MAX_BODY_BYTES ||
                (contentLength < 0 && bytes.size.toLong() >= MAX_BODY_BYTES)
            HttpLogSanitizer.body(bytes, response.body.contentType(), truncated)
        }.getOrElse { "[response body unavailable]" }
    }

    private companion object {
        const val MAX_BODY_BYTES = 8_192L
    }
}
