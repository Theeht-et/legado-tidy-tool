package io.legado.app.data.entities

import android.text.TextUtils
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.exception.NoStackTraceException
import io.legado.app.utils.splitNotBlank
import splitties.init.appCtx
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

data class ReplaceRule(
    var id: Long = System.currentTimeMillis(),
    //名称
    var name: String = "",
    //分组
    var group: String? = null,
    //替换内容
    var pattern: String = "",
    //替换为
    var replacement: String = "",
    //作用范围
    var scope: String? = null,
    //作用于标题
    var scopeTitle: Boolean = false,
    //作用于书源
    var scopeSource: Boolean = false,
    //作用于正文
    var scopeContent: Boolean = true,
    //排除范围
    var excludeScope: String? = null,
    //是否启用
    var isEnabled: Boolean = true,
    //是否正则
    var isRegex: Boolean = true,
    //超时时间
    var timeoutMillisecond: Long = 3000L,
    //排序
    var order: Int = Int.MIN_VALUE
) {

    /** Optional JSON-only explanation sample used by the replacement-rule editor. */
    var previewText: String? = null

    override fun equals(other: Any?): Boolean {
        if (other is ReplaceRule) {
            return other.id == id
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    @delegate:Transient
    val regex: Regex by lazy {
        pattern.toRegex()
    }

    fun getDisplayNameGroup(): String {
        return if (group.isNullOrBlank()) {
            name
        } else {
            String.format("%s (%s)", name, group)
        }
    }

    fun addGroup(groups: String): ReplaceRule {
        group?.splitNotBlank(AppPattern.splitGroupRegex)
            ?.toCollection(linkedSetOf())?.let {
                it.addAll(groups.splitNotBlank(AppPattern.splitGroupRegex))
                group = it.joinToString(",")
            }
        if (group.isNullOrBlank()) group = groups
        return this
    }

    fun removeGroup(groups: String): ReplaceRule {
        group?.splitNotBlank(AppPattern.splitGroupRegex)
            ?.toCollection(linkedSetOf())?.let {
                it.removeAll(groups.splitNotBlank(AppPattern.splitGroupRegex).toSet())
                group = it.joinToString(",")
            }
        return this
    }

    fun isValid(): Boolean {
        if (TextUtils.isEmpty(pattern)) {
            return false
        }
        //判断正则表达式是否正确
        if (isRegex) {
            try {
                Pattern.compile(pattern)
            } catch (ex: PatternSyntaxException) {
                AppLog.put("正则语法错误或不支持：${ex.localizedMessage}", ex)
                return false
            }
            // Pattern.compile测试通过，但是部分情况下会替换超时，报错，一般发生在修改表达式时漏删了
            if (pattern.endsWith('|') && !pattern.endsWith("\\|")) {
                return false
            }
        }
        return true
    }

    @Throws(NoStackTraceException::class)
    fun checkValid() {
        if (!isValid()) {
            throw NoStackTraceException(appCtx.getString(R.string.replace_rule_invalid))
        }
    }

    fun getValidTimeoutMillisecond(): Long {
        if (timeoutMillisecond <= 0) {
            return 3000L
        }
        return timeoutMillisecond
    }
}
