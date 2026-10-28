package io.legado.app.model.login

/**
 * headless 版 LoginUiV2 桩。
 *
 * 上游这个类是「登录 UI v2」的协议解析（渲染行 / 动作结果 / 字段回填），属于需要人工
 * 介入的交互式登录（见 docs/PATCHES.md「明确不支持的能力」）。headless 不实现登录 UI，
 * 一律快速失败（抛 [UnsupportedOperationException]），绝不能挂起等待把并发抓取池拖死。
 *
 * [MARKER] 是纯常量，必须与上游逐字一致：`JsSourceConfig` 解析 JS 源时用它给
 * `source.loginUi` 打标 —— 表示「这个源声明了 loginUi 函数」。
 */
object LoginUiV2 {

    const val MARKER = """{"version":2}"""

    fun isV2(loginUi: String?): Boolean {
        throw UnsupportedOperationException("headless 不支持登录 UI v2（需要人工介入的交互式登录）")
    }
}
