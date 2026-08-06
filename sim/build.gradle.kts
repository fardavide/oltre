plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "dev.fardavide.oltre.sim.MainKt"
}

dependencies {
    implementation(project(":core"))

    testImplementation(libs.kotlin.test)
}
