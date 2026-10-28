package android.annotation

/** 平台桩：引擎源码里的 `@SuppressLint(...)`，headless 下无 lint，直接忽略。 */
@Retention(AnnotationRetention.SOURCE)
@Target(
    AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER,
)
annotation class SuppressLint(vararg val value: String)
