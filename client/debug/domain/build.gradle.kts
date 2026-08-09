import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// No Compose plugin and no Compose dependency, and that is the reason this is a module rather than
// a file in `:client:debug:presentation`. Everything a debug action *decides* — where skipping
// lands, what the clock offset becomes, what the inspector reports — is arithmetic over game state,
// so it belongs where it can be tested as arithmetic. It is also the half a cloud session can
// build: see `.claude/tools/gradle-without-agp.sh`.
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
        namespace = "dev.fardavide.oltre.client.debug.domain"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // GameState and FutureEvent are this module's own vocabulary — a skip target *is* a
            // future event — so they travel with it.
            api(projects.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
