package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.Protocol
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
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
    // **What stops `/v1/auth/*` costing money**, and it is a parameter for the reason the clock is
    // one: a test that had to send twenty-one requests to prove the twenty-second is refused would be
    // a test of the transport rather than of the policy. Every rule it holds is judged in
    // `RateLimitTest`; what is here is which routes it guards. See `RateLimit.kt`.
    limiter: RateLimiter = RateLimiter(clock),
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
        // **What the Cloud Scheduler ping asks for, and it is deliberately the emptiest route here.**
        // `#106` §6 puts a job on this every ten minutes so a player never meets the cold start; Cloud
        // Run bills per request, so 144 a day is free.
        //
        // **It touches nothing, and that is the decision rather than laziness.** A health check that
        // asked the database whether it was there would be the better endpoint on almost any other
        // host — and here it would keep **Neon** awake around the clock. Neon's free plan bills
        // *compute hours* and scales to zero after a few minutes idle, so a ping that woke it every
        // ten minutes would run it 720 hours a month against an allowance of a small fraction of that.
        // The €0 in `#106` §6 depends on this route doing nothing.
        //
        // Outside `/v1`, because it is not part of the wire contract: there is no body, nothing to
        // negotiate, and a version prefix would say otherwise. `204` for the same reason — there is
        // nothing true to put in a body that the status line does not already say.
        get("/health") { call.respond(HttpStatusCode.NoContent) }

        route("/v1") {
            // **Every route under `/auth` is rate limited and no other route is**, which is step 45's
            // shape rather than a blanket policy. These are the only ones reachable without a session
            // and the only ones that do a signature check before knowing who is asking; everything
            // else costs a bearer-token read first, so a caller who cannot sign in cannot reach it.
            //
            // **`limited` wraps the handler rather than intercepting the route**, so that the
            // guarding is visible in the four lines below rather than in a plugin somebody has to go
            // and find. Ktor's own rate-limit plugin would have been the other way round, and would
            // have put the answer — a bare `429` with no `ApiError` in it — out of this file's reach.

            // **The provider is the path and not a field**, because the two are verified against
            // different issuers, different audiences and different key sets. One route branching on
            // its own body would be one route that has to be trusted to branch.
            post("/auth/apple") {
                call.limited(limiter) { signIn(identity, players, IdentityProvider.APPLE, call.receiveText()) }
            }

            post("/auth/google") {
                call.limited(limiter) { signIn(identity, players, IdentityProvider.GOOGLE, call.receiveText()) }
            }

            post("/auth/refresh") {
                call.limited(limiter) { refreshSession(identity, players, call.receiveText()) }
            }

            // **Registered with Apple at step 22, months before there was anything here to answer
            // it.** `api.oltre.space` is permanent, so entering it early cost nothing and saved a
            // second trip through a portal flow that drops values silently — and it starts 404ing the
            // day `#113` ships. See `appleNotification`, which verifies Apple's signature before it
            // acts on anything, because this is a POST target anybody can reach and one of the four
            // things it can say is *delete this account*.
            post("/auth/apple/notifications") {
                call.limited(limiter) { appleNotification(identity, players, call.receiveText()) }
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

            // **The two the profile needs, and neither of them touches a colony.** A name and a mark
            // are facts about an account, so they are `players`' business and are read and written
            // without the compare-and-set, the replay or the idempotency table that every colony
            // route spends. Not rate limited, on the same rule as the rest: both cost a bearer-token
            // read before they do anything, so a caller who cannot sign in cannot reach them.
            get("/profile") {
                call.send(readProfile(authenticator, players, call.credentials()))
            }

            post("/profile") {
                call.send(writeProfile(authenticator, players, call.credentials(), call.receiveText()))
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

// **One request's worth of quota, spent before the handler runs.** The `Retry-After` header goes out
// beside the body deliberately: the number is in the `ApiError` for this app's own client, which
// `#113` will read, and in the header for everything else on the wire that already knows what one is.
// **Every line here is plumbing, and it is short because the decisions are not.** Which caller a
// request counts as is `clientKey`; whether they are over quota is `RateLimiter.admit`; what a refusal
// says is `RateVerdict.Refused.answer`. All three are judged by plain unit tests, which this file's
// contents can never be.
//
// `remoteAddress` and not `remoteHost`, which resolves a name — a reverse lookup per request, on the
// one path that is reachable without a session and must therefore stay cheap.
private suspend fun ApplicationCall.limited(limiter: RateLimiter, handle: suspend () -> Answer) {
    val caller = clientKey(request.headers[HttpHeaders.XForwardedFor], request.origin.remoteAddress)
    val verdict = limiter.admit(caller)
    if (verdict is RateVerdict.Refused) {
        response.header(HttpHeaders.RetryAfter, verdict.retryAfterSeconds.toString())
        return send(verdict.answer())
    }
    send(handle())
}

// The arms exist because the payloads are different types, and `respond` picks its serializer from
// the **static** type of what it is handed. `Answer.Failed.error` is declared `ApiError`, so the
// sealed hierarchy's discriminator is written; a call that passed `ApiError.NoColony` directly would
// encode the object without one, and the client would fail to parse a message the server thought it
// had sent.
private suspend fun ApplicationCall.send(answer: Answer) {
    when (answer) {
        is Answer.Colony -> respond(answer.status, answer.response)
        is Answer.Session -> respond(answer.status, answer.response)
        is Answer.Profile -> respond(answer.status, answer.response)
        // `204` carries no body by definition, so there is nothing to serialize and nothing to pick
        // a serializer for. Two members share the arm and keep their own names, because what the
        // route files are read through is the name rather than the number — see `Answer.Noted`.
        Answer.Deleted,
        Answer.Noted,
        -> respond(answer.status)
        is Answer.Failed -> respond(answer.status, answer.error)
    }
}
