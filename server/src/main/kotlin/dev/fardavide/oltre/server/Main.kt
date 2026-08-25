package dev.fardavide.oltre.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlin.time.Clock

// The process, and nothing else — the wiring is `oltre` one file over, where a test can drive it.
// This file holds what only a running server has: a port, a socket and the real clock.
//
// The colony lives in memory and dies with the process, which is `#109`'s to fix. Until then
// `./gradlew :server:run` serves a colony that can be founded and played end to end with `curl`,
// and that is exactly what this slice set out to be able to say.
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: DEFAULT_PORT
    embeddedServer(Netty, port = port) {
        oltre(repository = InMemoryColonyRepository(), clock = Clock.System)
    }.start(wait = true)
}

private const val DEFAULT_PORT = 8080
