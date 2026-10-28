package io.legado.app.utils

import java.io.File

/**
 * 文件工具。**只保留引擎/glue 实际用到的方法**（`JsExtensions` 里的
 * `FileUtils.delete(file, true)` 与 `FileUtils.delete(path)`）。
 *
 * 与上游的差异：
 * - 上游 FileUtils 有 760 行，包含 `content://`、`Environment` 外部存储、MimeTypeMap、
 *   SimpleDateFormat 等 Android 依赖；headless 一律删除（没有对应场景，也没有平台桩）。
 * - `delete` / `deleteResolveEBUSY` 的删除语义与上游逐行一致（含 EBUSY 改名兜底）。
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
object FileUtils {

    /**
     * 删除文件或目录
     */
    @JvmOverloads
    fun delete(file: File, deleteRootDir: Boolean = false): Boolean {
        var result = false
        if (file.isFile) {
            //是文件
            result = deleteResolveEBUSY(file)
        } else {
            //是目录
            val files = file.listFiles() ?: return false
            if (files.isEmpty()) {
                result = deleteRootDir && deleteResolveEBUSY(file)
            } else {
                for (f in files) {
                    delete(f, deleteRootDir)
                    result = deleteResolveEBUSY(f)
                }
            }
            if (deleteRootDir) {
                result = deleteResolveEBUSY(file)
            }
        }
        return result
    }

    /**
     * bug: open failed: EBUSY (Device or resource busy)
     * fix: http://stackoverflow.com/questions/11539657/open-failed-ebusy-device-or-resource-busy
     */
    private fun deleteResolveEBUSY(file: File): Boolean {
        // Before you delete a Directory or File: rename it!
        val to = File(file.absolutePath + System.currentTimeMillis())

        file.renameTo(to)
        return to.delete()
    }

    /**
     * 删除文件或目录
     */
    @JvmOverloads
    fun delete(path: String, deleteRootDir: Boolean = true): Boolean {
        val file = File(path)

        return if (file.exists()) {
            delete(file, deleteRootDir)
        } else false
    }
}
