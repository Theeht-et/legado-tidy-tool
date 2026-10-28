package io.github.theeht.legadotidy.export

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.theeht.legadotidy.model.SourceField
import io.github.theeht.legadotidy.model.SourceIO
import io.github.theeht.legadotidy.organize.OrganizedSource
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 直链发布通道。默认不启用；实现方只需返回一个手机能打开的 URL。 */
fun interface DirectLinkPublisher {
    suspend fun publish(fileName: String, bytes: ByteArray): Result<String>
}

object NoDirectLink : DirectLinkPublisher {
    override suspend fun publish(fileName: String, bytes: ByteArray): Result<String> =
        Result.failure(UnsupportedOperationException("未配置直链通道"))
}

data class ExportConfig(
    /** 是否把清洗后的名字写回导出文件。 */
    val applyCleanNames: Boolean = true,
    /** 是否把分类与排序写回 `bookSourceGroup` / `customOrder`。 */
    val applyGroupAndOrder: Boolean = true,
    val pretty: Boolean = true,
)

data class ExportResult(
    val file: Path,
    val count: Int,
    val directLink: String? = null,
)

/**
 * S4 导出：默认写本地 JSON（用户手动导入阅读 App）；直链是可选通道。
 *
 * 导出保留每个源的所有未知字段——只改我们确实要改的那几个。
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
            if (config.applyCleanNames && org.name.isNotEmpty()) {
                copy.addProperty(SourceField.NAME, org.name)
            }
            if (config.applyGroupAndOrder) {
                copy.addProperty(SourceField.GROUP, org.group)
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

    /** 把一个数组里的源按分类拆成多个文件（大库分文件导入更友好）。 */
    fun writeSplit(
        raws: List<JsonObject>,
        organized: List<OrganizedSource>,
        dir: Path,
        config: ExportConfig = ExportConfig(),
    ): List<ExportResult> {
        val built = buildJson(raws, organized, config)
        val byGroup = LinkedHashMap<String, JsonArray>()
        organized.forEach { org ->
            val arr = byGroup.getOrPut(org.group) { JsonArray() }
            arr.add(built[org.index])
        }
        Files.createDirectories(dir)
        return byGroup.map { (group, arr) ->
            val safe = group.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val target = dir.resolve("$safe.json")
            val text = arr.toString()
            val prettyText = if (config.pretty) SourceIO.toJson(arr.map { it.asJsonObject }, true) else text
            Files.writeString(target, prettyText)
            ExportResult(target, arr.size())
        }
    }
}
