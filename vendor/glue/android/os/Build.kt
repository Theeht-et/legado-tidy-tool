package android.os

/**
 * 平台桩。SDK_INT 取 34（Android 14）而不是 0 ——
 * 它影响 `RhinoClassShutter`：SDK >= O 时会把 `java.nio.file.FileSystem/Path`
 * 也列入脚本黑名单。真实用户的设备基本都是 Android 8+，取 34 才与 App 行为一致，
 * 顺带也挡住了书源脚本直接读写本地文件。
 */
object Build {
    object VERSION {
        const val SDK_INT: Int = 34
    }

    object VERSION_CODES {
        const val O: Int = 26
    }
}
