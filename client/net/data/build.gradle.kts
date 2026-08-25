import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// **The only module in `client/` that opens a socket.** Everything above it — the shell, every
// presentation module, every screen — asks `OltreApi` a question and gets data back; what a
// connection is, what a status line means and what to do when nothing answers all stop here.
//
// A `data` module, so rules 2–4 forbid it from seeing a `presentation` or a `ui`. That is the point
// rather than a constraint to work around: this module cannot build a word the player reads, which
// is why a refusal leaves here as a type and not as a sentence. `#113` turns it into `TextRes`.
//
// **No dependency on `:client:save:data`, deliberately.** The outbox needs a file and that module
// already has one, but a `net -> save` edge is a cross-feature edge and the build warns on every one
// of them. So the outbox declares the port it needs — `OutboxFile`, three methods — and the
// composition root, which is the one module allowed to see both, hands it whatever writes bytes.
// That is dependency inversion doing the job the warning exists to protect.
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
        namespace = "dev.fardavide.oltre.client.net.data"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `api`: every method on `OltreApi` names a `SyncRequest` or a `SyncResponse`, and the
            // outbox queues `VerbEnvelope`s. A consumer that could not see `:protocol` could not
            // call anything here. `:protocol` exposes `:core` the same way, so `GameSnapshot`
            // travels with it.
            api(projects.protocol)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
        }
        commonTest.dependencies {
            // Rule 5's whole shape: a testing module reached from a test source set and from
            // nowhere else. The fake this module's own tests drive is the same one the behaviour
            // suite gets at `#113`, which is what makes it worth having tested here first.
            implementation(projects.client.net.dataTesting)

            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        val desktopMain by getting {
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }
    }
}
