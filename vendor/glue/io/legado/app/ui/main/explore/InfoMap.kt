package io.legado.app.utils

import androidx.annotation.Keep
import io.legado.app.help.CacheManager

/**
 * 平台桩。上游 `io.legado.app.utils.InfoMap` 不在编译范围内，但 UI 包（本任务范围内）
 * 的 `ExploreAdapter` 需要它的类型，故按上游逐字补桩（只依赖 [CacheManager] 与 GSON，
 * 两者 headless 都有，逻辑与上游一致）。
 *
 * 注：本文件物理上放在 `ui/main/explore/` 下（本任务只允许写这几个目录），但按 Kotlin
 * 的包语义声明在原本的 `io.legado.app.utils` 包名下，保证
 * `import io.legado.app.utils.InfoMap` 照常解析（同 `help/config/PageAnim.kt` 的做法）。
 *
 * 发现按钮信息（缓存发现页的解析结果，作为 JS 变量 `infoMap` 注入规则执行环境）。
 */
@Keep
class InfoMap(val sourceUrl: String) : MutableMap<String, String> {
    private var actualMap: MutableMap<String, String>
    var needSave = false
    private var saveTime = 0

    init {
        val cache = CacheManager.get("infoMap_${sourceUrl}")
        actualMap = GSON.fromJsonObject<MutableMap<String, String>>(cache).getOrNull() ?: mutableMapOf()
    }

    /**
     * time 保存时间 单位为秒
     */
    @JvmOverloads
    fun save(time: Int = 0, need: Boolean = true) {
        needSave = need
        saveTime = time
    }

    fun saveNow() {
        val json = GSON.toJson(actualMap)
        CacheManager.put("infoMap_${sourceUrl}", json, saveTime)
        needSave = false
    }

    fun get(): MutableMap<String, String> {
        return actualMap
    }

    fun set(value: Map<String, String>) {
        actualMap = value.toMutableMap()
    }

    override fun get(key: String) = actualMap[key]
    override fun put(key: String, value: String) = actualMap.put(key, value)
    override fun remove(key: String) = actualMap.remove(key)
    override fun putAll(from: Map<out String, String>) = actualMap.putAll(from)
    override fun containsKey(key: String) = actualMap.containsKey(key)
    override fun containsValue(value: String) = actualMap.containsValue(value)
    override val size get() = actualMap.size
    override val entries get() = actualMap.entries
    override val keys get() = actualMap.keys
    override val values get() = actualMap.values
    override fun isEmpty() = actualMap.isEmpty()
    override fun clear() = actualMap.clear()
}
