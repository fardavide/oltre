import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// What a `GameState` says about the sheet — which chip is lit, which seven switches are on, and the
// one thing the sheet says that is not a preference: when the next alert is actually due.
//
// **That last line is why this module exists at all.** Everything else here is a two-line mapping
// that would not have earned a layer; the timing line is a fold over `announcedEvents`, and it has to
// agree exactly with what the scheduler will book. `core` is where that rule lives, precisely so that
// this module and `:client:notifications:data` cannot disagree — a `presentation` may not see a
// `data`, and reproducing the gate here would have been the way round that rule rather than a way of
// keeping it.
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
        namespace = "dev.fardavide.oltre.client.settings.presentation"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core)
            // `api`, so the composition root names this feature once: the mapper returns an
            // `AlertSheetUiState` and the shell hands it straight to `AlertSheet`.
            api(projects.client.settings.ui)
            implementation(projects.client.design.format)
            implementation(projects.client.design.text)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
