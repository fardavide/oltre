import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The strip above the rail: who is playing, how far along, and the way to the settings that do not
// exist yet. Chrome by placement and a feature by content — the rail is three numbers, and this is a
// drawn mark, a name, a gauge, a control and a notice with a lifetime.
//
// **No `:core`, and that is the honest signal rather than an omission.** A `ui` module reaching
// `core` is permitted where the model is *keyed* by a domain type — `:client:colony:ui` takes
// `BuildingType` because a row is a facility. Nothing here is keyed by anything: the level and the
// experience are numbers and the name is a word. The day they are read off a `GameState` is the day
// this feature grows a `presentation` module, and the mapper goes there.
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
        namespace = "dev.fardavide.oltre.client.player.ui"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `api` like every other feature's `ui`: `PlayerStripUiState` carries `TextRes`, so a
            // consumer that builds one needs the type in its own compile classpath.
            api(projects.client.design.text)
            implementation(projects.client.design.component)
            implementation(projects.client.design.core)

            // No `:client:design:icon`. Both glyphs here have exactly one caller, and the shared
            // surface's own test is two callers plus "is it vocabulary" — a bell is vocabulary, a
            // player's mark is this feature's face.

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
