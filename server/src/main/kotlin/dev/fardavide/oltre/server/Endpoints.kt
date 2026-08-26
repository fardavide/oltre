package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.Protocol
import dev.fardavide.oltre.protocol.SessionResponse
import dev.fardavide.oltre.protocol.SyncRequest
import dev.fardavide.oltre.protocol.SyncResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlin.time.Clock
import kotlin.time.Instant

// **What the two routes decide, with nothing that knows what a socket is.** A request arrives as the
// two things it actually carries — who it claims to be from and the text of its body — and leaves as
// a status and a payload. `OltreServer.kt` is then routing and nothing else.
//
// The split is the one the `test-coverage` skill asks for, and it is the same move
// `PlayerStripGeometry.kt` made in the other half of the app: **a decision belongs where the kind of
// test that judges it can reach it.** A route handler needs a test host, which by this repository's
// taxonomy makes any test of it an `…IntegrationTest`; every rule below is reachable by a plain
// `…Test` with a handwritten repository and a clock a test moves by hand. What the integration
// suite is then for is the wiring — the header's name, the codec, the status line — rather than the
// rules, which is what it can actually prove.
internal sealed interface Answer {

    val status: HttpStatusCode

    data class Colony(override val status: HttpStatusCode, val response: SyncResponse) : Answer

    // A signed session, from a sign-in or a refresh. Its own member rather than a second `Colony`
    // because `respond` picks its serializer from the **static** type of what it is handed — see
    // `OltreServer.send`, where that fact already costs two arms.
    data class Session(override val status: HttpStatusCode, val response: SessionResponse) : Answer

    // **An account that is gone, with nothing to say about it.** `204` and no body, which is the one
    // honest answer: there is no colony to hand back, no session to refresh and nothing left to
    // describe. The client's next screen is the sign-in one.
    data object Deleted : Answer {

        override val status: HttpStatusCode = HttpStatusCode.NoContent
    }

    // **Heard, and there was nothing to say back.** Apple's server-to-server notifications, which are
    // the one route on this server whose caller is not a player: Apple retries anything that is not a
    // `2xx`, and there is no payload it would read if there were one.
    //
    // Its own member rather than `Deleted` reused, even though the two put the same status on the
    // wire, because the names are what the route files are read through — a `consent-revoked` that
    // answered `Deleted` would say the opposite of what it did.
    data object Noted : Answer {

        override val status: HttpStatusCode = HttpStatusCode.NoContent
    }

    data class Failed(override val status: HttpStatusCode, val error: ApiError) : Answer
}

// **Found a colony, and mint the galaxy while doing it.** Idempotent — a retry after a lost response
// gets the colony that is already there rather than a second one. See `Founding`.
internal suspend fun foundColony(
    repository: ColonyRepository,
    authenticator: Authenticator,
    clock: Clock,
    credentials: Credentials,
    body: String,
): Answer = served(repository, authenticator, clock, credentials, body) { founder, now ->
    when (val founding = repository.found(founder, newColony(founder, now))) {
        is Founding.Founded -> FoundColony(HttpStatusCode.Created, founding.colony)
        is Founding.AlreadyThere -> FoundColony(HttpStatusCode.OK, founding.colony)
    }
}

// **The whole API.** Everything queued since the last sync goes up; the authoritative colony comes
// back, and what became of each verb comes back with it.
internal suspend fun syncColony(
    repository: ColonyRepository,
    authenticator: Authenticator,
    clock: Clock,
    credentials: Credentials,
    body: String,
): Answer = served(repository, authenticator, clock, credentials, body) { owner, _ ->
    repository.colonyOf(owner)?.let { FoundColony(HttpStatusCode.OK, it) }
}

// The colony an endpoint found and what the status line says about having found it. Null is
// `ApiError.NoColony`, which is not a failure: it is what a first launch of the online build meets
// before the one-time upload.
private data class FoundColony(val status: HttpStatusCode, val colony: StoredColony)

