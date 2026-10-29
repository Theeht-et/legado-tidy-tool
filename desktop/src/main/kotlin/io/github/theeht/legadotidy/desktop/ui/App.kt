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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.theeht.legadotidy.dedup.DedupKind
import io.github.theeht.legadotidy.dedup.DedupReport
import io.github.theeht.legadotidy.dedup.Deduper
import io.github.theeht.legadotidy.engine.LegadoProbeEngine
import io.github.theeht.legadotidy.export.DirectLink
import io.github.theeht.legadotidy.export.ExportConfig
import io.github.theeht.legadotidy.export.ExportResult
import io.github.theeht.legadotidy.export.Exporter
import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.model.SourceField
import io.github.theeht.legadotidy.model.SourceIO
import io.github.theeht.legadotidy.organize.OrganizeConfig
import io.github.theeht.legadotidy.organize.OrganizeRule
import io.github.theeht.legadotidy.organize.Organizer
import io.github.theeht.legadotidy.organize.OrganizedSource
import io.github.theeht.legadotidy.probe.ProbeDepth
import io.github.theeht.legadotidy.probe.ProbeOptions
import io.github.theeht.legadotidy.probe.ProbeResult
import io.github.theeht.legadotidy.probe.ProbeStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
private val Canvas = Color(0xFFF8FAFC)
private val SoftBlue = Color(0xFFEFF4FF)

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    surface = Color.White,
    background = Canvas,
)

private enum class WorkflowStage(val label: String, val help: String) {
    IMPORT("导入", "导入书源文件"),
    DEDUP_REVIEW("去重审核", "系统已推荐保留项；请检查重复组"),
    PROBE_REVIEW("校验审核", "真实引擎校验完成后，请检查待删除建议"),
    ORGANIZE_REVIEW("整理预览", "先选择规则，再生成可编辑预览"),
    EXPORT("导出", "导出当前已确认的书源工作集"),
}

private data class WorkflowSource(
    val id: Int,
    val doc: BookSourceDoc,
    val duplicateGroup: Int? = null,
    val probe: ProbeResult? = null,
    val deleteMarked: Boolean = false,
    val suggestion: String = "待去重",
)

private data class DuplicateGroupUi(
    val number: Int,
    val kind: DedupKind,
    val sourceIds: List<Int>,
    val suggestedKeepId: Int,
    val similarity: Double,
)

private data class PreviewSource(
    val name: String,
    val group: String,
    val status: ProbeStatus,
    val rank: Int,
    val nameEdited: Boolean = false,
    val groupEdited: Boolean = false,
)

private sealed class SourceListRow(val key: String) {
    data class GroupHeader(val group: DuplicateGroupUi) : SourceListRow("group-${group.number}")
    data class SourceEntry(val source: WorkflowSource) : SourceListRow("source-${source.id}")
}

private class WorkflowState {
    var stage by mutableStateOf(WorkflowStage.IMPORT)
    val sources = mutableStateListOf<WorkflowSource>()
    var inputFile by mutableStateOf<File?>(null)
    var inputCount by mutableStateOf(0)
    var selectedId by mutableStateOf<Int?>(null)
    var filterQuery by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    var completed by mutableStateOf(0)
    var total by mutableStateOf(0)
    var removedCount by mutableStateOf(0)
    var duplicateGroups by mutableStateOf<List<DuplicateGroupUi>>(emptyList())
    var duplicateReport by mutableStateOf<DedupReport?>(null)
    var preview by mutableStateOf<Map<Int, PreviewSource>?>(null)
    var previewOrder by mutableStateOf<List<Int>>(emptyList())
    var exported by mutableStateOf<ExportResult?>(null)

    var keyword by mutableStateOf("剑来")
    var concurrency by mutableStateOf(100)
    var timeoutSeconds by mutableStateOf(20)
    var skipProbe by mutableStateOf(false)
    var withToc by mutableStateOf(false)
    var proxy by mutableStateOf("")
    var exportDir by mutableStateOf(defaultExportDir())
    var directUrl by mutableStateOf("")

    var selectedRules by mutableStateOf<Set<OrganizeRule>>(emptySet())
    var applyCleanNames by mutableStateOf(false)
    var applyGroups by mutableStateOf(false)
    var applyOrder by mutableStateOf(false)

    var showImportInfo by mutableStateOf(false)
    var showSettings by mutableStateOf(false)
    var showReport by mutableStateOf(false)
}

