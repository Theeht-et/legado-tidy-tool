package io.legado.app.utils

/**
 * 繁简转换（t2s / s2t）。
 *
 * ## 为什么必须真实实现
 * 实测 `java.t2s(...)` 在书源里被调用 96 次，很多源是“繁体站源 + 转简体”。
 * 转错就是错字，会直接影响仿真对比结论。
 *
 * ## 数据来源（与上游完全一致，保真度 = 逐字一致）
 * 上游走 `com.github.liuyueyi.quick-chinese-transfer:quick-transfer-core:0.2.17`
 * （`gradle/libs.versions.toml` 里 pin 的版本），字典随 jar 打包，位于 `tc/` 目录下的 txt，
 * **不在 assets 里**。这里把那两个字典原样搬进 `headless/src/main/resources/tc/`，
 * 用 classpath 读取，转换算法按该库 0.2.17 的源码（Trie 最长匹配 + 单字表）重写：
 *   - `tc/t2s.txt`（9978 行）→ [t2s]
 *   - `tc/s2t.txt`（52825 行）→ [s2t]
 * 两文件均与 `quick-transfer-core-0.2.17.jar` 内字节 **cmp 完全一致**（见 resources/tc/NOTICE.txt 的 sha256）。
 *
 * ## 与上游 legado 封装层的差异
 * - 上游 legado 的 `ChineseUtils` 还暴露 `preLoad` / `unLoad`（参数是第三方 `TransType`）。
 *   引擎只用 `t2s` / `s2t`，且 headless 不引入 quick-transfer 依赖（否则又要拖一个包），
 *   所以这里只保留 `t2s` / `s2t` 两个签名。
 * - 上游的 `fixT2sDict()`（一次性加载 t2s 排除词）改为在 [t2s] 首次使用时施加，
 *   排除词列表逐字照抄，转换结果与上游一致。
 */
object ChineseUtils {

    /** 上游 `fixT2sDict()` 里的排除词，逐字照抄，仅作用于 t2s。 */
    private val t2sExcludeList = listOf(
        "槃",
        "划槳", "列根", "雪梨", "雪糕", "多士", "起司", "芝士", "沙芬", "母音",
        "华乐", "民乐", "晶元", "晶片", "映像", "明覆", "明瞭", "新力", "新喻",
        "零錢", "零钱", "離線", "碟片", "模組", "桌球", "案頭", "機車", "電漿",
        "鳳梨", "魔戒", "載入", "菲林", "整合", "變數", "解碼", "散钱", "插水",
        "房屋", "房价", "快取", "德士", "建立", "常式", "席丹", "布殊", "布希",
        "巴哈", "巨集", "夜学", "向量", "半形", "加彭", "列印", "函式", "全形",
        "光碟", "介面", "乳酪", "沈船", "永珍", "演化", "牛油", "相容", "磁碟",
        "菲林", "規則", "酵素", "雷根", "饭盒",
        "路易斯", "非同步", "出租车", "周杰倫", "马铃薯", "馬鈴薯", "機械人", "電單車",
        "電扶梯", "音效卡", "飆車族", "點陣圖", "個入球", "顆進球", "沃尓沃", "晶片集",
        "斯瓦巴", "斜角巷", "战列舰", "快速面", "希特拉", "太空梭", "吐瓦魯", "吉布堤",
        "吉布地", "史太林", "南冰洋", "区域网", "波札那", "解析度", "酷洛米", "金夏沙",
        "魔獸紀元", "高空彈跳", "铁达尼号", "太空战士", "埃及妖后", "吉里巴斯", "附加元件",
        "魔鬼終結者", "純文字檔案", "奇幻魔法Melody", "列支敦斯登"
    )

    private val t2sDictionary: TransferDictionary by lazy {
        TransferDictionary.load("tc/t2s.txt").apply {
            t2sExcludeList.forEach(::remove)
        }
    }

    private val s2tDictionary: TransferDictionary by lazy {
        TransferDictionary.load("tc/s2t.txt")
    }

    fun s2t(content: String): String {
        return s2tDictionary.convert(content)
    }

    fun t2s(content: String): String {
        return t2sDictionary.convert(content)
    }
}

/**
 * quick-transfer 0.2.17 `BasicDictionary` 的等价实现：
 * 单字表 + Trie 最长匹配，逐字符扫描。
 */
private class TransferDictionary(
    private val charMap: HashMap<Char, Char>,
    private val root: TrieNode,
) {

    companion object {
        private const val EQUAL = '='
        private const val SHARP = '#'

        fun load(mappingFile: String): TransferDictionary {
            val charMap = HashMap<Char, Char>(8192)
            val root = TrieNode()
            val stream = TransferDictionary::class.java.classLoader
                .getResourceAsStream(mappingFile)
                ?: throw IllegalStateException("繁简字典缺失：$mappingFile")
            stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (rawLine in lines) {
                    val line = rawLine
                    if (line.isEmpty() || line[0] == SHARP) {
                        continue
                    }
                    val index = line.indexOf(EQUAL)
                    if (index < 0) {
                        continue
                    }
                    val from = line.substring(0, index)
                    val to = line.substring(index + 1)
                    if (from.length == 1 && to.length == 1) {
                        charMap[from[0]] = to[0]
                    } else {
                        root.add(from, to)
                    }
                }
            }
            return TransferDictionary(charMap, root)
        }
    }

    fun convert(str: String): String {
        val sb = StringBuilder(str.length)
        var i = 0
        while (i < str.length) {
            val match = root.bestMatch(str, i)
            if (match != null) {
                sb.append(match.value)
                i += match.length
            } else {
                sb.append(charMap[str[i]] ?: str[i])
                i++
            }
        }
        return sb.toString()
    }

    /** 上游 `BasicDictionary.remove`：单字从单字表删；词组加自身映射（即“不转换”）。 */
    fun remove(key: String) {
        if (key.length == 1) {
            charMap.remove(key[0])
        } else {
            root.add(key, key)
        }
    }
}

private class Match(val length: Int, val value: String)

private class TrieNode {
    var leaf = false
    var value: String = ""
    val children = HashMap<Char, TrieNode>()

    fun add(key: String, value: String) {
        if (key.isEmpty()) {
            return
        }
        var node = this
        for (c in key) {
            node = node.children.getOrPut(c) { TrieNode() }
        }
        node.leaf = true
        node.value = value
    }

    /** 最长前缀匹配；返回 null 表示当前位置没有任何词条。 */
    fun bestMatch(s: String, offset: Int): Match? {
        var node = this
        var best: Match? = null
        var i = offset
        while (i < s.length) {
            node = node.children[s[i]] ?: break
            i++
            if (node.leaf) {
                best = Match(i - offset, node.value)
            }
        }
        return best
    }
}

