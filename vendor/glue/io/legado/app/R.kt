package io.legado.app

/**
 * 平台桩：只列出引擎用到的字符串资源 id。
 * 真正的文案在 `io.legado.headless.Res` 里，值与 upstream 的 strings.xml 一致。
 */
object R {
    object string {
        const val error_get_web_content = 0x7f010001
        const val chapter_list_empty = 0x7f010002
        const val replace_rule_invalid = 0x7f010003
        const val intro_show = 0x7f010004
        const val intro_show_null = 0x7f010005
    }
}
