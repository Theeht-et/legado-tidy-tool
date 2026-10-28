plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":engine"))
}

application {
    mainClass.set("io.github.theeht.legadotidy.desktop.MainKt")
}
