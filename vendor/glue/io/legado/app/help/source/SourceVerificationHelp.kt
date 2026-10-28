package io.legado.app.help.source

import io.legado.app.data.entities.BaseSource
import kotlin.coroutines.CoroutineContext

/**
 * 取自 upstream 的 help/source/SourceVerificationHelp.kt，这里是 headless 的最小实现。
 *
 * 上游整套「源验证」（图片验证码 / 滑块 / 点击字符 / 内置浏览器）依赖 App 的
 * Activity、对话框、数据库与 WebView —— headless 全部没有，也不该尝试实现：
 * 这些源本就需要人工介入，必须快速失败，绝不能挂起等待（否则会拖死抓取池）。
 *
 * 因此本文件只保证 [VerificationResult] 与 [SourceVerificationHelp] 的签名存在，
 * 任何真正发起验证的调用都抛 [UnsupportedOperationException]；UI 回填用的
 * set/get/clear 方法留空。
 */

internal sealed class VerificationResult {
    data class Response(val value: Pair<String, String>) : VerificationResult()
    object Refetch : VerificationResult()
}

/**
 * 源验证
 */
object SourceVerificationHelp {

    /**
     * 启动内置浏览器 —— headless 无浏览器，调用即失败。
     */
    fun startBrowser(
        source: BaseSource?,
        url: String,
        title: String,
        saveResult: Boolean? = false,
        refetchAfterSuccess: Boolean? = true,
        html: String? = null,
        verificationResultKey: String? = null,
    ) {
        throw UnsupportedOperationException("headless 不支持 startBrowser: 需要内置浏览器人工过验证（url=$url）")
    }

    /**
     * 获取书源验证结果（图片验证码 / 防爬 / 滑动验证码 / 点击字符 …）。
     * headless 无法完成人工验证，直接快速失败。
     */
    internal fun getVerificationResult(
        source: BaseSource?,
        url: String,
        title: String,
        useBrowser: Boolean,
        refetchAfterSuccess: Boolean = true,
        html: String? = null,
        coroutineContext: CoroutineContext? = null,
    ): VerificationResult {
        throw UnsupportedOperationException("headless 不支持 getVerificationResult: 需要人工过验证（url=$url）")
    }

    // 以下为 UI 回填验证结果用的回调，headless 无 UI，留空实现（仅为对齐 upstream 签名）。
    fun checkResult(verificationResultKey: String?) {
    }

    fun setResult(verificationResultKey: String?, result: String, url: String = "") {
    }

    fun getResult(verificationResultKey: String?): Pair<String, String>? {
        return null
    }

    fun clearResult(verificationResultKey: String?) {
    }
}
