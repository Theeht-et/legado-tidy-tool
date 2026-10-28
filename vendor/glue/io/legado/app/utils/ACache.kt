package io.legado.app.utils

import io.legado.headless.Paths
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * headless 版磁盘缓存。
 *
 * ## 为什么不照抄上游（783 行）
 * 上游 ACache 依赖 Android 的文件目录（`appCtx.cacheDir` / `filesDir`）和
 * `Serializable` / `Bitmap` 序列化，headless 里这些都不需要，也不该引入。
 *
 * ## 实现
 * 目录下 **一个 key 一个文件**，文件名取 key 的 SHA-1 十六进制值（避免 key 里的
 * 非法路径字符）。文件格式固定为 8 字节大端 deadline（毫秒时间戳，0 表示不过期）
 * + payload —— 把过期时间写进文件本身，重启后依然有效，不需要额外的索引文件。
 *
 * 目前只有 [io.legado.app.model.SharedJsScope] 用到（缓存 `jsLib` 下载来的 JS）。
 * 注意：[io.legado.app.help.CacheManager] 内部有一份独立的、带 index.json 的
 * 文件缓存，二者**故意不共用**（CacheManager 由他人维护，避免引入耦合）。
 */
class ACache private constructor(private val cacheDir: File) {

    companion object {

        /** 默认目录：[Paths.cacheDir]/ACache。 */
        private const val DEFAULT_DIR_NAME = "ACache"

        /** 文件头 deadline(long) 的字节数。 */
        private const val HEADER_SIZE = 8

        /** 已创建的实例，按目录绝对路径去重（同一目录只会有一份）。 */
        private val instances = HashMap<String, ACache>()

        fun get(): ACache = get(File(Paths.cacheDir, DEFAULT_DIR_NAME))

        @Synchronized
        fun get(dir: File): ACache {
            val key = dir.absolutePath
            return instances.getOrPut(key) {
                dir.mkdirs()
                ACache(dir)
            }
        }
    }

    // =======================================
    // ============ String 数据 读写 ============
    // =======================================

    /**
     * 保存 String 数据。
     * @param saveTime 保存时间，单位秒；0 表示不过期。
     */
    @JvmOverloads
    fun put(key: String, value: String, saveTime: Int = 0) {
        putBytes(key, value.toByteArray(Charsets.UTF_8), saveTime)
    }

    /**
     * 保存二进制数据。
     * @param saveTime 保存时间，单位秒；0 表示不过期。
     */
    @JvmOverloads
    fun put(key: String, value: ByteArray, saveTime: Int = 0) {
        putBytes(key, value, saveTime)
    }

    /** 读取 String；不存在或已过期返回 null。 */
    fun getAsString(key: String): String? = readPayload(key)?.toString(Charsets.UTF_8)

    /** 读取二进制；不存在或已过期返回 null。 */
    fun getAsBinary(key: String): ByteArray? = readPayload(key)

    fun remove(key: String) {
        runCatching { keyFile(key).delete() }
    }

    fun clear() {
        runCatching {
            cacheDir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun putBytes(key: String, payload: ByteArray, saveTime: Int) {
        runCatching {
            val deadline =
                if (saveTime == 0) 0L else System.currentTimeMillis() + saveTime * 1000L
            DataOutputStream(BufferedOutputStream(keyFile(key).outputStream())).use { out ->
                out.writeLong(deadline)
                out.write(payload)
            }
        }
    }

    /** 文件里存的是 `deadline(8) + payload`；过期则顺手删掉并返回 null。 */
    private fun readPayload(key: String): ByteArray? {
        val file = keyFile(key)
        if (!file.isFile) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        if (bytes.size < HEADER_SIZE) return null
        val deadline = ByteBuffer.wrap(bytes, 0, HEADER_SIZE).long
        if (deadline != 0L && deadline <= System.currentTimeMillis()) {
            runCatching { file.delete() }
            return null
        }
        return bytes.copyOfRange(HEADER_SIZE, bytes.size)
    }

    private fun keyFile(key: String): File {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(key.toByteArray(Charsets.UTF_8))
        val name = digest.joinToString("") { "%02x".format(it) }
        return File(cacheDir, name)
    }
}
