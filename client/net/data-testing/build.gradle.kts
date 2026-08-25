import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// **A server that is not there, and it is load-bearing rather than a convenience.** `#106` §8:
// `App()` is about to require a network, the whole behaviour and screenshot suite runs on the
// desktop target, and that suite cannot reach production and must not try. So the fake transport
// lands here, one slice before the shell needs it — the same move the suite already makes with
// `FakeSaveFile`, `FakeNotificationScheduler` and `DebugClock`.
//
// `data-testing`, not `testing`: rule 5 matches on the `-testing` suffix and strips it to find the
// layer, so this module is `data` and carries a data module's restrictions. Reachable only from a
// test source set, which is what stops a fake server ever shipping.
//
// **Every target its sibling has, and that is not symmetry for its own sake.** A desktop-only module
// would be unreachable from `:client:net:data`'s `commonTest`, which is the source set the
// Kotlin/Native compiler checks on CI.
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
        namespace = "dev.fardavide.oltre.client.net.datatesting"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `api`, because `OltreApi` is what this module implements and `SyncResponse` is what
            // a caller scripts it with: a consumer that could not see either could not use the fake
            // at all.
            api(projects.client.net.data)

            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
