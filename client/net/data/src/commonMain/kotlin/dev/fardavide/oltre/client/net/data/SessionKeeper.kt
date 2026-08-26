package dev.fardavide.oltre.client.net.data

import dev.fardavide.oltre.protocol.SessionResponse
import dev.fardavide.oltre.protocol.SessionToken
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// **How early a token is replaced.** A token checked at the instant it is sent can still expire in
// flight on a slow connection, and the whole promise of the refresh pair is that the player never
// learns any of this happened. The cost of being a minute early is one extra request every
// fifty-nine minutes; the cost of being a second late is a screen that failed for no reason the
// player could act on.
private val MARGIN: Duration = 1.minutes

// **The one thing between the transport and the gate**, and it exists so that `ApiError
// .SessionExpired` is a sentence nobody ever reads. `#110` gave the two tokens different lifetimes
// for exactly this: an hour on the credential that travels on every request, ninety days on the one
// that does not, and a player who checks in twice a day never signs in twice.
//
// Every collaborator is a parameter for the reason every seam here is one, and the clock is the
// load-bearing one: what this class decides is entirely a question about *when*, so one that read
// `Clock.System` could not be asked a single question in `SessionKeeperTest`.
//
// **It holds nothing in memory between calls.** The store is the state, exactly as the outbox's file
// is: a cached pair is one more thing that can disagree with what is on disk, and the disagreement
// would be invisible until a relaunch.
class SessionKeeper(
    private val api: OltreApi,
    private val store: SessionStore,
    private val clock: Clock,
) {

    // **What to put in the header, or null for the gate.** Null is not a failure and it is three
    // different situations that all mean the same thing here — nobody has signed in, the refresh
    // token has run out, or a refresh was refused — because there is one thing to do about all of
    // them.
    //
    // Note the fourth caller of null, which is *not* one of those: a refresh that nobody answered.
    // The session survives that one; see `renewed`.
    suspend fun current(): SessionToken? {
        val held = store.read() ?: return null
        val now = clock.now()
        return if (now < held.accessExpiresAt - MARGIN) held.accessToken else renewed(held, now)
    }

    // **The forced renewal, and it asks a different question from `current`.** `current` asks
    // whether the token is still in date by this device's reckoning; this is called *after* the
    // server has answered `SessionExpired`, which means that reckoning was wrong — a phone whose
    // clock is off by more than the margin is the ordinary cause. Deciding again on the same
    // arithmetic would hand back the same dead token forever.
    suspend fun renew(): SessionToken? {
        val held = store.read() ?: return null
        return renewed(held, clock.now())
    }

    // What a sign-in does with what it got. Written before anything reads it, so a process killed on
    // the next line still has the session rather than sending the player back to the gate.
    suspend fun adopt(session: SessionResponse) {
        store.write(session)
    }

    // Deleting an account, and running out of refresh. Idempotent, because signing out of a device
    // that was never signed in is not an error.
    suspend fun forget() {
        store.clear()
    }

    private suspend fun renewed(held: SessionResponse, now: Instant): SessionToken? {
        // **The dead credential is not offered**, which saves a round trip and, more to the point,
        // saves a *failure*: asking with a token that expired last month gets a 401 that reads
        // exactly like a refusal worth acting on. The expiry is on the wire so the client can tell
        // these apart without decoding a credential it did not sign.
        if (now >= held.refreshExpiresAt) {
            store.clear()
            return null
        }

        return when (val result = api.refresh(held.refreshToken)) {
            is ApiResult.Answered -> {
                store.write(result.value)
                result.value.accessToken
            }

            // The token was in date and the server said no anyway — an account deleted on another
            // device is the case this is really about. There is nothing left to try and nothing to
            // keep.
            is ApiResult.Refused -> {
                store.clear()
                null
            }

            // **Not a signed-out player, and this arm is the reason `ApiResult` splits `Refused`
            // from `Unreachable` at all.** Clearing here would send somebody to a sign-in screen
            // they cannot use — on a train, with no signal — and would throw away the credential
            // that will work perfectly well when the signal comes back.
            ApiResult.Unreachable -> null
        }
    }
}
