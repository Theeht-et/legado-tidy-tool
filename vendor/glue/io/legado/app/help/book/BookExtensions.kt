package io.legado.app.help.book

import io.legado.app.constant.AppPattern
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.utils.MD5Utils
import java.time.LocalDate
import java.time.Period.between
import kotlin.math.max
import kotlin.math.min

/**
 * 书名/书籍类型判定（照抄 upstream `help/book/BookExtensions.kt` 的可用部分）。
 *
 * ## 保留了哪些
 * 引擎实测会调用的那批**真实类型判定逻辑**：`isAudio/isVideo/isImage/isWebFile/
 * isOnLineTxt/isEpub/isLocal*`、位掩码读写 `setType/addType/removeType/
 * removeAllBookType/clearType/isType`、以及「模拟阅读」的 `simulatedTotalChapterNum`。
 * 这些决定「一个书源/一本书能不能被本工具处理」，是实现，不是桩。
 *
 * ## 删掉了哪些（及原因）
 * 上游此文件后半段大量方法依赖 Android / 数据库 / UI，headless 用不到，直接删除：
 *  - `getLocalUri/getArchiveUri/cacheLocalUri/removeLocalUriCache/archiveName`：
 *    依赖 `android.net.Uri`、SAF `FileDoc`、`AppConfig.defaultBookTreeUri` 等本地文件访问；
 *  - `sync/update/savePreservingCustomCoverUrl`：依赖 `appDb`、`BookHelp`、UI 事件；
 *  - `getBookSource/isLocalModified/releaseHtmlData/hasVariable`：依赖 `appDb`/`LocalBook`/`RuleBigDataHelp`；
 *  - `getExportFileName/tryParesExportFileName/normalizeExportFileName`：依赖 Rhino 脚本引擎与
 *    `AppConfig` 的导出命名设置（属于导出功能，不在对比路径）。
 *
 * ## 与上游的其它差异
 *  - `getUseReplaceRule()` 不在本文件 —— 它是 `Book` 的成员方法（`Book.kt`），
 *    上游我们把类型判定放这里只是为了便于 import，实际调用点无需改动。
 *  - `getBookType()` 在 `help/source/BookSourceExtensions.kt`（`BookSource` 的扩展），
 *    同样是上游位置，未搬到这里。
 */
val Book.isAudio: Boolean
    get() = isType(BookType.audio)

val Book.isVideo: Boolean
    get() = isType(BookType.video)

val Book.isImage: Boolean
    get() = isType(BookType.image)

val Book.isLocal: Boolean
    get() {
        if (type == 0) {
            return origin == BookType.localTag || origin.startsWith(BookType.webDavTag)
        }
        return isType(BookType.local)
    }

val Book.isLocalTxt: Boolean
    get() = isLocal && originName.endsWith(".txt", true)

val Book.isEpub: Boolean
    get() = isLocal && originName.endsWith(".epub", true)

val Book.isUmd: Boolean
    get() = isLocal && originName.endsWith(".umd", true)

val Book.isPdf: Boolean
    get() = isLocal && originName.endsWith(".pdf", true)

val Book.isMobi: Boolean
    get() = isLocal && (originName.endsWith(".mobi", true) ||
            originName.endsWith(".azw3", true) ||
            originName.endsWith(".azw", true))

val Book.isOnLineTxt: Boolean
    get() = !isLocal && isType(BookType.text)

val Book.isWebFile: Boolean
    get() = isType(BookType.webFile)

val Book.isUpError: Boolean
    get() = isType(BookType.updateError)

val Book.isArchive: Boolean
    get() = isType(BookType.archive)

val Book.isNotShelf: Boolean
    get() = isType(BookType.notShelf)

fun Book.setType(@BookType.Type vararg types: Int) {
    type = 0
    addType(*types)
}

fun Book.addType(@BookType.Type vararg types: Int) {
    types.forEach {
        type = type or it
    }
}

fun Book.removeType(@BookType.Type vararg types: Int) {
    types.forEach {
        type = type and it.inv()
    }
}

fun Book.removeAllBookType() {
    removeType(BookType.allBookType)
}

fun Book.clearType() {
    type = 0
}

fun Book.isType(@BookType.Type bookType: Int): Boolean = type and bookType > 0

// 根据当前日期计算章节总数
fun Book.simulatedTotalChapterNum(): Int {
    return if (readSimulating()) {
        val currentDate = LocalDate.now()
        val daysPassed = between(config.startDate ?: currentDate, currentDate).days + 1
        // 计算当前应该解锁到哪一章
        val chaptersToUnlock =
            max(0, (config.startChapter ?: 0) + (daysPassed * config.dailyChapters))
        min(totalChapterNum, chaptersToUnlock)
    } else {
        totalChapterNum
    }
}

fun Book.readSimulating(): Boolean {
    return config.readSimulating
}

fun Book.getFolderNameNoCache(): String {
    return name.replace(AppPattern.fileNameRegex, "").let {
        it.substring(0, min(9, it.length)) + MD5Utils.md5Encode16(bookUrl)
    }
}
