package io.github.theeht.legadotidy.dedup

import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.normalize.TidyText

/** 去重强度，声明顺序即优先级（数字越小越强）。 */
enum class DedupKind(val label: String) {
    EXACT("完全重复"),
    FINGERPRINT("规则指纹相同"),
    SIMILAR("规则高度相似"),
}

data class DedupConfig(
    /** 相似层阈值。0.92 意味着「整段规则只有零星字符不同」才会被归为一组。 */
    val similarityThreshold: Double = 0.92,
    /** 候选对至少要共事的 3-gram 数，低于此值直接跳过精确比对。 */
    val minSharedShingles: Int = 3,
    /** 单个 shingle 超过这个文档数就视为噪声，不用于候选生成。 */
    val maxShingleDocFreq: Int = 60,
)

data class DedupGroup(
    val kind: DedupKind,
    /** 组内成员在输入列表里的下标，第一个是建议保留的那一个。 */
    val members: List<Int>,
    val keep: Int,
    val similarity: Double,
) {
    val size: Int get() = members.size
}

data class DedupReport(
    val groups: List<DedupGroup>,
    val total: Int,
) {
    /** 去重后建议保留的源数目（每组留 1 个 + 未分组的全部）。 */
    val keepCount: Int = total - groups.sumOf { it.size - 1 }
    val exactGroups: Int get() = groups.count { it.kind == DedupKind.EXACT }
    val fingerprintGroups: Int get() = groups.count { it.kind == DedupKind.FINGERPRINT }
    val similarGroups: Int get() = groups.count { it.kind == DedupKind.SIMILAR }
}

/**
 * 三层去重。
 *
 * 1. 精确：规范化 URL 相同（与 HTML 原型一致，忽略协议、www、大小写、查询参数、片段和尾斜杠）；
 * 2. 指纹：行为字段哈希相同（同站马甲）；
 * 3. 相似：行为文本 3-gram Jaccard 达到阈值（用倒排索引出候选，避免 O(n²) 全量比较）。
 *
 * 始终只分组，不删源——是否采纳由用户决定。
 */
object Deduper {

    fun run(sources: List<BookSourceDoc>, config: DedupConfig = DedupConfig()): DedupReport {
        val n = sources.size
        if (n <= 1) return DedupReport(emptyList(), n)

        val uf = UnionFind(n)
        val edges = ArrayList<Edge>(0)

        // 1) 精确
        val exactBuckets = HashMap<String, MutableList<Int>>(n * 2)
        sources.forEachIndexed { i, s ->
            val k = canonicalSourceUrl(s.url)
            if (k.isNotEmpty()) exactBuckets.getOrPut(k) { ArrayList(2) }.add(i)
        }
        for (bucket in exactBuckets.values) {
            if (bucket.size < 2) continue
            val keep = pickKeep(sources, bucket)
            for (i in bucket) {
                if (i == keep) continue
                if (uf.union(keep, i)) edges.add(Edge(keep, i, DedupKind.EXACT, 1.0))
            }
        }

        // 2) 指纹
        val byFingerprint = LinkedHashMap<String, MutableList<Int>>(n * 2)
        sources.forEachIndexed { i, s ->
            byFingerprint.getOrPut(BehaviorFingerprint.of(s)) { ArrayList(2) }.add(i)
        }
        for (bucket in byFingerprint.values) {
            if (bucket.size < 2) continue
            var root = bucket.first()
            for (i in bucket) {
                if (uf.find(i) == uf.find(root) && i == root) continue
                if (uf.find(i) != uf.find(root)) {
                    if (uf.union(root, i)) edges.add(Edge(root, i, DedupKind.FINGERPRINT, 1.0))
                }
            }
        }

        // 3) 相似：先算 shingle，再倒排出候选对
        if (config.similarityThreshold < 1.0) {
            val texts = sources.map { TidyText.ruleText(BehaviorFingerprint.canonical(it)) }
            val shingles = texts.map { Shingles.of(it, Shingles.DEFAULT_N) }
            val candidates = candidatePairs(shingles, config)
            for ((i, j) in candidates) {
                if (uf.find(i) == uf.find(j)) continue
                if (!Shingles.lengthRatioPass(shingles[i], shingles[j], config.similarityThreshold)) continue
                val sim = Shingles.jaccard(shingles[i], shingles[j])
                if (sim >= config.similarityThreshold) {
                    if (uf.union(i, j)) edges.add(Edge(i, j, DedupKind.SIMILAR, sim))
                }
            }
        }

        return buildReport(sources, uf, edges, config)
    }

