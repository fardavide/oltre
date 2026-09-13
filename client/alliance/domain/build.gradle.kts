import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Standing, member powers and seat arithmetic require no platform or transport.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    jvmToolchain(21)
    jvm("desktop")
    iosArm64()
    iosSimulatorArm64()
    android {
        namespace = "dev.fardavide.oltre.client.alliance.domain"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
    }
    sourceSets {
        commonMain.dependencies { api(projects.protocol) }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}
