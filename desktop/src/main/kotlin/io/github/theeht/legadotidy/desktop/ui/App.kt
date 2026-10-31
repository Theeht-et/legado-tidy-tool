package io.github.theeht.legadotidy.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
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
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Shapes
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
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
import javax.swing.JFileChooser

private val Primary = Color(0xFF2D5DB8)
private val PrimaryDeep = Color(0xFF244B98)
private val Ok = Color(0xFF146B49)
private val Warn = Color(0xFF83520B)
private val Bad = Color(0xFFB52E3D)
// Secondary text remains secondary, but now has enough contrast to stay crisp on light surfaces.
private val Muted = Color(0xFF536277)
private val Line = Color(0xFFCCD5E1)
private val Canvas = Color(0xFFF3F6FA)
private val SoftBlue = Color(0xFFE8EFFB)
private val SoftBlueBorder = Color(0xFFC5D4EF)
private val SelectedSurface = Color(0xFFEAF1FF)
private val ControlBorder = Color(0xFFC3CEDB)

private val Ink = Color(0xFF172B45)
private val BodyText = Color(0xFF29394F)
private val SoftGreen = Color(0xFFE9F5EE)
private val SoftGreenBorder = Color(0xFFB6D5C3)
private val SoftAmber = Color(0xFFFFF3DC)
private val SoftRed = Color(0xFFFFEEEE)
private val SoftNeutral = Color(0xFFF5F7FA)
private val SoftSlate = Color(0xFFEDF1F6)
private object UiTypography {
    val caption = 12.sp
    val label = 13.sp
    val body = 14.sp
    val emphasis = 15.sp
    val sectionTitle = 17.sp
    val brandTitle = 18.sp
    val metric = 20.sp
    val pageTitle = 22.sp
}

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

private enum class WorkflowStage(val label: String) {
    IMPORT("导入书源"),
    DEDUP_REVIEW("去重审核"),
    PROBE_REVIEW("书源校验"),
    ORGANIZE_REVIEW("整理审核"),
    EXPORT("导出结果"),
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
    var detailDialogId by remember { mutableStateOf<Int?>(null) }

