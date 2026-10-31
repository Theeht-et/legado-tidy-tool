package io.github.theeht.legadotidy.engine

import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.help.CacheManager
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.help.source.exploreKinds
import io.legado.app.help.source.initializeExploreInfoMap
import io.legado.app.ui.main.explore.ExploreAdapter
import io.legado.app.utils.MD5Utils
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.util.UUID

class BookSourceExploreKindsTest {

    private fun source(exploreUrl: String) = BookSource(
        bookSourceUrl = "https://explore-kinds.test/${UUID.randomUUID()}",
        bookSourceName = "Explore kinds fixture",
        exploreUrl = exploreUrl,
    )

    @Test
    fun `JSON 数组按上游 ExploreKind 结构解析`() = runBlocking {
        val source = source(
            """[
              {"title":"男频","url":"/male","type":"url"},
              {"title":"女频","url":"/female","type":"url"},
              {"title":"说明","type":"text","default":"请输入"}
            ]""".trimIndent(),
        )
        try {
            val kinds = source.exploreKinds()

            assertEquals(3, kinds.size)
            assertEquals(ExploreKind(title = "男频", url = "/male", type = "url"), kinds[0])
            assertEquals(ExploreKind(title = "女频", url = "/female", type = "url"), kinds[1])
            assertEquals("text", kinds[2].type)
        } finally {
            source.clearExploreKindsCache()
        }
    }

    @Test
    fun `title 冒号双冒号格式支持双与号和换行分隔`() = runBlocking {
        val source = source("男频::/male&&女频::/female\n出版::/published")
        try {
            val kinds = source.exploreKinds()

            assertEquals(3, kinds.size)
            assertEquals("男频", kinds[0].title)
            assertEquals("/male", kinds[0].url)
            assertEquals("女频", kinds[1].title)
            assertEquals("/female", kinds[1].url)
            assertEquals("出版", kinds[2].title)
            assertEquals("/published", kinds[2].url)
        } finally {
            source.clearExploreKindsCache()
        }
    }

    @Test
    fun `at js 分类执行结果会持久缓存并在清理时删除磁盘索引`() = runBlocking {
        val source = source(
            """@js: JSON.stringify([{title:'JS 男频',url:'/js-male',type:'url'}])""",
        )
        val cacheKey = MD5Utils.md5Encode(source.bookSourceUrl + source.exploreUrl)
        try {
            val kinds = source.exploreKinds()

            assertEquals(1, kinds.size)
            assertEquals("JS 男频", kinds.single().title)
            assertEquals("/js-male", kinds.single().url)
            assertTrue(CacheManager.get(cacheKey, true).orEmpty().contains("JS 男频"))
        } finally {
            source.clearExploreKindsCache()
            assertNull(CacheManager.get(cacheKey, true))
        }
    }

    @Test
    fun `script 标签分类先执行脚本并解析其 JSON 返回值`() = runBlocking {
        val source = source(
            """<js>JSON.stringify([{title:'标签分类',url:'/tagged',type:'url'}])</js>""",
        )
        try {
            val kinds = source.exploreKinds()

            assertEquals(1, kinds.size)
            assertEquals("标签分类", kinds.single().title)
            assertEquals("/tagged", kinds.single().url)
        } finally {
            source.clearExploreKindsCache()
        }
    }

    @Test
    fun `toggle 和 select 控件按上游规则初始化默认值且保留已有用户选择`() = runBlocking {
        val source = source("[]")
        try {
            val kinds = listOf(
                ExploreKind(
                    title = "排序",
                    type = ExploreKind.Type.toggle,
                    chars = arrayOf("最新", "最热"),
                ),
                ExploreKind(
                    title = "分类",
                    type = ExploreKind.Type.select,
                    chars = arrayOf("男频", "女频"),
                    default = "女频",
                ),
            )

            source.initializeExploreInfoMap(kinds)
            val infoMap = ExploreAdapter.exploreInfoMapList[source.bookSourceUrl]
            assertEquals("最新", infoMap?.get("排序"))
            assertEquals("女频", infoMap?.get("分类"))

            infoMap?.set("排序", "最热")
            source.initializeExploreInfoMap(kinds)
            assertEquals("最热", infoMap?.get("排序"))
        } finally {
            ExploreAdapter.exploreInfoMapList.remove(source.bookSourceUrl)
        }
    }

    @Test
    fun `JSON 分类缓存清理后会重新解析修改后的定义`() = runBlocking {
        val source = source("""[{"title":"旧分类","url":"/old"}]""")
        try {
            assertEquals("旧分类", source.exploreKinds().single().title)
            source.exploreUrl = """[{"title":"新分类","url":"/new"}]"""
            source.clearExploreKindsCache()

            val refreshed = source.exploreKinds()
            assertEquals("新分类", refreshed.single().title)
            assertTrue(refreshed.single().url == "/new")
        } finally {
            source.clearExploreKindsCache()
        }
    }
}
