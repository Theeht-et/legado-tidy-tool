package io.github.theeht.legadotidy.desktop

import io.github.theeht.legadotidy.engine.EngineFacade
import kotlin.system.exitProcess

/**
 * 临时入口：在 Compose 界面就绪前，用来验证「读一批书源 → 去重 → 整理 → 导出」的端到端链路。
 *
 * 用法：`gradle :desktop:run --args="<输入文件|目录> <输出目录> [--skip-probe]"`
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("用法: <输入文件|目录> <输出目录> [--skip-probe]")
        println(EngineFacade().status())
        return
    }
    println("界面尚未实现；当前仅验证库层。输入：${args[0]}")
    exitProcess(0)
}
