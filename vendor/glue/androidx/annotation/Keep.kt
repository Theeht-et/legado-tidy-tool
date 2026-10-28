package androidx.annotation

/** 平台桩：ProGuard 保留标记，headless 下无意义。 */
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FIELD,
)
annotation class Keep
