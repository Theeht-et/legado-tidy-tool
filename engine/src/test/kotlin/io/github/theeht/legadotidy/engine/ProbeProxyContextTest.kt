package io.github.theeht.legadotidy.engine

import io.legado.app.help.http.ProbeProxyContext
import io.legado.app.help.http.getProxyClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProbeProxyContextTest {

    @Test
    fun `并发校验各自使用默认代理且书源代理优先`() = runBlocking {
        val expected = listOf(
            Triple(18_701, 28_701, "http://127.0.0.1:18701"),
            Triple(18_702, 28_702, "http://127.0.0.1:18702"),
        )

        val actual = coroutineScope {
            expected.map { (defaultPort, sourcePort, defaultProxy) ->
                async(Dispatchers.Default) {
                    withContext(ProbeProxyContext(defaultProxy)) {
                        // Force suspension so each coroutine resumes on a potentially different thread.
                        delay(20)
                        val defaultClient = getProxyClient()
                        val sourceClient = getProxyClient("http://127.0.0.1:$sourcePort")
                        defaultPort to sourcePort to
                            (defaultClient.proxyPort() to sourceClient.proxyPort())
                    }
                }
            }.awaitAll()
        }

        assertEquals(
            expected.map { it.first to it.second },
            actual.map { it.first to it.second },
        )
        assertNull(ProbeProxyContext.currentProxy())
    }

    @Test
    fun `嵌套空代理上下文恢复外层代理`() = runBlocking {
        withContext(ProbeProxyContext(" http://127.0.0.1:18703 ")) {
            assertEquals("http://127.0.0.1:18703", ProbeProxyContext.currentProxy())
            withContext(ProbeProxyContext(null)) {
                assertNull(ProbeProxyContext.currentProxy())
            }
            assertEquals("http://127.0.0.1:18703", ProbeProxyContext.currentProxy())
        }
        assertNull(ProbeProxyContext.currentProxy())
    }

    private fun OkHttpClient.proxyPort(): Int =
        (proxy.address() as InetSocketAddress).port
}
