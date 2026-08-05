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
    implementation(projects.core)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)

    testImplementation(libs.kotlin.test)
}
