package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.ApiError

// **Who is asking, decided by a function that has never heard of Ktor.** `#108` moved the routes'
// rules into `Endpoints.kt` so a plain unit test could judge them; this is the same move for the
// question that file used to answer in one line — *"a request names its player in a header"*.
//
// **Why not `ktor-server-auth` and its `jwt {}` plugin**, which `#110` named. Two reasons, and the
// second is the one that decided it:
//
//  - `authenticate("session") { … }` puts the decision inside the routing file, which is the one
//    file in this module that is deliberately unreachable by a unit test. Everything below would
//    then be judged only by a test that stands up a server.
//  - Its challenge is a bare `401` with no body unless it is replaced wholesale, and this taxonomy's
//    whole point is that `Unauthenticated` and `SessionExpired` are **different sentences** —
//    *"sign in again"* against *"one moment"*. Flattening them would undo the split `ApiError` was
//    given at `#107` and `ColonySync` was built around at `#112`.
//
// So the catalogue gains neither artifact. A line with no consumer is dead weight — `#112`'s call on
// `ktor-client-content-negotiation`, made again.

// What the two headers a request can carry a credential in actually held. A type rather than two
// `String?` parameters threaded through four functions, because the pair is read once at the edge
// and passed down, and two nullable strings in a row is how they end up swapped.
internal data class Credentials(val authorization: String?, val playerHeader: String?)

// The conclusion. **`Refused` carries the `ApiError` rather than a boolean**, because the whole
// value of this layer is which of the two 401s it is.
internal sealed interface Caller {

    data class Known(val player: PlayerId) : Caller

    data class Refused(val error: ApiError) : Caller
}

internal fun interface Authenticator {

    suspend fun identify(credentials: Credentials): Caller
}

// **What a deployed server uses, and the only thing that ever should.** A bearer token this server
// signed, naming a player this server still has.
//
// The existence check is the line that makes `DELETE /v1/account` mean what App Review requires. A
// signature cannot be withdrawn, so a token minted before a deletion stays cryptographically perfect
// until it expires; asking the table turns "deleted" into "immediately" rather than "within the
// hour". It costs one primary-key lookup on a request path that sees two to four requests per player
// per day.
internal class SessionAuthenticator(
    private val sessions: Sessions,
    private val players: PlayerRepository,
) : Authenticator {

    override suspend fun identify(credentials: Credentials): Caller {
        // **`X-Oltre-Player` is not read here and that is the point of the class.** The header still
        // exists — `#112`'s client sends it on every request and deleting it would stop that client
        // compiling — and a server holding a session key ignores it completely. It stops being sent
        // at `#113`; it stops being *believed* here.
        val token = bearerToken(credentials.authorization)
            ?: return Caller.Refused(ApiError.Unauthenticated)

        return when (val verdict = sessions.read(token, SessionKind.ACCESS)) {
            // The one answer the client can act on without a screen: refresh, then ask again.
            SessionVerdict.Expired -> Caller.Refused(ApiError.SessionExpired)
            // Forged, wrong kind, or signed by something else. All one sentence to a player.
            is SessionVerdict.Invalid -> Caller.Refused(ApiError.Unauthenticated)
            is SessionVerdict.Valid -> if (players.exists(verdict.player)) {
                Caller.Known(verdict.player)
            } else {
                // A perfectly good token naming somebody who deleted their account. Not
                // `SessionExpired`: refreshing it would fail too, and telling the client to try
                // would be telling it to loop.
                Caller.Refused(ApiError.Unauthenticated)
            }
        }
    }
}

// **The dev loop, and it exists only where no session key is configured.** `./gradlew :server:run`
// with nothing in the environment still serves a colony that can be founded and played end to end,
// which is what `#108` shipped and what is still wanted locally — the same call `Main.kt` already
// makes about `DATABASE_URL`, and the same log line saying so out loud.
//
// **A deployed server can never reach this class**, and that is enforced rather than hoped for:
// `oltre()` picks the authenticator from whether identity is configured, and `Main.kt` refuses to
// start when a `DATABASE_URL` is set and a session key is not. A real database is what a deployment
// is, so the one combination that would be dangerous is the one that will not boot.
//
// The header resolves through the *same* upsert a real sign-in uses, under the provider name
// `#109` already wrote into the column. So the dev loop exercises the identity path rather than
// bypassing it, and the only difference is who vouched for the subject.
internal class HeaderAuthenticator(private val players: PlayerRepository) : Authenticator {

    override suspend fun identify(credentials: Credentials): Caller {
        val named = credentials.playerHeader?.takeIf { it.isNotBlank() }
            ?: return Caller.Refused(ApiError.Unauthenticated)
        return Caller.Known(players.resolve(ProviderIdentity(ProviderName.HEADER, named)))
    }
}
