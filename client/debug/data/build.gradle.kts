import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The platform edge of the debug menu, and the same shape `:client:notifications:data` has: a
// device service with nothing game-shaped in it, and no presentation layer because the UI it has
// belongs to the operating system — here, the accelerometer.
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
        namespace = "dev.fardavide.oltre.client.debug.data"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `data` may depend on `domain` — it is `domain` that may not look down here. What it
            // takes is `ShakeMonitor`: whether a stream of samples is a shake is a judgement, and a
            // judgement written once against Android's sensor and again against iOS's is a
            // judgement that drifts until the gesture means something different on each phone.
            implementation(projects.client.debug.domain)

            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
