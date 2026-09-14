import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// **What an `AllianceState` and a `TreasuryResponse` look like on a screen**, and the module
// `#143`'s own §1 said would exist the moment the tab stopped being a placeholder.
//
// `:client:alliance:ui` held the whole screen while that screen was one sentence, on the rule
// `module-rules` states for `:client:debug:ui`: a feature with nothing to decide has no
// `presentation`. There is plenty to decide now — which of four faces a standing is, what a
// contribute chip actually sends, whether a project row is affordable against the *pool* rather than
// against a colony, and which controls a role may touch — so the layer arrives with the work rather
// than ahead of it.
//
// **No Compose plugin and no Compose dependency**, which is the check that nothing here draws.
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
        namespace = "dev.fardavide.oltre.client.alliance.presentation"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core)
            implementation(projects.protocol)
            implementation(projects.client.alliance.domain)
            // `api`, so the composition root names this feature once: the mapper returns an
            // `AllianceUiState` and the shell hands it straight to `AllianceScreen`.
            api(projects.client.alliance.ui)
            implementation(projects.client.design.format)
            implementation(projects.client.design.text)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
