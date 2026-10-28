package io.legado.app.utils

import android.content.Context

/**
 * 偏好读取。**只实现引擎用到的 `getPrefBoolean` 这一个函数**，
 * 不搬 upstream 的整个 `ContextExtensions.kt`（那里面有 SharedPreferences / EventBus / toast 等）。
 *
 * 引擎调用点：`model/webBook/SearchModel.kt`
 *   `val precision = appCtx.getPrefBoolean(PreferKey.precisionSearch)`
 *
 * headless 差异：
 * - 上游从 `PreferenceManager.getDefaultSharedPreferences(appCtx)` 读；headless 没有 Android 偏好系统，
 *   改为读环境变量 / 系统属性：
 *       `LEGADO_<KEY 全大写>`（如 precisionSearch → LEGADO_PRECISIONSEARCH）
 *       或 `-Dlegado.<key>`（如 -Dlegado.precisionSearch=true）
 *   两者都没有时返回 [defValue]。
 * - **默认值与上游一致**（`defValue = false`）。未配置时行为与用户 App 默认设置一致，
 *   不会静默改变书源解析路径。
 */
@Suppress("unused")
fun Context.getPrefBoolean(key: String, defValue: Boolean = false): Boolean {
    val raw = System.getenv("LEGADO_${key.uppercase()}") ?: System.getProperty("legado.$key")
        ?: return defValue
    return when (raw.trim().lowercase()) {
        "1", "true", "yes", "on" -> true
        "0", "false", "no", "off" -> false
        else -> defValue
    }
}
