package io.github.theeht.legadotidy.export

import com.google.gson.JsonObject
import io.github.theeht.legadotidy.model.SourceField
import io.github.theeht.legadotidy.model.SourceIO
import io.github.theeht.legadotidy.organize.OrganizedSource
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class ExportConfig(
    /** 是否把清洗后的名字写回导出文件。 */
    val applyCleanNames: Boolean = true,
    /** 是否写回 `bookSourceGroup`。 */
    val applyGroups: Boolean = true,
    /** 是否写回 `customOrder`。 */
    val applyOrder: Boolean = true,
    val pretty: Boolean = true,
)

data class ExportResult(
    val file: Path,
    val count: Int,
)

/**
 * 将整理后的书源写入本地 JSON。
 *
 * 导出保留每个源的所有未知字段——只改用户明确勾选的字段。
 */
object Exporter {

    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    fun defaultFileName(now: LocalDateTime = LocalDateTime.now()): String = "sources-${now.format(stamp)}.json"

    fun buildJson(
        raws: List<JsonObject>,
        organized: List<OrganizedSource>,
        config: ExportConfig = ExportConfig(),
    ): List<JsonObject> {
        val byIndex = organized.associateBy { it.index }
        return raws.mapIndexed { i, raw ->
            val copy = raw.deepCopy()
            val org = byIndex[i] ?: return@mapIndexed copy
            // Preserve a missing name property, but allow an existing name to be cleaned to empty.
            if (config.applyCleanNames && (copy.has(SourceField.NAME) || org.name.isNotEmpty())) {
                copy.addProperty(SourceField.NAME, org.name)
            }
            if (config.applyGroups) {
                copy.addProperty(SourceField.GROUP, org.group)
            }
            if (config.applyOrder) {
                copy.addProperty(SourceField.CUSTOM_ORDER, org.rank)
            }
            copy
        }
    }

    fun write(
        raws: List<JsonObject>,
        organized: List<OrganizedSource>,
        dir: Path,
        fileName: String = defaultFileName(),
        config: ExportConfig = ExportConfig(),
    ): ExportResult {
        Files.createDirectories(dir)
        val target = dir.resolve(fileName)
        val tmp = dir.resolve("$fileName.tmp")
        val text = SourceIO.toJson(buildJson(raws, organized, config), config.pretty)
        Files.writeString(tmp, text)
        // 先写临时文件再替换：中途失败不会留下半份书源库
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
        return ExportResult(target, raws.size)
    }

}