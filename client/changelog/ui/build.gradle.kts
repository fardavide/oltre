import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The changelog sheet, and the one drawing in the app that two features share — the settings
// sheet's build row carries the same mark at 29dp that a page carries at 319. That edge is why
// `changelog` is in `sharedSurfaces`: it passes the same test `dispatch` and `world` passed, which
// is that nothing points *out* of it. This module reaches its own feature's domain and the design
// system and no other feature at all.
//
// It draws and decides nothing. What a page says arrives as a `ChangelogUiState` from
// `:client:changelog:presentation`; what a sky looks like is arithmetic in
// `:client:changelog:domain`. What is left here is four `drawCircle`s, an arc and a pager.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.roborazzi)
}

kotlin {
    jvmToolchain(21)

    jvm("desktop")
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "dev.fardavide.oltre.client.changelog.ui"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // **`api`, and the model genuinely is about one.** A page is keyed by a `ReleaseVersion`
            // — the mark is drawn from it and the version line prints it — so the type is in the
            // signature and travels with it. This is the `BuildingType` case module rule 4 names.
            api(projects.client.changelog.domain)
            // `api` like every other feature's `ui`: the ui state carries `TextRes`.
            api(projects.client.design.text)
            implementation(projects.client.design.component)
            implementation(projects.client.design.core)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        val desktopTest by getting {
            dependencies {
                implementation(projects.client.design.screenshotTesting)
                implementation(compose.desktop.uiTestJUnit4)
                implementation(compose.desktop.currentOs)
                implementation(libs.roborazzi.compose.desktop)
            }
        }
    }
}
