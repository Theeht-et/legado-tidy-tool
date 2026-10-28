package io.legado.app.utils

import io.legado.app.constant.AppPattern
import io.legado.headless.Paths
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * 压缩包解压。引擎只在 `JsExtensions.unArchiveFile` 里用到
 * `ArchiveUtils.deCompress(archivePath)` 与 `ArchiveUtils.TEMP_FOLDER_NAME`。
 *
 * 与上游的差异：
 * - upstream 走 `utils/compress/LibArchiveUtils`（libarchive NDK 库），支持 7z/rar/zip。
 *   该依赖是 Android NDK 库，headless 不引入，故：
 *     * **zip** 用 JDK 内置 `ZipInputStream` 实现；
 *     * **rar / 7z** 直接抛 `UnsupportedOperationException`（快速失败，见 docs/PATCHES.md
 *       的“明确不支持的能力”；绝不挂起等待）。
 * - 临时目录从 upstream 的 `appCtx.externalCache` 换成 `Paths.cacheDir`（run/cache），
 *   与 `JsExtensions.getFile` 的根目录一致 —— 这样 glue 拼出的相对路径
 *   `ArchiveTemp/<md5>` 仍然能被 `getFile` 解析到。
 * - 解压子目录命名与上游一致：`{TEMP_PATH}/{MD5Utils.md5Encode16(压缩包文件名)}`。
 * - 只保留引擎用到的 `deCompress(String, ...)`；上游基于 `Uri` / `DocumentFile` / `FileDoc`
 *   的重载全部去掉（那些类型 headless 没有）。
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
object ArchiveUtils {

    const val TEMP_FOLDER_NAME = "ArchiveTemp"

    // 临时目录
    val TEMP_PATH: String by lazy {
        File(Paths.cacheDir, TEMP_FOLDER_NAME).apply { mkdirs() }.absolutePath
    }

    fun deCompress(
        archivePath: String,
        path: String = TEMP_PATH,
        filter: ((String) -> Boolean)? = null
    ): List<File> {
        val archiveFile = File(archivePath)
        checkArchive(archiveFile.name)
        if (!archiveFile.name.endsWith(".zip", ignoreCase = true)) {
            throw UnsupportedOperationException(
                "headless 仅支持 zip 解压，rar/7z 需要 libarchive NDK 库：${archiveFile.name}"
            )
        }
        val workDir = File(path, MD5Utils.md5Encode16(archiveFile.name))
        val result = arrayListOf<File>()
        ZipInputStream(BufferedInputStream(FileInputStream(archiveFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name
                if (filter == null || filter(entryName)) {
                    val outFile = File(workDir, entryName)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { zis.copyTo(it) }
                        result.add(outFile)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return result
    }

    fun isArchive(name: String): Boolean {
        return AppPattern.archiveFileRegex.matches(name)
    }

    private fun checkArchive(name: String) {
        if (!isArchive(name)) {
            throw IllegalArgumentException("Unexpected file suffix: Only 7z rar zip Accepted")
        }
    }
}

