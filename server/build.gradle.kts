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

    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlinx.coroutines.test)
}