    fun importFile() {
        if (state.busy) return
        val dialog = FileDialog(null as Frame?, "选择书源 JSON 文件", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name -> name.endsWith(".json", ignoreCase = true) }
        dialog.isVisible = true
        val dir = dialog.directory ?: return
        val name = dialog.file ?: return
        val selectedFile = File(dir, name)
        try {
            val docs = SourceIO.load(selectedFile.toPath())
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
                            val result = when {
                                !source.doc.canSearch -> ProbeResult(
                                    index,
                                    ProbeStatus.NO_SEARCH_URL,
                                    detail = "未配置可执行的搜索规则（searchUrl + ruleSearch.bookList 或 mainJs）",
                                )
                                skipProbe -> ProbeResult(index, ProbeStatus.SKIPPED, detail = "按设置跳过校验")
                                else -> {
                                    try {
                                        gate.withPermit { probeEngine.probe(index, source.doc, options) }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Throwable) {
                                        ProbeResult(index, ProbeStatus.ERROR, detail = e.message ?: e::class.java.simpleName)
                                    }
                                }
                            }
                            withContext(Dispatchers.Main) {
                                val currentIndex = state.sources.indexOfFirst { it.id == source.id }
                                if (currentIndex >= 0) {
                                    val deleteByDefault = shouldMarkForDeletionByDefault(result.status)
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
        state.applyGroups = shouldApplyGroups(rules, preview.values.any { it.groupEdited })
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
        state.notice = null
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

    CompositionLocalProvider(
        LocalTextStyle provides TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = UiTypography.body,
            letterSpacing = 0.sp,
        ),
    ) {
    MaterialTheme(
        colorScheme = LightColors,
        shapes = Shapes(
            small = RoundedCornerShape(6.dp),
            medium = RoundedCornerShape(8.dp),
            large = RoundedCornerShape(12.dp),
        ),
    ) {
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
                    state.error?.let {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                                .border(1.dp, Bad.copy(alpha = 0.22f), RoundedCornerShape(8.dp)),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = SoftRed),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            Text(it, Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), color = Bad, fontSize = UiTypography.body)
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
                    if (state.busy && state.total > 0 && state.stage == WorkflowStage.PROBE_REVIEW) {
                        Column(
                            Modifier.width(245.dp).padding(start = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            LinearProgressIndicator(
                                progress = { if (state.total == 0) 0f else state.completed.toFloat() / state.total },
                                modifier = Modifier.fillMaxWidth().height(5.dp),
                            )
                            Text("校验进度：${state.completed}/${state.total}", fontSize = UiTypography.label, color = BodyText)
                        }
                    }
                    Row(
                        Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        SourceListPanel(
                            state = state,
                            onDetail = { id -> state.selectedId = id; detailDialogId = id },
                            onEdit = { editingId = it },
                            onCompare = { comparingGroup = it },
                            onGeneratePreview = ::generateOrganizePreview,
                            onRuleChange = { rule, checked ->
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
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
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
    detailDialogId?.let { id ->
        state.sources.firstOrNull { it.id == id }?.let { source ->
            Dialog(
                onDismissRequest = { detailDialogId = null },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(0.96f).widthIn(max = 1180.dp).heightIn(min = 560.dp, max = 920.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    shadowElevation = 24.dp,
                ) {
                    DetailPanel(
                        state = state,
                        sourceId = source.id,
                        onDismiss = { detailDialogId = null },
                        onEdit = { editingId = it; detailDialogId = null },
                        onToggleDelete = { sourceId -> toggleDeleteMark(state, sourceId) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
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
}

@Composable
private fun TopBar(state: WorkflowState, onImport: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.widthIn(min = 245.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(Primary, PrimaryDeep))),
                contentAlignment = Alignment.Center,
            ) {
                Text("书", color = Color.White, fontSize = UiTypography.brandTitle, fontWeight = FontWeight.Bold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("书源整理工具", fontSize = UiTypography.brandTitle, fontWeight = FontWeight.Bold, color = Ink)
                Text("Legado Tidy Tool · 分阶段审核", fontSize = UiTypography.caption, color = Muted)
            }
        }
        Row(
            Modifier.weight(1f).border(1.dp, Line, RoundedCornerShape(8.dp))
                .background(Color.White, RoundedCornerShape(8.dp)).padding(horizontal = 9.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("▤", fontSize = UiTypography.sectionTitle, color = Muted)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(state.inputFile?.name ?: "尚未导入书源 JSON", fontSize = UiTypography.body, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (state.inputFile == null) "导入本地文件，按阶段审核后导出"
                    else "${state.sources.size} 个当前书源 · " + when (state.stage) {
                        WorkflowStage.IMPORT -> "待开始"
                        WorkflowStage.EXPORT -> "待导出"
                        else -> "处理中"
                    },
                    fontSize = UiTypography.caption, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 9.dp, vertical = 3.dp),
            ) { Text("导入 JSON", fontSize = UiTypography.body) }
            OutlinedButton(
                onClick = { state.showSettings = true },
                enabled = !state.busy,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 9.dp, vertical = 3.dp),
            ) { Text("⚙ 校验设置", fontSize = UiTypography.body) }
        }
    }
}





@Composable
private fun OrganizeRulePanel(
    state: WorkflowState,
    onRuleChange: (OrganizeRule, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier.border(1.dp, SoftBlueBorder, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = SoftBlue),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("整理规则", fontSize = UiTypography.label, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.width(7.dp))
                Text("勾选项才执行", fontSize = UiTypography.caption, color = Muted)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RuleCheckbox(
                    OrganizeRule.CLEAN_DECORATIONS,
                    OrganizeRule.CLEAN_DECORATIONS in state.selectedRules,
                    onRuleChange,
                    modifier = Modifier.weight(1f),
                )
                RuleCheckbox(
                    OrganizeRule.REMOVE_ADVERTISING_WORDS,
                    OrganizeRule.REMOVE_ADVERTISING_WORDS in state.selectedRules,
                    onRuleChange,
                    modifier = Modifier.weight(1f),
                )
                RuleCheckbox(
                    OrganizeRule.NORMALIZE_PUNCTUATION,
                    OrganizeRule.NORMALIZE_PUNCTUATION in state.selectedRules,
                    onRuleChange,
                    modifier = Modifier.weight(1f),
                )
                GroupRuleBlock(
                    keepOriginal = OrganizeRule.KEEP_ORIGINAL_GROUPS in state.selectedRules,
                    regroupByType = OrganizeRule.REGROUP_BY_TYPE in state.selectedRules,
                    onKeepOriginal = { onRuleChange(OrganizeRule.KEEP_ORIGINAL_GROUPS, true) },
                    onRegroupByType = { onRuleChange(OrganizeRule.REGROUP_BY_TYPE, true) },
                    modifier = Modifier.weight(1f),
                )
                RuleCheckbox(
                    OrganizeRule.SORT_BY_RESPONSE_TIME,
                    OrganizeRule.SORT_BY_RESPONSE_TIME in state.selectedRules,
                    onRuleChange,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}


@Composable
private fun RuleCheckMark(checked: Boolean) {
    Box(
        Modifier.size(12.dp).clip(RoundedCornerShape(3.dp))
            .background(if (checked) Primary else Color.White)
            .border(1.dp, if (checked) Primary else ControlBorder, RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Text("✓", color = Color.White, fontSize = UiTypography.caption, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RuleRadio(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(14.dp).clip(RoundedCornerShape(50))
                .border(1.dp, if (selected) Primary else Muted, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(
                Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(Primary),
            )
        }
        Text(label, fontSize = UiTypography.label, color = BodyText)
    }
}

@Composable
private fun RuleCheckbox(
    rule: OrganizeRule,
    checked: Boolean,
    onRuleChange: (OrganizeRule, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.height(68.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, if (checked) SoftBlueBorder else Line, RoundedCornerShape(6.dp))
            .background(if (checked) SoftBlue else Color.White)
            .clickable { onRuleChange(rule, !checked) }
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        RuleCheckMark(checked)
        Text(
            rule.label,
            modifier = Modifier.weight(1f),
            fontSize = UiTypography.caption,
            color = if (checked) Primary else BodyText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GroupRuleBlock(
    keepOriginal: Boolean,
    regroupByType: Boolean,
    onKeepOriginal: () -> Unit,
    onRegroupByType: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.height(68.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, if (keepOriginal || regroupByType) SoftBlueBorder else Line, RoundedCornerShape(6.dp))
            .background(if (keepOriginal || regroupByType) SoftBlue else Color.White)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("分组方式", fontSize = UiTypography.caption, color = Muted, maxLines = 1)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RuleRadio("保留原组", keepOriginal, onKeepOriginal)
            RuleRadio("智能归类", regroupByType, onRegroupByType)
        }
    }
}


@Composable
private fun SourceListPanel(
    state: WorkflowState,
    onDetail: (Int) -> Unit,
    onEdit: (Int) -> Unit,
    onCompare: (Int) -> Unit,
    onRuleChange: (OrganizeRule, Boolean) -> Unit,
    onGeneratePreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    Card(modifier.border(1.dp, Line, RoundedCornerShape(12.dp)), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 11.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("书源池", fontSize = UiTypography.emphasis, fontWeight = FontWeight.Bold, color = Ink)
                }
                BasicTextField(
                    value = state.filterQuery,
                    onValueChange = { state.filterQuery = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = BodyText,
                        fontSize = UiTypography.body,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 0.sp,
                    ),
                    modifier = Modifier.width(214.dp).height(30.dp)
                        .clip(RoundedCornerShape(8.dp)).border(1.dp, Line, RoundedCornerShape(8.dp))
                        .background(Color.White),
                    decorationBox = { innerTextField ->
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("⌕", fontSize = UiTypography.sectionTitle, color = Muted)
                            Box(
                                Modifier.weight(1f).fillMaxHeight(),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (state.filterQuery.isEmpty()) {
                                    Text("搜索源名或 URL", fontSize = UiTypography.label, color = Muted, maxLines = 1)
                                }
                                innerTextField()
                            }
                        }
                    },
                )
                OutlinedButton(
                    onClick = { state.filterQuery = "" },
                    enabled = state.filterQuery.isNotEmpty(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 11.dp, vertical = 4.dp),
                ) { Text("清除", fontSize = UiTypography.body) }
            }
            HorizontalDivider(color = Line)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                sourceFilterLabels.forEach { label ->
                    val count = sourceFilterCount(state, label)
                    val selected = state.sourceFilter == label
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp))
                            .background(if (selected) SoftBlue else Color.Transparent)
                            .border(1.dp, if (selected) SoftBlueBorder else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable { state.sourceFilter = label }
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$label $count", fontSize = UiTypography.label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = if (selected) Primary else BodyText, maxLines = 1)
                    }
                }
                if (state.stage == WorkflowStage.DEDUP_REVIEW) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        val duplicateCount = state.duplicateGroups.sumOf { it.sourceIds.size }
                        Text(
                            "全部分组 · ${state.duplicateGroups.size} 组 · ${duplicateCount} 个重复候选",
                            fontSize = UiTypography.label,
                            color = BodyText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            HorizontalDivider(color = Line)
            if (state.stage == WorkflowStage.ORGANIZE_REVIEW) {
                OrganizeRulePanel(
                    state = state,
                    onRuleChange = onRuleChange,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp),
                )
                Spacer(Modifier.height(4.dp))
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val tableWidth = maxOf(maxWidth, 1040.dp)
                Row(Modifier.fillMaxHeight().horizontalScroll(rememberScrollState())) {
                Column(Modifier.width(tableWidth).fillMaxHeight()) {
            TableHeader()
            HorizontalDivider(color = Line)
            if (state.sources.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("尚未导入书源", fontSize = UiTypography.sectionTitle, color = BodyText)
                        Text("点击右上角「导入 JSON」选择书源文件。", fontSize = UiTypography.body, color = Muted)
                    }
                }
            } else {
                val rows = buildSourceRows(state)
                if (rows.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("没有符合条件的书源", fontSize = UiTypography.emphasis, color = BodyText)
                            Text("试试切换筛选条件或搜索词。", fontSize = UiTypography.label, color = Muted)
                        }
                    }
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(end = 10.dp),
                    ) {
                        items(rows, key = { it.key }) { row ->
                            when (row) {
                                is SourceListRow.GroupHeader -> {
                                    val countLabel = if (row.visibleCount == row.group.sourceIds.size) {
                                        "${row.visibleCount} 个候选"
                                    } else {
                                        "当前显示 ${row.visibleCount}/${row.group.sourceIds.size} 个候选"
                                    }
                                    Row(
                                        Modifier.fillMaxWidth().background(SoftBlue).padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "重复组 ${row.group.number} · ${row.group.kind.label}",
                                            fontSize = UiTypography.label,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryDeep,
                                        )
                                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                            Text(
                                                "$countLabel · 待删除 ${row.visibleMarkedCount}",
                                                fontSize = UiTypography.caption,
                                                color = Muted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { onCompare(row.group.number) },
                                            enabled = !state.busy,
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                                        ) { Text("对比重复组", fontSize = UiTypography.caption) }
                                    }
                                }
                                is SourceListRow.SourceEntry -> SourceRowItem(state = state, source = row.source, onDetail = onDetail, onEdit = onEdit)
                            }
                            HorizontalDivider(color = Line)
                        }
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(listState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 3.dp),
                    )
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
        Modifier.fillMaxWidth().background(SoftNeutral).padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("源名", Modifier.weight(1.55f), fontSize = UiTypography.label, fontWeight = FontWeight.Bold, color = BodyText)
        Text("URL", Modifier.weight(2.15f), fontSize = UiTypography.label, fontWeight = FontWeight.Bold, color = BodyText)
        Text("类型", Modifier.weight(0.65f), fontSize = UiTypography.label, fontWeight = FontWeight.Bold, color = BodyText)
        Text("分组", Modifier.weight(0.9f), fontSize = UiTypography.label, fontWeight = FontWeight.Bold, color = BodyText)
        Text("耗时", Modifier.weight(0.6f), fontSize = UiTypography.label, fontWeight = FontWeight.Bold, color = BodyText)
        Text("状态", Modifier.weight(0.95f), fontSize = UiTypography.label, fontWeight = FontWeight.Bold, color = BodyText)
        Text("建议", Modifier.weight(1.1f), fontSize = UiTypography.label, fontWeight = FontWeight.Bold, color = BodyText)
        Text("操作", Modifier.weight(1.35f), fontSize = UiTypography.label, fontWeight = FontWeight.Bold, color = BodyText)
    }
}


@Composable
private fun TableActionButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp))
            .border(1.dp, Line, RoundedCornerShape(6.dp))
            .background(Color.White)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = UiTypography.label, color = if (enabled) Primary else Muted)
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
    val probeStyle = probeStatusStyle(source.probe?.status)
    Row(
        Modifier.fillMaxWidth().background(if (selected) SelectedSurface else Color.White)
            .clickable { state.selectedId = source.id }.padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name.ifBlank { "（未命名）" },
            Modifier.weight(1.55f).padding(end = 5.dp),
            fontSize = UiTypography.body,
            fontWeight = FontWeight.SemiBold,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            source.doc.url.ifBlank { "—" },
            Modifier.weight(2.15f).padding(end = 5.dp),
            fontSize = UiTypography.label,
            fontWeight = FontWeight.Medium,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        SmallChip(sourceTypeLabel(source.doc.type), SoftSlate, Muted, Modifier.weight(0.65f))
        Text(group.ifBlank { "—" }, Modifier.weight(0.9f), fontSize = UiTypography.label, color = BodyText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(elapsed, Modifier.weight(0.6f), fontSize = UiTypography.caption, color = Muted, maxLines = 1)
        SmallChip(status, probeStyle.background, probeStyle.foreground, Modifier.weight(0.95f))
        SmallChip(
            advice,
            if (source.deleteMarked) SoftRed else if (source.probe?.status == ProbeStatus.OK) SoftGreen else SoftSlate,
            if (source.deleteMarked) Bad else if (source.probe?.status == ProbeStatus.OK) Ok else Muted,
            Modifier.weight(1.1f),
        )
        Row(Modifier.weight(1.35f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            TableActionButton("详情", onClick = { onDetail(source.id) })
            TableActionButton("编辑", onClick = { onEdit(source.id) }, enabled = !state.busy && state.stage != WorkflowStage.EXPORT)
        }
    }
}


internal data class SourceDetailField(
    val key: String,
    val label: String,
    val value: String,
    val configured: Boolean,
)

internal data class SourceDetailSection(
    val title: String,
    val description: String,
    val fields: List<SourceDetailField>,
)

internal enum class SourceDetailTab(
    val label: String,
    val sectionTitles: Set<String>,
) {
    OVERVIEW("概览", emptySet()),
    BASIC("基本", setOf("基本信息", "请求与登录", "备注与运行数据")),
    SEARCH("搜索", setOf("搜索配置", "搜索规则")),
    EXPLORE("发现", setOf("发现配置", "发现规则")),
    BOOK_INFO("书籍信息", setOf("书籍信息规则")),
    TOC("目录", setOf("目录规则")),
    CONTENT("正文", setOf("正文规则")),
    REVIEW("段评", setOf("段评规则")),
    SCRIPTS("脚本", setOf("JavaScript 与扩展")),
    EXTRA("扩展", setOf("其他 / 上游扩展字段")),
}

internal fun sourceDetailSectionsForTab(
    sections: List<SourceDetailSection>,
    tab: SourceDetailTab,
): List<SourceDetailSection> = sections.filter { it.title in tab.sectionTitles }

private data class SourceDetailFieldSpec(val key: String, val label: String)

internal fun sourceDetailSections(doc: BookSourceDoc): List<SourceDetailSection> {
    val raw = doc.raw
    val knownTopLevelKeys = linkedSetOf<String>()
    val sections = mutableListOf<SourceDetailSection>()

    fun field(key: String, label: String, element: com.google.gson.JsonElement?): SourceDetailField {
        if (element == null || element.isJsonNull) return SourceDetailField(key, label, "未配置", false)
        val value = if (element.isJsonPrimitive) {
            val primitive = element.asJsonPrimitive
            if (primitive.isString) primitive.asString else primitive.toString()
        } else {
            com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(element)
        }
        val configured = when {
            element.isJsonPrimitive -> value.isNotBlank()
            element.isJsonObject -> element.asJsonObject.size() > 0
            element.isJsonArray -> element.asJsonArray.size() > 0
            else -> true
        }
        return SourceDetailField(key, label, value.ifBlank { "未配置" }, configured)
    }

    fun addTop(title: String, description: String, specs: List<SourceDetailFieldSpec>) {
        specs.forEach { knownTopLevelKeys += it.key }
        sections += SourceDetailSection(title, description, specs.map { field(it.key, it.label, raw.get(it.key)) })
    }

    fun addRule(key: String, title: String, description: String, specs: List<SourceDetailFieldSpec>) {
        knownTopLevelKeys += key
        val original = raw.get(key)
        val obj = when {
            original == null || original.isJsonNull -> null
            original.isJsonObject -> original.asJsonObject
            original.isJsonPrimitive && original.asJsonPrimitive.isString -> runCatching {
                com.google.gson.JsonParser.parseString(original.asString).takeIf { it.isJsonObject }?.asJsonObject
            }.getOrNull()
            else -> null
        }
        val known = specs.mapTo(linkedSetOf()) { it.key }
        val fields = specs.map { field("$key.${it.key}", it.label, obj?.get(it.key)) }.toMutableList()
        obj?.entrySet()?.filterNot { it.key in known }?.forEach { (nestedKey, value) ->
            fields += field("$key.$nestedKey", "$nestedKey（上游扩展）", value)
        }
        if (obj == null && original != null && !original.isJsonNull) {
            fields += field(key, "原始规则内容", original)
        }
        sections += SourceDetailSection(title, description, fields)
    }

    addTop("基本信息", "书源身份与分类信息。", listOf(
        SourceDetailFieldSpec("bookSourceName", "书源名称"),
        SourceDetailFieldSpec("bookSourceUrl", "书源地址"),
        SourceDetailFieldSpec("bookSourceGroup", "书源分组"),
        SourceDetailFieldSpec("bookSourceType", "书源类型"),
        SourceDetailFieldSpec("bookUrlPattern", "详情页 URL 正则"),
        SourceDetailFieldSpec("customOrder", "手动排序"),
    ))
    // 总开关仍留在原始 JSON 与编辑流程中，但详情页不再重复展示启用状态。
    knownTopLevelKeys += setOf("enabled", "enabledExplore")
    addTop("请求与登录", "请求头、并发、Cookie 与登录流程配置。", listOf(
        SourceDetailFieldSpec("header", "请求头"),
        SourceDetailFieldSpec("concurrentRate", "并发率"),
        SourceDetailFieldSpec("enabledCookieJar", "自动保存 Cookie"),
        SourceDetailFieldSpec("loginUrl", "登录地址"),
        SourceDetailFieldSpec("loginUi", "登录界面配置"),
        SourceDetailFieldSpec("loginCheckJs", "登录状态检测 JS"),
    ))
    addTop("搜索配置", "搜索入口地址。", listOf(SourceDetailFieldSpec("searchUrl", "搜索 URL")))
    addRule("ruleSearch", "搜索规则", "搜索列表、书名、作者、简介及校验关键字等。", listOf(
        SourceDetailFieldSpec("checkKeyWord", "校验关键字"),
        SourceDetailFieldSpec("bookList", "书籍列表规则"),
        SourceDetailFieldSpec("name", "书名规则"),
        SourceDetailFieldSpec("author", "作者规则"),
        SourceDetailFieldSpec("intro", "简介规则"),
        SourceDetailFieldSpec("kind", "分类规则"),
        SourceDetailFieldSpec("lastChapter", "最新章节规则"),
        SourceDetailFieldSpec("updateTime", "更新时间规则"),
        SourceDetailFieldSpec("bookUrl", "书籍详情 URL 规则"),
        SourceDetailFieldSpec("coverUrl", "封面 URL 规则"),
        SourceDetailFieldSpec("wordCount", "字数规则"),
    ))
    addTop("发现配置", "发现入口与筛选配置。", listOf(
        SourceDetailFieldSpec("exploreUrl", "发现 URL"),
        SourceDetailFieldSpec("exploreScreen", "发现筛选规则"),
    ))
    addRule("ruleExplore", "发现规则", "发现列表及列表项的书籍信息提取规则。", listOf(
        SourceDetailFieldSpec("bookList", "书籍列表规则"),
        SourceDetailFieldSpec("name", "书名规则"),
        SourceDetailFieldSpec("author", "作者规则"),
        SourceDetailFieldSpec("intro", "简介规则"),
        SourceDetailFieldSpec("kind", "分类规则"),
        SourceDetailFieldSpec("lastChapter", "最新章节规则"),
        SourceDetailFieldSpec("updateTime", "更新时间规则"),
        SourceDetailFieldSpec("bookUrl", "书籍详情 URL 规则"),
        SourceDetailFieldSpec("coverUrl", "封面 URL 规则"),
        SourceDetailFieldSpec("wordCount", "字数规则"),
    ))
    addRule("ruleBookInfo", "书籍信息规则", "书籍详情页字段与初始化逻辑。", listOf(
        SourceDetailFieldSpec("init", "初始化规则"),
        SourceDetailFieldSpec("name", "书名规则"),
        SourceDetailFieldSpec("author", "作者规则"),
        SourceDetailFieldSpec("intro", "简介规则"),
        SourceDetailFieldSpec("kind", "分类规则"),
        SourceDetailFieldSpec("lastChapter", "最新章节规则"),
        SourceDetailFieldSpec("updateTime", "更新时间规则"),
        SourceDetailFieldSpec("coverUrl", "封面 URL 规则"),
        SourceDetailFieldSpec("tocUrl", "目录 URL 规则"),
        SourceDetailFieldSpec("wordCount", "字数规则"),
        SourceDetailFieldSpec("canReName", "允许重命名规则"),
        SourceDetailFieldSpec("downloadUrls", "下载地址规则"),
    ))
    addRule("ruleToc", "目录规则", "章节列表、章节名称、章节地址与目录更新处理。", listOf(
        SourceDetailFieldSpec("preUpdateJs", "更新前 JS"),
        SourceDetailFieldSpec("chapterList", "章节列表规则"),
        SourceDetailFieldSpec("chapterName", "章节名称规则"),
        SourceDetailFieldSpec("chapterUrl", "章节 URL 规则"),
        SourceDetailFieldSpec("formatJs", "格式化 JS"),
        SourceDetailFieldSpec("isVolume", "分卷规则"),
        SourceDetailFieldSpec("isVip", "VIP 章节规则"),
        SourceDetailFieldSpec("isPay", "付费章节规则"),
        SourceDetailFieldSpec("updateTime", "更新时间规则"),
        SourceDetailFieldSpec("nextTocUrl", "下一页目录 URL"),
    ))
    addRule("ruleContent", "正文规则", "正文提取、替换、图片处理、回调与批量下载配置。", listOf(
        SourceDetailFieldSpec("content", "正文规则"),
        SourceDetailFieldSpec("subContent", "副正文规则"),
        SourceDetailFieldSpec("title", "正文标题规则"),
        SourceDetailFieldSpec("nextContentUrl", "下一页正文 URL"),
        SourceDetailFieldSpec("webJs", "网页 JS"),
        SourceDetailFieldSpec("sourceRegex", "正文替换前正则"),
        SourceDetailFieldSpec("replaceRegex", "正文替换规则"),
        SourceDetailFieldSpec("imageStyle", "图片样式"),
        SourceDetailFieldSpec("imageDecode", "图片解密 JS"),
        SourceDetailFieldSpec("payAction", "购买操作"),
        SourceDetailFieldSpec("callBackJs", "事件回调 JS"),
        SourceDetailFieldSpec("contentBatch", "批量正文规则"),
        SourceDetailFieldSpec("maxBatchSize", "最大批量数量"),
    ))
    addRule("ruleReview", "段评规则", "段评列表、内容、回复及相关接口规则。", listOf(
        SourceDetailFieldSpec("reviewUrl", "段评 URL"),
        SourceDetailFieldSpec("avatarRule", "头像规则"),
        SourceDetailFieldSpec("contentRule", "段评内容规则"),
        SourceDetailFieldSpec("postTimeRule", "发布时间规则"),
        SourceDetailFieldSpec("reviewQuoteUrl", "段评回复 URL"),
        SourceDetailFieldSpec("voteUpUrl", "点赞 URL"),
        SourceDetailFieldSpec("voteDownUrl", "点踩 URL"),
        SourceDetailFieldSpec("postReviewUrl", "发送回复 URL"),
        SourceDetailFieldSpec("postQuoteUrl", "发送段评回复 URL"),
        SourceDetailFieldSpec("deleteUrl", "删除段评 URL"),
        SourceDetailFieldSpec("enabled", "启用段评"),
        SourceDetailFieldSpec("reviewSummaryUrl", "段评摘要 URL"),
        SourceDetailFieldSpec("summaryListRule", "摘要列表规则"),
        SourceDetailFieldSpec("summaryParagraphIndexRule", "摘要段落索引规则"),
        SourceDetailFieldSpec("summaryParagraphDataRule", "摘要段落内容规则"),
        SourceDetailFieldSpec("summaryCountRule", "摘要数量规则"),
        SourceDetailFieldSpec("reviewDetailUrl", "段评详情 URL"),
        SourceDetailFieldSpec("reviewDetailNextPageUrl", "段评详情下一页 URL"),
        SourceDetailFieldSpec("detailListRule", "详情列表规则"),
        SourceDetailFieldSpec("detailIdRule", "详情 ID 规则"),
        SourceDetailFieldSpec("detailAvatarRule", "详情头像规则"),
        SourceDetailFieldSpec("detailNameRule", "详情名称规则"),
        SourceDetailFieldSpec("detailBadgeRule", "详情徽章规则"),
        SourceDetailFieldSpec("detailContentRule", "详情内容规则"),
        SourceDetailFieldSpec("replyListRule", "回复列表规则"),
        SourceDetailFieldSpec("replyIdRule", "回复 ID 规则"),
        SourceDetailFieldSpec("replyAvatarRule", "回复头像规则"),
        SourceDetailFieldSpec("replyNameRule", "回复名称规则"),
        SourceDetailFieldSpec("replyBadgeRule", "回复徽章规则"),
        SourceDetailFieldSpec("replyContentRule", "回复内容规则"),
    ))
    addTop("JavaScript 与扩展", "脚本库、主脚本、封面解密与事件回调。", listOf(
        SourceDetailFieldSpec("jsLib", "JS 库"),
        SourceDetailFieldSpec("coverDecodeJs", "封面解密 JS"),
        SourceDetailFieldSpec("mainJs", "书源主 JS"),
        SourceDetailFieldSpec("eventListener", "事件监听"),
        SourceDetailFieldSpec("customButton", "自定义按钮"),
    ))
    addTop("备注与运行数据", "书源备注、变量说明及运行统计数据。", listOf(
        SourceDetailFieldSpec("bookSourceComment", "书源备注"),
        SourceDetailFieldSpec("variableComment", "变量说明"),
        SourceDetailFieldSpec("lastUpdateTime", "最后更新时间"),
        SourceDetailFieldSpec("respondTime", "响应耗时记录"),
        SourceDetailFieldSpec("weight", "智能排序权重"),
    ))

    val extra = raw.entrySet().filterNot { it.key in knownTopLevelKeys }
        .map { (key, value) -> field(key, key, value) }
    if (extra.isNotEmpty()) {
        sections += SourceDetailSection(
            "其他 / 上游扩展字段",
            "以下字段不属于当前固定字段清单，仍从原始 JSON 完整读取并展示。",
            extra,
        )
    }
    return sections
}

@Composable
private fun DetailPanel(
    state: WorkflowState,
    sourceId: Int,
    onDismiss: () -> Unit,
    onEdit: (Int) -> Unit,
    onToggleDelete: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = state.sources.firstOrNull { it.id == sourceId }
    Card(
        modifier.border(1.dp, Line, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        if (source == null) {
            Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
                Text("选择一条书源查看详情。", fontSize = UiTypography.body, color = Muted)
            }
        } else {
            val preview = state.preview?.get(source.id)
                .takeIf { state.stage == WorkflowStage.ORGANIZE_REVIEW || state.stage == WorkflowStage.EXPORT }
            val probeStyle = probeStatusStyle(source.probe?.status)
            val detailSections = remember(source.id, source.doc.raw, preview) { sourceDetailSections(source.doc) }
            var selectedDetailTab by remember(source.id) { mutableStateOf(SourceDetailTab.OVERVIEW) }
            val selectedDetailSections = sourceDetailSectionsForTab(detailSections, selectedDetailTab)
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().background(Color.White)
                        .padding(start = 20.dp, end = 12.dp, top = 15.dp, bottom = 13.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            "SOURCE #${(source.id + 1).toString().padStart(3, '0')}  /  ${state.stage.label}",
                            fontSize = UiTypography.caption, fontWeight = FontWeight.Bold, color = Primary,
                        )
                        Text(
                            preview?.name ?: source.doc.name.ifBlank { "（未命名书源）" },
                            fontSize = UiTypography.metric, fontWeight = FontWeight.Bold, color = Ink,
                        )
                        Text(source.doc.url.ifBlank { "未配置书源地址" }, fontSize = UiTypography.label, color = BodyText)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        SmallChip(source.probe?.status?.label ?: "待校验", probeStyle.background, probeStyle.foreground)
                        TextButton(onClick = onDismiss) { Text("关闭", fontSize = UiTypography.body) }
                    }
                }
                HorizontalDivider(color = Line)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DetailMetric("书源类型", sourceTypeLabel(source.doc.type), Modifier.weight(1f))
                    DetailMetric("已配置规则字段", source.doc.ruleCompleteness.toString(), Modifier.weight(1f))
                    DetailMetric("响应耗时", source.probe?.let { "${it.elapsedMs} ms" } ?: "暂无", Modifier.weight(1f))
                }
                HorizontalDivider(color = Line)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SourceDetailTab.values().forEach { tab ->
                        val selected = selectedDetailTab == tab
                        Column(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .clickable { selectedDetailTab = tab }
                                .padding(horizontal = 11.dp, vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                tab.label,
                                fontSize = UiTypography.body,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) Primary else Muted,
                                maxLines = 1,
                            )
                            Box(
                                Modifier.fillMaxWidth().height(2.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (selected) Primary else Color.Transparent),
                            )
                        }
                    }
                }
                HorizontalDivider(color = Line)
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (selectedDetailTab == SourceDetailTab.OVERVIEW) {
                        DetailOverviewContent(source, preview)
                    } else {
                        DetailSectionsContent(
                            tab = selectedDetailTab,
                            sections = selectedDetailSections,
                        )
                    }
                }
                HorizontalDivider(color = Line)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (
                        state.stage == WorkflowStage.DEDUP_REVIEW ||
                        state.stage == WorkflowStage.PROBE_REVIEW ||
                        state.stage == WorkflowStage.ORGANIZE_REVIEW
                    ) {
                        OutlinedButton(
                            onClick = { onToggleDelete(source.id) },
                            enabled = !state.busy,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (source.deleteMarked) Ok else Bad),
                        ) { Text(if (source.deleteMarked) "撤销删除标记" else "标记为删除") }
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { onEdit(source.id) },
                        enabled = !state.busy && state.stage != WorkflowStage.EXPORT,
                    ) { Text(if (preview != null && state.stage == WorkflowStage.ORGANIZE_REVIEW) "编辑整理结果" else "编辑常用字段") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color.White),
                    ) { Text("完成") }
                }
            }
        }
    }
}

