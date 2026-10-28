package io.github.theeht.legadotidy.dedup

import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.normalize.TidyText
import java.security.MessageDigest

/**
 * 行为指纹：把书源的「可取书行为」规范化后哈希。
 *
 * 与名称、分组、备注无关，所以「同一个站点的两个马甲源」会得到同一个指纹；
 * 反之「名字一样但规则不同」不会误判。
 */
object BehaviorFingerprint {

    fun canonical(source: BookSourceDoc): String {
        val fields = source.behaviorFields()
        val sb = StringBuilder(256)
        sb.append("v1\n")
        for ((k, v) in fields) {
            sb.append(k).append('=').append(TidyText.ruleText(v)).append('\n')
        }
        return sb.toString()
    }

    fun of(source: BookSourceDoc): String = sha256(canonical(source))

    fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(64)
        for (b in digest) sb.append("%02x".format(b))
        return sb.toString()
    }
}
