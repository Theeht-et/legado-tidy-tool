package io.github.theeht.legadotidy.export

import com.google.gson.JsonParser
import io.github.theeht.legadotidy.dedup.Docs
import io.github.theeht.legadotidy.model.SourceIO
import io.github.theeht.legadotidy.organize.OrganizedSource
import io.github.theeht.legadotidy.probe.ProbeStatus
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExporterTest {

    @Test
    fun `导出保留未知字段并写回名称分组与排序`(@TempDir dir: Path) {
        val doc = Docs.docFromJson(
            """
            {"bookSourceName":"  🔥某站  ","bookSourceUrl":"https://a.com",
             "unknownField":"keep-me","searchUrl":"https://a.com/s?q=%s",
             "ruleSearch":{"bookList":"//li"}}
            """.trimIndent()
        )
        val organized = listOf(OrganizedSource(0, "某站", "小说", ProbeStatus.OK, 3))
        val result = Exporter.write(listOf(doc.raw), organized, dir, "out.json")

        assertTrue(Files.exists(result.file))
        val parsed = SourceIO.parse(Files.readString(result.file))
        assertEquals(1, parsed.size)
        val obj = parsed[0]
        assertEquals("某站", obj.get("bookSourceName").asString)
        assertEquals("小说", obj.get("bookSourceGroup").asString)
        assertEquals(3, obj.get("customOrder").asInt)
        assertEquals("keep-me", obj.get("unknownField").asString)
    }

    @Test
    fun `不写回时保持原字段`(@TempDir dir: Path) {
        val raw = JsonParser.parseString("{\"bookSourceName\":\"原名字\",\"bookSourceUrl\":\"https://a.com\"}").asJsonObject
        val organized = listOf(OrganizedSource(0, "新名字", "分类", ProbeStatus.OK, 0))
        Exporter.write(
            listOf(raw), organized, dir, "x.json",
            ExportConfig(applyCleanNames = false, applyGroupAndOrder = false),
        )
        val parsed = SourceIO.parse(Files.readString(dir.resolve("x.json")))[0]
        assertEquals("原名字", parsed.get("bookSourceName").asString)
        assertTrue(parsed.get("bookSourceGroup") == null)
    }

    @Test
    fun `只改排序时保留原分组字段`(@TempDir dir: Path) {
        val raw = JsonParser.parseString(
            """{"bookSourceName":"原名字","bookSourceUrl":"https://a.com","bookSourceGroup":"原分组","customOrder":8}"""
        ).asJsonObject
        val organized = listOf(OrganizedSource(0, "原名字", "新分组", ProbeStatus.OK, 2))

        Exporter.write(
            listOf(raw),
            organized,
            dir,
            "order-only.json",
            ExportConfig(
                applyCleanNames = false,
                applyGroupAndOrder = true,
                applyGroups = false,
                applyOrder = true,
            ),
        )

        val parsed = SourceIO.parse(Files.readString(dir.resolve("order-only.json"))).single()
        assertEquals("原分组", parsed.get("bookSourceGroup").asString)
        assertEquals(2, parsed.get("customOrder").asInt)
    }

}
