import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// No Compose plugin and no Compose dependency — that is the whole reason this is a module of its
// own rather than a file in `:client:design:component`. How the game writes a duration or groups a
// number by thousands is a decision about language, not about rendering; keeping it here means it
// builds without the compose compiler and its tests are plain unit tests over plain functions.
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
        namespace = "dev.fardavide.oltre.client.design.format"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `api`: every function in this module returns a `TextRes` now, so a consumer cannot
            // call one without seeing the type. The two modules divide one job — this one decides
            // *which* numbers to show, the catalogue writes them down — and neither is usable
            // without the other.
            api(projects.client.design.text)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
