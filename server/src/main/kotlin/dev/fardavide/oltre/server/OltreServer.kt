package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.Protocol
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlin.time.Clock

// **The engine, answering — and nothing in this file decides anything.** Two routes and no more:
// `#106` §2's whole list of what moves is *"the server holds the state and calls `advance`"*, and
// everything a player can do to a colony is already one of twelve verbs on one envelope. What each
// route does is `Endpoints.kt`, which knows nothing about HTTP and is therefore reachable by a plain
// unit test; what is here is the transport.
//
// The repository and the clock are parameters for the reason every seam on `App` is one: a module
// that reached for `Clock.System` and a map of its own could not be driven by a test, and this is
// the layer where being wrong is silent. `Main.kt` supplies the real pair.
internal fun Application.oltre(repository: ColonyRepository, clock: Clock) {
    // `Protocol.json` and not a Ktor default. It is the one codec both ends use, and the properties
    // that make it that one — `encodeDefaults`, and deliberately no `ignoreUnknownKeys` — are the
    // contract rather than a preference. A server that answered in a different dialect would be a
    // server the client cannot read.
    //
    // Responses only: a request body is decoded by `admit`, so that a body which does not parse is a
    // designed answer rather than an exception somebody has to catch and translate back.
    install(ContentNegotiation) { json(Protocol.json) }

    routing {
        route("/v1") {
            post("/colony") {
                call.send(foundColony(repository, clock, call.player(), call.receiveText()))
            }

            post("/sync") {
                call.send(syncColony(repository, clock, call.player(), call.receiveText()))
            }
        }
    }
}

// `Protocol.PLAYER_HEADER` rather than a constant of this module's own, which is where `#108` put
// it: the name of a header is a thing both ends have to agree on, and a client that spelled it out
// separately could disagree by one character and read as a player who never signed in.
private fun ApplicationCall.player(): String? = request.headers[Protocol.PLAYER_HEADER]

// The two arms exist because the two payloads are different types, and `respond` picks its
// serializer from the **static** type of what it is handed. `Answer.Failed.error` is declared
// `ApiError`, so the sealed hierarchy's discriminator is written; a call that passed
// `ApiError.NoColony` directly would encode the object without one, and the client would fail to
// parse a message the server thought it had sent.
private suspend fun ApplicationCall.send(answer: Answer) {
    when (answer) {
        is Answer.Colony -> respond(answer.status, answer.response)
        is Answer.Failed -> respond(answer.status, answer.error)
    }
}
