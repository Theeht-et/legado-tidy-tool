package io.github.theeht.legadotidy.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.theeht.legadotidy.engine.LegadoProbeEngine
import io.github.theeht.legadotidy.export.DirectLink
import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.model.SourceIO
import io.github.theeht.legadotidy.pipeline.Phase
import io.github.theeht.legadotidy.pipeline.Pipeline
import io.github.theeht.legadotidy.pipeline.PipelineConfig
import io.github.theeht.legadotidy.pipeline.PipelineEvent
import io.github.theeht.legadotidy.probe.ProbeDepth
import io.github.theeht.legadotidy.probe.ProbeOptions
import io.github.theeht.legadotidy.probe.ProbeStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.nio.file.Path
import javax.swing.JFileChooser

private val Primary = Color(0xFF2563EB)
private val Ok = Color(0xFF15803D)
private val Warn = Color(0xFFB45309)
private val Bad = Color(0xFFB91C1C)
private val Muted = Color(0xFF6B7280)
private val Line = Color(0xFFE5E7EB)

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    surface = Color.White,
    background = Color(0xFFF8FAFC),
)

@Composable
fun TidyApp() {
    val state = remember { AppState() }
    var tab by remember { mutableStateOf(Tab.WORKBENCH) }

    MaterialTheme(colorScheme = LightColors) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                TopBar(state)
                HorizontalDivider(color = Line)
                Row(Modifier.fillMaxSize()) {
                    Rail(tab) { tab = it }
                    Box(Modifier.fillMaxSize().background(Color(0xFFF8FAFC))) {
                        when (tab) {
                            Tab.WORKBENCH -> Workbench(state)
                            Tab.POOL -> Pool(state)
                            Tab.REPORT -> Report(state)
                            Tab.SETTINGS -> Settings(state)
                        }
                    }
                }
            }
        }
    }
}

private enum class Tab(val label: String) {
    WORKBENCH("工作台"),
    POOL("书源池"),
    REPORT("报告"),
    SETTINGS("设置"),
}

@Composable
private fun TopBar(state: AppState) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("书源整理", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Primary)
        Spacer(Modifier.width(16.dp))
        Text(
            state.inputFile?.name?.let { "$it · ${state.docs.size} 个书源" } ?: "尚未选择文件",
            fontSize = 13.sp,
            color = Muted,
        )
        Spacer(Modifier.weight(1f))
        if (state.busy) {
            Text("运行中", fontSize = 13.sp, color = Primary)
        }
    }
}

@Composable
private fun Rail(current: Tab, onSelect: (Tab) -> Unit) {
    Column(
        Modifier.fillMaxHeight().width(148.dp).background(Color.White).padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Tab.entries.forEach { tab ->
            val selected = tab == current
            Row(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .background(if (selected) Color(0xFFEFF4FF) else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tab.label, fontSize = 14.sp, color = if (selected) Primary else Color(0xFF374151))
            }
        }
    }
}

// ---------------------------------------------------------------- 工作台

