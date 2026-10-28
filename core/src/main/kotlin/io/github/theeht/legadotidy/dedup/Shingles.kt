package io.github.theeht.legadotidy.dedup

/** 字符 n-gram 集合与 Jaccard 相似度。 */
object Shingles {

    const val DEFAULT_N = 3

    fun of(text: String, n: Int = DEFAULT_N): IntArray {
        if (text.isEmpty()) return IntArray(0)
        if (text.length <= n) return intArrayOf(text.hashCode())
        val set = HashSet<Int>(text.length)
        for (i in 0..text.length - n) set.add(text.substring(i, i + n).hashCode())
        return set.toIntArray()
    }

    /** Jaccard：交集 / 并集。先拿较小的那个集合去探，避免大集合建表。 */
    fun jaccard(a: IntArray, b: IntArray): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val (small, large) = if (a.size <= b.size) a to b else b to a
        val largeSet = HashSet<Int>(large.size * 2)
        for (x in large) largeSet.add(x)
        var inter = 0
        for (x in small) if (largeSet.contains(x)) inter++
        val union = a.size + b.size - inter
        return if (union == 0) 0.0 else inter.toDouble() / union
    }

    /** 长度比预筛：两个集合大小相差过大时 Jaccard 必然上不去。 */
    fun lengthRatioPass(a: IntArray, b: IntArray, threshold: Double): Boolean {
        val lo = minOf(a.size, b.size).toDouble()
        val hi = maxOf(a.size, b.size).toDouble()
        if (hi == 0.0) return true
        return lo / hi >= threshold * 0.6
    }
}
