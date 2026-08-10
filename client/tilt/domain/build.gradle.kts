import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// No Compose plugin, no Compose dependency, and — unlike every other `domain` in the build — no
// `:core` either. That absence is the point rather than an omission: how a device is being held has
// nothing to do with a colony, so this module knows about angles and about nothing else. It is also
// the half a cloud session can build and test: see `.claude/tools/gradle-without-agp.sh`.
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
        namespace = "dev.fardavide.oltre.client.tilt.domain"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