@Composable
private fun Workbench(state: AppState) {
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }

    fun pickFile() {
        val dialog = FileDialog(null as Frame?, "选择书源文件", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name -> name.endsWith(".json", ignoreCase = true) }
        dialog.isVisible = true
        val dir = dialog.directory ?: return
        val name = dialog.file ?: return
        state.parseError = null
        state.inputFile = File(dir, name)
        try {
            val parsed = SourceIO.parse(state.inputFile!!.readText())
            state.docs = parsed.map { BookSourceDoc(it) }
            state.result = null
            state.log("读到 ${state.docs.size} 个书源：$name")
        } catch (e: Exception) {
            state.docs = emptyList()
            state.parseError = "解析失败：${e.message ?: e::class.java.simpleName}"
        }
    }

    fun start() {
        if (state.docs.isEmpty() || state.busy) return
        state.reset()
        state.busy = true
        job = scope.launch {
            try {
                val config = PipelineConfig(
                    concurrency = state.concurrency,
                    skipProbe = state.skipProbe,
                    probe = ProbeOptions(
                        depth = if (state.withToc) ProbeDepth.WITH_TOC else ProbeDepth.SEARCH,
                        timeoutMs = state.timeoutSeconds * 1000L,
                        keyword = state.keyword,
                        proxy = state.proxy.takeIf { it.isNotBlank() },
                    ),
                    exportDir = run {
                        // 目录可能被用户删了，跑之前补建一次。
                        val dir = File(state.exportDir)
                        dir.mkdirs()
                        dir.toPath()
                    },
                )
                val result = Pipeline(LegadoProbeEngine()).run(state.docs, config) { event ->
                    withContext(Dispatchers.Main) { apply(state, event) }
                }
                state.result = result
            } catch (e: CancellationException) {
                state.log("已取消")
            } catch (e: Throwable) {
                state.log("失败：${e.message ?: e::class.java.simpleName}")
            } finally {
                state.busy = false
                state.cancelling = false
                job = null
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = ::pickFile, enabled = !state.busy) { Text("选择书源文件") }
            OutlinedButton(onClick = ::start, enabled = !state.busy && state.docs.isNotEmpty()) { Text("开始处理") }
            if (state.busy) {
                OutlinedButton(
                    onClick = { state.cancelling = true; job?.cancel() },
                    enabled = !state.cancelling,
                ) { Text(if (state.cancelling) "正在停止…" else "停止") }
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (state.skipProbe) "跳过校验" else "关键词「${state.keyword}」",
                fontSize = 12.sp,
                color = Muted,
            )
        }

        state.parseError?.let { Text(it, color = Bad, fontSize = 13.sp) }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PhaseCard("S1 去重", state, Phase.DEDUP, Modifier.weight(1f))
            PhaseCard("S2 校验", state, Phase.PROBE, Modifier.weight(1f))
            PhaseCard("S3 整理", state, Phase.ORGANIZE, Modifier.weight(1f))
            PhaseCard("S4 导出", state, Phase.EXPORT, Modifier.weight(1f))
        }

        Progress(state)
        LogPane(state, Modifier.weight(1f))
    }
}

@Composable
private fun PhaseCard(title: String, state: AppState, phase: Phase, modifier: Modifier = Modifier) {
    val note = state.notes[phase]
    val active = state.busy && state.phase == phase
    Card(
        modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (active) Color(0xFFEFF4FF) else Color.White,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Primary)
            Text(
                note ?: if (active) "进行中…" else "等待",
                fontSize = 12.sp,
                color = if (note != null) Color(0xFF111827) else Muted,
            )
        }
    }
}

@Composable
private fun Progress(state: AppState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth().height(6.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(state.phase?.label ?: "未开始", fontSize = 12.sp, color = Muted)
            if (state.total > 0) {
                Text("${state.done}/${state.total}", fontSize = 12.sp, color = Muted)
                Text("可用 ${state.ok}", fontSize = 12.sp, color = Ok)
                Text("失败 ${state.failed}", fontSize = 12.sp, color = if (state.failed > 0) Bad else Muted)
            }
        }
    }
}

@Composable
private fun LogPane(state: AppState, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
        LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
            itemsIndexed(state.logs) { _, line ->
                Text(
                    line,
                    fontSize = 12.sp,
                    color = Color(0xFFE2E8F0),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 书源池

@Composable
private fun Pool(state: AppState) {
    val results = state.result?.probeResults.orEmpty()
    val organized = state.result?.organize?.sources.orEmpty().associateBy { it.index }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("书源池 · ${state.docs.size} 个", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (state.docs.isEmpty()) {
            Text("还没有载入书源。回到工作台选择文件。", fontSize = 13.sp, color = Muted)
            return@Column
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text("名称", Modifier.weight(2.2f), fontSize = 12.sp, color = Muted)
            Text("分组", Modifier.weight(1.2f), fontSize = 12.sp, color = Muted)
            Text("状态", Modifier.weight(1.4f), fontSize = 12.sp, color = Muted)
            Text("权重", Modifier.weight(0.7f), fontSize = 12.sp, color = Muted)
            Text("地址", Modifier.weight(3f), fontSize = 12.sp, color = Muted)
        }
        HorizontalDivider(color = Line)
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.docs.withIndex().toList()) { (index, doc) ->
                val probe = results[index]
                val org = organized[index]
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(doc.name ?: "（未命名）", Modifier.weight(2.2f), fontSize = 13.sp)
                    Text(org?.group ?: doc.group ?: "—", Modifier.weight(1.2f), fontSize = 12.sp, color = Muted)
                    Box(Modifier.weight(1.4f)) {
                        if (probe == null) {
                            Text("未校验", fontSize = 12.sp, color = Muted)
                        } else {
                            StatusChip(probe.status)
                        }
                    }
                    Text((org?.rank ?: 0).toString(), Modifier.weight(0.7f), fontSize = 12.sp, color = Muted)
                    Text(
                        doc.url ?: "—",
                        Modifier.weight(3f),
                        fontSize = 12.sp,
                        color = Muted,
                        maxLines = 1,
                    )
                }
                HorizontalDivider(color = Color(0xFFF1F5F9))
            }
        }
    }
}

