import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// **The changelog itself** — sixty-five releases, in two languages, and the mapping from one of them
// into the page a sheet draws. It is the first surface in the app whose content is not derived from
// game state: nothing here reads a colony, and `core` is not on the classpath at all.
//
// The copy is two documents rather than two hundred and sixty catalogue ids. `.claude/docs/
// changelog-sheet.md` §4 is the argument; the short of it is that the enum's exhaustive `when` can
// only catch a missing id, while `ChangelogTranslationTest` catches a release Italian never got, a
// date that drifted, and a page that lost a line in translation.
//
// **No Compose plugin and no Compose dependency**, which is the check that nothing here draws.
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
        namespace = "dev.fardavide.oltre.client.changelog.presentation"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `api`, so the composition root names this feature once: the mapper returns a
            // `ChangelogUiState` and the shell hands it straight to `ChangelogSheet`.
            api(projects.client.changelog.ui)
            api(projects.client.changelog.domain)
            implementation(projects.client.design.text)
            // A release has a date, and a date is the one thing on this sheet the app has never
            // written before: every other instant it prints is a clock time or a duration.
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

// **The catalogue is checked against the README, and this is what lets a test find it.** Sixty-five
// hand-written entries plus a permanent per-release obligation is exactly the shape that rots
// quietly; `ReleaseCatalogueIntegrationTest` reads the changelog out of the repository and asserts
// every release has a page with the same date and that no page invents one. A test's working
// directory is its own module, so the root has to be handed in.
tasks.withType<Test>().configureEach {
    systemProperty("oltre.rootDir", rootDir.absolutePath)
}
