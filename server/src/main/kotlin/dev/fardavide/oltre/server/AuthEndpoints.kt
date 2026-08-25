package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.RefreshRequest
import dev.fardavide.oltre.protocol.SessionResponse
import dev.fardavide.oltre.protocol.SignInRequest
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException

// **What the four identity routes decide, with nothing that knows what a socket is.** It is
// `Endpoints.kt`'s file for the other half of the API, written the same way and for the same reason:
// a route handler needs a test host, which by this repository's taxonomy makes any test of it an
// `…IntegrationTest`, and every rule below is reachable by a plain `…Test` with a handwritten JWKS
// and a clock a test moves by hand.

// Everything a server needs to be able to say who somebody is. Null everywhere below means **this
// deployment has no session key**, which is a state `./gradlew :server:run` is deliberately allowed
// to be in and a deployed server is not — see `identityConfig` and `Main.kt`.
internal class Identity(val verifier: IdTokenVerifier, val sessions: Sessions)

// **A sign-in, and the client never sends a provider token anywhere but here** — `#106` §4.
//
// The three steps are: prove it, resolve it, sign it. What makes the middle one the interesting part
// is that it is *find or create* — the same call for somebody who has played for a year and somebody
// who has never opened the app, so an upgrade and a fresh install are one code path and the only
// difference is whether a colony comes back afterwards.
internal suspend fun signIn(
    identity: Identity?,
    players: PlayerRepository,
    provider: IdentityProvider,
    body: String,
): Answer = answering {
    if (identity == null) return@answering unconfigured()

    val request = when (val read = readRequest(SignInRequest.serializer(), body) { it.apiVersion }) {
        is Read.No -> return@answering read.answer
        is Read.Yes -> read.value
    }

    when (val verdict = identity.verifier.verify(provider, request.idToken, request.nonce)) {
        // **One sentence for every refusal**, and the reason is not laziness: telling a client
        // *which* check failed tells anybody holding a stolen token which check to work on, and
        // there is nothing a player could do differently about any of them. The diagnostic is in
        // the verdict for a log to carry.
        is TokenVerdict.Refused -> Answer.Failed(HttpStatusCode.Unauthorized, ApiError.Unauthenticated)

        is TokenVerdict.Trusted -> {
            val player = players.resolve(verdict.identity)
            identity.sessions.issue(player).answer(HttpStatusCode.OK)
        }
    }
}

// **What `ApiError.SessionExpired` is answered with, and why that member is not a flag on
// `Unauthenticated`.** An access token running out is the one failure the app fixes by itself: no
// screen, no interruption, and the player never learns it happened.
//
// **A refresh token that has itself run out is `Unauthenticated` and not `SessionExpired`**, which
// looks inconsistent and is the whole point. `SessionExpired` means *"ask again in a moment"*; a
// client told that about the credential it asks *with* would loop forever. Ninety days without
// opening the game ends in the sign-in screen, and this is the line that says so.
internal suspend fun refreshSession(
    identity: Identity?,
    players: PlayerRepository,
    body: String,
): Answer = answering {
    if (identity == null) return@answering unconfigured()

    val request = when (val read = readRequest(RefreshRequest.serializer(), body) { it.apiVersion }) {
        is Read.No -> return@answering read.answer
        is Read.Yes -> read.value
    }

    when (val verdict = identity.sessions.read(request.refreshToken, SessionKind.REFRESH)) {
        SessionVerdict.Expired,
        is SessionVerdict.Invalid,
        -> Answer.Failed(HttpStatusCode.Unauthorized, ApiError.Unauthenticated)

        is SessionVerdict.Valid -> if (players.exists(verdict.player)) {
            // **A fresh pair rather than a fresh access token alone.** The refresh token slides
            // forward every time the game is opened, so a player who checks in twice a day never
            // signs in twice — which is the whole of what the ninety days is for. What keeps that
            // safe is the line above: the row is asked about on every refresh, so a deleted account
            // cannot slide anything forward.
            //
            // **What this is not is rotation, and the residual is worth stating rather than
            // implying.** These tokens are stateless, so issuing a new one does not retire the old
            // one: a refresh token that leaked stays usable for the rest of its own ninety days,
            // and nothing here can tell a thief's refresh from the player's. Rotation with reuse
            // detection needs a table of live tokens, which is a fourth table and a revocation story
            // — worth having the day there is something in a colony worth stealing, and not before.
            // Deleting the account is the revocation that exists today, and it is immediate.
            identity.sessions.issue(verdict.player).answer(HttpStatusCode.OK)
        } else {
            // A perfectly good refresh token naming somebody who deleted their account.
            Answer.Failed(HttpStatusCode.Unauthorized, ApiError.Unauthenticated)
        }
    }
}

