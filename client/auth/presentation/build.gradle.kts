import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// **What the gate says, which is the whole of what it decides.** Five states and one screen: the
// mapping from *what just happened* into a lead line, a body line and a colour is the only judgement
// on this surface, and it is exactly the kind a `presentation` module exists to hold.
//
// It also builds the deletion face, whose four fact rows are a fold over a `GameState` — the colony's
// own numbers, which is what makes the sentence after them land.
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
        namespace = "dev.fardavide.oltre.client.auth.presentation"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core)
            // `api`, so the composition root names this feature once: the mapper hands back a
            // `GateUiState` and the shell gives it straight to `Gate`.
            api(projects.client.auth.ui)
            implementation(projects.client.design.component)
            implementation(projects.client.design.text)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
