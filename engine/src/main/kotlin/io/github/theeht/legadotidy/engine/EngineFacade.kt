package io.github.theeht.legadotidy.engine

/**
 * 引擎对外的唯一入口（待接入 vendored 上游源码）。
 *
 * 设计：外部只调一个「探一个源」的函数，并发/超时/重试全部留在调用方，
 * 引擎不持有全局状态（不维护 db、不维护缓存、不做全局锁）。
 *
 * 接入步骤（见 docs/ENGINE.md）：
 * 1. `python scripts/vendor-upstream.py` 生成 vendor/legado；
 * 2. 在本模块补齐上游依赖的平台类（BaseSource / AppConfig / appDb / JsExtensions …）；
 * 3. 实现 [LegadoProbeEngine]，内部用 AnalyzeUrl + AnalyzeRule 跑一次搜索。
 */
class EngineFacade {
    val ready: Boolean get() = false

    fun status(): String =
        if (ready) "引擎就绪"
        else "引擎未接入：vendor/legado 尚未生成或补全层未完成"
}
