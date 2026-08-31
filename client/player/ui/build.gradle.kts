import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The strip above the rail: who is playing, how far along, and the way to the settings that do not
// exist yet. Chrome by placement and a feature by content — the rail is three numbers, and this is a
// drawn mark, a name, a gauge, a control and a notice with a lifetime.
//
// **Still no `:core`, and that is the honest signal rather than an omission.** A `ui` module reaching
// past the design system is permitted where the model is *keyed* by a type it does not own —
// `:client:colony:ui` takes `BuildingType` because a row is a facility — and that is now true of one
// thing here and one only: the drawing is keyed by which mark was chosen, so `:protocol` is in the
// list below. The level and the experience are still numbers and the name is still a word, and none
// of them is read off a `GameState` here: `:client:player:presentation` folds the log and hands this
// module a state, which is what this file said would happen the day the numbers became real.
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
            // **`MarkPreset`, `MarkBody`, `MarkPath`, `MarkTerminus` and the sealed `PlayerMark` they
            // make, and nothing else off the wire.** A `ui` module reaching past the design system is
            // a judgement rather than a violation, and this is the shape it is allowed in:
            // `:client:colony:ui` takes `BuildingType` because a row *is* a facility, and the same
            // sentence holds here — a drawing is *keyed* by which mark was chosen, so the enums are
            // the model's own vocabulary rather than a detail leaking upward. The alternative is a
            // second parallel set of four enums that nothing keeps in step with the first, and the
            // day they drift the player picks a mark and is drawn a different one.
            //
            // `api` for `:client:design:text`'s reason: `PlayerStripUiState` carries a `PlayerMark`
            // as well as a `TextRes`, so a consumer that builds one needs both types in its own
            // compile classpath.
            api(projects.protocol)
            api(projects.client.design.text)
            implementation(projects.client.design.component)
            implementation(projects.client.design.core)

            // No `:client:design:icon`. The shared surface's own test is two callers plus "is it
            // vocabulary" — a bell is vocabulary, a player's mark is this feature's face — and
            // growing from two glyphs to seventeen changes the first half of that and not the
            // second. Eleven of the seventeen are parts of one mark rather than icons at all, and a
            // set nobody outside this feature can name is a set that belongs to this feature.

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
