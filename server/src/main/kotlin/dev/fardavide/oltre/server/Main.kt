package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.Protocol
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock

// The process, and nothing else — the wiring is `oltre` one file over, where a test can drive it.
// This file holds what only a running server has: a port, a socket, the real clock, a database and
// the secrets nothing in this repository is allowed to know.
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: DEFAULT_PORT
    val url = System.getenv(DATABASE_URL)
    val configured = identityConfig { System.getenv(it) }

    // **The one combination that must never boot.** With no session key a request names its player
    // in a header, which is exactly what the dev loop wants and exactly what a deployed server must
    // never do — impersonating anybody would be a matter of typing their id. A `DATABASE_URL` is
    // what a deployment *is*, so the pair of "real store, no identity" is refused here rather than
    // trusted to a checklist. Failing at boot is the point: a misspelled variable is then a
    // deployment that does not start, instead of one that starts and lets anyone in.
    check(configured != null || url == null) {
        "$DATABASE_URL is set but SESSION_SIGNING_KEY is not, so this server would accept " +
            "${Protocol.PLAYER_HEADER} from anybody. See `#111`."
    }
    if (configured == null) {
        println("SESSION_SIGNING_KEY is not set: a request names its player in a header and proves nothing.")
    }

    val (colonies, players) = url?.let(::postgres) ?: inMemory()
    val identity = configured?.let {
        Identity(
            verifier = IdTokenVerifier(
                specs = it.specs(),
                keys = JwksKeys(httpJwksSource(), Clock.System),
                clock = Clock.System,
            ),
            sessions = Sessions(it.signingKey, Clock.System),
        )
    }

    embeddedServer(Netty, port = port) {
        oltre(colonies = colonies, players = players, clock = Clock.System, identity = identity)
    }.start(wait = true)
}

// **A store behind a URL, and the schema applied before the first request rather than by hand.**
// Cloud Run starts this process again every time it scales up from zero, so applying the DDL at
// startup has to be a no-op the second time — which is what every `IF NOT EXISTS` in `schema.sql`
// buys.
//
// **A pair rather than a type of its own**, deliberately: a private top-level class in this file
// would compile to its own class file, land in the coverage report at 0% and fail a gate on a PR
// that had touched no shipping code — which is exactly what `:sim`'s three option holders did at
// 0.1.1. `MainKt` is excluded by name and a sibling class would not be.
private fun postgres(url: String): Pair<ColonyRepository, PlayerRepository> = runBlocking {
    val pool = connectionPool(url)
    pool.applySchema()
    val repository = PostgresColonyRepository(pool, Clock.System)

    // **Pruning at startup and not on a timer**, which looks like the lazier of the two and is the
    // only one that works where this is going to run. Cloud Run throttles an instance's CPU to
    // nearly nothing outside a request, so a background coroutine sleeping for a day would be a
    // thing that reads as scheduled maintenance and never once fires — the shape the dead-control
    // rule is about, arriving on a server where nobody would go looking for it.
    //
    // What makes startup enough is the same fact from the other side: the host scales to zero and
    // idles instances out within minutes, so a process that has been up long enough for this to
    // matter is one somebody configured to stay up — and that configuration is `#111`'s, along with
    // the Cloud Scheduler ping it would want anyway.
    //
    // Failing here fails the boot, deliberately. A `DELETE` that cannot run against a database whose
    // DDL applied a line earlier is not a maintenance hiccup, and a server that started anyway would
    // be one whose first sign of trouble is a colony.
    repository.prune(before = Clock.System.now() - APPLIED_RETENTION)
    repository to PostgresPlayerRepository(pool, Clock.System)
}

// **The dev loop, and it says so out loud.** `./gradlew :server:run` with no database serves a
// colony that can be founded and played end to end and that dies with the process, which is what
// slice 1 shipped and what is still wanted locally. What must never happen is a *deployed* server
// quietly doing this because an environment variable was misspelled, so it is a line in the log
// rather than a silence — and `#111` is the slice that sets the variable.
private fun inMemory(): Pair<ColonyRepository, PlayerRepository> {
    println("$DATABASE_URL is not set: colonies will live in memory and die with this process.")
    val colonies = InMemoryColonyRepository()
    return colonies to InMemoryPlayerRepository(colonies)
}

private const val DATABASE_URL = "DATABASE_URL"
private const val DEFAULT_PORT = 8080
