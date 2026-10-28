plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
