package io.legado.app.help.http

import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * The default proxy for one validation coroutine.
 *
 * A ThreadLocal is only used as the bridge to Legado's synchronous OkHttp client factory;
 * ThreadContextElement installs and restores it whenever the coroutine moves between threads.
 * This avoids mutating JVM-wide properties while many source probes run concurrently.
 */
internal class ProbeProxyContext(
    private val proxy: String?,
) : ThreadContextElement<String?>, AbstractCoroutineContextElement(Key) {

    override fun updateThreadContext(context: CoroutineContext): String? {
        val previous = currentProxyLocal.get()
        val normalized = proxy?.trim()?.takeIf { it.isNotEmpty() }
        if (normalized == null) {
            currentProxyLocal.remove()
        } else {
            currentProxyLocal.set(normalized)
        }
        return previous
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: String?) {
        if (oldState == null) {
            currentProxyLocal.remove()
        } else {
            currentProxyLocal.set(oldState)
        }
    }

    companion object Key : CoroutineContext.Key<ProbeProxyContext> {
        private val currentProxyLocal = ThreadLocal<String?>()

        fun currentProxy(): String? = currentProxyLocal.get()
    }
}
