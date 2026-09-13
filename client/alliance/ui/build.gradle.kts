import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The alliance's tab, ahead of the feature it names: one screen, saying plainly that the alliance
// is not built yet (`alliance-sheet.md` §7, `#143` narrowed). A `ui` module and nothing else, on
// the same footing `module-rules` states for `:client:debug:ui` — the screen decides nothing, so a
// `presentation` here would forward its one string and no more. `:client:alliance:data` and
// `:client:alliance:domain` already exist from `#141`; this module does not depend on either, since
// a screen with no logic reads nothing from them yet.
//
// Roborazzi, unlike the debug menu: this screen is player-facing, even if its content is a
// stopgap, so it is held to the same baseline discipline every other destination is.
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
        namespace = "dev.fardavide.oltre.client.alliance.ui"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.client.design.component)
            implementation(projects.client.design.core)
            implementation(projects.client.design.text)

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