@Composable
private fun DetailOverviewContent(source: WorkflowSource, preview: PreviewSource?) {
    Text("审核与处理信息", fontSize = UiTypography.emphasis, fontWeight = FontWeight.Bold, color = Ink)
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (source.deleteMarked) SoftRed else SoftNeutral),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                if (source.deleteMarked) "当前标记：待删除" else "当前建议：${source.suggestion}",
                fontSize = UiTypography.body,
                fontWeight = FontWeight.SemiBold,
                color = if (source.deleteMarked) Bad else Ink,
            )
            Text(
                when {
                    source.deleteMarked -> "该书源会在确认进入下一阶段时从当前工作池移除。"
                    source.probe?.status == ProbeStatus.OK -> "校验成功；仍可根据规则完整度与重复候选情况人工判断。"
                    source.probe?.status?.let { it == ProbeStatus.TIMEOUT || it == ProbeStatus.NETWORK || it == ProbeStatus.HTTP_ERROR } == true ->
                        "网络或服务响应异常不代表书源一定失效，建议人工复核。"
                    else -> "系统建议仅供审核参考，确认阶段前可以修改标记。"
                },
                fontSize = UiTypography.label,
                color = Muted,
                lineHeight = 16.sp,
            )
            source.probe?.let { result ->
                if (!result.detail.isNullOrBlank()) {
                    HorizontalDivider(color = Line)
                    Text("校验详情", fontSize = UiTypography.label, fontWeight = FontWeight.Bold, color = Ink)
                    Text(result.detail.orEmpty(), fontSize = UiTypography.label, color = BodyText, lineHeight = 16.sp)
                }
                result.httpCode?.let { Text("HTTP 状态码：$it", fontSize = UiTypography.caption, color = Muted) }
            }
        }
    }
    if (preview != null) {
        Text("整理预览", fontSize = UiTypography.emphasis, fontWeight = FontWeight.Bold, color = Ink)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(SoftRed).padding(11.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text("修改前", fontSize = UiTypography.label, fontWeight = FontWeight.Bold, color = Bad)
                Text(source.doc.name.ifBlank { "（未命名）" }, fontSize = UiTypography.label, color = BodyText)
                Text(source.doc.group.ifBlank { "未分组" }, fontSize = UiTypography.caption, color = Muted)
            }
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(SoftGreen).padding(11.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text("预览结果", fontSize = UiTypography.label, fontWeight = FontWeight.Bold, color = Ok)
                Text(preview.name.ifBlank { "（未命名）" }, fontSize = UiTypography.label, color = BodyText)
                Text(preview.group.ifBlank { "未分组" }, fontSize = UiTypography.caption, color = Muted)
            }
        }
    }
}

