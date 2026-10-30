package io.github.theeht.legadotidy.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

private val Primary = Color(0xFF3565C9)
private val Ok = Color(0xFF16845B)
private val Warn = Color(0xFF9A6417)
private val Bad = Color(0xFFC43C48)
private val Muted = Color(0xFF748096)
private val Line = Color(0xFFD9E0E9)
private val Canvas = Color(0xFFF5F7FA)
private val SoftBlue = Color(0xFFEAF0FB)

private val Ink = Color(0xFF24344D)
private val BodyText = Color(0xFF344054)
private val SoftGreen = Color(0xFFE8F7F0)
private val SoftAmber = Color(0xFFFFF5DF)
private val SoftRed = Color(0xFFFFF0F0)
private val SoftNeutral = Color(0xFFF7F9FC)
private val SoftSlate = Color(0xFFF0F2F6)
private val FooterSurface = Color(0xFF17233A)
private val FooterButton = Color(0xFF263652)
private val FooterPrimary = Color(0xFF5C86F5)

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = SoftBlue,
    onPrimaryContainer = Ink,
    secondary = Ok,
    onSecondary = Color.White,
    tertiary = Warn,
    error = Bad,
    onError = Color.White,
    surface = Color.White,
    surfaceVariant = SoftNeutral,
    onSurface = Ink,
    onSurfaceVariant = Muted,
    background = Canvas,
    onBackground = Ink,
    outline = Line,
    outlineVariant = Line,
)

/**
 * 响应当前窗口的可用 dp 尺寸。Compose 的 dp/sp 已经处理 Windows DPI 缩放；
 * 这里仅调整内容最大宽度、栏宽和留白，避免在 2K 屏上把 UI 再次按物理像素放大。
 */
internal data class DesktopLayoutSpec(
    val maxContentWidth: Dp,
    val horizontalPadding: Dp,
    val topPadding: Dp,
    val bottomPadding: Dp,
    val panelSpacing: Dp,
    val detailPanelWidth: Dp,
)

internal fun desktopLayoutSpec(availableWidth: Dp, availableHeight: Dp): DesktopLayoutSpec {
    val wide = when {
        availableWidth >= 2400.dp -> DesktopLayoutSpec(
            maxContentWidth = 2400.dp,
            horizontalPadding = 40.dp,
            topPadding = 28.dp,
            bottomPadding = 32.dp,
            panelSpacing = 20.dp,
            detailPanelWidth = 440.dp,
        )
        availableWidth >= 1900.dp -> DesktopLayoutSpec(
            maxContentWidth = 2200.dp,
            horizontalPadding = 32.dp,
            topPadding = 24.dp,
            bottomPadding = 28.dp,
            panelSpacing = 18.dp,
            detailPanelWidth = 400.dp,
        )
        else -> DesktopLayoutSpec(
            maxContentWidth = 1720.dp,
            horizontalPadding = 26.dp,
            topPadding = 22.dp,
            bottomPadding = 26.dp,
            panelSpacing = 16.dp,
            detailPanelWidth = 350.dp,
        )
    }
    return when {
        availableHeight < 820.dp -> wide.copy(topPadding = 12.dp, bottomPadding = 16.dp)
        availableHeight < 1100.dp -> wide.copy(topPadding = minOf(wide.topPadding, 22.dp), bottomPadding = minOf(wide.bottomPadding, 24.dp))
        else -> wide
    }
}

private enum class WorkflowStage(val label: String, val help: String) {
    IMPORT("导入书源", "文件与数据"),
    DEDUP_REVIEW("去重审核", "自动建议保留项"),
    PROBE_REVIEW("书源校验", "状态与删除标记"),
    ORGANIZE_REVIEW("整理审核", "按勾选规则整理"),
    EXPORT("导出结果", "生成 JSON"),
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
    data class GroupHeader(
        val group: DuplicateGroupUi,
        val visibleCount: Int,
        val visibleMarkedCount: Int,
    ) : SourceListRow("group-${group.number}")
    data class SourceEntry(val source: WorkflowSource) : SourceListRow("source-${source.id}")
}

