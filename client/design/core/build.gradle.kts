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
        namespace = "dev.fardavide.oltre.client.design.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            api(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

compose.resources {
    // Pinned, because the default is derived from the Gradle group — which means a generated
    // package, and every import of it, silently moves when the group does. It moved at 0.0.10,
    // when the group started carrying the project path to keep module coordinates unique
    // (see `.claude/docs/decisions.md`). Naming it here makes the two independent.
    //
    // Deliberately *not* re-pinned when `:client:design` split into layer modules at 0.0.14 and
    // this file became `:client:design:core`. The whole value of the pin is that the generated
    // package stops moving when the build layout does; spending it to make the string match the
    // new module name would be the pin conceding the one thing it exists to prevent.
    packageOfResClass = "dev.fardavide.oltre.client.design.generated.resources"
}
