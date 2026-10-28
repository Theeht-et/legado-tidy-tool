package io.legado.app.help.config

/**
 * headless 版 AppConfig。
 *
 * 上游这个文件有 40KB（全是 SharedPreferences 读写），但**引擎实际只用到 4 个成员**，
 * 所以这里只实现这 4 个 —— 多实现一个都可能引入与上游不一致的行为。
 *
 * 默认值对齐上游：
 *  - userAgent    上游是 "Chrome/<CronetMainVersion>"，值取自 upstream/gradle.properties
 *                 的 CronetMainVersion（152.0.0.0）。部分站点会校验 UA，不能随便填。
 *  - adaptSpecialStyle / tocCountWords  上游默认均为 true。
 *  - threadCount  上游是「书架线程数」设置。本工具是并发抓取，语义不同，
 *                 所以这里只作为一个「引擎内部并行度」的默认值使用。
 */
object AppConfig {

    /** 详见类注释：与 upstream/gradle.properties 的 CronetMainVersion 保持一致。 */
    private const val CRONET_MAIN_VERSION = "152.0.0.0"

    @Volatile
    var userAgent: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/$CRONET_MAIN_VERSION Safari/537.36"

    @Volatile
    var adaptSpecialStyle: Boolean = true

    @Volatile
    var tocCountWords: Boolean = true

    /**
     * 引擎内部并行度（目录翻页 / 正文分页的并发抓取）。
     *
     * 注意这与「书源级并发」是两回事：书源级并发由我们的调度器控制（默认上百），
     * 这个值是**单个书源内部**并行取多个分页时的上限。上游默认取书架线程数，
     * 这里给一个温和的默认值，避免单个源就对站点打出几十个并发请求。
     */
    @Volatile
    var threadCount: Int = 8

    // ------------------------------------------------------------------
    // 下面 4 个是实体层（Book / BookChapter）会引用的成员。
    // 上游它们在 40KB 的 SharedPreferences 实现里，这里只给默认值 ——
    // 值必须与上游一致，否则会**静默改变对比结果**。
    // ------------------------------------------------------------------

    /**
     * 是否默认启用替换规则（净化规则）。上游 `getPrefBoolean(key, true)` → 默认 true。
     *
     * ⚠️ headless 下目前**没有加载任何替换规则**（那是用户的个人配置，不在书源里）。
     * 这个开关只影响实体方法的分支走向，不会真的应用规则。
     */
    val replaceEnableDefault: Boolean = true

    /**
     * 繁简转换模式：`0` 不转换 / `1` 繁→简 / `2` 简→繁。
     *
     * ★ 默认必须是 **0**。这是用户看小说时的**显示偏好**，不是书源的内容属性。
     * 本工具要对比的是各站点的**原文**；如果跟着用户的转换偏好走，
     * 就把「站点本来就是繁体」和「用户开了转换」两件事混在一起了。
     * 上游 `getPrefInt(key)` 未传默认值，即 0。
     *
     * 至于书源自己的 `java.t2s(...)`（实测 96 处）——那是书源规则里显式写的，
     * 属于内容处理，照常执行。
     */
    @Volatile
    var chineseConverterType: Int = 0

    /** 有声书跳过片头秒数。headless 不做音频，仅为一个整数值以满足实体编译。 */
    @Volatile
    var audioSkipOpenCredits: Int = 0

    /** 有声书跳过片尾秒数。同上。 */
    @Volatile
    var audioSkipCloseCredits: Int = 0
}
