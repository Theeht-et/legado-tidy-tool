package io.legado.app.exception

/**
 * 正则替换超时。照搬上游，签名一致。
 * `ContentProcessor` 用它把替换规则的匹配超时转成可识别的异常。
 */
class RegexTimeoutException(msg: String) : NoStackTraceException(msg)
