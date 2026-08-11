import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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
    }
}
