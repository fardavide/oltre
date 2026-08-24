import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    jvmToolchain(21)

    // `core`'s target set, copied deliberately rather than narrowed. The JVM target is the
    // server's; the two iOS ones and Android are the client's. A wire contract that could not be
    // read on one of them would be a wire contract with an end missing.
    jvm()
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "dev.fardavide.oltre.protocol"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `api`, not `implementation`: `SyncResponse` hands back a `GameSnapshot` and every verb
            // names a `core` type, so anything that can read this module has to be able to read
            // those. The edge points inward and never the other way — `core` depends on nothing,
            // which is the sentence this module exists to preserve.
            api(projects.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
