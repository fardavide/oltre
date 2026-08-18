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
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "dev.fardavide.oltre.client.design.component"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `core` is here for `ResourceKind` alone, and it is an inward-pointing edge like every
            // other. A chip is priced in a resource, and the palette in `:client:design:core`
            // already names all three (`OltreColors.metal` / `.crystal` / `.deuterium`) — so the
            // design system already knows this vocabulary as strings. Taking the real enum is
            // strictly less duplication than that. Rejected: a design-owned tint enum, which would
            // buy independence from `core` by making every caller translate `ResourceKind` into it
            // — reintroducing, per feature, exactly the drift this module exists to remove.
            implementation(projects.core)
            // `implementation`, not `api`: the tokens are used inside these composables and appear
            // in none of their signatures, so a caller gets the component without inheriting the
            // palette. Every consumer already declares `:client:design:core` for itself.
            implementation(projects.client.design.core)
            // The same edge, for the same reason: `WatchSquare` draws the beacon inside itself and
            // hands no glyph back, so a caller gets the control without inheriting the icon set.
            // The set stays the leaf it was designed as — this points at it, never the other way.
            implementation(projects.client.design.icon)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
        }
        // The design system's own baselines, and until 0.13.1 it had none — every component here
        // was pinned only through whichever feature happened to render it. That is fine for what a
        // component *says*, which a feature's frame shows anyway, and useless for what a component
        // *does under a finger*: no screen's baseline holds a press, so the ripple that spilled
        // square out of every rounded button in the app was invisible to all fourteen of them.
        // **`desktopTest` and no `commonTest`, deliberately.** `OltreCardTest` is pure Kotlin over an
        // enum and would compile for every target, but a `commonTest` source set in a module with iOS
        // targets pulls Kotlin/Native test compilation and linking into `check` — which is a large
        // amount of build for a test that asserts six colours, and it made the suite heavy enough to
        // tip over several already-marginal screenshot and behaviour tests in other modules. It also
        // walks into the Native comma trap that `commonTest` names have to dodge. Nothing here needs
        // to run on a device, and a `…Test` class counts as a unit test wherever it sits.
        val desktopTest by getting {
            dependencies {
                implementation(projects.client.design.screenshotTesting)

                implementation(compose.desktop.uiTestJUnit4)
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlin.test)
                implementation(libs.roborazzi.compose.desktop)
            }
        }
    }
}
