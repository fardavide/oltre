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
        namespace = "dev.fardavide.oltre.client.design.icon"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // No dependency on `:client:design:core`, deliberately: a glyph takes the colour it is
            // drawn in as a parameter, so the icon set knows the palette's *shape* and none of its
            // values. That is what lets a caller tint one mark by state — amber throttled, green
            // supplied — without the icon needing to know what either state means.
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
        }
        // **The icon set's first tests, and what made them possible is that the drawing stopped
        // being a composable.** A glyph used to be four primitives inside a `Canvas { }` lambda,
        // which nothing but a rendered screen could reach — so every mark in here was held up by
        // whichever feature's baseline happened to draw it, at 17dp, inside a card. `drawBell` is a
        // plain `DrawScope` function, so a test can hand it a bitmap and measure what it put there.
        //
        // **`desktopTest` and no `commonTest`**, on `:client:design:component`'s precedent and for
        // its reasons: a `commonTest` in a module with iOS targets pulls Kotlin/Native compilation
        // and linking into `check` for tests nothing needs to run on a device, and walks into the
        // Native comma trap that backticked names there have to dodge.
        val desktopTest by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlin.test)
            }
        }
    }
}