@Composable
private fun DetailSectionsContent(
    tab: SourceDetailTab,
    sections: List<SourceDetailSection>,
) {
    Text(tab.label, fontSize = UiTypography.emphasis, fontWeight = FontWeight.Bold, color = Ink)
    if (sections.isEmpty()) {
        Text("该分类没有可展示的字段。", fontSize = UiTypography.label, color = Muted)
    }
    sections.forEach { section ->
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .background(SoftNeutral).padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(section.title, fontSize = UiTypography.body, fontWeight = FontWeight.Bold, color = Ink)
                SmallChip("${section.fields.size} 项", SoftNeutral, Muted)
            }
            Text(section.description, fontSize = UiTypography.label, color = BodyText)
            section.fields.chunked(2).forEach { pair ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    DetailValueCard(pair[0], Modifier.weight(1f))
                    if (pair.size > 1) DetailValueCard(pair[1], Modifier.weight(1f))
                    else Spacer(Modifier.weight(1f))
                }
            }
        }
    }
    Text(
        "字段展示以导入 JSON 为准；未知字段不会被丢弃，导出时仍由原始 JSON 数据保留。",
        fontSize = UiTypography.caption,
        color = Muted,
    )
}

@Composable
private fun DetailMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(8.dp)).background(SoftNeutral).padding(horizontal = 11.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label, fontSize = UiTypography.caption, color = Muted)
        Text(value, fontSize = UiTypography.emphasis, fontWeight = FontWeight.SemiBold, color = Ink)
    }
}

