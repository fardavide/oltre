package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.protocol.IdempotencyKey

// What founding a colony answered, and there are exactly two answers because founding is
// **idempotent**. A `POST /v1/colony` whose response is lost on a flaky connection gets retried, and
// a retry that minted a second galaxy would throw the first one away — which is the same failure
// `IdempotencyKey` exists to prevent one route over, arriving at the route that has no envelope to
// hang a key on.
//
// Both members carry the colony, so the caller never has to ask twice; they differ only in what the
// route says about it, which is `201 Created` against `200 OK`.
internal sealed interface Founding {

    val snapshot: GameSnapshot

    data class Founded(override val snapshot: GameSnapshot) : Founding

    data class AlreadyThere(override val snapshot: GameSnapshot) : Founding
}

// **Where a colony lives, stated as four questions rather than as a store.** The implementation here
// is a map; `#109`'s is three tables and a JDBC driver, and the shape below is chosen so that swap
// is a class rather than a redesign — every method is one indexed statement against
// `colonies (player_id, …)` or `applied_verbs (idempotency_key, player_id, …)`.
//
// Two of the four exist in the shape they do only because of what SQL will make of them:
//
// - `appliedAmong` asks *"which of these keys has this player already applied?"* rather than
//   handing back everything they ever applied. The table is append-only and prunable, so the second
//   would be an unbounded read on every sync; the first is one `WHERE idempotency_key IN (…)`.
// - `write` takes the colony **and** the keys, because the two have to land or fail together. A
//   snapshot written without its keys is a colony that will re-apply every verb in it on the next
//   retry, and keys written without the snapshot are verbs the player paid for and did not get.
//   One method is what lets `#109` make it one transaction; two would make the atomicity the
//   caller's problem and the caller cannot solve it.
internal interface ColonyRepository {

    // Stores `snapshot` as this player's colony if they have none. See `Founding`: a second call is
    // not an error and does not overwrite.
    suspend fun found(player: PlayerId, snapshot: GameSnapshot): Founding

    // Null is `ApiError.NoColony` and is not a failure — it is what a first launch of the online
    // build meets before the one-time upload.
    suspend fun colonyOf(player: PlayerId): GameSnapshot?

    suspend fun appliedAmong(player: PlayerId, keys: Set<IdempotencyKey>): Set<IdempotencyKey>

    suspend fun write(player: PlayerId, snapshot: GameSnapshot, applied: Set<IdempotencyKey>)
}
