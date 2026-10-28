package io.legado.app.constant

/**
 * headless 版：upstream 的 AppConst 主体是 Android 路径 / 签名 / 资源常量，
 * 这里只搬实际被引用的常量：
 *  - `UA_NAME` / `MAX_THREAD`：引擎 AnalyzeUrl / SearchModel 引用
 *  - `androidId`：data.entities 的 BaseSource / ReadRecord 引用
 */
object AppConst {

    const val UA_NAME = "User-Agent"

    const val MAX_THREAD = 9

    /**
     * headless 占位：upstream 读 Settings.Secure.ANDROID_ID（设备标识）。
     *
     * 取固定 16 字符常量而不是 "null"（upstream 的兜底值）：
     * BaseSource 会用它作为 AES-128 密钥种子（`encodeToByteArray(0, 16)`），
     * 长度不足 16 会让密钥非法、登录信息读写静默失败；ReadRecord 用它标识设备。
     * headless 无设备概念，用稳定常量保证行为可复现。
     */
    val androidId: String = "legado-headless!"

}
