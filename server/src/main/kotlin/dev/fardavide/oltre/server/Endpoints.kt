package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.Protocol
import dev.fardavide.oltre.protocol.SyncRequest
import dev.fardavide.oltre.protocol.SyncResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
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

    data class Failed(override val status: HttpStatusCode, val error: ApiError) : Answer
}

// **Found a colony, and mint the galaxy while doing it.** Idempotent — a retry after a lost response
// gets the colony that is already there rather than a second one. See `Founding`.
internal suspend fun foundColony(
    repository: ColonyRepository,
    clock: Clock,
    player: String?,
    body: String,
): Answer = served(repository, clock, player, body) { founder, now ->
    when (val founding = repository.found(founder, newColony(founder, now))) {
        is Founding.Founded -> FoundColony(HttpStatusCode.Created, founding.snapshot)
        is Founding.AlreadyThere -> FoundColony(HttpStatusCode.OK, founding.snapshot)
    }
}

// **The whole API.** Everything queued since the last sync goes up; the authoritative colony comes
// back, and what became of each verb comes back with it.
internal suspend fun syncColony(
    repository: ColonyRepository,
    clock: Clock,
    player: String?,
    body: String,
): Answer = served(repository, clock, player, body) { owner, _ ->
    repository.colonyOf(owner)?.let { FoundColony(HttpStatusCode.OK, it) }
}

// The colony an endpoint found and what the status line says about having found it. Null is
// `ApiError.NoColony`, which is not a failure: it is what a first launch of the online build meets
// before the one-time upload.
private data class FoundColony(val status: HttpStatusCode, val snapshot: GameSnapshot)

// **One shape for both endpoints**, because they differ in exactly one step. Admit the request, get
// the colony, replay what was queued against it, persist, answer.
private suspend fun served(
    repository: ColonyRepository,
    clock: Clock,
    player: String?,
    body: String,
    colonyOf: suspend (PlayerId, Instant) -> FoundColony?,
): Answer {
    val admitted = when (val answer = admit(player, body)) {
        is Admitted.No -> return answer.answer
        is Admitted.Yes -> answer
    }

    // **The one place an exception becomes an answer.** `ApiError.Internal` is in the taxonomy
    // because a client that gets an unreadable 500 cannot tell it from a proxy having eaten the
    // request, and everything below this line touches a store that `#109` will give a network. The
    // `detail` is a diagnostic and never player copy — every word the game says is a `TextRes` built
    // through `Strings`, and a server cannot build one.
    return try {
        val now = clock.now()
        val found = colonyOf(admitted.player, now)
            ?: return Answer.Failed(HttpStatusCode.NotFound, ApiError.NoColony)

        val queued = admitted.request.envelopes
        val replayed = replay(
            colony = found.snapshot,
            envelopes = queued,
            // Only the keys this request is asking about. The table is append-only and prunable, so
            // reading back everything a player ever applied would grow with the account.
            alreadyApplied = repository.appliedAmong(admitted.player, queued.map { it.idempotencyKey }.toSet()),
            serverNow = now,
        )
        // The colony and the keys land together or not at all — see `ColonyRepository`. Keys that
        // were already there are written again and the store treats that as the no-op it is.
        repository.write(admitted.player, replayed.snapshot, replayed.applied)

        Answer.Colony(
            status = found.status,
            response = SyncResponse(
                // What this build speaks, rather than an echo of what was asked for. A client that
                // reads a version beyond its own knows it is the one that is behind, which is only
                // useful if the server states its own position.
                apiVersion = ApiVersion.CURRENT,
                snapshot = replayed.snapshot,
                applied = replayed.applied,
                rejected = replayed.rejected,
            ),
        )
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

private fun admit(player: String?, body: String): Admitted {
    val asking = player
        ?.takeIf { it.isNotBlank() }
        ?.let(::PlayerId)
        ?: return refused(HttpStatusCode.Unauthorized, ApiError.Unauthenticated)

    // **Decoded here rather than by content negotiation, deliberately.** A body that does not parse
    // has a designed answer — `ApiError.Malformed`, which is `GameSave.decode`'s shape one layer out
    // — and routing it through the plugin would turn that answer into an exception somebody has to
    // catch and translate back. `SerializationException` is itself an `IllegalArgumentException`, so
    // the second arm is for the model's own guards: a blank `IdempotencyKey`, a coordinate off the
    // edge of the map, a manifest that breaks an `init`.
    val request = try {
        Protocol.json.decodeFromString<SyncRequest>(body)
    } catch (e: SerializationException) {
        return refused(HttpStatusCode.BadRequest, ApiError.Malformed(e.message.orEmpty()))
    } catch (e: IllegalArgumentException) {
        return refused(HttpStatusCode.BadRequest, ApiError.Malformed(e.message.orEmpty()))
    }

    // `ApiVersion` refuses nothing on construction, including zero, precisely so that this is a
    // negotiation rather than a parse failure — and the answer carries the window so the client can
    // tell *"update the app"* from *"this server is older than you are"*.
    if (!request.apiVersion.isServed()) {
        return refused(
            HttpStatusCode.UpgradeRequired,
            ApiError.UnsupportedApiVersion(oldestServed = ApiVersion.OLDEST_SERVED, current = ApiVersion.CURRENT),
        )
    }

    return Admitted.Yes(asking, request)
}

private fun refused(status: HttpStatusCode, error: ApiError): Admitted.No =
    Admitted.No(Answer.Failed(status, error))
