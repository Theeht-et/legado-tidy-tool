plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.gson)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
