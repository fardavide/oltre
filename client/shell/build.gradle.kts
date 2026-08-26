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
            // The changelog, in three layers, and the composition root is where they meet: `domain`
            // decides whether this launch has anything new to say, `presentation` turns the document
            // into pages, and `ui` is the face the one sheet wears. `:client:settings:ui` reaches
            // none of them — the build row that leads to the changelog is filled into a slot here.
            // The gate, the deletion face, the three platform sign-ins, and the queue read as a set
            // of controls. The composition root is the only module that may see all four, which is
            // the whole of its job.
            implementation(projects.client.auth.data)
            implementation(projects.client.auth.presentation)
            implementation(projects.client.auth.ui)
            implementation(projects.client.net.data)
            implementation(projects.client.net.domain)
            implementation(projects.client.changelog.domain)
            implementation(projects.client.changelog.presentation)
            implementation(projects.client.changelog.ui)
            implementation(projects.client.colony.presentation)
            // All the layers of the debug feature, which is the composition root's privilege and
            // nobody else's: `domain` for the clock and the skip, `data` for the accelerometer, and
            // `ui` for the panel. Rule 7 is what makes that safe — nothing depends on the shell, so
            // the layers it mixes cannot travel anywhere.
            implementation(projects.client.debug.data)
            implementation(projects.client.debug.domain)
            implementation(projects.client.debug.ui)
            // **`:client:design:component`, for exactly one thing, and 0.19 is where that changed.**
            // The rule was *no components here at all* — the shell draws chrome and none of the
            // row-level pieces a screen is built from — and the half of it that matters is unchanged:
            // nothing here draws a card, a chip, a dial or a row.
            //
            // What it takes is `OltreBottomSheet`, which is chrome by its own file's argument: it is
            // *the only way this app raises a panel over a screen*. Until 0.19 every sheet was raised
            // by the feature that filled it, because each one had a single face. The settings sheet
            // now has two — the ladders and the changelog — and they come from two different
            // features, so the only place that can raise it is the one place allowed to know both.
            // The alternative was a wrapper in one feature that composed the other, which is the
            // cross-feature edge this build warns about.
            implementation(projects.client.design.component)
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
            // The mapper only; `AlertSheet` arrives with it, because a `presentation` exposes the
            // `ui` it maps into — the same edge `:client:player:presentation` has.
            implementation(projects.client.settings.presentation)
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
                // **The fake server, and rule 5's whole shape**: a testing module reached from a test
                // source set and from nowhere else. It is what keeps the behaviour suite off a socket
                // pointed at production — `#106` §8 — and it is the reason `App` takes an `OltreApi`
                // at all rather than building one for itself.
                implementation(projects.client.net.dataTesting)

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
