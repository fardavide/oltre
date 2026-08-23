import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The one thing the strip decides: what a `GameState` says about who is playing.
//
// **The layer `:client:player:ui`'s build file said would arrive when the numbers became real**, and
// this is that day — the level and the gauge now come off `GameState.experience`, so there is a
// mapping to do and somewhere for it to live. The name is still one catalogue entry and travels with
// the mapping rather than staying behind in `ui`, because a state assembled in two modules is a state
// whose shape nobody owns.
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
        namespace = "dev.fardavide.oltre.client.player.presentation"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core)
            // `api`, so the composition root names this feature once: the mapper returns a
            // `PlayerStripUiState` and the shell hands it straight to `PlayerStrip`.
            api(projects.client.player.ui)
            implementation(projects.client.design.text)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