private class WorkflowState {
    var stage by mutableStateOf(WorkflowStage.IMPORT)
    val sources = mutableStateListOf<WorkflowSource>()
    var inputFile by mutableStateOf<File?>(null)
    var inputCount by mutableStateOf(0)
    var selectedId by mutableStateOf<Int?>(null)
    var filterQuery by mutableStateOf("")
    var sourceFilter by mutableStateOf("全部")
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
    var markDisabledForDeletion by mutableStateOf(false)
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
            editingId = null
            comparingGroup = null
            state.inputFile = selectedFile
            state.inputCount = docs.size
            state.selectedId = state.sources.firstOrNull()?.id
            state.filterQuery = ""
            state.stage = WorkflowStage.IMPORT
            state.sourceFilter = "全部"
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
                        sourceIds = stableIdsAtIndexes(snapshot, group.members) { it.id },
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
                state.sourceFilter = "全部"
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
        val skipProbe = state.skipProbe
        val concurrency = state.concurrency.coerceIn(1, 2000)
        state.notice = if (skipProbe) "按设置跳过真实校验。" else "正在使用 Legado 引擎校验书源。"
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
                val gate = Semaphore(concurrency)
                val probeEngine = LegadoProbeEngine()
                coroutineScope {
                    pending.map { source ->
                        async(Dispatchers.IO) {
                            val index = snapshot.indexOfFirst { it.id == source.id }
                            val result = if (skipProbe) {
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
        val emptyDuplicateGroup = state.duplicateGroups.firstOrNull { group ->
            group.sourceIds.isNotEmpty() && group.sourceIds.all { id ->
                state.sources.firstOrNull { it.id == id }?.deleteMarked == true
            }
        }
        if (emptyDuplicateGroup != null) {
            state.error = "重复组 ${emptyDuplicateGroup.number} 的候选项全部被标记为删除。每组至少需要保留一项，或确认它们并不重复后取消相应标记。"
            state.selectedId = emptyDuplicateGroup.sourceIds.firstOrNull { id ->
                state.sources.firstOrNull { it.id == id }?.deleteMarked == true
            }
            return
        }
        if (!commitMarkedDeletions(state)) return
        state.duplicateGroups = emptyList()
        state.duplicateReport = null
        state.stage = WorkflowStage.PROBE_REVIEW
        state.sourceFilter = "全部"
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
        state.sourceFilter = "全部"
        state.selectedRules = setOf(
            OrganizeRule.CLEAN_DECORATIONS,
            OrganizeRule.REMOVE_ADVERTISING_WORDS,
            OrganizeRule.NORMALIZE_PUNCTUATION,
            OrganizeRule.KEEP_ORIGINAL_GROUPS,
        )
        state.markDisabledForDeletion = false
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
                // Re-grouping changes the group field, not the user's source order.
                // Only the explicit response-time sorting rule may reorder preview/export rows.
                val orderedIds = if (usesOrganizerOutputOrder(rules)) {
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
                if (state.markDisabledForDeletion) {
                    state.sources.indices.forEach { sourceIndex ->
                        val source = state.sources[sourceIndex]
                        if (source.isDisabledForDisplay() && !source.deleteMarked && source.suggestion != "用户决定保留") {
                            state.sources[sourceIndex] = source.copy(
                                deleteMarked = true,
                                suggestion = "禁用书源，建议删除",
                            )
                        }
                    }
                }
                state.preview = previewRows
                state.previewOrder = orderedIds
                state.notice = if (state.markDisabledForDeletion) {
                    "整理预览已生成；禁用书源已标记为待删除，请复核后确认。"
                } else {
                    "整理预览已生成。请检查新名称、分组和顺序，再确认应用。"
                }
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

        // Manual group edits can change which group a source belongs to. Recalculate
        // per-group ranks in the exact final preview order before writing customOrder.
        val rankedPreview = preview.toMutableMap()
        val rankByGroup = HashMap<String, Int>()
        state.previewOrder.forEach { id ->
            val row = rankedPreview[id] ?: return@forEach
            val rank = rankByGroup[row.group] ?: 0
            rankByGroup[row.group] = rank + 1
            rankedPreview[id] = row.copy(rank = rank)
        }
        state.preview = rankedPreview

        val byId = state.sources.associateBy { it.id }
        val allOrdered = state.previewOrder.mapNotNull { byId[it] }
        if (allOrdered.isEmpty()) {
            state.error = "整理结果为空，无法进入导出。"
            return
        }
        val deletionPlan = planMarkedDeletions(allOrdered) { it.deleteMarked }
        if (deletionPlan.allItemsMarked) {
            state.error = "当前所有书源都被标记为删除。请至少保留一项，或修改删除标记后再进入导出。"
            return
        }
        val ordered = deletionPlan.retained
        if (ordered.isEmpty()) {
            state.error = "整理结果为空，无法进入导出。"
            return
        }
        state.removedCount += deletionPlan.removedCount
        val retainedIds = ordered.map { it.id }.toSet()
        state.preview = rankedPreview.filterKeys { it in retainedIds }
        state.previewOrder = ordered.map { it.id }
        state.sources.clear()
        state.sources.addAll(ordered.map { it.copy(deleteMarked = false, duplicateGroup = null) })
        state.stage = WorkflowStage.EXPORT
        state.sourceFilter = "全部"
        state.selectedId = state.sources.firstOrNull()?.id
        state.exported = null
        state.error = null
        state.notice = if (deletionPlan.removedCount > 0) {
            "整理结果已确认，并将 " + deletionPlan.removedCount + " 个标记项从当前工作池移除。导出时只写入已确认或手动修改的字段。"
        } else {
            "整理结果已确认。导出时只写入已确认或手动修改的字段。"
        }


    }

    fun exportFile() {
        if (state.busy || state.sources.isEmpty()) return
        state.error = null
        val outputDir = resolveExportDirectory(state.exportDir).getOrElse { error ->
            state.error = "导出目录无效：${error.message ?: error::class.java.simpleName}"
            return
        }
        val outputName = runCatching {
            nextSafeExportFileName(state.inputFile, outputDir.toFile(), Exporter.defaultFileName())
        }.getOrElse { error ->
            state.error = "无法检查导出文件名：${error.message ?: error::class.java.simpleName}"
            return
        }
        state.busy = true
        val snapshot = state.sources.toList()
        val preview = state.preview.orEmpty()
        val exportConfig = ExportConfig(
            applyCleanNames = state.applyCleanNames,
            applyGroupAndOrder = state.applyGroups || state.applyOrder,
            pretty = true,
            applyGroups = state.applyGroups,
            applyOrder = state.applyOrder,
        )
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
                        dir = outputDir,
                        fileName = outputName,
                        config = exportConfig,
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
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val layout = desktopLayoutSpec(maxWidth, maxHeight)
                Column(
                    Modifier.widthIn(max = layout.maxContentWidth).fillMaxWidth().fillMaxHeight().padding(
                        start = layout.horizontalPadding,
                        end = layout.horizontalPadding,
                        top = layout.topPadding,
                        bottom = layout.bottomPadding,
                    ),
                ) {
                TopBar(state, onImport = ::importFile)
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Stepper(state.stage)
                    KpiRow(state)
                    state.error?.let {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SoftRed),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            Text(it, Modifier.fillMaxWidth().padding(10.dp), color = Bad, fontSize = 12.sp)
                        }
                    }
                    if (state.busy && state.total > 0 && state.stage == WorkflowStage.PROBE_REVIEW) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            LinearProgressIndicator(
                                progress = { if (state.total == 0) 0f else state.completed.toFloat() / state.total },
                                modifier = Modifier.fillMaxWidth().height(5.dp),
                            )
                            Text("校验进度：${state.completed}/${state.total}", fontSize = 11.sp, color = Muted)
                        }
                    }
                    Row(
                        Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(layout.panelSpacing),
                    ) {
                        SourceListPanel(
                            state = state,
                            onDetail = { state.selectedId = it },
                            onEdit = { editingId = it },
                            onCompare = { comparingGroup = it },
                            onGeneratePreview = ::generateOrganizePreview,
                            markDisabledForDeletion = state.markDisabledForDeletion,
                            onMarkDisabledChange = { checked ->
                                state.markDisabledForDeletion = checked
                                if (!checked) {
                                    state.sources.indices.forEach { index ->
                                        val source = state.sources[index]
                                        if (source.suggestion == "禁用书源，建议删除") {
                                            state.sources[index] = source.copy(
                                                deleteMarked = false,
                                                suggestion = if (source.probe?.status == ProbeStatus.OK) "建议保留" else "建议复核",
                                            )
                                        }
                                    }
                                }
                                state.notice = if (checked) "已选择在生成整理预览时标记禁用书源；可逐项撤销。" else "已取消禁用书源删除规则。"
                                state.preview = null
                                state.previewOrder = emptyList()
                            },
                            onRuleChange = { rule, checked ->
                                val next = if (checked) state.selectedRules + rule else state.selectedRules - rule
                                state.selectedRules = when (rule) {
                                    OrganizeRule.KEEP_ORIGINAL_GROUPS -> if (checked) next - OrganizeRule.REGROUP_BY_TYPE else next
                                    OrganizeRule.REGROUP_BY_TYPE -> if (checked) next - OrganizeRule.KEEP_ORIGINAL_GROUPS else next
                                    else -> next
                                }
                                if (state.preview != null) {
                                    state.sources.indices.forEach { index ->
                                        val source = state.sources[index]
                                        if (source.suggestion == "禁用书源，建议删除") {
                                            state.sources[index] = source.copy(
                                                deleteMarked = false,
                                                suggestion = if (source.probe?.status == ProbeStatus.OK) "建议保留" else "建议复核",
                                            )
                                        }
                                    }
                                    state.preview = null
                                    state.previewOrder = emptyList()
                                    state.notice = "整理规则已变化，自动删除标记已撤销；请重新生成预览。"
                                }
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        DetailPanel(
                            state = state,
                            onEdit = { editingId = it },
                            onToggleDelete = { id -> toggleDeleteMark(state, id) },
                            modifier = Modifier.width(layout.detailPanelWidth).fillMaxHeight(),
                        )
                    }
                }
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
                        val editedProbeInputsChanged = probeInputsChanged(
                            source.doc,
                            editedDocument(source.doc, name, url, group, type, enabled),
                        )
                        val anyMetadataChanged = name != source.doc.name ||
                            url != source.doc.url ||
                            group != source.doc.group ||
                            type != source.doc.type ||
                            enabled != isDocumentEnabled(source.doc)
                        updateSource(state, id) { old ->
                            val edited = editedDocument(old.doc, name, url, group, type, enabled)
                            when {
                                (state.stage == WorkflowStage.PROBE_REVIEW ||
                                    state.stage == WorkflowStage.ORGANIZE_REVIEW) && editedProbeInputsChanged -> old.copy(
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
                            state.sourceFilter = "全部"
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
                        } else if (
                            (state.stage == WorkflowStage.PROBE_REVIEW ||
                                state.stage == WorkflowStage.ORGANIZE_REVIEW) && editedProbeInputsChanged
                        ) {
                            state.completed = state.sources.count { it.probe != null }
                            state.total = state.sources.size
                            if (state.stage == WorkflowStage.ORGANIZE_REVIEW) {
                                state.stage = WorkflowStage.PROBE_REVIEW
                                state.sourceFilter = "全部"
                                state.preview = null
                                state.previewOrder = emptyList()
                            }
                            state.notice = "书源地址或类型已修改，该书源需要重新校验；已返回校验审核阶段。"
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
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.widthIn(min = 245.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF4779F4), Color(0xFF2D56C8)))),
                contentAlignment = Alignment.Center,
            ) {
                Text("书", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("书源整理工具", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("Legado Tidy Tool · 分阶段审核", fontSize = 10.sp, color = Muted)
            }
        }
        Row(
            Modifier.weight(1f).border(1.dp, Line, RoundedCornerShape(10.dp))
                .background(Color.White, RoundedCornerShape(8.dp)).padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("▤", fontSize = 15.sp, color = Muted)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(state.inputFile?.name ?: "尚未导入书源 JSON", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (state.inputFile == null) "导入本地文件，按阶段审核后导出"
                    else "${state.sources.size} 个当前书源 · " + when (state.stage) {
                        WorkflowStage.IMPORT -> "待开始"
                        WorkflowStage.EXPORT -> "待导出"
                        else -> "处理中"
                    },
                    fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.busy) LinearProgressIndicator(modifier = Modifier.width(52.dp).height(4.dp))
            OutlinedButton(
                onClick = onImport,
                enabled = !state.busy,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 9.dp, vertical = 5.dp),
            ) { Text("导入 JSON", fontSize = 12.sp) }
            OutlinedButton(
                onClick = { state.showSettings = true },
                enabled = !state.busy,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 9.dp, vertical = 5.dp),
            ) { Text("⚙ 校验设置", fontSize = 12.sp) }
        }
    }
}


