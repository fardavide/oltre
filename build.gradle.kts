// Every plugin is declared here (apply false) so the Kotlin/Android Gradle plugins load in a
// single classloader — applying them per-module only leads to BuildService class-cast clashes.
plugins {
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
    alias(libs.plugins.roborazzi) apply false
}

val oltreVersion = libs.versions.oltre.get()

allprojects {
    group = "dev.fardavide.oltre"
    version = oltreVersion
}
