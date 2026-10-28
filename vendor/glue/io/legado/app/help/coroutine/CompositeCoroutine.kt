package io.legado.app.help.coroutine

/**
 * 照抄 upstream `help/coroutine/CompositeCoroutine.kt`。
 *
 * 上游的这个类本身**不含任何 Android / 生命周期依赖**，只在 `clear()` 时取消容器里
 * 所有协程，所以 headless 原样保留（这也满足任务里「基于 CoroutineScope + SupervisorJob
 * 的最简实现、公开方法与上游同名同参」的要求 —— 这里没有被包装的生命周期，容器本身不变）。
 *
 * 与上游的差异：无。公开 API 与 upstream 一致（add / addAll / remove / delete / clear），
 * 未新增 `cancelAll` —— 上游没有该方法，当前也无人调用。
 */
@Suppress("unused")
class CompositeCoroutine : CoroutineContainer {

    private var resources: HashSet<Coroutine<*>>? = null

    val size: Int
        get() = resources?.size ?: 0

    val isEmpty: Boolean
        get() = size == 0

    constructor()

    constructor(vararg coroutines: Coroutine<*>) {
        this.resources = hashSetOf(*coroutines)
    }

    constructor(coroutines: Iterable<Coroutine<*>>) {
        this.resources = hashSetOf()
        for (d in coroutines) {
            this.resources?.add(d)
        }
    }

    override fun add(coroutine: Coroutine<*>): Boolean {
        synchronized(this) {
            var set: HashSet<Coroutine<*>>? = resources
            if (resources == null) {
                set = hashSetOf()
                resources = set
            }
            return set!!.add(coroutine)
        }
    }

    override fun addAll(vararg coroutines: Coroutine<*>): Boolean {
        synchronized(this) {
            var set: HashSet<Coroutine<*>>? = resources
            if (resources == null) {
                set = hashSetOf()
                resources = set
            }
            for (coroutine in coroutines) {
                val add = set!!.add(coroutine)
                if (!add) {
                    return false
                }
            }
        }
        return true
    }

    override fun remove(coroutine: Coroutine<*>): Boolean {
        if (delete(coroutine)) {
            coroutine.cancel()
            return true
        }
        return false
    }

    override fun delete(coroutine: Coroutine<*>): Boolean {
        synchronized(this) {
            val set = resources
            if (set == null || !set.remove(coroutine)) {
                return false
            }
        }
        return true
    }

    override fun clear() {
        val set: HashSet<Coroutine<*>>?
        synchronized(this) {
            set = resources
            resources = null
        }

        set?.forEachIndexed { _, coroutine ->
            coroutine.cancel()
        }
    }
}
