import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Everything the Colony tab decides: one mapper from `GameState` into the models
// `:client:colony:ui` draws, and the unit tests that pin every sentence it writes.
//
// **No Compose plugin and no Compose dependency**, which is the check that this module holds
// nothing that draws. What it does hold from the design system is the *vocabulary* — a cost chip, a
// sheet line, a verdict — because those are the shapes the mapper fills in, and they are plain data
// classes with no Compose type in any of their signatures.
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
        namespace = "dev.fardavide.oltre.client.colony.presentation"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core)
            // `api`, and it is the edge that makes the composition root's dependency list one line
            // per feature: `toColonyUiState` returns a `ColonyUiState`, so the models are this
            // module's own vocabulary and travel with it — and the shell that asks for the mapper
            // gets `ColonyScreen` in the same breath. As `implementation` the shell would have to
            // name both halves and would be re-declaring what this module already exposes.
            api(projects.client.colony.ui)
            implementation(projects.client.design.component)
            implementation(projects.client.design.text)
            implementation(projects.client.design.format)

            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
