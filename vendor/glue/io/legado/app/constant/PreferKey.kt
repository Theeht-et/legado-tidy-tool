package io.legado.app.constant

/**
 * 占位：headless 只保留引擎实际引用到的键。
 * 去掉 upstream 依赖 appCtx.getPrefBoolean 的那套偏好读取后，其余键无意义。
 */
object PreferKey {

    const val precisionSearch = "precisionSearch"

}
