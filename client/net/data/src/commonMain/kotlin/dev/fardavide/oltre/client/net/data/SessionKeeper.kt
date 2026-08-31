package dev.fardavide.oltre.client.net.data

import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.SessionResponse
import dev.fardavide.oltre.protocol.SessionToken
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// **What this device has to send, or why it has nothing.** Three members rather than a nullable
// token, and the third is the whole reason the type exists: *no token* is two different situations
// and everything above this class has to tell them apart.
//
// The one that had to be split out is `Unreachable`. A renewal nobody answered leaves the session on
// disk, intact and good for another eighty-nine days — so treating it like a signed-out player costs
// somebody their account because they went through a tunnel. That is not a hypothetical: it is what
// a single nullable return did until #113, and it took the queue's promise with it.
sealed interface Credential {

    // A token to put in the header. Either it was still in date or a renewal has just replaced it,
    // and nothing above here needs to know which.
    data class Held(val access: SessionToken) : Credential

    // **Nobody has signed in, the refresh token has run out, or a refresh was refused.** One member
    // for three, because the store is empty in all three and there is one thing to do about it: the
    // gate. An account deleted on another device is the case this is really about.
    data object Gone : Credential

    // **A renewal nobody answered**, which is a fact about the network and not about the player.
    // The session is where it was and the right answer is the offline one — hold the queue, say the
    // network is out, and ask again.
    data object Unreachable : Credential
}

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

    // **What to put in the header, or why there is nothing to put there.** See `Credential`: the
    // two ways of having no token are a signed-out player and a silent network, and they are not the
    // same instruction.
    suspend fun current(): Credential {
        val held = store.read() ?: return Credential.Gone
        val now = clock.now()
        return if (now < held.accessExpiresAt - MARGIN) Credential.Held(held.accessToken) else renewed(held, now)
    }

    // **The forced renewal, and it asks a different question from `current`.** `current` asks
    // whether the token is still in date by this device's reckoning; this is called *after* the
    // server has answered `SessionExpired`, which means that reckoning was wrong — a phone whose
    // clock is off by more than the margin is the ordinary cause. Deciding again on the same
    // arithmetic would hand back the same dead token forever.
    suspend fun renew(): Credential {
        val held = store.read() ?: return Credential.Gone
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

    private suspend fun renewed(held: SessionResponse, now: Instant): Credential {
        // **The dead credential is not offered**, which saves a round trip and, more to the point,
        // saves a *failure*: asking with a token that expired last month gets a 401 that reads
        // exactly like a refusal worth acting on. The expiry is on the wire so the client can tell
        // these apart without decoding a credential it did not sign.
        if (now >= held.refreshExpiresAt) {
            store.clear()
            return Credential.Gone
        }

        return when (val result = api.refresh(held.refreshToken)) {
            is ApiResult.Answered -> {
                store.write(result.value)
                Credential.Held(result.value.accessToken)
            }

            // The token was in date and the server said no anyway — an account deleted on another
            // device is the case this is really about. There is nothing left to try and nothing to
            // keep.
            is ApiResult.Refused -> {
                store.clear()
                Credential.Gone
            }

            // **Not a signed-out player, and this arm is the reason `ApiResult` splits `Refused`
            // from `Unreachable` at all.** Clearing here would send somebody to a sign-in screen
            // they cannot use — on a train, with no signal — and would throw away the credential
            // that will work perfectly well when the signal comes back.
            //
            // **Keeping the session was never enough on its own**, which is what #113 found: this
            // arm kept the credential on disk and then answered `null`, and the one caller read
            // `null` as *signed out* and cleared it two frames later. A distinct member is what
            // makes the promise this comment has always made actually hold.
            ApiResult.Unreachable -> Credential.Unreachable
        }
    }
}

// **The promise above, kept on the routes that have no queue behind them.** `current()` decides
// entirely on this device's clock arithmetic; `renew()` exists for the case where the *server*
// disagrees, and a caller that never calls it hands the same dead token back forever. Until this,
// exactly one caller did — `ColonySync.drain` — so the account routes met `ApiError.SessionExpired`
// and read it as *nothing answered*: the amber card went up, the chrome line said **no network
// since 09:41** about a server that had just replied, and the retry re-sent the token that had been
// refused.
//
// **Once, and then it is not an expiry any more.** A second `SessionExpired` on a credential this
// server has just minted is not a clock disagreement, and it must not surface as `SessionExpired`
// either — that member means *ask again in a moment* and the moment has been had. One meaning
// reaches the shell: sign in again.
//
// **`drain` keeps its own copy of this and that is deliberate**, not an oversight to tidy away
// later: its renewal has to go back round *without spending a retry attempt* and has to carry the
// new token into the attempts that follow, neither of which a wrapper around a single call can
// express. What is shared is the rule, which is stated in both places and tested in both.
suspend fun <T> SessionKeeper.renewing(
    access: SessionToken,
    call: suspend (SessionToken) -> ApiResult<T>,
): ApiResult<T> {
    val first = call(access)
    if (first !is ApiResult.Refused || first.error != ApiError.SessionExpired) return first

    val renewed = when (val credential = renew()) {
        is Credential.Held -> credential.access

        // Nothing left to try and nothing to keep — the gate, said in the one word every caller
        // here already knows how to draw.
        Credential.Gone -> return ApiResult.Refused(ApiError.Unauthenticated)

        // **A renewal nobody answered is a train.** The session is on disk and good, so this is the
        // offline answer rather than a sign-out, exactly as `drain` reads it as `NotNow`.
        Credential.Unreachable -> return ApiResult.Unreachable
    }

    val second = call(renewed)
    return if (second is ApiResult.Refused && second.error == ApiError.SessionExpired) {
        ApiResult.Refused(ApiError.Unauthenticated)
    } else {
        second
    }
}
