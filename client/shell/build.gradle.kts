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

        // See `:client:design:core`, which holds the resources this one only generates an empty
        // accessor for. Enabled here too so the two modules cannot disagree the day the shell
        // bundles something of its own.
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core)
            // A feature is named once, at its `presentation`, which declares `api` on its own `ui`
            // — a screen's composable and the models the shell hands it travel together, so naming
            // both here would be the composition root re-declaring what the layer above it already
            // exposes. `:client:debug` is the exception in the other direction: it *has* no
            // presentation, so its `ui` is what there is to name.
            implementation(projects.client.colony.presentation)
            // All the layers of the debug feature, which is the composition root's privilege and
            // nobody else's: `domain` for the clock and the skip, `data` for the accelerometer, and
            // `ui` for the panel. Rule 7 is what makes that safe — nothing depends on the shell, so
            // the layers it mixes cannot travel anywhere.
            implementation(projects.client.debug.data)
            implementation(projects.client.debug.domain)
            implementation(projects.client.debug.ui)
            // No `:client:design:component` — the shell draws chrome (the rail, the tab bar), and
            // none of the row-level components a screen is built from.
            implementation(projects.client.design.core)
            // The composition root chooses the language and hands it to the theme and to
            // `GameNotifications`, and it names the five destinations — so it uses the catalogue
            // directly rather than only through the design system.
            implementation(projects.client.design.text)
            implementation(projects.client.design.format)
            implementation(projects.client.design.icon)
            implementation(projects.client.fleets.presentation)
            implementation(projects.client.galaxy.presentation)
            implementation(projects.client.notifications.data)
            implementation(projects.client.player.presentation)
            implementation(projects.client.research.presentation)
            implementation(projects.client.save.data)
            implementation(projects.client.shipyard.presentation)
            // Only the `data` half is named, because it declares `api` on its own `domain` —
            // `TiltSource` hands out a `Tilt`, so the type travels with the module that emits it.
            implementation(projects.client.tilt.data)

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
        androidMain.dependencies {
            // `MainActivity` and nothing else. The Activity is the platform's entry point, so it
            // needs the platform's Compose host (`setContent`) and its edge-to-edge call; the
            // rest of the Android app is a manifest in `:androidApp`.
            implementation(libs.androidx.activity.compose)
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
