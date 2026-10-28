@file:Suppress("unused")

package io.legado.app.help.http

import android.text.TextUtils
import androidx.annotation.Keep
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern.equalsRegex
import io.legado.app.constant.AppPattern.semicolonRegex
import io.legado.app.help.CacheManager
import io.legado.app.help.http.CookieManager.getCookieNoSession
import io.legado.app.help.http.CookieManager.mergeCookiesToMap
import io.legado.app.help.http.api.CookieManagerInterface
import io.legado.app.utils.NetworkUtils
import java.util.concurrent.ConcurrentHashMap

/**
 * 书源脚本里的全局 `cookie` 对象（实测 `cookie.getKey` 出现 883 次、`removeCookie` 104 次）。
 *
 * 与 upstream 的差异（只有存储后端）：
 *  upstream 用 Room（`appDb.cookieDao`）持久化。headless 没有 Room，改成：
 *    · [cookies]：`ConcurrentHashMap<domain, cookieString>`，**按二级域名隔离**，天然并发安全；
 *    · [CacheManager]：同 key 再写一份到内存 + `run/cache` 磁盘，进程重启后可回填。
 *  对外接口（[CookieManagerInterface] + `getKey` / `clear`）与 upstream 完全一致。
 *
 * 并发：500 个虚拟线程会同时读写这里，所以共享状态一律用并发容器 / 加锁：
 *  · [cookies] 是 ConcurrentHashMap；
 *  · `get→put` 的回填是幂等的，即使并发重复回填也只是写同一个值。
 *
 * 已知限制：`clear()` 只能清掉「本进程写过/读过」的域名，无法枚举磁盘上历史域名
 * （CacheManager 没暴露 key 遍历）。App 里 `clear()` 是清 Room 全表，这里语义接近但非严格全清。
 */
@Keep
object CookieStore : CookieManagerInterface {

    private const val COOKIE_KEY_SUFFIX = "_cookie"

    /** 非会话 cookie：domain -> cookieString。按二级域名隔离。 */
    private val cookies = ConcurrentHashMap<String, String>()

    private fun cacheKey(domain: String) = "$domain$COOKIE_KEY_SUFFIX"

    /**
     * 按域名取「非会话」cookie 原始串。
     *
     * 先查内存 map，miss 时回填 [CacheManager]（内存 miss 会读磁盘）。
     * 这是 [CookieManager.getCookieNoSession] 的唯一数据源，替代 upstream 的 Room DAO。
     */
    internal fun storedCookie(domain: String): String {
        cookies[domain]?.let { return it }
        val persisted = CacheManager.get(cacheKey(domain)) ?: return ""
        cookies[domain] = persisted
        return persisted
    }

    /**
     * 保存cookie到数据库（headless：内存 map + CacheManager），会自动识别url的二级域名
     */
    override fun setCookie(url: String, cookie: String?) {
        try {
            saveCookie(url, cookie)
        } catch (e: Exception) {
            AppLog.put("保存Cookie失败\n$e", e)
        }
    }

    private fun saveCookie(url: String, cookie: String?) {
        val domain = NetworkUtils.getSubDomain(url)
        val cookieValue = cookie ?: ""
        cookies[domain] = cookieValue
        // put(..., saveTime = 0) 在 headless CacheManager 里同时进内存和磁盘
        CacheManager.put(cacheKey(domain), cookieValue)
    }

    override fun replaceCookie(url: String, cookie: String) {
        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(cookie)) {
            return
        }
        setCookie(url, mergeCookie(url, cookie))
    }

    internal fun restoreCookie(url: String, cookie: String) {
        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(cookie)) {
            return
        }
        saveCookie(url, mergeCookie(url, cookie))
    }

    private fun mergeCookie(url: String, cookie: String): String {
        return mergeCookieValues(getCookieNoSession(url), cookie)
    }

    internal fun mergeCookieValues(oldCookie: String, cookie: String): String {
        if (oldCookie.isEmpty()) {
            return cookie
        }
        val cookieMap = cookieToMap(oldCookie)
        cookieMap.putAll(cookieToMap(cookie))
        return mapToCookie(cookieMap).orEmpty()
    }

    /**
     *获取url所属的二级域名的cookie
     */
    override fun getCookie(url: String): String {
        val domain = NetworkUtils.getSubDomain(url)

        val cookie = getCookieNoSession(url)
        val sessionCookie = CookieManager.getSessionCookie(domain)

        val cookieMap = mergeCookiesToMap(cookie, sessionCookie)

        var ck = mapToCookie(cookieMap) ?: ""
        while (ck.length > 4096) {
            val removeKey = cookieMap.keys.random()
            CookieManager.removeCookie(url, removeKey)
            cookieMap.remove(removeKey)
            ck = mapToCookie(cookieMap) ?: ""
        }
        return ck
    }

    fun getKey(url: String, key: String): String {
        val cookie = getCookie(url)
        val sessionCookie = CookieManager.getSessionCookie(url)
        val cookieMap = mergeCookiesToMap(cookie, sessionCookie)
        return cookieMap[key] ?: ""
    }

    override fun removeCookie(url: String) {
        val domain = NetworkUtils.getSubDomain(url)
        cookies.remove(domain)
        CacheManager.delete(cacheKey(domain))
        CacheManager.deleteMemory(cacheKey(domain))
        CacheManager.deleteMemory("${domain}_session_cookie")
        // upstream 还会清 android.webkit.CookieManager；headless 没有 WebView，略过。
    }

    override fun cookieToMap(cookie: String): MutableMap<String, String> {
        val cookieMap = mutableMapOf<String, String>()
        if (cookie.isBlank()) {
            return cookieMap
        }
        val pairArray = cookie.split(semicolonRegex).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (pair in pairArray) {
            val pairs = pair.split(equalsRegex, 2).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (pairs.size <= 1) {
                continue
            }
            val key = pairs[0].trim { it <= ' ' }
            val value = pairs[1]
            if (value.isNotBlank() || value.trim { it <= ' ' } == "null") {
                cookieMap[key] = value.trim { it <= ' ' }
            }
        }
        return cookieMap
    }

    override fun mapToCookie(cookieMap: Map<String, String>?): String? {
        if (cookieMap.isNullOrEmpty()) {
            return null
        }
        val builder = StringBuilder()
        cookieMap.keys.forEachIndexed { index, key ->
            if (index > 0) builder.append("; ")
            builder.append(key).append("=").append(cookieMap[key])
        }
        return builder.toString()
    }

    fun clear() {
        cookies.keys.forEach { domain ->
            CacheManager.delete(cacheKey(domain))
            CacheManager.deleteMemory(cacheKey(domain))
        }
        cookies.clear()
    }

}
