plugins {
    alias(libs.plugins.kotlin.jvm)
}

// 上游引擎源码（vendored，见 docs/ENGINE.md）。
val vendored = rootProject.layout.projectDirectory.dir("vendor/legado")

val engineSourceDirs = listOf(
    "app/src/main/java/io/legado/app/model/analyzeRule",
    "app/src/main/java/io/legado/app/model/webBook",
    "app/src/main/java/io/legado/app/model/jsSource",
    "modules/rhino/src/main/java",
)

dependencies {
    implementation(project(":core"))
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.coroutines.core)

    // 上游 third_party/maven 里 vendored 的 htmlunit-core-js（Maven Central 无此版本）
    val rhinoJar = vendored
        .dir("third_party/maven/org/htmlunit/htmlunit-core-js/5.3.0-legado.4/htmlunit-core-js-5.3.0-legado.4.jar")
        .asFile
    if (rhinoJar.exists()) implementation(files(rhinoJar))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

// 这三个目录既有 .kt 也有 .java：必须同时进 kotlin 与 java 源集，否则「编译过、运行 NoClassDefFoundError」。
if (vendored.asFile.exists()) {
    kotlin.sourceSets["main"].kotlin.srcDirs(engineSourceDirs.map { vendored.dir(it).asFile })
    sourceSets["main"].java.srcDirs(engineSourceDirs.map { vendored.dir(it).asFile })
}