@Composable
fun TidyApp() {
    val state = remember { WorkflowState() }
    val scope = rememberCoroutineScope()
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var editingId by remember { mutableStateOf<Int?>(null) }
    var comparingGroup by remember { mutableStateOf<Int?>(null) }

    fun importFile() {
        if (state.busy) return
        val dialog = FileDialog(null as Frame?, "选择书源 JSON 文件", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name -> name.endsWith(".json", ignoreCase = true) }
        dialog.isVisible = true
        val dir = dialog.directory ?: return
        val name = dialog.file ?: return
        val selectedFile = File(dir, name)
        try {
            val docs = SourceIO.parse(selectedFile.readText())
            if (docs.isEmpty()) {
                state.error = "文件中没有可导入的书源对象。"
                return
            }
            state.sources.clear()
            state.sources.addAll(docs.mapIndexed { index, raw -> WorkflowSource(index, BookSourceDoc(raw)) })
            state.inputFile = selectedFile
            state.inputCount = docs.size
            state.selectedId = state.sources.firstOrNull()?.id
            state.filterQuery = ""
            state.stage = WorkflowStage.IMPORT
            state.duplicateGroups = emptyList()
            state.duplicateReport = null
            state.preview = null
            state.previewOrder = emptyList()
            state.exported = null
            state.error = null
            state.notice = "已导入 ${docs.size} 个书源；原始文件不会被修改。"
            state.removedCount = 0
            state.completed = 0
            state.total = 0
        } catch (e: Exception) {
            state.error = "导入失败：${e.message ?: e::class.java.simpleName}"
        }
    }

    fun startDedup() {
        if (state.busy || state.sources.isEmpty()) return
        state.busy = true
        state.error = null
        state.notice = "正在分析重复候选，不会直接删除书源。"
        activeJob = scope.launch {
            try {
                val snapshot = state.sources.toList()
                val report = withContext(Dispatchers.Default) {
                    Deduper.run(snapshot.map { it.doc })
                }
                val groups = report.groups.mapIndexed { index, group ->
                    DuplicateGroupUi(
                        number = index + 1,
                        kind = group.kind,
                        sourceIds = group.members.mapNotNull { snapshot.getOrNull(it)?.id },
                        suggestedKeepId = snapshot[group.keep].id,
                        similarity = group.similarity,
                    )
                }
                val groupById = groups.flatMap { group -> group.sourceIds.map { it to group } }.toMap()
                state.sources.clear()
                state.sources.addAll(snapshot.map { source ->
                    val group = groupById[source.id]
                    val keep = group?.suggestedKeepId == source.id
                    source.copy(
                        duplicateGroup = group?.number,
                        deleteMarked = group != null && !keep,
                        suggestion = when {
                            group == null -> "未发现重复"
                            keep -> "建议保留"
                            else -> "建议删除"
                        },
                    )
                })
                state.duplicateGroups = groups
                state.duplicateReport = report
                state.stage = WorkflowStage.DEDUP_REVIEW
                state.selectedId = state.sources.firstOrNull()?.id
                state.notice = "去重分析完成：发现 ${groups.size} 个重复组。进入校验时才会应用删除标记。"
            } catch (e: CancellationException) {
                state.error = "去重分析已停止。"
            } catch (e: Throwable) {
                state.error = "去重分析失败：${e.message ?: e::class.java.simpleName}"
            } finally {
                state.busy = false
                activeJob = null
            }
        }
    }

    fun runValidation() {
        if (state.busy || state.sources.isEmpty()) return
        state.busy = true
        state.error = null
        state.total = state.sources.size
        state.completed = state.sources.count { it.probe != null }
        state.notice = if (state.skipProbe) "按设置跳过真实校验。" else "正在使用 Legado 引擎校验书源。"
        val snapshot = state.sources.toList()
        val pending = snapshot.filter { it.probe == null }
        val options = ProbeOptions(
            depth = if (state.withToc) ProbeDepth.WITH_TOC else ProbeDepth.SEARCH,
            timeoutMs = state.timeoutSeconds.coerceAtLeast(1) * 1000L,
            keyword = state.keyword,
            proxy = state.proxy.takeIf { it.isNotBlank() },
        )
        activeJob = scope.launch {
            try {
                val gate = Semaphore(state.concurrency.coerceIn(1, 2000))
                val probeEngine = LegadoProbeEngine()
                coroutineScope {
                    pending.map { source ->
                        async(Dispatchers.IO) {
                            val index = snapshot.indexOfFirst { it.id == source.id }
                            val result = if (state.skipProbe) {
                                ProbeResult(index, ProbeStatus.SKIPPED, detail = "按设置跳过校验")
                            } else {
                                try {
                                    gate.withPermit { probeEngine.probe(index, source.doc, options) }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Throwable) {
                                    ProbeResult(index, ProbeStatus.ERROR, detail = e.message ?: e::class.java.simpleName)
                                }
                            }
                            withContext(Dispatchers.Main) {
                                val currentIndex = state.sources.indexOfFirst { it.id == source.id }
                                if (currentIndex >= 0) {
                                    val deleteByDefault = result.status in setOf(
                                        ProbeStatus.NO_SEARCH_URL,
                                        ProbeStatus.UNSUPPORTED,
                                        ProbeStatus.PARSE_EMPTY,
                                    )
                                    state.sources[currentIndex] = state.sources[currentIndex].copy(
                                        probe = result,
                                        deleteMarked = deleteByDefault,
                                        suggestion = when {
                                            deleteByDefault -> "建议删除"
                                            result.status == ProbeStatus.OK -> "建议保留"
                                            result.status == ProbeStatus.SKIPPED -> "跳过校验"
                                            else -> "建议复核"
                                        },
                                    )
                                }
                                state.completed = (state.completed + 1).coerceAtMost(state.total)
                            }
                        }
                    }.awaitAll()
                }
                if (state.sources.all { it.probe != null }) {
                    val marked = state.sources.count { it.deleteMarked }
                    state.notice = "校验完成。当前有 ${marked} 个书源被标记为待删除；网络错误和超时默认建议复核。"
                }
            } catch (e: CancellationException) {
                state.error = "校验已停止；已完成的结果已保留，可继续校验剩余书源。"
            } catch (e: Throwable) {
                state.error = "校验任务失败：${e.message ?: e::class.java.simpleName}"
            } finally {
                state.busy = false
                activeJob = null
            }
        }
    }

    fun confirmDedupAndValidate() {
        if (!commitMarkedDeletions(state)) return
        state.stage = WorkflowStage.PROBE_REVIEW
        state.sources.indices.forEach { index ->
            state.sources[index] = state.sources[index].copy(duplicateGroup = null, probe = null)
        }
        runValidation()
    }

    fun confirmValidation() {
        if (state.sources.any { it.probe == null }) {
            state.error = "还有书源没有校验结果。请完成或停止后继续校验，再进入整理。"
            return
        }
        if (!commitMarkedDeletions(state)) return
        state.sources.indices.forEach { index ->
            state.sources[index] = state.sources[index].copy(duplicateGroup = null)
        }
        state.stage = WorkflowStage.ORGANIZE_REVIEW
        state.selectedRules = emptySet()
        state.preview = null
        state.previewOrder = emptyList()
        state.error = null
        state.notice = "校验阶段已确认。现在选择需要执行的整理规则；未勾选的规则不会执行。"
    }

    fun generateOrganizePreview() {
        if (state.busy || state.sources.isEmpty()) return
        state.busy = true
        state.error = null
        state.notice = "正在按已勾选的规则生成预览。"
        val snapshot = state.sources.toList()
        val rules = state.selectedRules.toSet()
        val resultMap = snapshot.mapIndexedNotNull { index, source ->
            source.probe?.let { index to it.copy(index = index) }
        }.toMap()
        activeJob = scope.launch {
            try {
                val report = withContext(Dispatchers.Default) {
                    Organizer.run(
                        sources = snapshot.map { it.doc },
                        results = resultMap,
                        config = OrganizeConfig(selectedRules = rules),
                    )
                }
                val byIndex = report.sources.associateBy { it.index }
                // Re-grouping and explicit sorting use the organizer's grouped order.
                // Name-only rules keep the source pool's existing order.
                val orderedIds = if (
                    OrganizeRule.REGROUP_BY_TYPE in rules ||
                    OrganizeRule.SORT_BY_RESPONSE_TIME in rules
                ) {
                    report.sources.mapNotNull { organized ->
                        snapshot.getOrNull(organized.index)?.id
                    }
                } else {
                    snapshot.map { it.id }
                }
                val previewRows = linkedMapOf<Int, PreviewSource>()
                orderedIds.forEach { id ->
                    val inputIndex = snapshot.indexOfFirst { it.id == id }
                    val organized = byIndex[inputIndex]
                    if (organized != null) {
                        previewRows[id] = PreviewSource(
                            name = organized.name,
                            group = organized.group,
                            status = organized.status,
                            rank = organized.rank,
                        )
                    }
                }
                state.preview = previewRows
                state.previewOrder = orderedIds
                state.notice = "整理预览已生成。请检查新名称、分组和顺序，再确认应用。"
            } catch (e: CancellationException) {
                state.error = "整理预览已取消。"
            } catch (e: Throwable) {
                state.error = "整理预览失败：${e.message ?: e::class.java.simpleName}"
            } finally {
                state.busy = false
                activeJob = null
            }
        }
    }

    fun confirmOrganize() {
        val preview = state.preview ?: run {
            state.error = "请先生成整理预览。"
            return
        }
        val rules = state.selectedRules
        state.applyCleanNames = rules.any {
            it == OrganizeRule.CLEAN_DECORATIONS ||
                it == OrganizeRule.REMOVE_ADVERTISING_WORDS ||
                it == OrganizeRule.NORMALIZE_PUNCTUATION
        } || preview.values.any { it.nameEdited }
        state.applyGroups = OrganizeRule.KEEP_ORIGINAL_GROUPS in rules ||
            OrganizeRule.REGROUP_BY_TYPE in rules ||
            preview.values.any { it.groupEdited }
        state.applyOrder = OrganizeRule.SORT_BY_RESPONSE_TIME in rules
        val byId = state.sources.associateBy { it.id }
        val ordered = state.previewOrder.mapNotNull { byId[it] }
        if (ordered.isEmpty()) {
            state.error = "整理结果为空，无法进入导出。"
            return
        }
        state.sources.clear()
        state.sources.addAll(ordered.map { it.copy(deleteMarked = false, duplicateGroup = null) })
        state.stage = WorkflowStage.EXPORT
        state.selectedId = state.sources.firstOrNull()?.id
        state.exported = null
        state.error = null
        state.notice = "整理结果已确认。导出时只写入已勾选或手动修改的字段。"
    }

    fun exportFile() {
        if (state.busy || state.sources.isEmpty()) return
        state.busy = true
        state.error = null
        val snapshot = state.sources.toList()
        val preview = state.preview.orEmpty()
        activeJob = scope.launch {
            try {
                val raws = snapshot.map { it.doc.raw }
                val organized = snapshot.mapIndexed { index, source ->
                    val planned = preview[source.id]
                    OrganizedSource(
                        index = index,
                        name = planned?.name ?: source.doc.name,
                        group = planned?.group ?: source.doc.group,
                        status = source.probe?.status ?: ProbeStatus.SKIPPED,
                        rank = planned?.rank ?: index,
                    )
                }
                val result = withContext(Dispatchers.IO) {
                    Exporter.write(
                        raws = raws,
                        organized = organized,
                        dir = Path.of(state.exportDir),
                        fileName = Exporter.defaultFileName(),
                        config = ExportConfig(
                            applyCleanNames = state.applyCleanNames,
                            applyGroupAndOrder = state.applyGroups || state.applyOrder,
                            pretty = true,
                            applyGroups = state.applyGroups,
                            applyOrder = state.applyOrder,
                        ),
                    )
                }
                state.exported = result
                state.notice = "导出完成：${result.count} 个书源已写入 ${result.file.toAbsolutePath()}"
            } catch (e: CancellationException) {
                state.error = "导出已取消。"
            } catch (e: Throwable) {
                state.error = "导出失败：${e.message ?: e::class.java.simpleName}"
            } finally {
                state.busy = false
                activeJob = null
            }
        }
    }

    MaterialTheme(colorScheme = LightColors) {
        Surface(Modifier.fillMaxSize(), color = Canvas) {
            Column(Modifier.fillMaxSize()) {
                TopBar(state, onImport = ::importFile)
                HorizontalDivider(color = Line)
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Stepper(state.stage)
                    KpiRow(state)
                    if (state.stage == WorkflowStage.ORGANIZE_REVIEW) {
                        OrganizeRulePanel(state) { rule, checked ->
                            val next = if (checked) state.selectedRules + rule else state.selectedRules - rule
                            state.selectedRules = when (rule) {
                                OrganizeRule.KEEP_ORIGINAL_GROUPS -> if (checked) next - OrganizeRule.REGROUP_BY_TYPE else next
                                OrganizeRule.REGROUP_BY_TYPE -> if (checked) next - OrganizeRule.KEEP_ORIGINAL_GROUPS else next
                                else -> next
                            }
                            if (state.preview != null) {
                                state.preview = null
                                state.previewOrder = emptyList()
                                state.notice = "整理规则已变化，请重新生成预览。"
                            }
                        }
                    }
                    state.error?.let {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2))) {
                            Text(it, Modifier.fillMaxWidth().padding(10.dp), color = Bad, fontSize = 13.sp)
                        }
                    }
                    state.notice?.let {
                        Text(it, fontSize = 12.sp, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (state.busy && state.total > 0 && state.stage == WorkflowStage.PROBE_REVIEW) {
                        LinearProgressIndicator(
                            progress = { if (state.total == 0) 0f else state.completed.toFloat() / state.total },
                            modifier = Modifier.fillMaxWidth().height(5.dp),
                        )
                        Text("校验进度：${state.completed}/${state.total}", fontSize = 12.sp, color = Muted)
                    }
                    Row(
                        Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SourceListPanel(
                            state = state,
                            onDetail = { state.selectedId = it },
                            onEdit = { editingId = it },
                            onCompare = { comparingGroup = it },
                            modifier = Modifier.weight(0.70f).fillMaxHeight(),
                        )
                        DetailPanel(
                            state = state,
                            onEdit = { editingId = it },
                            onToggleDelete = { id -> toggleDeleteMark(state, id) },
                            modifier = Modifier.weight(0.30f).fillMaxHeight(),
                        )
                    }
                }
                HorizontalDivider(color = Line)
                BottomActions(
                    state = state,
                    onShowImportInfo = { state.showImportInfo = true },
                    onStartDedup = ::startDedup,
                    onConfirmDedup = ::confirmDedupAndValidate,
                    onStop = { activeJob?.cancel() },
                    onContinueValidation = ::runValidation,
                    onConfirmValidation = ::confirmValidation,
                    onGeneratePreview = ::generateOrganizePreview,
                    onConfirmOrganize = ::confirmOrganize,
                    onExport = ::exportFile,
                    onOpenFolder = {
                        val file = state.exported?.file?.toFile()
                        val dir = file?.parentFile ?: File(state.exportDir)
                        runCatching { if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(dir) }
                            .onFailure { state.error = "无法打开目录：${it.message}" }
                    },
                    onCopyImportLink = {
                        try {
                            val link = DirectLink.forSourceUrl(state.directUrl)
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(link), null)
                            state.notice = "已复制 Legado 一键导入链接。"
                            state.error = null
                        } catch (e: Exception) {
                            state.error = "无法生成导入链接：${e.message ?: e::class.java.simpleName}"
                        }
                    },
                )
            }
        }
    }

    if (state.showImportInfo) ImportInfoDialog(state, onDismiss = { state.showImportInfo = false })
    if (state.showSettings) {
        SettingsDialog(state, onDismiss = { state.showSettings = false }) {
            val chooser = JFileChooser(File(state.exportDir))
            chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            chooser.isAcceptAllFileFilterUsed = false
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                state.exportDir = chooser.selectedFile.absolutePath
            }
        }
    }
    if (state.showReport) ReportDialog(state, onDismiss = { state.showReport = false })
    comparingGroup?.let { groupNo ->
        state.duplicateGroups.firstOrNull { it.number == groupNo }?.let { group ->
            CompareGroupDialog(
                state = state,
                group = group,
                onDismiss = { comparingGroup = null },
            )
        }
    }
    editingId?.let { id ->
        state.sources.firstOrNull { it.id == id }?.let { source ->
            val previewRow = if (state.stage == WorkflowStage.ORGANIZE_REVIEW) state.preview?.get(id) else null
            EditSourceDialog(
                source = source,
                preview = previewRow,
                onDismiss = { editingId = null },
                onSave = { name, url, group, type, enabled ->
                    if (previewRow != null) {
                        val all = state.preview.orEmpty().toMutableMap()
                        val latest = all[id] ?: previewRow
                        all[id] = latest.copy(
                            name = name,
                            group = group,
                            nameEdited = latest.nameEdited || name != latest.name,
                            groupEdited = latest.groupEdited || group != latest.group,
                        )
                        state.preview = all
                    } else {
                        val changedUrl = url != source.doc.url
                        val anyMetadataChanged = name != source.doc.name ||
                            changedUrl ||
                            group != source.doc.group ||
                            type != source.doc.type ||
                            enabled != source.doc.enabled
                        updateSource(state, id) { old ->
                            val edited = editedDocument(old.doc, name, url, group, type, enabled)
                            when {
                                state.stage == WorkflowStage.PROBE_REVIEW && changedUrl -> old.copy(
                                    doc = edited,
                                    probe = null,
                                    deleteMarked = false,
                                    suggestion = "待重新校验",
                                )
                                else -> old.copy(doc = edited)
                            }
                        }
                        if (state.stage == WorkflowStage.DEDUP_REVIEW && anyMetadataChanged) {
                            // These fields influence candidate grouping or keep scoring. Invalidate
                            // old recommendations instead of letting stale groups delete the wrong source.
                            state.stage = WorkflowStage.IMPORT
                            state.duplicateGroups = emptyList()
                            state.duplicateReport = null
                            state.sources.indices.forEach { index ->
                                state.sources[index] = state.sources[index].copy(
                                    duplicateGroup = null,
                                    deleteMarked = false,
                                    suggestion = "待去重",
                                    probe = null,
                                )
                            }
                            state.notice = "源信息已修改，旧去重建议已重置。请重新开始去重。"
                        } else if (state.stage == WorkflowStage.PROBE_REVIEW && changedUrl) {
                            state.completed = state.sources.count { it.probe != null }
                            state.total = state.sources.size
                            state.notice = "URL 已修改，该书源需要重新校验。"
                        }
                    }
                    editingId = null
                },
            )
        }
    }
}

