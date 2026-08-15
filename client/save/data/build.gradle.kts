import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    // For `Preferences` and nothing else. The colony's format is core's business and arrives here
    // already encoded, but the preferences file is this module's own — it is deliberately not in
    // the save — so the serializer for it has to be generated here. Only the compiler plugin is
    // declared: the runtime comes with `api(projects.core)` below, which core exposes as `api`
    // precisely so the format travels with it.
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    jvmToolchain(21)

    jvm("desktop")
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "dev.fardavide.oltre.client.save.data"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // GameSnapshot is this module's own vocabulary, so it travels with it.
            api(projects.core)

            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
