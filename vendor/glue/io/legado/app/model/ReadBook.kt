package io.legado.app.model

import io.legado.app.data.entities.Book

/**
 * headless 版 ReadBook。
 *
 * 上游的 `ReadBook` 是「阅读页运行时状态机」（当前书、当前章、翻页、朗读、正文净化缓存……），
 * 依赖 Activity / 播放器 / 通知栏。本工具只做「并发抓正文」，没有阅读页，
 * 因此这里**只保留引擎与实体真正引用的那一份状态**：[book]（当前正在阅读的书）。
 *
 * 当前唯一调用方是 `Book.delete()`：删除书架上的书时，如果它正好是「当前书」，
 * 就把缓存指针清空（`Book.kt` 第 431~432 行）。其余方法一律不实现。
 */
object ReadBook {

    @Volatile
    var book: Book? = null
}
