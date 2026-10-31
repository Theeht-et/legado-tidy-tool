package io.github.theeht.legadotidy.desktop

import io.github.theeht.legadotidy.engine.LegadoProbeEngine
import io.github.theeht.legadotidy.export.ExportConfig
import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.model.SourceIO
import io.github.theeht.legadotidy.pipeline.Phase
import io.github.theeht.legadotidy.pipeline.Pipeline
import io.github.theeht.legadotidy.pipeline.PipelineConfig
import io.github.theeht.legadotidy.pipeline.PipelineEvent
import io.github.theeht.legadotidy.probe.ProbeDepth
import io.github.theeht.legadotidy.probe.ProbeOptions
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Path

/**
 * 命令行入口。
 *
 * 界面出问题时先跑它：这条路对了、界面不对，问题就只在界面。
 * 批量跑几千个源时也应该用它——比界面里盯着进度条靠谱，而且 `--verbose` 能逐条看清是哪个源、因为什么失败。
 *
 * ```
 * legado-tidy --in sources.json [--out 输出目录] [--keyword 剑来]
 *             [--limit N] [--concurrency 100] [--skip-probe] [--with-toc|--full-chain] [--timeout 20] [--verbose]
 * ```
 */
fun runCli(args: Array<String>): Int {
    if (args.any { it == "--help" || it == "-h" }) {
        printUsage()
        return 0
    }
    val opts = parseArgs(args) ?: run {
        printUsage()
        return 2
    }

    val input = File(opts.input)
    if (!input.isFile) {
        System.err.println("找不到输入文件：${input.absolutePath}")
        return 2
    }

    val docs = runCatching { SourceIO.load(input.toPath()).map { BookSourceDoc(it) } }.getOrElse {
        System.err.println("解析失败：${it.message ?: it::class.java.simpleName}")
        return 2
    }
    if (docs.isEmpty()) {
        System.err.println("文件里没有书源：${input.absolutePath}")
        return 2
    }

    val selected = if (opts.limit > 0) docs.take(opts.limit) else docs
    println("读到 ${docs.size} 个书源" + if (selected.size != docs.size) "，本次只处理前 ${selected.size} 个" else "")

    val config = PipelineConfig(
        concurrency = opts.concurrency,
        skipProbe = opts.skipProbe,
        probe = ProbeOptions(
            depth = when {
                opts.fullChain -> ProbeDepth.FULL_CHAIN
                opts.withToc -> ProbeDepth.WITH_TOC
                else -> ProbeDepth.SEARCH
            },
            timeoutMs = opts.timeoutSeconds * 1000L,
            keyword = opts.keyword,
            proxy = opts.proxy,
        ),
        export = ExportConfig(applyCleanNames = false),
        exportDir = Path.of(opts.out),
    )

    val started = System.currentTimeMillis()
    val result = runBlocking {
        Pipeline(LegadoProbeEngine()).run(selected, config) { event -> render(event) }
    }
    val cost = System.currentTimeMillis() - started

    println()
    println("用时 ${"%.1f".format(cost / 1000.0)} 秒")
    if (!opts.skipProbe) {
        val stat = result.probeResults.values
            .groupingBy { it.status }.eachCount()
            .entries.sortedBy { it.key.severity }
        println("校验：" + stat.joinToString("，") { "${it.key.label} ${it.value}" })
    }
    println(
        "去重：${result.dedup.groups.size} 组重复，建议保留 ${result.dedup.keepCount}/${result.dedup.total}"
    )
    println("整理：${result.organize.pools.size} 个分组，${result.organize.sources.size} 个源")
    result.exported?.let { println("导出：${it.count} 个源 → ${it.file.toAbsolutePath()}") }

    if (opts.verbose) {
        println()
        println("逐个结果：")
        result.probeResults.toSortedMap().forEach { (index, probe) ->
            val name = selected.getOrNull(index)?.name?.take(22) ?: "#$index"
            val http = probe.httpCode?.let { " HTTP $it" }.orEmpty()
            val detail = probe.detail?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            println(
                "  #%-3d %-22s %-12s %6dms%s%s".format(
                    index, name, probe.status.label, probe.elapsedMs, http, detail,
                )
            )
        }
    }
    return 0
}

