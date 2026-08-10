import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The platform edge of the parallax, and the same shape `:client:debug:data` has: a device service
// with nothing game-shaped in it, and no presentation layer because what it feeds is a `Canvas` the
// shell already owns.
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
        namespace = "dev.fardavide.oltre.client.tilt.data"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `api` rather than the `implementation` its sibling in `:client:debug:data` uses, and
            // the difference is forced rather than chosen: `ShakeDetector` emits `Unit`, while
            // `TiltSource` emits a `Tilt` — a type from this module's own signature. A consumer
            // that cannot name the thing it is handed cannot compile.
            api(projects.client.tilt.domain)

            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