@Composable
private fun TopBar(state: WorkflowState, onImport: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("书源整理", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Primary)
            Text(
                state.inputFile?.let { "${it.name} · 原始 ${state.inputCount} 个" } ?: "导入本地书源 JSON，分阶段审核后导出",
                fontSize = 12.sp,
                color = Muted,
            )
        }
        Spacer(Modifier.weight(1f))
        if (state.busy) {
            LinearProgressIndicator(progress = { 0.65f }, modifier = Modifier.width(72.dp).height(4.dp))
            Spacer(Modifier.width(12.dp))
        }
        OutlinedButton(onClick = onImport, enabled = !state.busy) { Text("导入书源") }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = { state.showReport = true }) { Text("报告") }
        TextButton(onClick = { state.showSettings = true }) { Text("设置") }
    }
}

@Composable
private fun Stepper(stage: WorkflowStage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WorkflowStage.entries.forEachIndexed { index, item ->
            val current = item == stage
            val complete = item.ordinal < stage.ordinal
            Card(
                Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = if (current) SoftBlue else if (complete) Color(0xFFF1F5F9) else Color.White,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp)) {
                    Text(
                        "${index + 1}. ${item.label}",
                        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (current) Primary else if (complete) Color(0xFF374151) else Muted,
                        fontSize = 13.sp,
                    )
                    Text(item.help, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun KpiRow(state: WorkflowState) {
    val values = listOf(
        "当前书源" to state.sources.size.toString(),
        "重复组" to state.duplicateGroups.size.toString(),
        "待删除" to state.sources.count { it.deleteMarked }.toString(),
        "校验可用" to state.sources.count { it.probe?.status == ProbeStatus.OK }.toString(),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        values.forEach { (label, value) ->
            Card(
                Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                    Text(label, color = Muted, fontSize = 11.sp)
                    Text(value, color = Color(0xFF111827), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun OrganizeRulePanel(
    state: WorkflowState,
    onRuleChange: (OrganizeRule, Boolean) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("整理规则（未勾选的规则不会执行）", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrganizeRule.entries.take(3).forEach { rule ->
                    RuleCheckbox(rule, rule in state.selectedRules, onRuleChange, Modifier.weight(1f))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrganizeRule.entries.drop(3).forEach { rule ->
                    RuleCheckbox(rule, rule in state.selectedRules, onRuleChange, Modifier.weight(1f))
                }
            }
            Text("「保留原有分组」与「按类型重新分组」互斥；响应时间排序使用本轮校验耗时。", fontSize = 10.sp, color = Muted)
        }
    }
}

@Composable
private fun RuleCheckbox(
    rule: OrganizeRule,
    checked: Boolean,
    onRuleChange: (OrganizeRule, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onRuleChange(rule, it) })
        Text(rule.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SourceListPanel(
    state: WorkflowState,
    onDetail: (Int) -> Unit,
    onEdit: (Int) -> Unit,
    onCompare: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("书源列表", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        when (state.stage) {
                            WorkflowStage.IMPORT -> "导入后先查看书源，再开始去重"
                            WorkflowStage.DEDUP_REVIEW -> "重复候选按组相邻显示，可在组内对比"
                            WorkflowStage.PROBE_REVIEW -> "检查校验状态与系统删除建议"
                            WorkflowStage.ORGANIZE_REVIEW -> if (state.preview == null) "选择规则后生成整理预览" else "预览可编辑，确认后才应用"
                            WorkflowStage.EXPORT -> "以下为最终待导出的书源顺序"
                        },
                        fontSize = 11.sp,
                        color = Muted,
                    )
                }
                OutlinedTextField(
                    value = state.filterQuery,
                    onValueChange = { state.filterQuery = it },
                    modifier = Modifier.width(230.dp),
                    placeholder = { Text("搜索源名、URL、分组", fontSize = 11.sp) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                Text("${visibleSourceCount(state)} / ${state.sources.size}", fontSize = 11.sp, color = Muted)
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Line)
            TableHeader()
            HorizontalDivider(color = Line)
            if (state.sources.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("尚未导入书源", fontSize = 16.sp, color = Color(0xFF374151))
                        Text("点击右上角「导入书源」选择 JSON 文件。", fontSize = 12.sp, color = Muted)
                    }
                }
            } else {
                val rows = buildSourceRows(state)
                LazyColumn(Modifier.fillMaxSize()) {
                    items(rows, key = { it.key }) { row ->
                        when (row) {
                            is SourceListRow.GroupHeader -> {
                                Row(
                                    Modifier.fillMaxWidth().background(Color(0xFFF1F5F9)).padding(horizontal = 6.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "重复组 ${row.group.number} · ${row.group.kind.label}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF334155),
                                        )
                                        Text(
                                            "${row.group.sourceIds.size} 个候选 · 相似度 ${(row.group.similarity * 100).toInt()}%",
                                            fontSize = 10.sp,
                                            color = Muted,
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { onCompare(row.group.number) },
                                        enabled = !state.busy,
                                    ) { Text("对比重复组", fontSize = 11.sp) }
                                }
                            }
                            is SourceListRow.SourceEntry -> SourceRowItem(
                                state = state,
                                source = row.source,
                                onDetail = onDetail,
                                onEdit = onEdit,
                            )
                        }
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                    }
                }
            }
        }
    }
}

@Composable
private fun TableHeader() {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFFFAFAFA)).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("源名 / URL", Modifier.weight(2.2f), fontSize = 10.sp, color = Muted)
        Text("类型", Modifier.weight(0.55f), fontSize = 10.sp, color = Muted)
        Text("分组", Modifier.weight(0.8f), fontSize = 10.sp, color = Muted)
        Text("状态", Modifier.weight(1.15f), fontSize = 10.sp, color = Muted)
        Text("耗时", Modifier.weight(0.65f), fontSize = 10.sp, color = Muted)
        Text("建议", Modifier.weight(0.9f), fontSize = 10.sp, color = Muted)
        Text("操作", Modifier.width(92.dp), fontSize = 10.sp, color = Muted)
    }
}

@Composable
private fun SourceRowItem(
    state: WorkflowState,
    source: WorkflowSource,
    onDetail: (Int) -> Unit,
    onEdit: (Int) -> Unit,
) {
    val selected = source.id == state.selectedId
    val preview = state.preview?.get(source.id).takeIf { state.stage == WorkflowStage.ORGANIZE_REVIEW || state.stage == WorkflowStage.EXPORT }
    val name = preview?.name ?: source.doc.name
    val group = preview?.group ?: source.doc.group
    val status = source.probe?.status?.label ?: if (state.stage == WorkflowStage.IMPORT || state.stage == WorkflowStage.DEDUP_REVIEW) "待校验" else "未校验"
    val elapsed = source.probe?.let { if (it.elapsedMs >= 1000) "${"%.1f".format(it.elapsedMs / 1000.0)}s" else "${it.elapsedMs}ms" } ?: "—"
    val advice = when {
        source.deleteMarked -> "待删除"
        source.suggestion.isNotBlank() -> source.suggestion
        else -> "待审核"
    }
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) SoftBlue else Color.White)
            .clickable { onDetail(source.id) }
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(2.2f).padding(end = 5.dp)) {
            Text(name.ifBlank { "（未命名）" }, fontSize = 11.sp, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(source.doc.url, fontSize = 9.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(source.doc.type.toString(), Modifier.weight(0.55f), fontSize = 10.sp, color = Muted, maxLines = 1)
        Text(group.ifBlank { "—" }, Modifier.weight(0.8f), fontSize = 10.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(status, Modifier.weight(1.15f), fontSize = 10.sp, color = statusColor(source.probe?.status), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(elapsed, Modifier.weight(0.65f), fontSize = 10.sp, color = Muted, maxLines = 1)
        Text(
            advice,
            Modifier.weight(0.9f),
            fontSize = 10.sp,
            color = if (source.deleteMarked) Bad else if (source.probe?.status == ProbeStatus.OK) Ok else Muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(Modifier.width(92.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TextButton(onClick = { onDetail(source.id) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 3.dp, vertical = 2.dp)) {
                Text("详情", fontSize = 10.sp)
            }
            TextButton(
                onClick = { onEdit(source.id) },
                enabled = !state.busy && state.stage != WorkflowStage.EXPORT,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 3.dp, vertical = 2.dp),
            ) { Text("编辑", fontSize = 10.sp) }
        }
    }
}

@Composable
private fun DetailPanel(
    state: WorkflowState,
    onEdit: (Int) -> Unit,
    onToggleDelete: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = state.sources.firstOrNull { it.id == state.selectedId }
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        if (source == null) {
            Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
                Text("选择一条书源查看详情。", fontSize = 13.sp, color = Muted)
            }
        } else {
            val preview = state.preview?.get(source.id).takeIf { state.stage == WorkflowStage.ORGANIZE_REVIEW || state.stage == WorkflowStage.EXPORT }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("书源详情", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(preview?.name ?: source.doc.name.ifBlank { "（未命名）" }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
                Text(source.doc.url, fontSize = 11.sp, color = Muted)
                HorizontalDivider(color = Line)
                DetailField("类型", source.doc.type.toString())
                DetailField("分组", preview?.group ?: source.doc.group.ifBlank { "未分组" })
                DetailField("启用状态", if (source.doc.enabled) "启用" else "禁用")
                DetailField("规则字段数量", source.doc.ruleCompleteness.toString())
                DetailField("校验状态", source.probe?.status?.label ?: "尚未校验")
                DetailField("响应耗时", source.probe?.let { "${it.elapsedMs} ms" } ?: "—")
                source.probe?.httpCode?.let { DetailField("HTTP 状态码", it.toString()) }
                if (!source.probe?.detail.isNullOrBlank()) {
                    Text("校验详情", fontSize = 11.sp, color = Muted, fontWeight = FontWeight.SemiBold)
                    Text(source.probe?.detail.orEmpty(), fontSize = 11.sp, color = Color(0xFF374151))
                }
                Text("系统建议", fontSize = 11.sp, color = Muted, fontWeight = FontWeight.SemiBold)
                Text(if (source.deleteMarked) "标记为待删除" else source.suggestion, fontSize = 12.sp, color = if (source.deleteMarked) Bad else Color(0xFF374151))
                if (preview != null && state.stage == WorkflowStage.ORGANIZE_REVIEW) {
                    Text("整理预览可通过「编辑」修改；当前显示的值不会在确认前写入工作集。", fontSize = 10.sp, color = Muted)
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { onEdit(source.id) },
                    enabled = !state.busy && state.stage != WorkflowStage.EXPORT,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (preview != null && state.stage == WorkflowStage.ORGANIZE_REVIEW) "编辑整理结果" else "编辑书源") }
                if (state.stage != WorkflowStage.EXPORT && !state.busy) {
                    Button(onClick = { onToggleDelete(source.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (source.deleteMarked) "撤销删除标记" else "标记为删除")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 10.sp, color = Muted)
        Text(value.ifBlank { "—" }, fontSize = 12.sp, color = Color(0xFF111827))
    }
}

@Composable
private fun BottomActions(
    state: WorkflowState,
    onShowImportInfo: () -> Unit,
    onStartDedup: () -> Unit,
    onConfirmDedup: () -> Unit,
    onStop: () -> Unit,
    onContinueValidation: () -> Unit,
    onConfirmValidation: () -> Unit,
    onGeneratePreview: () -> Unit,
    onConfirmOrganize: () -> Unit,
    onExport: () -> Unit,
    onOpenFolder: () -> Unit,
    onCopyImportLink: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(state.stage.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                when (state.stage) {
                    WorkflowStage.IMPORT -> "开始后自动生成去重建议"
                    WorkflowStage.DEDUP_REVIEW -> "确认后从工作池移除标记删除的重复项，并自动开始校验"
                    WorkflowStage.PROBE_REVIEW -> "超时与网络异常默认复核；确认后才移除删除项"
                    WorkflowStage.ORGANIZE_REVIEW -> "仅执行勾选的整理规则，确认后应用预览"
                    WorkflowStage.EXPORT -> "只导出已确认的当前工作池，不覆盖导入文件"
                },
                fontSize = 10.sp,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when (state.stage) {
            WorkflowStage.IMPORT -> {
                OutlinedButton(onClick = onShowImportInfo, enabled = state.inputFile != null && !state.busy) {
                    Text("查看导入信息")
                }
                Button(onClick = onStartDedup, enabled = state.sources.isNotEmpty() && !state.busy) {
                    Text("开始去重")
                }
            }
            WorkflowStage.DEDUP_REVIEW -> {
                OutlinedButton(onClick = onShowImportInfo, enabled = state.inputFile != null && !state.busy) {
                    Text("查看导入信息")
                }
                Button(onClick = onConfirmDedup, enabled = state.sources.isNotEmpty() && !state.busy) {
                    Text("确认去重并进入校验")
                }
            }
            WorkflowStage.PROBE_REVIEW -> {
                if (state.busy) {
                    OutlinedButton(onClick = onStop) { Text("停止校验") }
                } else if (state.sources.any { it.probe == null }) {
                    Button(onClick = onContinueValidation) { Text("继续校验") }
                } else {
                    Button(onClick = onConfirmValidation, enabled = state.sources.isNotEmpty()) {
                        Text("确认校验并进入整理")
                    }
                }
            }
            WorkflowStage.ORGANIZE_REVIEW -> {
                Button(onClick = onGeneratePreview, enabled = state.sources.isNotEmpty() && !state.busy) {
                    Text(if (state.preview == null) "生成整理预览" else "重新生成预览")
                }
                Button(onClick = onConfirmOrganize, enabled = state.preview != null && !state.busy) {
                    Text("确认整理并进入导出")
                }
            }
            WorkflowStage.EXPORT -> {
                if (DirectLink.isUsable(state.directUrl)) {
                    OutlinedButton(onClick = onCopyImportLink, enabled = !state.busy) { Text("复制导入链接") }
                }
                OutlinedButton(onClick = onOpenFolder, enabled = !state.busy) { Text("打开导出目录") }
                Button(onClick = onExport, enabled = state.sources.isNotEmpty() && !state.busy) {
                    Text(if (state.exported == null) "导出 JSON" else "再次导出 JSON")
                }
            }
        }
    }
}

@Composable
private fun ImportInfoDialog(state: WorkflowState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入信息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailField("文件", state.inputFile?.absolutePath ?: "尚未导入")
                DetailField("导入时书源数", state.inputCount.toString())
                DetailField("当前工作池", state.sources.size.toString())
                DetailField("从工作池移除", state.removedCount.toString())
                Text("阶段确认只修改本次内存工作集。原始导入文件不会被覆盖。", fontSize = 12.sp, color = Muted)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun SettingsDialog(state: WorkflowState, onDismiss: () -> Unit, onChooseFolder: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("处理设置") },
        text = {
            Column(
                Modifier.widthIn(max = 520.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = state.keyword,
                    onValueChange = { state.keyword = it },
                    label = { Text("校验关键词") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.concurrency.toString(),
                    onValueChange = { it.toIntOrNull()?.let { value -> state.concurrency = value.coerceIn(1, 2000) } },
                    label = { Text("并发数（1–2000）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.timeoutSeconds.toString(),
                    onValueChange = { it.toIntOrNull()?.let { value -> state.timeoutSeconds = value.coerceIn(1, 300) } },
                    label = { Text("单源超时秒数（1–300）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.proxy,
                    onValueChange = { state.proxy = it },
                    label = { Text("代理地址（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = state.skipProbe, onCheckedChange = { state.skipProbe = it })
                    Spacer(Modifier.width(8.dp))
                    Text("跳过真实校验", fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = state.withToc, onCheckedChange = { state.withToc = it })
                    Spacer(Modifier.width(8.dp))
                    Text("校验时继续检查目录（更慢）", fontSize = 12.sp)
                }
                OutlinedTextField(
                    value = state.exportDir,
                    onValueChange = { state.exportDir = it },
                    label = { Text("导出目录") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = onChooseFolder, modifier = Modifier.fillMaxWidth()) { Text("选择导出目录") }
                OutlinedTextField(
                    value = state.directUrl,
                    onValueChange = { state.directUrl = it },
                    label = { Text("已有 JSON 的 HTTP(S) 直链（可选）") },
                    supportingText = { Text("工具不会上传文件；此项只用于生成 Legado 导入链接。") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

@Composable
private fun ReportDialog(state: WorkflowState, onDismiss: () -> Unit) {
    val counts = state.sources.mapNotNull { it.probe?.status }.groupingBy { it }.eachCount()
        .entries.sortedBy { it.key.severity }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("本次处理报告") },
        text = {
            Column(
                Modifier.widthIn(max = 520.dp).heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DetailField("导入书源", state.inputCount.toString())
                DetailField("当前工作池", state.sources.size.toString())
                DetailField("重复组", state.duplicateGroups.size.toString())
                DetailField("已从工作池移除", state.removedCount.toString())
                DetailField("待删除标记", state.sources.count { it.deleteMarked }.toString())
                HorizontalDivider(color = Line)
                Text("校验分类", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                if (counts.isEmpty()) Text("尚无校验结果", color = Muted, fontSize = 12.sp)
                counts.forEach { (status, count) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(status.label, Modifier.weight(1f), fontSize = 12.sp, color = statusColor(status))
                        Text(count.toString(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                state.exported?.let {
                    HorizontalDivider(color = Line)
                    DetailField("最近导出", it.file.toAbsolutePath().toString())
                    DetailField("导出数量", it.count.toString())
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun CompareGroupDialog(
    state: WorkflowState,
    group: DuplicateGroupUi,
    onDismiss: () -> Unit,
) {
    val candidates = group.sourceIds.mapNotNull { id -> state.sources.firstOrNull { it.id == id } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("对比重复组 ${group.number}") },
        text = {
            Column(
                Modifier.widthIn(max = 760.dp).heightIn(max = 580.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("${group.kind.label} · 相似度 ${(group.similarity * 100).toInt()}%", fontSize = 12.sp, color = Muted)
                candidates.forEach { source ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (source.deleteMarked) Color(0xFFFFF7ED) else SoftBlue),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(source.doc.name.ifBlank { "（未命名）" }, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Checkbox(
                                    checked = !source.deleteMarked,
                                    onCheckedChange = { keep -> toggleDeleteMark(state, source.id, marked = !keep) },
                                    enabled = !state.busy,
                                )
                                Text(if (source.deleteMarked) "待删除" else "保留", fontSize = 11.sp, color = if (source.deleteMarked) Bad else Ok)
                            }
                            Text(source.doc.url, fontSize = 11.sp, color = Muted)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("类型 ${source.doc.type}", fontSize = 11.sp, color = Muted)
                                Text("分组 ${source.doc.group.ifBlank { "未分组" }}", fontSize = 11.sp, color = Muted)
                                Text("规则字段 ${source.doc.ruleCompleteness}", fontSize = 11.sp, color = Muted)
                            }
                            Text("系统建议：${if (source.id == group.suggestedKeepId) "保留" else "删除"}", fontSize = 11.sp, color = Muted)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成对比") } },
    )
}

@Composable
private fun EditSourceDialog(
    source: WorkflowSource,
    preview: PreviewSource?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int, Boolean) -> Unit,
) {
    val previewMode = preview != null
    var name by remember(source.id, previewMode, preview?.name) {
        mutableStateOf(preview?.name ?: source.doc.name)
    }
    var url by remember(source.id) { mutableStateOf(source.doc.url) }
    var group by remember(source.id, previewMode, preview?.group) {
        mutableStateOf(preview?.group ?: source.doc.group)
    }
    var type by remember(source.id) { mutableStateOf(source.doc.type.toString()) }
    var enabled by remember(source.id) { mutableStateOf(source.doc.enabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (previewMode) "编辑整理预览" else "编辑书源") },
        text = {
            Column(
                Modifier.widthIn(max = 500.dp).heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("源名") }, modifier = Modifier.fillMaxWidth())
                if (!previewMode) {
                    OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("类型（数字）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                        Spacer(Modifier.width(8.dp))
                        Text("启用书源", fontSize = 12.sp)
                    }
                }
                OutlinedTextField(value = group, onValueChange = { group = it }, label = { Text("分组") }, modifier = Modifier.fillMaxWidth())
                if (previewMode) {
                    Text("这里只编辑预览值；点击「确认整理并进入导出」后才会应用。", fontSize = 11.sp, color = Muted)
                } else {
                    Text("只修改已填写的字段，其余未知 JSON 字段会原样保留。", fontSize = 11.sp, color = Muted)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(name, url, group, type.toIntOrNull() ?: source.doc.type, enabled)
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun buildSourceRows(state: WorkflowState): List<SourceListRow> {
    val rows = ArrayList<SourceListRow>(state.sources.size + state.duplicateGroups.size)
    val sourceById = state.sources.associateBy { it.id }
    val query = state.filterQuery.trim().lowercase()
    fun matches(source: WorkflowSource): Boolean {
        if (query.isEmpty()) return true
        return listOf(
            source.doc.name,
            source.doc.url,
            source.doc.group,
            source.doc.type.toString(),
            source.probe?.status?.label.orEmpty(),
            source.suggestion,
            source.probe?.detail.orEmpty(),
        ).any { it.lowercase().contains(query) }
    }
    if (state.stage == WorkflowStage.DEDUP_REVIEW) {
        val grouped = HashSet<Int>()
        state.duplicateGroups.forEach { group ->
            val members = group.sourceIds.mapNotNull { sourceById[it] }.filter(::matches)
            if (members.isNotEmpty()) {
                rows += SourceListRow.GroupHeader(group)
                members.forEach { member ->
                    rows += SourceListRow.SourceEntry(member)
                    grouped += member.id
                }
            }
        }
        state.sources.filterNot { it.id in grouped }.filter(::matches).forEach { rows += SourceListRow.SourceEntry(it) }
    } else {
        val order = if (
            state.stage == WorkflowStage.ORGANIZE_REVIEW &&
            state.preview != null &&
            state.previewOrder.isNotEmpty()
        ) state.previewOrder.mapNotNull { sourceById[it] } else state.sources.toList()
        order.filter(::matches).forEach { rows += SourceListRow.SourceEntry(it) }
    }
    return rows
}

private fun visibleSourceCount(state: WorkflowState): Int {
    val query = state.filterQuery.trim().lowercase()
    if (query.isEmpty()) return state.sources.size
    return state.sources.count { source ->
        listOf(
            source.doc.name,
            source.doc.url,
            source.doc.group,
            source.doc.type.toString(),
            source.probe?.status?.label.orEmpty(),
            source.suggestion,
            source.probe?.detail.orEmpty(),
        ).any { it.lowercase().contains(query) }
    }
}

private fun commitMarkedDeletions(state: WorkflowState): Boolean {
    val marked = state.sources.count { it.deleteMarked }
    if (marked == 0) {
        state.notice = "没有待删除的书源需要移除。"
        return state.sources.isNotEmpty()
    }
    if (marked >= state.sources.size) {
        state.error = "当前所有书源都被标记为删除。请至少保留一项，或修改删除标记后再进入下一阶段。"
        return false
    }
    state.sources.removeAll { it.deleteMarked }
    state.removedCount += marked
    state.sources.indices.forEach { index ->
        state.sources[index] = state.sources[index].copy(deleteMarked = false, duplicateGroup = null)
    }
    if (state.selectedId == null || state.sources.none { it.id == state.selectedId }) state.selectedId = state.sources.firstOrNull()?.id
    state.notice = "已将 ${marked} 个标记项从当前工作池移除；导入文件未修改。"
    return state.sources.isNotEmpty()
}

private fun toggleDeleteMark(state: WorkflowState, id: Int, marked: Boolean? = null) {
    val index = state.sources.indexOfFirst { it.id == id }
    if (index < 0 || state.busy || state.stage == WorkflowStage.EXPORT) return
    val old = state.sources[index]
    val value = marked ?: !old.deleteMarked
    state.sources[index] = old.copy(
        deleteMarked = value,
        suggestion = if (value) "用户标记删除" else "用户决定保留",
    )
}

private fun updateSource(state: WorkflowState, id: Int, transform: (WorkflowSource) -> WorkflowSource) {
    val index = state.sources.indexOfFirst { it.id == id }
    if (index >= 0) state.sources[index] = transform(state.sources[index])
}

private fun editedDocument(
    doc: BookSourceDoc,
    name: String,
    url: String,
    group: String,
    type: Int,
    enabled: Boolean,
): BookSourceDoc {
    val raw = doc.raw.deepCopy()
    if (name != doc.name) raw.addProperty(SourceField.NAME, name)
    if (url != doc.url) raw.addProperty(SourceField.URL, url)
    if (group != doc.group) raw.addProperty(SourceField.GROUP, group)
    if (type != doc.type) raw.addProperty(SourceField.TYPE, type)
    if (enabled != doc.enabled) raw.addProperty(SourceField.ENABLED, enabled)
    return BookSourceDoc(raw)
}

private fun statusColor(status: ProbeStatus?): Color = when (status) {
    ProbeStatus.OK -> Ok
    ProbeStatus.TIMEOUT, ProbeStatus.NETWORK, ProbeStatus.HTTP_ERROR -> Warn
    ProbeStatus.NO_SEARCH_URL, ProbeStatus.UNSUPPORTED, ProbeStatus.PARSE_EMPTY, ProbeStatus.NEED_JS, ProbeStatus.ERROR -> Bad
    else -> Muted
}

private fun rawString(source: WorkflowSource, key: String): String {
    val value = source.doc.raw.get(key) ?: return ""
    if (value.isJsonNull) return ""
    return if (value.isJsonPrimitive) value.asString else value.toString()
}

private fun defaultExportDir(): String {
    val home = File(System.getProperty("user.home"))
    val documents = File(home, "Documents")
    val base = if (documents.isDirectory) documents else home
    return File(base, "legado-tidy").absolutePath
}
