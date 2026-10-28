package io.legado.app.constant

/**
 * 最小 PageAnim 桩。
 *
 * 上游这个文件位于 `io.legado.app.constant`（**不是** UI 包），是一个纯常量对象 +
 * `@PageAnim.Anim`（androidx.annotation.IntDef）注解。headless 只用到 [scrollPageAnim]
 * （`Book.getPageAnim()` 判断图片书源时），但常量值必须与上游逐字一致，否则翻页相关分支
 * 会静默走错。
 *
 * 注：本文件物理上放在 `help/config/` 下（本任务只允许写这几个目录），但按 Kotlin 的
 * 包语义声明在原本的 `io.legado.app.constant` 包名下，以保证 `import
 * io.legado.app.constant.PageAnim` 照常解析。上游的 `@PageAnim.Anim` 注解 headless 用不到
 * （`androidx.annotation.IntDef` 未做桩），故不提供。
 */
@Suppress("ConstPropertyName")
object PageAnim {

    const val coverPageAnim = 0

    const val slidePageAnim = 1

    const val simulationPageAnim = 2

    const val scrollPageAnim = 3

    const val noAnim = 4
}
