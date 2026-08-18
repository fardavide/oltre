import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
        namespace = "dev.fardavide.oltre.client.notifications.data"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }

        // For `src/androidMain/res/drawable/ic_notification.xml` — the status-bar icon, which
        // lives here because the module that posts a notification owns what it posts it with,
        // and because a non-transitive R class means the app module's resources are not visible
        // to this one anyway.
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `api`: `PendingNotification` carries `TextRes`, and `GameNotifications` takes the
            // `Translations` that resolves it — both are this module's public surface.
            api(projects.client.design.text)
            // GameState and FutureEvent are this module's own vocabulary, so they travel with it.
            api(projects.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
