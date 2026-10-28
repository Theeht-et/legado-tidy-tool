package io.legado.app.help

import android.webkit.JavascriptInterface
import androidx.annotation.Keep
import androidx.collection.LruCache
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.legado.app.model.analyzeRule.QueryTTF
import java.io.File
import java.security.MessageDigest

/**
 * 编译后的 TTF 字体对象缓存（字体反爬用）。
 * 上游是 4 格，照搬 —— QueryTTF 解析很贵，但一个源通常只用一两个字体文件。
 */
private val queryTTFMap = LruCache<String, QueryTTF>(4)

/**
 * 内存缓存。上游用 `LruCache<String, Any>(1024*1024*50)` 并且**没有**重写 sizeOf，
 * 所以实际是「最多 5000 万条」。这里保持一致（不要改成按字节计），
 * 否则书源行为可能不同 —— 有些源依赖「放进去的东西不会被挤掉」。
 */
private val memoryLruCache = LruCache<String, Any>(1024 * 1024 * 50)

object AppCacheManager {
    fun put(key: String, queryTTF: QueryTTF) {
        queryTTFMap.put(key, queryTTF)
    }

    fun getQueryTTF(key: String): QueryTTF? = queryTTFMap[key]

    /** 清掉「源变量 / 登录信息 / infoMap」这类跨会话状态。上游在换源时调用。 */
    fun clearSourceVariables() {
        memoryLruCache.snapshot().keys.forEach {
            if (it.startsWith("v_")
                || it.startsWith("userInfo_")
                || it.startsWith("loginHeader_")
                || it.startsWith("sourceVariable_")
                || it.startsWith("infoMap_")
            ) {
                memoryLruCache.remove(it)
            }
        }
    }
}

/**
 * headless 版 CacheManager —— 书源脚本通过全局 `cache` 访问的就是 [WebCacheManager]。
 *
 * 上游后端是 Room（`appDb.cacheDao`）+ `ACache`（磁盘）。headless 下换成：
 *   · 内存：仍然用 LruCache
 *   · 磁盘：`run/cache/` 下一个 key 一个文件 + 一份 index.json
 *
 * 为什么要磁盘缓存：书源普遍用它存 token / 加密 key / 中间结果，
 * 跑一次对比就是几千次 `cache.put`。只放内存的话，**按书源分批抓取时状态会丢**，
 * 表现成「第一次能抓到、第二次就失败」这种最难查的差异。
 */
@Keep
@Suppress("unused")
object CacheManager {

    private val gson = Gson()
    private var root: File? = null
    private val index = HashMap<String, Entry>()

    private data class Entry(val file: String, val deadline: Long)

    @Synchronized
    private fun dir(): File {
        root?.let { return it }
        val d = File(io.legado.headless.Paths.cacheDir, "files").apply { mkdirs() }
        root = d
        runCatching { loadIndex(d.parentFile) }
        return d
    }

    private fun loadIndex(base: File) {
        val f = File(base, "index.json")
        if (!f.isFile) return
        val type = object : TypeToken<HashMap<String, Entry>>() {}.type
        runCatching {
            val m: HashMap<String, Entry>? = gson.fromJson(f.readText(), type)
            m?.let { index.putAll(it) }
        }
    }

    @Synchronized
    fun flush() {
        val base = dir().parentFile ?: return
        runCatching { File(base, "index.json").writeText(gson.toJson(index)) }
    }

