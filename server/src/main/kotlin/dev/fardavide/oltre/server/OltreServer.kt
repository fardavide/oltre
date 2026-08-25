package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.Protocol
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlin.time.Clock

// **The engine, answering — and nothing in this file decides anything.** Six routes: the two `#108`
// landed, and the four `#110` added so that a colony belongs to somebody. What each one does is
// `Endpoints.kt` and `AuthEndpoints.kt`, which know nothing about HTTP and are therefore reachable by
// plain unit tests; what is here is the transport.
//
// The repositories, the clock and the identity are parameters for the reason every seam on `App` is
// one: a module that reached for `Clock.System` and a map of its own could not be driven by a test,
// and this is the layer where being wrong is silent. `Main.kt` supplies the real set.
internal fun Application.oltre(
    colonies: ColonyRepository,
    players: PlayerRepository,
    clock: Clock,
    // **Null is a server with no session key**, which is `./gradlew :server:run` and nothing that is
    // deployed — `Main.kt` refuses to start in the one combination where that would be dangerous.
    // The choice below is the whole of what it changes: with a key, a request proves itself with a
    // bearer token and `X-Oltre-Player` is ignored outright; without one, the header names a player
    // exactly as it did at `#108`.
    identity: Identity?,
) {
    // `Protocol.json` and not a Ktor default. It is the one codec both ends use, and the properties
    // that make it that one — `encodeDefaults`, and deliberately no `ignoreUnknownKeys` — are the
    // contract rather than a preference. A server that answered in a different dialect would be a
    // server the client cannot read.
    //
    // Responses only: a request body is decoded by `readRequest`, so that a body which does not
    // parse is a designed answer rather than an exception somebody has to catch and translate back.
    install(ContentNegotiation) { json(Protocol.json) }

    val authenticator = identity
        ?.let { SessionAuthenticator(it.sessions, players) }
        ?: HeaderAuthenticator(players)

    routing {
        route("/v1") {
            // **The provider is the path and not a field**, because the two are verified against
            // different issuers, different audiences and different key sets. One route branching on
            // its own body would be one route that has to be trusted to branch.
            post("/auth/apple") {
                call.send(signIn(identity, players, IdentityProvider.APPLE, call.receiveText()))
            }

            post("/auth/google") {
                call.send(signIn(identity, players, IdentityProvider.GOOGLE, call.receiveText()))
            }

            post("/auth/refresh") {
                call.send(refreshSession(identity, players, call.receiveText()))
            }

            // App Review 5.1.1(v). `DELETE` on the account rather than a `POST` to something named
            // for deleting it: the method is the verb, and a proxy that logs one of these logs the
            // right thing.
            delete("/account") {
                call.send(deleteAccount(authenticator, players, call.credentials()))
            }

            post("/colony") {
                call.send(foundColony(colonies, authenticator, clock, call.credentials(), call.receiveText()))
            }

            post("/sync") {
                call.send(syncColony(colonies, authenticator, clock, call.credentials(), call.receiveText()))
            }
        }
    }
}

// Both headers, read once and handed down as a pair. **The legacy one is still read here and that is
// trap 1 of `#110`**: `#112`'s client sends `Protocol.PLAYER_HEADER` on every request, so removing
// it would stop `:client:net:data` compiling and take five CI jobs with it. What decides whether it
// is *believed* is which `Authenticator` is in play, one function up. It comes out at `#113`, when
// the client starts sending a bearer token instead.
//
// Both names are `Protocol`'s rather than this module's, which is where `#112` put the first one: the
// name of a header is a thing both ends have to agree on, and a client that spelled it out separately
// could disagree by one character and read as a player who never signed in.
private fun ApplicationCall.credentials(): Credentials = Credentials(
    authorization = request.headers[Protocol.AUTHORIZATION_HEADER],
    playerHeader = request.headers[Protocol.PLAYER_HEADER],
)

// The arms exist because the payloads are different types, and `respond` picks its serializer from
// the **static** type of what it is handed. `Answer.Failed.error` is declared `ApiError`, so the
// sealed hierarchy's discriminator is written; a call that passed `ApiError.NoColony` directly would
// encode the object without one, and the client would fail to parse a message the server thought it
// had sent.
private suspend fun ApplicationCall.send(answer: Answer) {
    when (answer) {
        is Answer.Colony -> respond(answer.status, answer.response)
        is Answer.Session -> respond(answer.status, answer.response)
        // `204` carries no body by definition, so there is nothing to serialize and nothing to pick
        // a serializer for.
        Answer.Deleted -> respond(answer.status)
        is Answer.Failed -> respond(answer.status, answer.error)
    }
}
