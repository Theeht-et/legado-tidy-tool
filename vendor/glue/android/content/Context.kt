package android.content

import io.legado.headless.Res

/**
 * 平台桩。只为一个用途存在：引擎里有 `appCtx.getString(R.string.xxx, args)` 这种
 * 取字符串资源的调用，以及 `RhinoClassShutter` 把 `Context` 列入脚本黑名单。
 *
 * 放在 `android.content` 包下是有意的 —— 这样 `is Context` 的黑名单判断照旧生效，
 * 书源脚本无法通过 Rhino 拿到我们的宿主对象。
 */
open class Context {
    open fun getString(resId: Int): String = Res.get(resId)

    open fun getString(resId: Int, vararg formatArgs: Any?): String =
        Res.get(resId, *formatArgs)
}
