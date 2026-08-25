plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "dev.fardavide.oltre.server.MainKt"
}

dependencies {
    // The engine, unmodified. `advance` and the twelve verbs are pure and take time as a
    // parameter, so the server's simulation is the same function the phone runs — which is the
    // whole bet `#106` collects on.
    implementation(projects.core)
    // The wire. Named explicitly even though `:protocol` re-exports `core`, because this module
    // reads both and a build file should say what it reads.
    implementation(projects.protocol)

    // **No `kotlinx-serialization` plugin here, deliberately.** Every type that crosses the wire is
    // already compiled in `:protocol` and `core`, so this module declares no `@Serializable` of its
    // own and has nothing for the compiler plugin to generate. The day it needs one — an admin
    // payload, a health body — is the day the plugin goes in, and it should have to be argued for
    // then rather than inherited now.
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.kotlinx.coroutines.core)

    // The colony's home. A driver and a pool and no ORM — the save is already a self-contained JSON
    // document that `core` knows how to carry forward, so there is nothing to map and nothing a
    // framework would keep in step.
    implementation(libs.hikari)
    implementation(libs.postgresql)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlinx.coroutines.test)

    // Real PostgreSQL for the integration suite. `platform(...)` is not decoration: without it the
    // binaries resolve to 14.22.0 through `embedded-postgres`'s own default, so this machine would
    // test 17 and CI would test 14 — and this slice's SQL is chosen for what 17 can do.
    testImplementation(platform(libs.embedded.postgres.binaries.bom))
    testImplementation(libs.embedded.postgres)
    // The two platforms the pom omits — it ships amd64 only. Without the linux one an arm64 runner
    // has no binary jar on the classpath at all and nothing starts.
    testRuntimeOnly(libs.embedded.postgres.binaries.darwin.arm64v8)
    testRuntimeOnly(libs.embedded.postgres.binaries.linux.arm64v8)
}
