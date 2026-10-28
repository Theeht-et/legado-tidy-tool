package io.legado.app.help.config

/**
 * headless 版 ReadBookConfig 桩。
 *
 * 上游这个对象把「阅读界面设置」全部存进 SharedPreferences / readConfig.json，并带一整套
 * Config 数据类与归一化逻辑（见 upstream ReadBookConfig.kt，约 1300 行）。headless 没有
 * 阅读界面，只保留引擎与实体实际读取的**两个默认值**，默认值均与上游一致：
 *
 *  - [pageAnim]：翻页动画，上游 Config 默认 0（= PageAnim.coverPageAnim）。
 *    `Book.getPageAnim()` 在书籍自身未配置翻页动画时用它兜底。
 *  - [paragraphIndent]：段落缩进，上游默认两个全角空格 "　　"。
 *    `ContentProcessor` 拼接正文段落时会在段首补它。
 */
object ReadBookConfig {

    /** 翻页动画，默认 0（即 PageAnim.coverPageAnim），对齐上游 Config.pageAnim 默认值。 */
    var pageAnim: Int = 0

    /** 段落缩进，默认两个全角空格（U+3000 × 2），对齐上游默认值。 */
    var paragraphIndent: String = "　　"
}
