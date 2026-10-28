package io.legado.app.help.coroutine

/**
 * 照抄 upstream `help/coroutine/CoroutineContainer.kt`。
 * 纯 Kotlin，无 Android 依赖，未做任何改动。
 */
internal interface CoroutineContainer {

    fun add(coroutine: Coroutine<*>): Boolean

    fun addAll(vararg coroutines: Coroutine<*>): Boolean

    fun remove(coroutine: Coroutine<*>): Boolean

    fun delete(coroutine: Coroutine<*>): Boolean

    fun clear()

}