    private fun buildReport(
        sources: List<BookSourceDoc>,
        uf: UnionFind,
        edges: List<Edge>,
        config: DedupConfig,
    ): DedupReport {
        val clusters = LinkedHashMap<Int, MutableList<Int>>()
        for (i in sources.indices) clusters.getOrPut(uf.find(i)) { ArrayList(2) }.add(i)

        val strongestKind = HashMap<Int, DedupKind>()
        val strongestSim = HashMap<Int, Double>()
        for (e in edges) {
            val root = uf.find(e.a)
            val cur = strongestKind[root]
            if (cur == null || e.kind < cur) {
                strongestKind[root] = e.kind
                strongestSim[root] = e.similarity
            } else if (e.kind == cur && e.similarity > (strongestSim[root] ?: 0.0)) {
                strongestSim[root] = e.similarity
            }
        }

        val groups = ArrayList<DedupGroup>()
        for ((root, members) in clusters) {
            if (members.size < 2) continue
            val kind = strongestKind[root] ?: DedupKind.SIMILAR
            val keep = pickKeep(sources, members)
            groups.add(DedupGroup(kind, members, keep, strongestSim[root] ?: 1.0))
        }
        groups.sortWith(compareBy<DedupGroup> { it.kind }.thenByDescending { it.size })
        return DedupReport(groups, sources.size)
    }

    /**
     * Match the prototype's URL identity: protocol/www/query/fragment/path case
     * do not create a different source identity. The original URL is never modified.
     */
    private fun canonicalSourceUrl(raw: String): String = raw.trim()
        .lowercase()
        .replace(Regex("^https?://"), "")
        .removePrefix("www.")
        .substringBefore('?')
        .substringBefore('#')
        .trimEnd('/')

    /**
     * 组内选保留：规则完整度高者优先，其次名称干净、URL 合法（https）、有搜索地址。
     * 完全平等时取下标小的，保证结果稳定可复现。
     */
    fun pickKeep(sources: List<BookSourceDoc>, members: List<Int>): Int {
        var best = members.first()
        var bestScore = Int.MIN_VALUE
        for (i in members) {
            val s = sources[i]
            var score = 0
            score += s.ruleCompleteness * 10
            if (s.canSearch) score += 30
            if (s.url.startsWith("https://")) score += 5
            if (s.enabled) score += 3
            if (s.group.isNotEmpty()) score += 2
            // 名字越「干净」越好：被括号/营销后缀污染的扣分
            if (TidyText.key(s.name) == TidyText.key(TidyText.display(s.name)).lowercase()) score += 2
            if (score > bestScore) {
                bestScore = score
                best = i
            }
        }
        return best
    }

    private fun candidatePairs(shingles: List<IntArray>, config: DedupConfig): Set<Pair<Int, Int>> {
        val index = HashMap<Int, MutableList<Int>>(shingles.size * 8)
        shingles.forEachIndexed { doc, arr ->
            for (sh in arr) index.getOrPut(sh) { ArrayList(4) }.add(doc)
        }
        val shared = HashMap<Long, Int>(shingles.size * 4)
        val n = shingles.size.toLong()
        for (docs in index.values) {
            if (docs.size < 2 || docs.size > config.maxShingleDocFreq) continue
            for (x in docs.indices) for (y in x + 1 until docs.size) {
                val i = docs[x].toLong()
                val j = docs[y].toLong()
                val key = if (i < j) i * n + j else j * n + i
                shared[key] = (shared[key] ?: 0) + 1
            }
        }
        val out = HashSet<Pair<Int, Int>>()
        for ((key, count) in shared) {
            if (count < config.minSharedShingles) continue
            val i = (key / n).toInt()
            val j = (key % n).toInt()
            out.add(i to j)
        }
        return out
    }

    private data class Edge(val a: Int, val b: Int, val kind: DedupKind, val similarity: Double)
}

internal class UnionFind(size: Int) {
    private val parent = IntArray(size) { it }
    private val rank = ByteArray(size)

    fun find(x: Int): Int {
        var r = x
        while (parent[r] != r) r = parent[r]
        var c = x
        while (parent[c] != c) {
            val next = parent[c]
            parent[c] = r
            c = next
        }
        return r
    }

    fun union(a: Int, b: Int): Boolean {
        var ra = find(a)
        var rb = find(b)
        if (ra == rb) return false
        if (rank[ra] < rank[rb]) { val t = ra; ra = rb; rb = t }
        parent[rb] = ra
        if (rank[ra] == rank[rb]) rank[ra] = (rank[ra] + 1).toByte()
        return true
    }
}
