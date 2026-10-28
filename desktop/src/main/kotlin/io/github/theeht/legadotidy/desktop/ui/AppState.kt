package io.github.theeht.legadotidy.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.theeht.legadotidy.model.BookSourceDoc
import io.github.theeht.legadotidy.pipeline.Phase
import io.github.theeht.legadotidy.pipeline.PipelineResult
import java.io.File

/**
 * 界面的全部可变状态，一处声明。
 *
 * 约定：
 * - 只有界面线程改写（外部事件先 `SwingUtilities.invokeLater` 再进来），避免 Compose 快照错乱。
 * - 不在这里放「派生值」，比如「可用率」由读的一方现算——两处各存一份必然会不同步。
 */
class AppState {

    // ---- 输入 ----
    var inputFile by mutableStateOf<File?>(null)
    var docs by mutableStateOf<List<BookSourceDoc>>(emptyList())
    var parseError by mutableStateOf<String?>(null)

    // ---- 运行参数 ----
    var keyword by mutableStateOf("剑来")
    var concurrency by mutableStateOf(100)
    var timeoutSeconds by mutableStateOf(20)
    var skipProbe by mutableStateOf(false)
    var withToc by mutableStateOf(false)
    var proxy by mutableStateOf("")

    // ---- 运行状态 ----
    var busy by mutableStateOf(false)
    var cancelling by mutableStateOf(false)
    var phase by mutableStateOf<Phase?>(null)
    var done by mutableStateOf(0)
    var total by mutableStateOf(0)
    var ok by mutableStateOf(0)
    var failed by mutableStateOf(0)
    var result by mutableStateOf<PipelineResult?>(null)

    /** 每个阶段的收尾说明（「去重完成：12 组」这种），按阶段存，界面直接取。 */
    val notes = mutableStateMapOf<Phase, String>()

    /** 日志只留最近 500 行：界面看的是「刚才发生了什么」，不是完整审计。 */
    val logs = mutableStateListOf<String>()

    fun log(message: String) {
        logs += message
        if (logs.size > 500) logs.removeAt(0)
    }

    fun reset() {
        phase = null
        done = 0
        total = 0
        ok = 0
        failed = 0
        notes.clear()
        result = null
    }

    val progress: Float
        get() = if (total <= 0) 0f else done.toFloat() / total
}
