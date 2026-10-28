plugins {
    alias(libs.plugins.kotlin.jvm)
}

// 上游引擎源码（vendor/legado）与平台补全层（vendor/glue），都不手改。
val vendored = rootProject.layout.projectDirectory.dir("vendor/legado")
val glueDir = rootProject.layout.projectDirectory.dir("vendor/glue")

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
    implementation(libs.json.path)
    implementation(libs.jsoup.xpath)
    implementation(libs.hutool.core)
    implementation(libs.hutool.crypto)
    implementation(libs.commons.text)
    implementation(libs.annotations)
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

// 这几个目录既有 .kt 也有 .java：必须同时进 kotlin 与 java 源集，
// 否则会出现「编译通过、运行期 NoClassDefFoundError」。
if (vendored.asFile.exists()) {
    kotlin.sourceSets["main"].kotlin.srcDirs(engineSourceDirs.map { vendored.dir(it).asFile })
    sourceSets["main"].java.srcDirs(engineSourceDirs.map { vendored.dir(it).asFile })
}

if (glueDir.asFile.exists()) {
    kotlin.sourceSets["main"].kotlin.srcDir(glueDir.asFile)
    sourceSets["main"].java.srcDir(glueDir.asFile)
}
