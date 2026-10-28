package android.webkit

/**
 * 平台桩。上游用它把方法标记为「可被 WebView 的 JS 调用」。
 *
 * 已确认 `modules/rhino` **不读**这个注解 —— 它是 Android WebView 专有的，
 * 而本项目的 JS 执行走 Rhino。所以这里只需要让标注它的代码能编译，
 * 运行时不产生任何行为。
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class JavascriptInterface