// **Required, not optional** — App Review guideline 5.1.1(v) requires in-app account deletion for
// any app offering account creation, and `#106` §4 says so in as many words. It cascades to the
// colony and to every spent idempotency key, and it **actually deletes**: a soft-deleted row is a
// row still holding a provider subject, which is the thing this design went out of its way not to
// keep more of than it had to.
//
// **No body, and therefore no version negotiation**, which is the one deliberate omission in this
// file. There is nothing in either direction to disagree about: a client that can spell the URL and
// hold a session can be understood by any build of this server, and a `426` here would be a build
// refusing to delete data because it disliked a number.
//
// **What this does not do is tell Apple**, and that is flagged rather than forgotten. Apple's
// guidance since June 2022 is that an app offering account deletion *and* Sign in with Apple must
// call `/auth/revoke` — which needs the client secret (a JWT signed with the `.p8`, expiring within
// six months) and a token from `/auth/token`, which needs an authorization code the client does not
// send here today. Nothing else in this slice needs that secret at all, which is exactly why the
// obligation is easy to lose. **Davide's call, 2026-08-25: it lands in `#113`** — the blocker is the
// authorization code, which only the sign-in flow can produce. `status.md` and `decisions.md` carry
// the argument.
internal suspend fun deleteAccount(
    authenticator: Authenticator,
    players: PlayerRepository,
    credentials: Credentials,
): Answer = answering {
    when (val caller = authenticator.identify(credentials)) {
        is Caller.Refused -> Answer.Failed(HttpStatusCode.Unauthorized, caller.error)
        is Caller.Known -> {
            // The answer is `204` whether a row went away or not. Deleting twice is not an error —
            // a client that lost the response to the first attempt will send a second, and telling
            // it that its account does not exist is telling it the thing it asked for.
            players.forget(caller.player)
            Answer.Deleted
        }
    }
}

// A session, on the wire. `ApiVersion.CURRENT` is what *this build* speaks rather than an echo of
// what was asked for — `Endpoints.kt` makes the same call and for the same reason.
private fun IssuedSession.answer(status: HttpStatusCode): Answer = Answer.Session(
    status = status,
    response = SessionResponse(
        apiVersion = ApiVersion.CURRENT,
        accessToken = access,
        accessExpiresAt = accessExpiresAt,
        refreshToken = refresh,
        refreshExpiresAt = refreshExpiresAt,
    ),
)

// **Not a dead control, which is the whole of why this is an answer and not an absent route.** A
// server with no session key still has a sign-in URL, because `#111` sets the variables and the day
// it has not is a day somebody is looking at a deploy wondering why nothing happens. `503` with a
// diagnostic says which variable is missing; a `404` would say the endpoint does not exist, which is
// both untrue and unactionable.
private fun unconfigured(): Answer = Answer.Failed(
    HttpStatusCode.ServiceUnavailable,
    ApiError.Internal("sign-in is not configured on this server: SESSION_SIGNING_KEY is not set"),
)

// `served()`'s one `catch`, for the routes that have no colony to retry. Everything below touches a
// store a network away and, on sign-in, a provider further away than that.
private suspend fun answering(block: suspend () -> Answer): Answer = try {
    block()
} catch (e: CancellationException) {
    // **Not a failure and not ours.** A cancelled request is the caller having gone away, and
    // swallowing it here would both answer a socket nobody is holding and break the coroutine that
    // is trying to unwind.
    throw e
} catch (e: Exception) {
    Answer.Failed(HttpStatusCode.InternalServerError, ApiError.Internal(e.message ?: e::class.simpleName.orEmpty()))
}
