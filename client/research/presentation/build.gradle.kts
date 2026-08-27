import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Everything the Research tab decides: one mapper from `GameState` into the models
// `:client:research:ui` draws, and the unit tests that pin every sentence it writes.
//
// **No Compose plugin and no Compose dependency**, which is the check that nothing here draws. What
// it takes from the design system is the *vocabulary* — a cost chip, a sheet line, a verdict — which
// are plain data classes with no Compose type in any of their signatures.
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
        namespace = "dev.fardavide.oltre.client.research.presentation"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core)
            // `api`, so the composition root names this feature once: `toResearchUiState` returns a
            // `ResearchUiState` and the shell hands it straight to `ResearchScreen`, so the models
            // and the screen are this module's vocabulary and travel with it.
            api(projects.client.research.ui)
            implementation(projects.client.design.component)
            implementation(projects.client.design.text)
            implementation(projects.client.design.format)
            // What the outbox means to a row — see the colony's presentation module, which takes it
            // the same way and for the same reason.
            implementation(projects.client.net.domain)

            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