// **How many times one request will replay itself before giving up.** A sync that loses the
// compare-and-set has not failed — another device wrote the colony between this request's read and
// its write, so the work was done against a colony that is no longer the colony and the honest
// answer is to do it again against the one that won.
//
// Three, and the number is a judgement rather than a measurement: contention here is two of *one
// player's own devices* syncing in the same second, which is rare and never adversarial, so the
// second attempt is very nearly always the last one. What the bound is actually for is the case
// nobody plans for — a client in a loop, a retry storm after a deploy — where an unbounded retry
// turns one wedged colony into a wedged instance. Losing three times in a row is `ApiError
// .StaleColony`, and the client's answer to that is to sync again in a moment, which is the same
// work with the queue in front of it drained.
private const val WRITE_ATTEMPTS = 3

// **One shape for both endpoints**, because they differ in exactly one step. Admit the request, get
// the colony, replay what was queued against it, persist, answer.
private suspend fun served(
    repository: ColonyRepository,
    authenticator: Authenticator,
    clock: Clock,
    credentials: Credentials,
    body: String,
    colonyOf: suspend (PlayerId, Instant) -> FoundColony?,
): Answer {
    // **The one place an exception becomes an answer.** `ApiError.Internal` is in the taxonomy
    // because a client that gets an unreadable 500 cannot tell it from a proxy having eaten the
    // request, and everything below this line touches a store that is a network away. The
    // `detail` is a diagnostic and never player copy — every word the game says is a `TextRes` built
    // through `Strings`, and a server cannot build one.
    //
    // **`admit` is inside it from `#110`**, and that is a change rather than tidying: identifying the
    // caller now asks the store whether that player still exists, so the first thing a request does
    // is a query that can fail. Left outside, a database that had gone away would leave Ktor to
    // answer a bare 500 with no `ApiError` in it — which `#112`'s client reads as `Unreachable` and
    // retries forever rather than as a server having said something.
    return try {
        val admitted = when (val answer = admit(authenticator, credentials, body)) {
            is Admitted.No -> return answer.answer
            is Admitted.Yes -> answer
        }

        // **The whole attempt is inside the loop, and every line of it has to be.** A retry that
        // reused the colony it had already read would write the loser's work back over the winner's,
        // and a retry that reused the keys it had already read would apply a verb the other device
        // had just applied — a double-spend arriving through the mechanism built to prevent one. So
        // a lost compare-and-set discards everything and asks again.
        repeat(WRITE_ATTEMPTS) {
            val now = clock.now()
            val found = colonyOf(admitted.player, now)
                ?: return Answer.Failed(HttpStatusCode.NotFound, ApiError.NoColony)

            val queued = admitted.request.envelopes
            val replayed = replay(
                colony = found.colony.snapshot,
                envelopes = queued,
                // Only the keys this request is asking about. The table is append-only and prunable,
                // so reading back everything a player ever applied would grow with the account.
                alreadyApplied = repository.appliedAmong(admitted.player, queued.map { it.idempotencyKey }.toSet()),
                serverNow = now,
            )
            // The colony and the keys land together or not at all — see `ColonyRepository`. Keys
            // that were already there are written again and the store treats that as the no-op it
            // is. `expected` is what makes the pair a compare-and-set rather than a last-write-wins.
            val written = repository.write(
                player = admitted.player,
                snapshot = replayed.snapshot,
                applied = replayed.applied,
                expected = found.colony.version,
            )

            when (written) {
                WriteResult.WRITTEN -> return Answer.Colony(
                    status = found.status,
                    response = SyncResponse(
                        // What this build speaks, rather than an echo of what was asked for. A
                        // client that reads a version beyond its own knows it is the one that is
                        // behind, which is only useful if the server states its own position.
                        apiVersion = ApiVersion.CURRENT,
                        snapshot = replayed.snapshot,
                        applied = replayed.applied,
                        rejected = replayed.rejected,
                    ),
                )

                // Around again. `replay` is a pure function of `(colony, envelopes, serverNow)`
                // precisely so that this is a second computation on fresher input rather than a
                // repair of a half-finished one.
                WriteResult.STALE -> Unit
            }
        }

        // **Lost every time, and this is the only thing that produces `StaleColony`.** Nothing the
        // player queued was judged — the verbs are still in their outbox and the colony they have is
        // still the one they last saw — so there is nothing to say to them and nothing to undo. The
        // client syncs again.
        Answer.Failed(HttpStatusCode.Conflict, ApiError.StaleColony)
    } catch (e: CancellationException) {
        // **Not a failure and not ours.** A cancelled request is the caller having gone away — the
        // phone lost signal, the load balancer timed out — and swallowing it here would both answer
        // a socket nobody is holding and break the coroutine that is trying to unwind.
        throw e
    } catch (e: Exception) {
        Answer.Failed(HttpStatusCode.InternalServerError, ApiError.Internal(e.message ?: e::class.simpleName.orEmpty()))
    }
}

