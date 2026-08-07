import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "OltreClient"
            isStatic = true
        }
    }

    android {
        namespace = "dev.fardavide.oltre.client"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core)
            implementation(projects.client.colony.presentation)
            // No `:client:design:component` — the shell draws chrome (the rail, the tab bar), and
            // none of the row-level components a screen is built from.
            implementation(projects.client.design.core)
            implementation(projects.client.design.format)
            implementation(projects.client.design.icon)
            implementation(projects.client.galaxy.presentation)
            implementation(projects.client.notifications.data)
            implementation(projects.client.research.presentation)
            implementation(projects.client.save.data)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
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

compose.desktop {
    application {
        mainClass = "dev.fardavide.oltre.client.MainKt"

        // Icons are generated from the SVG masters by `python3 art/icon/generate.py` —
        // regenerate rather than editing them.
        nativeDistributions {
            packageName = "Oltre"

            macOS {
                bundleID = "dev.fardavide.oltre"
                iconFile.set(project.file("icons/oltre.icns"))
            }
            windows {
                iconFile.set(project.file("icons/oltre.ico"))
            }
            linux {
                iconFile.set(project.file("icons/oltre.png"))
            }
        }
    }
}