@Composable
private fun DetailValueCard(field: SourceDetailField, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (field.configured) Line else SoftSlate, RoundedCornerShape(8.dp))
            .background(if (field.configured) Color.White else SoftSlate)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(field.label, Modifier.weight(1f), fontSize = UiTypography.label, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(if (field.configured) "已配置" else "未配置", fontSize = UiTypography.caption, color = if (field.configured) Ok else Muted)
        }
        Text(field.key, fontSize = UiTypography.caption, color = Muted)
        Text(
            field.value,
            fontSize = UiTypography.body,
            color = if (field.configured) Ink else Muted,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun DetailField(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, Modifier.width(110.dp), fontSize = UiTypography.label, color = Muted)
        Text(value.ifBlank { "—" }, Modifier.weight(1f), fontSize = UiTypography.body, color = Ink)
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
    val stageNumber = state.stage.ordinal + 1
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, Line, RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.width(154.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                Modifier.size(25.dp).clip(RoundedCornerShape(50)).background(SoftBlue),
                contentAlignment = Alignment.Center,
            ) {
                Text(stageNumber.toString(), fontSize = UiTypography.label, fontWeight = FontWeight.Bold, color = Primary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("当前阶段", fontSize = UiTypography.caption, color = Muted, maxLines = 1)
                Text(state.stage.label, fontSize = UiTypography.label, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1)
            }
            Text("$stageNumber/5", fontSize = UiTypography.caption, color = Muted, maxLines = 1)
        }
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                .background(SoftBlue.copy(alpha = 0.65f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("✓", fontSize = UiTypography.label, fontWeight = FontWeight.Bold, color = Primary)
            Text(
                state.notice?.takeIf { it.isNotBlank() } ?: "选择当前阶段的操作继续工作流",
                Modifier.weight(1f),
                fontSize = UiTypography.label,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (state.stage) {
                WorkflowStage.IMPORT -> {
                    OutlinedButton(
                        onClick = onShowImportInfo,
                        enabled = state.inputFile != null && !state.busy,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                    ) { Text("导入信息", fontSize = UiTypography.label) }
                    Button(
                        onClick = onStartDedup,
                        enabled = state.sources.isNotEmpty() && !state.busy,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 3.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color.White),
                    ) { Text("开始去重", fontSize = UiTypography.label) }
                }
                WorkflowStage.DEDUP_REVIEW -> {
                    Button(
                        onClick = onConfirmDedup,
                        enabled = state.sources.isNotEmpty() && !state.busy,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 3.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color.White),
                    ) { Text("确认去重并进入校验", fontSize = UiTypography.label) }
                }
                WorkflowStage.PROBE_REVIEW -> {
                    OutlinedButton(
                        onClick = { state.sourceFilter = "异常 / 复核" },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                    ) { Text("异常项", fontSize = UiTypography.label) }
                    if (state.busy) {
                        OutlinedButton(
                            onClick = onStop,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                        ) { Text("停止", fontSize = UiTypography.label) }
                    } else if (state.sources.any { it.probe == null }) {
                        Button(
                            onClick = onContinueValidation,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 3.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color.White),
                        ) { Text("继续校验", fontSize = UiTypography.label) }
                    } else {
                        Button(
                            onClick = onConfirmValidation,
                            enabled = state.sources.isNotEmpty(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 3.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color.White),
                        ) { Text("确认校验并进入整理", fontSize = UiTypography.label) }
                    }
                }
                WorkflowStage.ORGANIZE_REVIEW -> {
                    if (state.preview == null) {
                        OutlinedButton(
                            onClick = { state.sourceFilter = "待删除" },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                        ) { Text("待删除项", fontSize = UiTypography.label) }
                        Button(
                            onClick = onGeneratePreview,
                            enabled = state.sources.isNotEmpty() && !state.busy,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 3.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color.White),
                        ) { Text("生成预览", fontSize = UiTypography.label) }
                    } else {
                        OutlinedButton(
                            onClick = onGeneratePreview,
                            enabled = !state.busy,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                        ) { Text("重新生成", fontSize = UiTypography.label) }
                        Button(
                            onClick = onConfirmOrganize,
                            enabled = !state.busy,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 3.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color.White),
                        ) { Text("确认整理并导出", fontSize = UiTypography.label) }
                    }
                }
                WorkflowStage.EXPORT -> {
                    OutlinedButton(
                        onClick = { state.showReport = true },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                    ) { Text("最终清单", fontSize = UiTypography.label) }
                    if (DirectLink.isUsable(state.directUrl)) {
                        OutlinedButton(
                            onClick = onCopyImportLink,
                            enabled = !state.busy,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                        ) { Text("复制导入链接", fontSize = UiTypography.label) }
                    }
                    OutlinedButton(
                        onClick = onOpenFolder,
                        enabled = !state.busy,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                    ) { Text("打开目录", fontSize = UiTypography.label) }
                    Button(
                        onClick = onExport,
                        enabled = state.sources.isNotEmpty() && !state.busy,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 3.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color.White),
                    ) { Text(if (state.exported == null) "导出 JSON" else "再次导出", fontSize = UiTypography.label) }
                }
            }
        }
    }
}