@Composable
private fun StatusChip(status: ProbeStatus) {
    val color = when {
        status.severity <= 0 -> Ok
        status.severity == 1 -> Warn
        else -> Bad
    }
    Text(
        status.label,
        fontSize = 12.sp,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

// ---------------------------------------------------------------- 报告

@Composable
private fun Report(state: AppState) {
    val result = state.result
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("报告", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (result == null) {
            Text("还没有跑过。回到工作台点「开始处理」。", fontSize = 13.sp, color = Muted)
            return@Column
        }

        val dedup = result.dedup
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("去重", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "共 ${dedup.total} 个源，归并为 ${dedup.groups.size} 组，建议保留 ${dedup.keepCount} 个",
                    fontSize = 13.sp,
                )
                if (dedup.groups.isNotEmpty()) {
                    Text(
                        "按「完全重复 → 指纹重复 → 高相似」三层逐步合并；不会自动删任何源，保留哪个由你决定。",
                        fontSize = 12.sp,
                        color = Muted,
                    )
                }
            }
        }

        if (!state.skipProbe) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("校验", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    val stat = result.probeResults.values.groupingBy { it.status }.eachCount()
                        .entries.sortedBy { it.key.severity }
                    val total = result.probeResults.size.coerceAtLeast(1)
                    stat.forEach { (status, count) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(84.dp)) { StatusChip(status) }
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .background(Color(0xFFF1F5F9), RoundedCornerShape(4.dp)),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(count.toFloat() / total)
                                        .fillMaxHeight()
                                        .background(
                                            when {
                                                status.severity <= 0 -> Ok
                                                status.severity == 1 -> Warn
                                                else -> Bad
                                            },
                                            RoundedCornerShape(4.dp),
                                        ),
                                )
                            }
                            Text(
                                "$count",
                                Modifier.width(52.dp),
                                fontSize = 12.sp,
                                color = Muted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            )
                        }
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("整理与导出", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("${result.organize.pools.size} 个分组，${result.organize.sources.size} 个源", fontSize = 13.sp)
                result.exported?.let { exported ->
                    Text("已导出 ${exported.count} 个源 → ${exported.file.toAbsolutePath()}", fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            runCatching { Desktop.getDesktop().open(exported.file.parent.toFile()) }
                        }) { Text("打开所在目录") }
                        TextButton(onClick = {
                            // 复制路径比「打开目录」更快：用户常常要把它粘到别处。
                            runCatching {
                                Toolkit.getDefaultToolkit().systemClipboard
                                    .setContents(StringSelection(exported.file.toAbsolutePath().toString()), null)
                            }
                        }) { Text("复制路径") }
                    }
                }
                // 直链：只有填了地址才出现。没地址时给二维码只会误导人（QR 也装不下整份书源）。
                val direct = DirectLink.let { link ->
                    state.directUrl.takeIf { DirectLink.isUsable(it) }?.let {
                        runCatching { link.forSourceUrl(it) }.getOrNull()
                    }
                }
                if (direct != null) {
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    Text("一键导入链接（手机上用 Legado 打开）", fontSize = 12.sp, color = Muted)
                    Text(direct, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF111827))
                    TextButton(onClick = {
                        runCatching {
                            Toolkit.getDefaultToolkit().systemClipboard
                                .setContents(StringSelection(direct), null)
                        }
                    }) { Text("复制导入链接") }
                }
            }
        }

        // 失败明细：这是这个工具最该给出的东西——不是「失败 37 个」，
        // 而是「这 37 个各自因为什么失败」。按严重度、再按名称排，方便成批外理。
        val problems = result.probeResults.entries
            .filter { it.value.status.severity > 0 }
            .sortedWith(compareBy({ it.value.status.severity }, { state.docs.getOrNull(it.key)?.name ?: "" }))
        if (problems.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("失败明细 · ${problems.size} 个", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "同一状态往往对应同一种修法：超时→降并发或换关键词；网络不通→源站己挂或需要代理；" +
                            "需要浏览器→只能去 App 里手验。",
                        fontSize = 12.sp,
                        color = Muted,
                    )
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    // 只列前 200 个：几千个源全展开会让页面卡住，而人一次也看不过来。
                    problems.take(200).forEach { (index, probe) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(96.dp)) { StatusChip(probe.status) }
                            Text(
                                state.docs.getOrNull(index)?.name ?: "（未命名）",
                                Modifier.weight(1f),
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                            Text(
                                probe.detail?.takeIf { it.isNotBlank() } ?: "—",
                                Modifier.weight(1.4f),
                                fontSize = 11.sp,
                                color = Muted,
                                maxLines = 1,
                            )
                        }
                    }
                    if (problems.size > 200) {
                        Text("还有 ${problems.size - 200} 个未列出；导出后可用于外部分析。", fontSize = 12.sp, color = Muted)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 设置

@Composable
private fun Settings(state: AppState) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("设置", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("改动即时生效，开始处理时读当前值。", fontSize = 12.sp, color = Muted)

        OutlinedTextField(
            value = state.keyword,
            onValueChange = { state.keyword = it },
            label = { Text("校验关键词（找一本书来验证源可用）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.concurrency.toString(),
            onValueChange = { text -> text.toIntOrNull()?.let { state.concurrency = it.coerceIn(1, 2000) } },
            label = { Text("并发数（1–2000，默认 100）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.timeoutSeconds.toString(),
            onValueChange = { text -> text.toIntOrNull()?.let { state.timeoutSeconds = it.coerceIn(5, 120) } },
            label = { Text("单个书源超时（秒，5–120）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.proxy,
            onValueChange = { state.proxy = it },
            label = { Text("代理（可空，如 http://127.0.0.1:7890）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        ToggleRow("跳过校验（只做去重与整理，秒级完成）", state.skipProbe) { state.skipProbe = it }
        ToggleRow("校验时连目录一起验（更准，慢 2~3 倍）", state.withToc) { state.withToc = it }

        HorizontalDivider(color = Line)
        Text("导出", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.exportDir,
                onValueChange = { state.exportDir = it },
                label = { Text("导出目录") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = {
                // AWT 的 FileDialog 在 Windows 上没有「只选目录」模式，所以这里用 Swing 的选择器。
                val chooser = JFileChooser(File(state.exportDir).takeIf { it.isDirectory })
                    .apply {
                        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                        dialogTitle = "选导出目录"
                    }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    state.exportDir = chooser.selectedFile.absolutePath
                }
            }) { Text("选择…") }
        }
        Text(
            "默认放在「文档\\legado-tidy」。导出文件名带时间戳，不会覆盖上一次的结果。",
            fontSize = 12.sp,
            color = Muted,
        )

        OutlinedTextField(
            value = state.directUrl,
            onValueChange = { state.directUrl = it },
            label = { Text("直链地址（可空）：导出文件上传后的公开 URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "填了才会在报告页出现「一键导入链接」（legado://import/auto?src=…）。" +
                "工具不会帮你上传文件——那需要公网服务或局域网监听，前者超出本项目范围，后者被" +
                "「不做局域网 HTTP」这条约束排除了。本地文件导入始终可用，不需要这一栏。",
            fontSize = 12.sp,
            color = Muted,
        )

        HorizontalDivider(color = Line)
        Text(
            "细则：并发越高越快，但超时会快速变多——源站点自己扛不住，不是你网络的问题。" +
                "想看清慢在哪，先把并发降到 50 以下再跑一次。",
            fontSize = 12.sp,
            color = Muted,
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ---------------------------------------------------------------- 事件 → 状态

private fun apply(state: AppState, event: PipelineEvent) {
    when (event) {
        is PipelineEvent.Log -> state.log(event.message)
        is PipelineEvent.PhaseStarted -> {
            state.phase = event.phase
            state.done = 0
            state.total = 0
            state.ok = 0
            state.failed = 0
            state.log("== ${event.phase.label} ==")
        }
        is PipelineEvent.Progress -> {
            state.done = event.done
            state.total = event.total
            state.ok = event.ok
            state.failed = event.failed
        }
        is PipelineEvent.PhaseFinished -> {
            val seconds = "%.1f".format(event.elapsedMs / 1000.0)
            state.notes[event.phase] = "${event.note}（${seconds}s）"
            state.log("${event.phase.label} 完成：${event.note}（${seconds}s）")
        }
        is PipelineEvent.Failed -> state.log("失败：${event.message}")
    }
}