    private fun keyFile(key: String): File {
        val h = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir(), h)
    }

    /**
     * saveTime 单位为秒；0 表示不过期。
     */
    @JvmOverloads
    @Synchronized
    fun put(key: String, value: Any, saveTime: Int = 0) {
        val deadline = if (saveTime == 0) 0L else System.currentTimeMillis() + saveTime * 1000L
        when (value) {
            is ByteArray -> {
                keyFile(key).writeBytes(value)
                index[key] = Entry(keyFile(key).name, deadline)
            }

            else -> {
                val valueStr = value.toString()
                keyFile(key).writeText(valueStr, Charsets.UTF_8)
                index[key] = Entry(keyFile(key).name, deadline)
                // 上游语义：永久缓存同时进内存，带过期的只落盘
                if (deadline == 0L) putMemory(key, valueStr) else deleteMemory(key)
            }
        }
    }

    fun putMemory(key: String, value: Any) {
        memoryLruCache.put(key, value)
    }

    fun getFromMemory(key: String): Any? = memoryLruCache[key]

    fun deleteMemory(key: String) {
        memoryLruCache.remove(key)
    }

    @Synchronized
    fun get(key: String): String? {
        getFromMemory(key)?.let { if (it is String) return it }
        val e = index[key] ?: return null
        if (e.deadline != 0L && e.deadline <= System.currentTimeMillis()) return null
        val f = File(dir(), e.file)
        if (!f.isFile) return null
        val v = f.readText(Charsets.UTF_8)
        if (e.deadline == 0L) putMemory(key, v)
        return v
    }

    @Synchronized
    fun get(key: String, onlyDisk: Boolean): String? {
        if (!onlyDisk) return get(key)
        val e = index[key] ?: return null
        if (e.deadline != 0L && e.deadline <= System.currentTimeMillis()) return null
        val f = File(dir(), e.file)
        return if (f.isFile) f.readText(Charsets.UTF_8) else null
    }

    fun getInt(key: String): Int? {
        getFromMemory(key)?.let { if (it is Int) return it }
        return get(key, true)?.toIntOrNull()
    }

    fun getLong(key: String): Long? {
        getFromMemory(key)?.let { if (it is Long) return it }
        return get(key, true)?.toLongOrNull()
    }

    fun getDouble(key: String): Double? {
        getFromMemory(key)?.let { if (it is Double) return it }
        return get(key, true)?.toDoubleOrNull()
    }

    fun getFloat(key: String): Float? {
        getFromMemory(key)?.let { if (it is Float) return it }
        return get(key, true)?.toFloatOrNull()
    }

    fun getByteArray(key: String): ByteArray? {
        val e = index[key] ?: return null
        val f = File(dir(), e.file)
        return if (f.isFile) f.readBytes() else null
    }

    fun putFile(key: String, value: String, saveTime: Int = 0) = put(key, value, saveTime)

    fun getFile(key: String): String? = get(key)

    @Synchronized
    fun delete(key: String) {
        index.remove(key)?.let { runCatching { File(dir(), it.file).delete() } }
        deleteMemory(key)
    }
}

/**
 * 书源脚本看到的 `cache` 对象。
 *
 * 上游把它拆成两个类（CacheManager 给 Kotlin 用、WebCacheManager 给脚本用），
 * 参数类型不同（Any vs String）—— 照搬这个分层，因为脚本传进来的永远只有字符串。
 */
@Keep
@Suppress("unused")
object WebCacheManager {
    @JvmOverloads
    @JavascriptInterface
    fun put(key: String, value: String, saveTime: Int = 0) {
        CacheManager.put(key, value, saveTime)
    }

    @JavascriptInterface
    fun putMemory(key: String, value: String) {
        memoryLruCache.put(key, value)
    }

    @JavascriptInterface
    fun getFromMemory(key: String): String? = memoryLruCache[key]?.toString()

    @JavascriptInterface
    fun deleteMemory(key: String) {
        memoryLruCache.remove(key)
    }

    @JavascriptInterface
    fun get(key: String): String? = CacheManager.get(key)

    @JavascriptInterface
    fun get(key: String, onlyDisk: Boolean): String? = CacheManager.get(key, onlyDisk)

    @JavascriptInterface
    fun putFile(key: String, value: String, saveTime: Int = 0) {
        CacheManager.putFile(key, value, saveTime)
    }

    @JavascriptInterface
    fun getFile(key: String): String? = CacheManager.getFile(key)

    @JavascriptInterface
    fun delete(key: String) {
        CacheManager.delete(key)
    }
}