class Options(
    val input: String,
    val out: String,
    val keyword: String,
    val timeoutSeconds: Long,
    val limit: Int,
    val concurrency: Int,
    val skipProbe: Boolean,
    val withToc: Boolean,
    val proxy: String?,
    val verbose: Boolean = false,
    val fullChain: Boolean = false,
)

private fun parseArgs(args: Array<String>): Options? {
    var input: String? = null
    var out = "out"
    var keyword = "剑来"
    var timeout = 20L
    var limit = 0
    var concurrency = 100
    var skipProbe = false
    var withToc = false
    var fullChain = false
    var proxy: String? = null
    var verbose = false

    var i = 0
    while (i < args.size) {
        when (val a = args[i]) {
            "--in" -> input = args.getOrNull(++i)
            "--out" -> out = args.getOrNull(++i) ?: out
            "--keyword" -> keyword = args.getOrNull(++i) ?: keyword
            "--timeout" -> timeout = args.getOrNull(++i)?.toLongOrNull() ?: timeout
            "--limit" -> limit = args.getOrNull(++i)?.toIntOrNull() ?: limit
            "--concurrency" -> concurrency = args.getOrNull(++i)?.toIntOrNull() ?: concurrency
            "--proxy" -> proxy = args.getOrNull(++i)?.takeIf { it.isNotBlank() }
            "--skip-probe" -> skipProbe = true
            "--with-toc" -> withToc = true
            "--full-chain" -> fullChain = true
            "--verbose", "-v" -> verbose = true
            "--help", "-h" -> return null
            else -> {
                if (a.startsWith("-")) {
                    System.err.println("未知参数：$a")
                    return null
                }
                if (input == null) input = a else {
                    System.err.println("多余的参数：$a")
                    return null
                }
            }
        }
        i++
    }
    if (input.isNullOrBlank()) return null
    return Options(
        input, out, keyword, timeout, limit, concurrency.coerceIn(1, 2000),
        skipProbe, withToc, proxy, verbose, fullChain,
    )
}

private fun printUsage() {
    println(
        """
        用法: legado-tidy --in <书源JSON> [选项]

          不带参数启动图形界面。

          --in <文件>          书源 JSON（数组 / 单对象 / {sources:[...]} 都支持）
          --out <目录>         导出目录，默认 out
          --keyword <书名>     校验用的关键词，默认「剑来」
          --timeout <秒>       单个书源超时，默认 20
          --limit <N>          只处理前 N 个（调试用）
          --concurrency <N>    并发书源数，默认 100（上限 2000）
          --proxy <地址>       代理，如 http://127.0.0.1:7890
          --skip-probe         跳过 S2 校验（只做去重与整理，秒级完成）
          --with-toc           搜索后按上游顺序校验书籍详情与目录
          --full-chain         搜索后完整校验详情、目录与第一条有效章节正文（最慢）
          --verbose, -v        逐条列出每个书源的校验结果与失败原因
        """.trimIndent()
    )
}

private fun render(event: PipelineEvent) {
    when (event) {
        is PipelineEvent.Log -> println(event.message)
        is PipelineEvent.PhaseStarted -> {
            // 阶段之间空一行，否则四段进度会挤在一起。
            if (event.phase != Phase.DEDUP) println()
            println("== ${event.phase.label} ==")
        }
        is PipelineEvent.Progress -> {
            // 进度写同一行，每几个刷新一次，避免几千个源刷屏。
            print("\r  ${event.done}/${event.total}（可用 ${event.ok}，失败 ${event.failed}）")
            System.out.flush()
        }
        is PipelineEvent.PhaseFinished -> {
            val sec = "%.1f".format(event.elapsedMs / 1000.0)
            println("\n  ${event.phase.label} 完成：${event.note}（${sec}s）")
        }
        is PipelineEvent.Failed -> System.err.println("失败：${event.message}")
    }
}
