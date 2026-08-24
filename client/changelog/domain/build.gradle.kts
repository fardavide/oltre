import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The two things about a changelog that are neither copy nor drawing: what a version *is*, and
// whether this launch has anything new to say. Both are arithmetic, so both are testable without a
// screen — and the second one is the whole feature's only rule.
//
// It depends on nothing at all, `:core` included. A release number is not a fact about a colony.
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
        namespace = "dev.fardavide.oltre.client.changelog.domain"
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