@Composable
private fun Stepper(stage: WorkflowStage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WorkflowStage.entries.forEachIndexed { index, item ->
            val current = item == stage
            val complete = item.ordinal < stage.ordinal
            val badgeColor = when { current -> Primary; complete -> SoftGreen; else -> SoftSlate }
            val badgeTextColor = when { current -> Color.White; complete -> Ok; else -> Muted }
            Card(
                Modifier.weight(1f).border(1.dp, if (current) Color(0xFF91B0FF) else Line, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = if (current) Color(0xFFEDF3FF) else Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(badgeColor), contentAlignment = Alignment.Center) {
                        Text("${index + 1}", color = badgeTextColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(item.label, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium, color = if (current) Primary else if (complete) BodyText else Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.help, color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (complete) Text("✓", color = Ok, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}


@Composable
private fun KpiRow(state: WorkflowState) {
    val passed = state.sources.count { it.probe?.status == ProbeStatus.OK }
    val hasProbeResult = state.sources.any { it.probe != null }
    val values = listOf(
        Triple("当前书源池", state.sources.size.toString(), if (state.removedCount == 0) "当前导入文件" else "原始 ${state.inputCount} · 已移出 ${state.removedCount}"),
        Triple("重复组", state.duplicateGroups.size.toString(), "已标记系统建议保留项与删除项"),
        Triple("校验通过", if (hasProbeResult) passed.toString() else "—", "校验阶段完成后显示"),
        Triple("待处理标记", state.sources.count { it.deleteMarked }.toString(), "进入下一阶段时才实际移出书源池"),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        values.forEach { (label, value, hint) ->
            Card(
                Modifier.weight(1f).border(1.dp, Line, RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(label, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    Text(value, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(hint, color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}


@Composable
private fun OrganizeRulePanel(
    state: WorkflowState,
    onRuleChange: (OrganizeRule, Boolean) -> Unit,
    onGeneratePreview: () -> Unit,
    markDisabledForDeletion: Boolean,
    onMarkDisabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier.border(1.dp, Color(0xFFDCE5FB), RoundedCornerShape(11.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FBFF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("整理规则", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.width(8.dp))
                Text("仅执行已勾选的规则；未勾选项保持原样", fontSize = 11.sp, color = Muted)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                RuleCheckbox(
                    OrganizeRule.CLEAN_DECORATIONS,
                    OrganizeRule.CLEAN_DECORATIONS in state.selectedRules,
                    onRuleChange,
                    Modifier.weight(1f),
                )
                RuleCheckbox(
                    OrganizeRule.REMOVE_ADVERTISING_WORDS,
                    OrganizeRule.REMOVE_ADVERTISING_WORDS in state.selectedRules,
                    onRuleChange,
                    Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuleCheckbox(
                    OrganizeRule.NORMALIZE_PUNCTUATION,
                    OrganizeRule.NORMALIZE_PUNCTUATION in state.selectedRules,
                    onRuleChange,
                    Modifier.weight(1f),
                )
                RuleOptionCard(
                    title = "标记已禁用书源为删除",
                    description = "只打删除标记，确认进入导出阶段时才从当前书源池移除。",
                    checked = markDisabledForDeletion,
                    onCheckedChange = onMarkDisabledChange,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Top) {
                Column(
                    Modifier.weight(1f).border(1.dp, Line, RoundedCornerShape(9.dp))
                        .background(Color.White, RoundedCornerShape(8.dp)).padding(7.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("分组规则", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RuleRadio(
                            label = "保留原有分组",
                            selected = OrganizeRule.KEEP_ORIGINAL_GROUPS in state.selectedRules,
                            onClick = { onRuleChange(OrganizeRule.KEEP_ORIGINAL_GROUPS, true) },
                        )
                        RuleRadio(
                            label = "重新分组",
                            selected = OrganizeRule.REGROUP_BY_TYPE in state.selectedRules,
                            onClick = { onRuleChange(OrganizeRule.REGROUP_BY_TYPE, true) },
                        )
                    }
                    Text(
                        "重新分组按书源类型和名称关键词分类，未命中时归入综合。",
                        fontSize = 9.sp,
                        color = Muted,
                        lineHeight = 12.sp,
                    )
                }
                RuleCheckbox(
                    OrganizeRule.SORT_BY_RESPONSE_TIME,
                    OrganizeRule.SORT_BY_RESPONSE_TIME in state.selectedRules,
                    onRuleChange,
                    Modifier.weight(1f),
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "默认建议：清理名称装饰与广告词，规范标点，保留现有分组。",
                    Modifier.weight(1f).padding(end = 8.dp),
                    fontSize = 9.sp,
                    color = Muted,
                )
                Button(
                    onClick = onGeneratePreview,
                    enabled = state.sources.isNotEmpty() && !state.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color.White),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 9.dp, vertical = 5.dp),
                ) { Text("生成整理预览", fontSize = 11.sp) }
            }
        }
    }
}

@Composable
private fun RuleOptionCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.clip(RoundedCornerShape(9.dp))
            .border(1.dp, if (checked) Color(0xFFB8CCFF) else Line, RoundedCornerShape(9.dp))
            .background(if (checked) SoftBlue else Color.White, RoundedCornerShape(9.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 7.dp, vertical = 5.dp)
            .heightIn(min = 42.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RuleCheckMark(checked)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(description, fontSize = 9.sp, color = Muted, lineHeight = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RuleCheckMark(checked: Boolean) {
    Box(
        Modifier.size(12.dp).clip(RoundedCornerShape(3.dp))
            .background(if (checked) Primary else Color.White)
            .border(1.dp, if (checked) Primary else Color(0xFFB8C1D0), RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Text("✓", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RuleRadio(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(7.dp)).clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(14.dp).clip(RoundedCornerShape(50))
                .border(1.dp, if (selected) Primary else Color(0xFF9AA5B5), RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(
                Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(Primary),
            )
        }
        Text(label, fontSize = 11.sp, color = BodyText)
    }
}

@Composable
private fun RuleCheckbox(
    rule: OrganizeRule,
    checked: Boolean,
    onRuleChange: (OrganizeRule, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = when (rule) {
        OrganizeRule.CLEAN_DECORATIONS -> "移除图标、装饰字符及首尾多余符号，尽量保留站点主体名称。"
        OrganizeRule.REMOVE_ADVERTISING_WORDS -> "清理精品、高速、推荐、免广告、VIP 等常见宣传标签。"
        OrganizeRule.NORMALIZE_PUNCTUATION -> "去除首尾标点、重复感叹号及多余空格。"
        OrganizeRule.KEEP_ORIGINAL_GROUPS -> "保持导入时已有的书源分组。"
        OrganizeRule.REGROUP_BY_TYPE -> "按书源类型和名称关键词重新分组。"
        OrganizeRule.SORT_BY_RESPONSE_TIME -> "响应较快的书源排在前面；未获得耗时的书源排在最后。"
    }
    RuleOptionCard(
        title = rule.label,
        description = description,
        checked = checked,
        onCheckedChange = { onRuleChange(rule, it) },
        modifier = modifier,
    )
}


@Composable
private fun SourceListPanel(
    state: WorkflowState,
    onDetail: (Int) -> Unit,
    onEdit: (Int) -> Unit,
    onCompare: (Int) -> Unit,
    onRuleChange: (OrganizeRule, Boolean) -> Unit,
    onGeneratePreview: () -> Unit,
    markDisabledForDeletion: Boolean,
    onMarkDisabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.border(1.dp, Line, RoundedCornerShape(14.dp)), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("书源池", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                    val panelHint = when (state.stage) {
                        WorkflowStage.IMPORT -> "导入后统一管理；点击书源查看详情，也可编辑本阶段的处理建议。"
                        WorkflowStage.DEDUP_REVIEW -> "重复候选按组相邻显示，可打开横向对比并调整保留建议。"
                        WorkflowStage.PROBE_REVIEW -> ""
                        WorkflowStage.ORGANIZE_REVIEW -> if (state.preview == null) "先选择整理规则，再生成可编辑的预览。" else "整理预览可编辑；确认前不会写回当前工作池。"
                        WorkflowStage.EXPORT -> "以下是本次最终确认、准备导出的书源。"
                    }
                    if (panelHint.isNotBlank()) Text(panelHint, fontSize = 10.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BasicTextField(
                    value = state.filterQuery,
                    onValueChange = { state.filterQuery = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = BodyText, fontSize = 12.sp),
                    modifier = Modifier.width(200.dp).height(32.dp)
                        .clip(RoundedCornerShape(9.dp)).border(1.dp, Line, RoundedCornerShape(9.dp))
                        .background(Color.White).padding(horizontal = 11.dp, vertical = 8.dp),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (state.filterQuery.isEmpty()) Text("搜索名称、分组或网址…", fontSize = 12.sp, color = Muted)
                            innerTextField()
                        }
                    },
                )
                OutlinedButton(
                    onClick = { state.filterQuery = "" },
                    enabled = state.filterQuery.isNotEmpty(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 11.dp, vertical = 7.dp),
                ) { Text("清除", fontSize = 12.sp) }
            }
            HorizontalDivider(color = Line)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                sourceFilterLabels.forEach { label ->
                    val count = sourceFilterCount(state, label)
                    val selected = state.sourceFilter == label
                    Box(
                        Modifier.clip(RoundedCornerShape(7.dp))
                            .background(if (selected) SoftBlue else Color.Transparent)
                            .border(1.dp, if (selected) Color(0xFFD8E4FF) else Color.Transparent, RoundedCornerShape(7.dp))
                            .clickable { state.sourceFilter = label }
                            .padding(horizontal = 7.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$label $count", fontSize = 10.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) Primary else Muted, maxLines = 1)
                    }
                }
            }
            HorizontalDivider(color = Line)
            state.notice?.let {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = SoftBlue),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Text(it, Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), color = Color(0xFF3156A7), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (state.stage == WorkflowStage.ORGANIZE_REVIEW) {
                OrganizeRulePanel(
                    state = state,
                    onRuleChange = onRuleChange,
                    onGeneratePreview = onGeneratePreview,
                    markDisabledForDeletion = markDisabledForDeletion,
                    onMarkDisabledChange = onMarkDisabledChange,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp),
                )
                Spacer(Modifier.height(7.dp))
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val tableWidth = maxOf(maxWidth, 1180.dp)
                Row(Modifier.fillMaxHeight().horizontalScroll(rememberScrollState())) {
                Column(Modifier.width(tableWidth).fillMaxHeight()) {
            TableHeader()
            HorizontalDivider(color = Line)
            if (state.sources.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("尚未导入书源", fontSize = 16.sp, color = BodyText)
                        Text("点击右上角「导入 JSON」选择书源文件。", fontSize = 12.sp, color = Muted)
                    }
                }
            } else {
                val rows = buildSourceRows(state)
                if (rows.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("没有符合条件的书源", fontSize = 14.sp, color = BodyText)
                            Text("试试切换筛选条件或搜索词。", fontSize = 11.sp, color = Muted)
                        }
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                        items(rows, key = { it.key }) { row ->
                            when (row) {
                                is SourceListRow.GroupHeader -> {
                                    val countLabel = if (row.visibleCount == row.group.sourceIds.size) {
                                        "${row.visibleCount} 个候选"
                                    } else {
                                        "当前显示 ${row.visibleCount}/${row.group.sourceIds.size} 个候选"
                                    }
                                    Row(
                                        Modifier.fillMaxWidth().background(Color(0xFFF1F5FF)).padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                            Text("重复组 ${row.group.number} · ${row.group.kind.label}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3156A7))
                                            Text("$countLabel · 当前显示项中待删除 ${row.visibleMarkedCount} 个", fontSize = 10.sp, color = Muted)
                                        }
                                        OutlinedButton(
                                            onClick = { onCompare(row.group.number) },
                                            enabled = !state.busy,
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        ) { Text("对比重复组", fontSize = 10.sp) }
                                    }
                                }
                                is SourceListRow.SourceEntry -> SourceRowItem(state = state, source = row.source, onDetail = onDetail, onEdit = onEdit)
                            }
                            HorizontalDivider(color = Line)
                        }
                    }
                }
            }
                }
                }
            }
        }
    }
}


@Composable
private fun TableHeader() {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFFFBFCFE)).padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("源名 / URL", Modifier.weight(3.1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Muted)
        Text("类型", Modifier.weight(1.0f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Muted)
        Text("分组", Modifier.weight(1.2f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Muted)
        Text("状态", Modifier.weight(1.2f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Muted)
        Text("耗时", Modifier.weight(0.75f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Muted)
        Text("建议", Modifier.weight(1.4f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Muted)
        Text("操作", Modifier.weight(1.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Muted)
    }
}


@Composable
private fun TableActionButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        Modifier.clip(RoundedCornerShape(7.dp))
            .border(1.dp, Line, RoundedCornerShape(7.dp))
            .background(Color.White)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 11.sp, color = if (enabled) Primary else Muted)
    }
}

private fun sourceTypeLabel(type: Int): String = when (type) {
    0 -> "文本"
    1 -> "有声"
    2 -> "漫画"
    3 -> "其他"
    else -> "其他"
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
        state.stage == WorkflowStage.IMPORT -> "等待去重"
        source.suggestion.isNotBlank() -> source.suggestion
        else -> "待审核"
    }
    Row(
        Modifier.fillMaxWidth().background(if (selected) Color(0xFFF0F5FF) else Color.White)
            .clickable { onDetail(source.id) }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(3.1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name.ifBlank { "（未命名）" }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(source.doc.url, fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        SmallChip(sourceTypeLabel(source.doc.type), SoftSlate, Color(0xFF57647A), Modifier.weight(1.0f))
        Text(group.ifBlank { "—" }, Modifier.weight(1.2f), fontSize = 12.sp, color = BodyText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        SmallChip(status, statusBackground(source.probe?.status), statusColor(source.probe?.status), Modifier.weight(1.2f))
        Text(elapsed, Modifier.weight(0.75f), fontSize = 11.sp, color = Muted, maxLines = 1)
        SmallChip(
            advice,
            if (source.deleteMarked) SoftRed else if (source.probe?.status == ProbeStatus.OK) SoftGreen else SoftSlate,
            if (source.deleteMarked) Bad else if (source.probe?.status == ProbeStatus.OK) Ok else Muted,
            Modifier.weight(1.4f),
        )
        Row(Modifier.weight(1.7f), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            TableActionButton("详情", onClick = { onDetail(source.id) })
            TableActionButton("编辑", onClick = { onEdit(source.id) }, enabled = !state.busy && state.stage != WorkflowStage.EXPORT)
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
    Card(
        modifier.border(1.dp, Line, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        if (source == null) {
            Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
                Text("选择一条书源查看详情。", fontSize = 12.sp, color = Muted)
            }
        } else {
            val preview = state.preview?.get(source.id).takeIf { state.stage == WorkflowStage.ORGANIZE_REVIEW || state.stage == WorkflowStage.EXPORT }
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text("SOURCE #${(source.id + 1).toString().padStart(3, '0')} · ${state.stage.label}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Primary)
                    Text(preview?.name ?: source.doc.name.ifBlank { "（未命名）" }, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(source.doc.url, fontSize = 11.sp, color = Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                HorizontalDivider(color = Line)
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(17.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("基本信息", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
                        val statusLabel = source.probe?.status?.label ?: "待校验"
                        SmallChip(statusLabel, statusBackground(source.probe?.status), statusColor(source.probe?.status))
                    }
                    DetailField("类型", sourceTypeLabel(source.doc.type))
                    DetailField("源分组", preview?.group ?: source.doc.group.ifBlank { "未分组" })
                    DetailField("启用状态", if (source.isDisabledForDisplay()) "禁用" else "启用")
                    DetailField("搜索规则", if (source.doc.canSearch) "已配置 / 待引擎确认" else "未发现常规搜索规则")
                    DetailField("规则字段", source.doc.ruleCompleteness.toString())
                    DetailField("校验状态", source.probe?.status?.label ?: "尚未校验")
                    DetailField("响应耗时", source.probe?.let { "${it.elapsedMs} ms" } ?: "—")
                    source.probe?.httpCode?.let { DetailField("HTTP 状态码", it.toString()) }
                    if (!source.probe?.detail.isNullOrBlank()) {
                        HorizontalDivider(color = Line)
                        Text("校验详情", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text(source.probe?.detail.orEmpty(), fontSize = 11.sp, color = BodyText, lineHeight = 17.sp)
                    }
                    HorizontalDivider(color = Line)
                    Text("本阶段建议", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (source.deleteMarked) SoftRed else SoftNeutral,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                if (source.deleteMarked) "标记为待删除" else source.suggestion,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (source.deleteMarked) Bad else BodyText,
                            )
                            Text(
                                when {
                                    source.deleteMarked -> "该书源会在确认进入下一阶段时从当前工作池移除。"
                                    source.probe?.status == ProbeStatus.OK -> "校验成功；仍可根据规则完整度与重复候选情况人工判断。"
                                    source.probe?.status?.let { it == ProbeStatus.TIMEOUT || it == ProbeStatus.NETWORK || it == ProbeStatus.HTTP_ERROR } == true -> "网络或服务响应异常不代表书源一定失效，建议人工复核。"
                                    else -> "系统建议仅供审核参考，确认阶段前可以修改标记。"
                                },
                                fontSize = 10.sp,
                                color = Muted,
                                lineHeight = 15.sp,
                            )
                        }
                    }
                    if (preview != null) {
                        HorizontalDivider(color = Line)
                        Text("整理预览", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Color(0xFFFFF1F1)).padding(9.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Text("修改前", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Bad)
                                Text(source.doc.name.ifBlank { "（未命名）" }, fontSize = 10.sp, color = BodyText, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Text(source.doc.group.ifBlank { "未分组" }, fontSize = 9.sp, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Color(0xFFEAF8F0)).padding(9.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Text("规则结果", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Ok)
                                Text(preview.name.ifBlank { "（未命名）" }, fontSize = 10.sp, color = BodyText, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Text(preview.group.ifBlank { "未分组" }, fontSize = 9.sp, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Text("仅已勾选的规则生效；确认进入导出阶段后，标记删除项才会移出当前书源池。", fontSize = 10.sp, color = Muted)
                    }
                    Text("操作", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
                    OutlinedButton(
                        onClick = { onEdit(source.id) },
                        enabled = !state.busy && state.stage != WorkflowStage.EXPORT,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (preview != null && state.stage == WorkflowStage.ORGANIZE_REVIEW) "编辑整理结果" else "编辑书源") }
                    if ((state.stage == WorkflowStage.DEDUP_REVIEW || state.stage == WorkflowStage.PROBE_REVIEW || state.stage == WorkflowStage.ORGANIZE_REVIEW) && !state.busy) {
                        OutlinedButton(
                            onClick = { onToggleDelete(source.id) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (source.deleteMarked) Ok else Bad),
                        ) { Text(if (source.deleteMarked) "撤销删除标记" else "标记为删除") }
                    }
                }
            }
        }
    }
}


@Composable
private fun DetailField(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, Modifier.width(82.dp), fontSize = 11.sp, color = Muted)
        Text(value.ifBlank { "—" }, Modifier.weight(1f), fontSize = 12.sp, color = Ink)
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
        Modifier.fillMaxWidth().padding(top = 16.dp)
            .border(1.dp, FooterSurface, RoundedCornerShape(13.dp))
            .clip(RoundedCornerShape(13.dp)).background(FooterSurface)
            .padding(horizontal = 17.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(state.stage.label + " · " + when (state.stage) {
                WorkflowStage.IMPORT -> "准备去重"
                WorkflowStage.DEDUP_REVIEW -> "确认重复项"
                WorkflowStage.PROBE_REVIEW -> "复核校验结果"
                WorkflowStage.ORGANIZE_REVIEW -> "预览整理规则"
                WorkflowStage.EXPORT -> "生成 JSON"
            }, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                when (state.stage) {
                    WorkflowStage.IMPORT -> "开始后自动生成去重建议"
                    WorkflowStage.DEDUP_REVIEW -> "确认后从工作池移除标记删除的重复项，并自动开始校验"
                    WorkflowStage.PROBE_REVIEW -> "超时与网络异常默认复核；确认后才移除删除项"
                    WorkflowStage.ORGANIZE_REVIEW -> "仅执行勾选的整理规则，确认后应用预览"
                    WorkflowStage.EXPORT -> "只导出已确认的当前工作池，不覆盖导入文件"
                },
                fontSize = 11.sp,
                color = Color(0xFFC4CDDD),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when (state.stage) {
            WorkflowStage.IMPORT -> {
                OutlinedButton(onClick = onShowImportInfo, enabled = state.inputFile != null && !state.busy, colors = ButtonDefaults.outlinedButtonColors(containerColor = FooterButton, contentColor = Color.White)) {
                    Text("查看导入信息")
                }
                Button(colors = ButtonDefaults.buttonColors(containerColor = FooterPrimary, contentColor = Color.White), onClick = onStartDedup, enabled = state.sources.isNotEmpty() && !state.busy) {
                    Text("开始去重")
                }
            }
            WorkflowStage.DEDUP_REVIEW -> {
                Button(colors = ButtonDefaults.buttonColors(containerColor = FooterPrimary, contentColor = Color.White), onClick = onConfirmDedup, enabled = state.sources.isNotEmpty() && !state.busy) {
                    Text("确认去重并进入校验")
                }
            }
            WorkflowStage.PROBE_REVIEW -> {
                OutlinedButton(
                    onClick = { state.sourceFilter = "异常 / 复核" },
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = FooterButton, contentColor = Color.White),
                ) { Text("查看异常项") }
                if (state.busy) {
                    OutlinedButton(onClick = onStop, colors = ButtonDefaults.outlinedButtonColors(containerColor = FooterButton, contentColor = Color.White)) { Text("停止校验") }
                } else if (state.sources.any { it.probe == null }) {
                    Button(colors = ButtonDefaults.buttonColors(containerColor = FooterPrimary, contentColor = Color.White), onClick = onContinueValidation) { Text("继续校验") }
                } else {
                    Button(colors = ButtonDefaults.buttonColors(containerColor = FooterPrimary, contentColor = Color.White), onClick = onConfirmValidation, enabled = state.sources.isNotEmpty()) {
                        Text("确认校验并进入整理")
                    }
                }
            }
            WorkflowStage.ORGANIZE_REVIEW -> {
                if (state.preview == null) {
                    OutlinedButton(
                        onClick = { state.sourceFilter = "待删除" },
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = FooterButton, contentColor = Color.White),
                    ) { Text("查看待删除项") }
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = FooterPrimary, contentColor = Color.White),
                        onClick = onGeneratePreview,
                        enabled = state.sources.isNotEmpty() && !state.busy,
                    ) { Text("生成整理预览") }
                } else {
                    OutlinedButton(
                        onClick = onGeneratePreview,
                        enabled = !state.busy,
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = FooterButton, contentColor = Color.White),
                    ) { Text("重新生成预览") }
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = FooterPrimary, contentColor = Color.White),
                        onClick = onConfirmOrganize,
                        enabled = !state.busy,
                    ) { Text("确认整理并进入导出") }
                }
            }
            WorkflowStage.EXPORT -> {
                OutlinedButton(
                    onClick = { state.showReport = true },
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = FooterButton, contentColor = Color.White),
                ) { Text("查看最终清单") }
                if (DirectLink.isUsable(state.directUrl)) {
                    OutlinedButton(onClick = onCopyImportLink, enabled = !state.busy, colors = ButtonDefaults.outlinedButtonColors(containerColor = FooterButton, contentColor = Color.White)) { Text("复制导入链接") }
                }
                OutlinedButton(onClick = onOpenFolder, enabled = !state.busy, colors = ButtonDefaults.outlinedButtonColors(containerColor = FooterButton, contentColor = Color.White)) { Text("打开导出目录") }
                Button(colors = ButtonDefaults.buttonColors(containerColor = FooterPrimary, contentColor = Color.White), onClick = onExport, enabled = state.sources.isNotEmpty() && !state.busy) {
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
        shape = RoundedCornerShape(14.dp),
        containerColor = Color.White,
        titleContentColor = Ink,
        textContentColor = BodyText,
        tonalElevation = 8.dp,
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
        shape = RoundedCornerShape(14.dp),
        containerColor = Color.White,
        titleContentColor = Ink,
        textContentColor = BodyText,
        tonalElevation = 8.dp,
        title = { Text("处理设置") },
        text = {
            Column(
                Modifier.widthIn(max = 540.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
        shape = RoundedCornerShape(14.dp),
        containerColor = Color.White,
        titleContentColor = Ink,
        textContentColor = BodyText,
        tonalElevation = 8.dp,
        title = { Text("本次处理报告") },
        text = {
            Column(
                Modifier.widthIn(max = 540.dp).heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
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
                if (state.stage == WorkflowStage.EXPORT || state.preview != null) {
                    HorizontalDivider(color = Line)
                    DetailField("最终输出数", state.sources.size.toString())
                    DetailField(
                        "名称待变更",
                        state.sources.count { source ->
                            state.preview?.get(source.id)?.name?.let { it != source.doc.name } == true
                        }.toString(),
                    )
                    DetailField(
                        "分组待变更",
                        state.sources.count { source ->
                            state.preview?.get(source.id)?.group?.let { it != source.doc.group } == true
                        }.toString(),
                    )
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
    val compareFields = listOf(
        "searchUrl" to "搜索入口",
        "exploreUrl" to "发现入口",
        "mainJs" to "主脚本",
        "ruleSearch" to "搜索规则",
        "ruleExplore" to "发现规则",
        "ruleBookInfo" to "详情规则",
        "ruleToc" to "目录规则",
        "ruleContent" to "正文规则",
        "ruleReview" to "书评规则",
        "header" to "请求头",
        "jsLib" to "JS 库",
        "loginUrl" to "登录入口",
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).widthIn(max = 1080.dp).heightIn(max = 760.dp),
            shape = RoundedCornerShape(15.dp),
            color = Color.White,
            shadowElevation = 24.dp,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().background(SoftNeutral).padding(horizontal = 18.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("对比重复组 ${group.number}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text(
                            "${group.kind.label} · 相似度 ${(group.similarity * 100).toInt()}% · ${candidates.size} 个候选",
                            fontSize = 11.sp,
                            color = Muted,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("×", fontSize = 20.sp, color = Muted) }
                }
                HorizontalDivider(color = Line)
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Text("横向比较基础信息与关键规则；勾选表示保留，不勾选表示待删除。每组至少保留一项。", fontSize = 11.sp, color = Muted)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        candidates.forEach { source ->
                            val recommended = source.id == group.suggestedKeepId
                            val isPreferred = recommended && !source.deleteMarked
                            Card(
                                Modifier.width(300.dp).border(
                                    1.dp,
                                    if (isPreferred) Color(0xFFB8D9CB) else Line,
                                    RoundedCornerShape(11.dp),
                                ),
                                shape = RoundedCornerShape(11.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        isPreferred -> Color(0xFFFBFFFD)
                                        source.deleteMarked -> SoftAmber
                                        else -> Color.White
                                    },
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(7.dp),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .background(if (isPreferred) SoftGreen else if (source.deleteMarked) SoftAmber else SoftNeutral)
                                            .padding(end = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(
                                            checked = !source.deleteMarked,
                                            onCheckedChange = { keep -> toggleDeleteMark(state, source.id, marked = !keep) },
                                            enabled = !state.busy,
                                        )
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                source.doc.name.ifBlank { "（未命名）" },
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Ink,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                when {
                                                    source.deleteMarked -> "待删除"
                                                    recommended -> "系统推荐保留"
                                                    else -> "候选书源"
                                                },
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = when {
                                                    source.deleteMarked -> Bad
                                                    recommended -> Ok
                                                    else -> Muted
                                                },
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = Line)
                                    Column(
                                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalArrangement = Arrangement.spacedBy(7.dp),
                                    ) {
                                    DetailField("URL", source.doc.url)
                                    DetailField("类型 / 分组", "${source.doc.type} / ${source.doc.group.ifBlank { "未分组" }}")
                                    DetailField("启用 / 规则字段", "${if (source.isDisabledForDisplay()) "禁用" else "启用"} / ${source.doc.ruleCompleteness}")
                                    Text("关键规则", fontSize = 10.sp, color = Muted, fontWeight = FontWeight.SemiBold)
                                    compareFields.forEach { (key, label) ->
                                        val value = rawString(source, key)
                                        if (value.isNotBlank()) {
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(label, fontSize = 9.sp, color = Muted)
                                                Text(
                                                    value,
                                                    fontSize = 10.sp,
                                                    color = BodyText,
                                                    maxLines = 3,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider(color = Line)
                                    Text(
                                        "系统建议：${if (recommended) "保留" else "删除"}",
                                        fontSize = 10.sp,
                                        color = if (recommended) Ok else Muted,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = Line)
                Row(
                    Modifier.fillMaxWidth().background(SoftNeutral).padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                ) {
                    OutlinedButton(onClick = onDismiss) { Text("完成对比") }
                }
            }
        }
    }
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
    var enabled by remember(source.id) { mutableStateOf(!source.isDisabledForDisplay()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(14.dp),
        containerColor = Color.White,
        titleContentColor = Ink,
        textContentColor = BodyText,
        tonalElevation = 8.dp,
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

private val sourceFilterLabels = listOf("全部", "重复候选", "待校验", "校验通过", "异常 / 复核", "待删除")

private fun sourceFilterCount(state: WorkflowState, filter: String): Int = when (filter) {
    "全部" -> state.sources.size
    "重复候选" -> state.sources.count { it.duplicateGroup != null }
    "待校验" -> state.sources.count { it.probe == null }
    "校验通过" -> state.sources.count { it.probe?.status == ProbeStatus.OK }
    "异常 / 复核" -> state.sources.count {
        val status = it.probe?.status
        status != null && status != ProbeStatus.OK && status != ProbeStatus.SKIPPED
    }
    "待删除" -> state.sources.count { it.deleteMarked }
    else -> state.sources.size
}

private fun sourceMatchesFilter(state: WorkflowState, source: WorkflowSource): Boolean {
    val query = state.filterQuery.trim().lowercase()
    val matchesQuery = query.isEmpty() || listOf(
        source.doc.name, source.doc.url, source.doc.group, source.doc.type.toString(),
        source.probe?.status?.label.orEmpty(), source.suggestion, source.probe?.detail.orEmpty(),
    ).any { it.lowercase().contains(query) }
    val matchesFilter = when (state.sourceFilter) {
        "全部" -> true
        "重复候选" -> source.duplicateGroup != null
        "待校验" -> source.probe == null
        "校验通过" -> source.probe?.status == ProbeStatus.OK
        "异常 / 复核" -> {
            val status = source.probe?.status
            status != null && status != ProbeStatus.OK && status != ProbeStatus.SKIPPED
        }
        "待删除" -> source.deleteMarked
        else -> true
    }
    return matchesQuery && matchesFilter
}

@Composable
private fun SmallChip(
    text: String,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Text(
            text,
            Modifier.clip(RoundedCornerShape(6.dp))
                .background(backgroundColor)
                .padding(horizontal = 7.dp, vertical = 4.dp),
            fontSize = 11.sp,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun statusBackground(status: ProbeStatus?): Color = when (status) {
    ProbeStatus.OK -> SoftGreen
    ProbeStatus.TIMEOUT, ProbeStatus.NETWORK, ProbeStatus.HTTP_ERROR -> SoftAmber
    ProbeStatus.NO_SEARCH_URL, ProbeStatus.UNSUPPORTED, ProbeStatus.PARSE_EMPTY, ProbeStatus.NEED_JS, ProbeStatus.ERROR -> SoftRed
    else -> SoftSlate
}

private fun WorkflowSource.isDisabledForDisplay(): Boolean {
    return !isDocumentEnabled(doc)
}

private fun buildSourceRows(state: WorkflowState): List<SourceListRow> {
    val rows = ArrayList<SourceListRow>(state.sources.size + state.duplicateGroups.size)
    val sourceById = state.sources.associateBy { it.id }
    fun matches(source: WorkflowSource): Boolean = sourceMatchesFilter(state, source)
    if (state.stage == WorkflowStage.DEDUP_REVIEW) {
        val grouped = HashSet<Int>()
        state.duplicateGroups.forEach { group ->
            val members = group.sourceIds.mapNotNull { sourceById[it] }.filter(::matches)
            if (members.isNotEmpty()) {
                rows += SourceListRow.GroupHeader(
                    group = group,
                    visibleCount = members.size,
                    visibleMarkedCount = members.count { it.deleteMarked },
                )
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

private fun visibleSourceCount(state: WorkflowState): Int =
    state.sources.count { sourceMatchesFilter(state, it) }

private fun commitMarkedDeletions(state: WorkflowState): Boolean {
    val plan = planMarkedDeletions(state.sources.toList()) { it.deleteMarked }
    if (plan.allItemsMarked) {
        state.error = "当前所有书源都被标记为删除。请至少保留一项，或修改删除标记后再进入下一阶段。"
        return false
    }
    if (plan.removedCount == 0) {
        state.notice = "没有待删除的书源需要移除。"
        return plan.canAdvance
    }
    if (!plan.canAdvance) return false

    state.sources.clear()
    state.sources.addAll(plan.retained.map { it.copy(deleteMarked = false, duplicateGroup = null) })
    state.removedCount += plan.removedCount
    state.selectedId = resolveSelectedId(state.selectedId, state.sources) { it.id }
    state.notice = "已将 ${plan.removedCount} 个标记项从当前工作池移除；导入文件未修改。"
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
    if (enabled != isDocumentEnabled(doc)) setDocumentEnabled(raw, enabled)
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