@Composable
private fun ImportInfoDialog(state: WorkflowState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(12.dp),
        containerColor = Color.White,
        titleContentColor = Ink,
        textContentColor = BodyText,
        tonalElevation = 8.dp,
        title = { Text("导入信息", fontSize = UiTypography.sectionTitle, fontWeight = FontWeight.Bold, color = Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailField("文件", state.inputFile?.absolutePath ?: "尚未导入")
                DetailField("导入时书源数", state.inputCount.toString())
                DetailField("当前工作池", state.sources.size.toString())
                DetailField("从工作池移除", state.removedCount.toString())
                Text("阶段确认只修改本次内存工作集。原始导入文件不会被覆盖。", fontSize = UiTypography.body, color = Muted)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun SettingsDialog(state: WorkflowState, onDismiss: () -> Unit, onChooseFolder: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(12.dp),
        containerColor = Color.White,
        titleContentColor = Ink,
        textContentColor = BodyText,
        tonalElevation = 8.dp,
        title = { Text("处理设置", fontSize = UiTypography.sectionTitle, fontWeight = FontWeight.Bold, color = Ink) },
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
                    Text("跳过真实校验", fontSize = UiTypography.body)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = state.withToc, onCheckedChange = { state.withToc = it })
                    Spacer(Modifier.width(8.dp))
                    Text("校验时继续检查目录（更慢）", fontSize = UiTypography.body)
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
        shape = RoundedCornerShape(12.dp),
        containerColor = Color.White,
        titleContentColor = Ink,
        textContentColor = BodyText,
        tonalElevation = 8.dp,
        title = { Text("本次处理报告", fontSize = UiTypography.sectionTitle, fontWeight = FontWeight.Bold, color = Ink) },
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
                Text("校验分类", fontWeight = FontWeight.SemiBold, fontSize = UiTypography.body)
                if (counts.isEmpty()) Text("尚无校验结果", color = Muted, fontSize = UiTypography.body)
                counts.forEach { (status, count) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(status.label, Modifier.weight(1f), fontSize = UiTypography.body, color = probeStatusStyle(status).foreground)
                        Text(count.toString(), fontSize = UiTypography.body, fontWeight = FontWeight.SemiBold)
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
    var keepId by remember(group.number) {
        mutableStateOf(candidates.firstOrNull { !it.deleteMarked }?.id ?: group.suggestedKeepId)
    }
    var keepAll by remember(group.number) {
        mutableStateOf(candidates.isNotEmpty() && candidates.all { !it.deleteMarked })
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).widthIn(max = 1080.dp).heightIn(max = 760.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            shadowElevation = 24.dp,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().background(SoftNeutral).padding(horizontal = 18.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("对比重复组 ${group.number}", fontSize = UiTypography.sectionTitle, fontWeight = FontWeight.Bold, color = Ink)
                        Text(
                            "相同 URL · ${candidates.size} 个候选",
                            fontSize = UiTypography.label,
                            color = Muted,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("×", fontSize = UiTypography.pageTitle, color = Muted) }
                }
                HorizontalDivider(color = Line)
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        candidates.forEach { source ->
                            val recommended = source.id == group.suggestedKeepId
                            val proposedKeep = keepAll || source.id == keepId
                            val isPreferred = source.id == keepId && !keepAll
                            Card(
                                Modifier.weight(1f).widthIn(min = 230.dp).border(
                                    1.dp,
                                    if (isPreferred) SoftGreenBorder else Line,
                                    RoundedCornerShape(8.dp),
                                ),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        isPreferred -> SoftGreen
                                        proposedKeep -> SoftGreen
                                        else -> SoftAmber
                                    },
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(7.dp),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .background(if (proposedKeep) SoftGreen else SoftAmber)
                                            .padding(end = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            selected = !keepAll && keepId == source.id,
                                            onClick = { keepId = source.id },
                                            enabled = !state.busy && !keepAll,
                                        )
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                source.doc.name.ifBlank { "（未命名）" },
                                                fontSize = UiTypography.body,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Ink,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                when {
                                                    !proposedKeep -> "将标记删除"
                                                    keepAll -> "本组全部保留"
                                                    recommended -> "系统推荐保留"
                                                    else -> "当前选择保留"
                                                },
                                                fontSize = UiTypography.caption,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (proposedKeep) Ok else Bad,
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
                                    DetailField("启用状态", if (source.isDisabledForDisplay()) "已禁用" else "启用")
                                    DetailField("配置权重", source.doc.raw.get("weight")?.takeIf { it.isJsonPrimitive }?.asString ?: "—")
                                    DetailField("规则字段", "${source.doc.raw.size()} 个")
                                    DetailField("系统评分", String.format(java.util.Locale.ROOT, "%.2f", Deduper.keepScore(source.doc)))
                                    HorizontalDivider(color = Line)
                                    Text(
                                        "系统建议：${if (recommended) "保留" else "删除"}",
                                        fontSize = UiTypography.caption,
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
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = keepAll, onCheckedChange = { keepAll = it }, enabled = !state.busy)
                        Text("本组全部保留（认为它们并不重复）", fontSize = UiTypography.label, color = BodyText)
                    }
                    OutlinedButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(7.dp))
                    Button(
                        onClick = {
                            candidates.forEach { candidate ->
                                val keep = keepAll || candidate.id == keepId
                                val index = state.sources.indexOfFirst { it.id == candidate.id }
                                if (index >= 0) state.sources[index] = state.sources[index].copy(
                                    deleteMarked = !keep,
                                    suggestion = if (keep) {
                                        if (keepAll) "用户确认：本组全部保留" else "用户指定保留"
                                    } else "用户确认：同组其他候选删除",
                                )
                            }
                            onDismiss()
                        },
                        enabled = !state.busy && candidates.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color.White),
                    ) { Text("保存本组决定") }
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
        shape = RoundedCornerShape(12.dp),
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
                        Text("启用书源", fontSize = UiTypography.body)
                    }
                }
                OutlinedTextField(value = group, onValueChange = { group = it }, label = { Text("分组") }, modifier = Modifier.fillMaxWidth())
                if (previewMode) {
                    Text("这里只编辑预览值；点击「确认整理并进入导出」后才会应用。", fontSize = UiTypography.label, color = Muted)
                } else {
                    Text("只修改已填写的字段，其余未知 JSON 字段会原样保留。", fontSize = UiTypography.label, color = Muted)
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

private fun sourceFilterCount(state: WorkflowState, filter: String): Int =
    state.sources.count { source ->
        isSourceIncludedInFilter(
            filter = filter,
            hasDuplicateGroup = source.duplicateGroup != null,
            needsProbe = source.probe == null,
            probeStatus = source.probe?.status,
            deleteMarked = source.deleteMarked,
        )
    }

private fun sourceMatchesFilter(state: WorkflowState, source: WorkflowSource): Boolean {
    val query = state.filterQuery.trim().lowercase()
    val matchesQuery = query.isEmpty() || listOf(
        source.doc.name, source.doc.url, source.doc.group, source.doc.type.toString(),
        source.probe?.status?.label.orEmpty(), source.suggestion, source.probe?.detail.orEmpty(),
    ).any { it.lowercase().contains(query) }
    val matchesFilter = isSourceIncludedInFilter(
        filter = state.sourceFilter,
        hasDuplicateGroup = source.duplicateGroup != null,
        needsProbe = source.probe == null,
        probeStatus = source.probe?.status,
        deleteMarked = source.deleteMarked,
    )
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
            fontSize = UiTypography.label,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class ProbeStatusStyle(val background: Color, val foreground: Color)

private fun probeStatusStyle(status: ProbeStatus?): ProbeStatusStyle = when (status) {
    ProbeStatus.OK -> ProbeStatusStyle(SoftGreen, Ok)
    ProbeStatus.TIMEOUT, ProbeStatus.NETWORK, ProbeStatus.HTTP_ERROR -> ProbeStatusStyle(SoftAmber, Warn)
    ProbeStatus.NO_SEARCH_URL, ProbeStatus.UNSUPPORTED, ProbeStatus.PARSE_EMPTY, ProbeStatus.NEED_JS, ProbeStatus.ERROR ->
        ProbeStatusStyle(SoftRed, Bad)
    else -> ProbeStatusStyle(SoftSlate, Muted)
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

private fun defaultExportDir(): String {
    val home = File(System.getProperty("user.home"))
    val documents = File(home, "Documents")
    val base = if (documents.isDirectory) documents else home
    return File(base, "legado-tidy").absolutePath
}
