import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// **The two surfaces that exist because the colony moved off the phone**: the gate, which is the
// first screen in the game that is not about a colony, and the deletion face, which is the door back
// out of it.
//
// It draws and decides nothing. Which of the gate's five states is showing, and what each of them
// says, arrives as a `GateUiState` from `:client:auth:presentation`.
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
        namespace = "dev.fardavide.oltre.client.auth.ui"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `api`: every callback on the gate says *which provider was pressed*, and that type is
            // `:protocol`'s — see `AuthProvider` there for why it lives on the contract rather than
            // in either of the two modules that name it.
            api(projects.protocol)
            api(projects.client.design.text)
            implementation(projects.client.design.component)
            implementation(projects.client.design.core)
            // The game's mark at 88dp, and the two marks the game does not own. The gate is the only
            // screen that draws any of the three.
            implementation(projects.client.design.icon)

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
