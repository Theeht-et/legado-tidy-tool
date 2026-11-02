package io.github.theeht.legadotidy.engine

import io.legado.app.help.http.ProbeProxyContext
import io.legado.app.help.http.getProxyClient
import io.legado.app.model.analyzeRule.validateDnsIpProxyCompatibility
import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.probe.ProbeOptions
import io.github.theeht.legadotidy.probe.ProbeStatus
import com.google.gson.JsonParser
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
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
                        Triple(
                            defaultPort,
                            sourcePort,
                            defaultClient.proxyPort() to sourceClient.proxyPort(),
                        )
                    }
                }
            }.awaitAll()
        }

        assertEquals(
            expected.map { it.first to it.second },
            actual.map { it.third },
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


    @Test
    fun `显式空书源代理覆盖默认代理且默认代理参与 dnsIp 互斥检查`() = runBlocking {
        val defaultProxy = "http://127.0.0.1:18704"
        val sourceProxy = "http://127.0.0.1:28704"

        withContext(ProbeProxyContext(defaultProxy)) {
            assertEquals(null, ProbeProxyContext.resolveProxy("", sourceProxyConfigured = true))
            assertEquals(defaultProxy, ProbeProxyContext.resolveProxy(null, sourceProxyConfigured = false))
            assertEquals(sourceProxy, ProbeProxyContext.resolveProxy(sourceProxy, sourceProxyConfigured = true))

            assertFailsWith<IllegalArgumentException> {
                validateDnsIpProxyCompatibility(
                    ProbeProxyContext.resolveProxy(null, sourceProxyConfigured = false),
                    "192.0.2.10",
                )
            }
            validateDnsIpProxyCompatibility(
                ProbeProxyContext.resolveProxy("", sourceProxyConfigured = true),
                "192.0.2.10",
            )
            assertNull(getProxyClient(null, inheritContextProxy = false).proxy)
        }
        assertNull(ProbeProxyContext.currentProxy())
    }

    @Test
    fun `无效代理配置在网络请求前返回明确错误`() = runBlocking {
        val source = BookSourceDoc(
            JsonParser.parseString(
                """{
                  "bookSourceUrl":"https://example.test",
                  "searchUrl":"/search",
                  "ruleSearch":{"bookList":"div.book","name":"h2","bookUrl":"href"}
                }""",
            ).asJsonObject,
        )

        val result = LegadoProbeEngine().probe(
            index = 0,
            source = source,
            options = ProbeOptions(proxy = "not a proxy"),
        )

        assertEquals(ProbeStatus.ERROR, result.status)
        assertEquals("not a proxy", result.proxy)
        assertContains(result.detail.orEmpty(), "代理配置无效")
        assertNull(ProbeProxyContext.currentProxy())
    }

    private fun OkHttpClient.proxyPort(): Int {
        val configuredProxy = proxy ?: error("Expected an explicitly configured proxy")
        return (configuredProxy.address() as InetSocketAddress).port
    }
}
