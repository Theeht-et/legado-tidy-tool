package android.text

/** 平台桩：只实现引擎用到的 isEmpty / join，语义对齐 android.text.TextUtils。 */
object TextUtils {
    @JvmStatic
    fun isEmpty(str: CharSequence?): Boolean = str == null || str.isEmpty()

    @JvmStatic
    fun join(delimiter: CharSequence, tokens: Iterable<*>): String =
        tokens.joinToString(delimiter.toString())

    @JvmStatic
    fun join(delimiter: CharSequence, tokens: Array<out Any?>): String =
        tokens.joinToString(delimiter.toString())
}
