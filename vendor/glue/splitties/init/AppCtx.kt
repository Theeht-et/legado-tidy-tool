package splitties.init

import android.content.Context

/**
 * 平台桩：splitties 的全局 Application context。
 *
 * 引擎只用它取字符串资源（`appCtx.getString(R.string.xxx, args)`），
 * 所以类型仍是 `android.content.Context` —— 这样 `RhinoClassShutter`
 * 对 Context 的黑名单判断照旧生效。
 */
val appCtx: Context = object : Context() {}
