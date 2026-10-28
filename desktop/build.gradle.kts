plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":engine"))

    // core 的公开 API 里有 JsonObject（BookSourceDoc.raw），engine 的入口是 suspend 函数，
    // 所以入口模块必须自己拿到 gson 与 coroutines —— 不能指望传递依赖暴露它们。
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.core)
}

application {
    mainClass.set("io.github.theeht.legadotidy.desktop.MainKt")
}
