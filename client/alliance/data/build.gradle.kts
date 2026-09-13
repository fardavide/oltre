import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The gateway joins transport and alliance vocabulary without exposing a socket or queue.
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
        namespace = "dev.fardavide.oltre.client.alliance.data"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
    }
    sourceSets {
        commonMain.dependencies {
            api(projects.client.alliance.domain)
            api(projects.client.net.data)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(projects.client.net.dataTesting)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.ktor.client.core)
            }
        }
    }
}
