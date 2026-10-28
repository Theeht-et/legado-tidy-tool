package io.legado.app.help.source

import com.script.ScriptBindings
import com.script.SharedGlobalStateHandle
import io.legado.app.constant.SourceType
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.BookSource
import io.legado.app.model.SharedJsScope
import io.legado.app.utils.MD5Utils
import kotlin.coroutines.CoroutineContext

/**
 * 逐字照搬 upstream 的同名文件。
 *
 * 这几个符号是「书源的 JS 全局作用域」与「能力标签」的地基：书源脚本靠
 * `source.getVariable/setVariable` 跨请求存状态，而共享作用域（jsLib + 全局 key）
 * 就由这里决定，headless 必须原样保留。
 */

fun BaseSource.getShareScope(coroutineContext: CoroutineContext? = null): ScriptBindings? {
    return SharedJsScope.getScope(jsLib, coroutineContext)
}

fun BaseSource.getSharedGlobalStateKey(): SharedGlobalStateHandle? {
    val library = jsLib?.takeIf { it.isNotBlank() } ?: return null
    val key = "${MD5Utils.md5Encode(library)}:${javaClass.name}:${MD5Utils.md5Encode(getKey())}"
    return ScriptBindings.getSharedGlobalStateHandle(key)
}

fun BaseSource.clearSharedGlobalState() {
    getSharedGlobalStateKey()?.let(ScriptBindings::removeSharedGlobalState)
}

fun clearSharedGlobalStateBySourceKey(
    sourceClass: Class<out BaseSource>,
    sourceKey: String,
) {
    ScriptBindings.removeSharedGlobalStatesBySource(
        sourceClass.name,
        MD5Utils.md5Encode(sourceKey),
    )
}

fun BaseSource.getSourceType(): Int {
    return when (this) {
        is BookSource -> SourceType.book
        // 上游：`is RssSource -> SourceType.rss`。headless 不提供 RssSource 实体
        // （整个工程只处理书源），故把非 BookSource 归为 rss 标签：语义一致、零额外依赖。
        else -> SourceType.rss
    }
}
