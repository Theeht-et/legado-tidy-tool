package io.legado.app.exception

/**
 * 并发限制。照搬上游 —— 它的 `waitTime` 被 ConcurrentRateLimiter 用来决定等多久重试。
 */
class ConcurrentException(msg: String, val waitTime: Long) : NoStackTraceException(msg)
