package io.legado.app.constant

@Suppress("ConstPropertyName")
object SourceType {

    const val book = 0
    const val rss = 1

    // upstream 用 androidx.annotation.IntDef 约束取值；headless 无该平台桩，
    // 去掉 @IntDef 但保留注解符号本身（引擎未引用，不改变运行行为）。
    @Target(AnnotationTarget.VALUE_PARAMETER)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Type

}
