package io.legado.app.utils

import android.content.Context

/**
 * headless 版 UI 提示桩。
 *
 * 上游 ToastUtils.kt 依赖 android.widget.Toast / ViewBinding / 主题配色，headless 没有 UI。
 * 这些调用只是给用户看的提示（见 docs/PATCHES.md「明确不支持的能力」：toast / longToast
 * 一律 no-op），因此这里全部写成空实现 —— 不弹任何东西，也不影响解析结果。
 *
 * 签名与上游对齐：上游 `toastOnUi` 的 `duration` 默认值是 `Toast.LENGTH_SHORT`，
 * 该常量本身就是 0。headless 没有 `android.widget.Toast` 可用，不需要这个参数，
 * 但保留它以兼容可能传入 duration 的调用点（值被忽略）。
 */

fun Context.toastOnUi(message: Int, duration: Int = 0) {
    // headless 无 UI，no-op
}

fun Context.toastOnUi(message: CharSequence?, duration: Int = 0) {
    // headless 无 UI，no-op
}

fun Context.longToastOnUi(message: Int) {
    // headless 无 UI，no-op
}

fun Context.longToastOnUi(message: CharSequence?) {
    // headless 无 UI，no-op
}