// What every request has to get past before a colony is loaded: who is asking, whether the body
// parses, and whether this build can answer that one. Three questions and three first-class answers
// in `ApiError`, which is what lets a client say *"sign in again"*, *"update the app"* and *"that
// did not make sense"* rather than the vaguest of the three to everybody.
private sealed interface Admitted {

    data class Yes(val player: PlayerId, val request: SyncRequest) : Admitted

    data class No(val answer: Answer.Failed) : Admitted
}

private suspend fun admit(authenticator: Authenticator, credentials: Credentials, body: String): Admitted {
    // **Who is asking is no longer a string somebody sent** — `#110`. What changed is only where the
    // answer comes from: a session token this server signed, or, where no session key is configured
    // at all, the header `#108` read. Both arrive as a `PlayerId` and nothing below this line can
    // tell which. See `Authenticator.kt`.
    val asking = when (val caller = authenticator.identify(credentials)) {
        is Caller.Refused -> return refused(HttpStatusCode.Unauthorized, caller.error)
        is Caller.Known -> caller.player
    }

    val request = when (val read = readRequest(SyncRequest.serializer(), body) { it.apiVersion }) {
        is Read.No -> return Admitted.No(read.answer)
        is Read.Yes -> read.value
    }

    return Admitted.Yes(asking, request)
}

private fun refused(status: HttpStatusCode, error: ApiError): Admitted.No =
    Admitted.No(Answer.Failed(status, error))

// **Every request body this server reads, and the two ways one can be refused before anybody looks
// at what it says.** Shared by the sync pair and by `AuthEndpoints.kt` rather than written three
// times, because a sign-in that answered a malformed body differently from a sync would be one more
// thing for a client to special-case.
internal sealed interface Read<out T> {

    data class Yes<T>(val value: T) : Read<T>

    data class No(val answer: Answer.Failed) : Read<Nothing>
}

// **Decoded here rather than by content negotiation, deliberately.** A body that does not parse has
// a designed answer — `ApiError.Malformed`, which is `GameSave.decode`'s shape one layer out — and
// routing it through the plugin would turn that answer into an exception somebody has to catch and
// translate back. `SerializationException` is itself an `IllegalArgumentException`, so the second arm
// is for the model's own guards: a blank `IdempotencyKey`, a blank `IdToken`, a coordinate off the
// edge of the map, a manifest that breaks an `init`.
internal fun <T> readRequest(
    serializer: DeserializationStrategy<T>,
    body: String,
    versionOf: (T) -> ApiVersion,
): Read<T> {
    val request = try {
        Protocol.json.decodeFromString(serializer, body)
    } catch (e: SerializationException) {
        return Read.No(Answer.Failed(HttpStatusCode.BadRequest, ApiError.Malformed(e.message.orEmpty())))
    } catch (e: IllegalArgumentException) {
        return Read.No(Answer.Failed(HttpStatusCode.BadRequest, ApiError.Malformed(e.message.orEmpty())))
    }

    // `ApiVersion` refuses nothing on construction, including zero, precisely so that this is a
    // negotiation rather than a parse failure — and the answer carries the window so the client can
    // tell *"update the app"* from *"this server is older than you are"*.
    if (!versionOf(request).isServed()) {
        return Read.No(
            Answer.Failed(
                HttpStatusCode.UpgradeRequired,
                ApiError.UnsupportedApiVersion(oldestServed = ApiVersion.OLDEST_SERVED, current = ApiVersion.CURRENT),
            ),
        )
    }

    return Read.Yes(request)
}
