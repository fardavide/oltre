import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// What the game says, as a type rather than as a literal. No Compose reaches this file, and that is
// the whole reason it is a module of its own rather than a corner of `:client:design:component` —
// `stringResource()` is a `@Composable`, and every string in this app is *built* in a `presentation`
// module that has no compiler plugin, hours before anything knows how it will be drawn. A
// notification goes further still: it is booked with the OS outside composition entirely.
//
// So the split is the same one `:client:design:format` already draws. Which words the game uses is a
// decision about language; turning them into pixels is a decision about rendering. This module holds
// the first, `LocalTranslations` in `:client:design:core` holds the seam to the second, and nothing
// here knows that Compose exists.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    jvmToolchain(21)

    jvm("desktop")
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "dev.fardavide.oltre.client.design.text"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `ResourceKind` alone, and for `:client:design:component`'s stated reason: the game
            // names three resources, the catalogue has to say them in sentences, and a design-owned
            // copy of the enum would buy independence by making every caller translate one enum into
            // another. An inward edge like every other.
            implementation(projects.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
