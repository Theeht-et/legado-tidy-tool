package io.legado.app.model

/**
 * headless 版 AudioPlay。
 *
 * 上游是「音频书播放器状态机 + 后台播放服务」的入口，依赖 Service / AudioPlayService /
 * 通知栏 / 音频焦点 / 缓存下载。本工具不播放音频，也没有任何已编译代码调用它 ——
 * 之所以保留这个空对象，是因为 `BookSource.kt` 里有一行 `import io.legado.app.model.AudioPlay`
 * （上游遗留），符号必须存在才能编译。
 *
 * 它的真实方法一律不实现；未来若真的需要「音频书源能力探测」，不应调用这里，
 * 而应由 `UnsupportedDetector` 按规则文本静态判定。
 */
@Suppress("unused")
object AudioPlay
