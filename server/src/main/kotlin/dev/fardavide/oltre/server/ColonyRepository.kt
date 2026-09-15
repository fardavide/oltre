package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.AllianceMemberId
import dev.fardavide.oltre.protocol.IdempotencyKey

// **Which write a colony is a descendant of, and the only thing that makes two devices safe.**
//
// A sync reads a colony, replays what was queued against it and writes the result. Between those
// two moments another device can do the same, and without a token the second write silently throws
// the first one away — the failure `#106` §5.4 put a `version` column in the schema to prevent.
// So the read hands one back, the write asserts it, and a write that asserts a version the row no
// longer holds touches nothing and says so.
//
// A `Long` rather than a timestamp or a hash: it is a counter and nothing more, it is compared for
// equality and never for order, and `bigint` is what the column is.
@JvmInline
internal value class ColonyVersion(val value: Long) {

    fun next(): ColonyVersion = ColonyVersion(value + 1)

    companion object {

        // What founding writes. One rather than zero so that a version is never falsy in any
        // language the wire eventually reaches, and so an unset column and a founded colony cannot
        // read the same.
        val FIRST: ColonyVersion = ColonyVersion(1)
    }
}

// A colony and the version it was read at. The pair travels together because using one without the
// other is the bug: a snapshot with no version cannot be written back safely, and a version with no
// snapshot has nothing to replay against.
internal data class StoredColony(val snapshot: GameSnapshot, val version: ColonyVersion)

// **What a write did**, and it is two answers rather than a `Boolean` for the reason every other
// outcome in this module is a type: a `when` with no `else` at the call site cannot forget one.
internal enum class WriteResult {

    WRITTEN,

    // The compare-and-set lost — another device wrote between this caller's read and its write, or
    // there is no colony to assert a version against. Both mean the same thing to the caller: what
    // was replayed was replayed against a colony that is no longer the colony, so read again.
    STALE,
}

// What founding a colony answered, and there are exactly two answers because founding is
// **idempotent**. A `POST /v1/colony` whose response is lost on a flaky connection gets retried, and
// a retry that minted a second galaxy would throw the first one away — which is the same failure
// `IdempotencyKey` exists to prevent one route over, arriving at the route that has no envelope to
// hang a key on.
//
// Both members carry the colony **and its version**, so the caller never has to ask twice; they
// differ only in what the route says about it, which is `201 Created` against `200 OK`.
internal sealed interface Founding {

    val colony: StoredColony

    data class Founded(override val colony: StoredColony) : Founding

    data class AlreadyThere(override val colony: StoredColony) : Founding
}

// **Where a colony lives, stated as four questions rather than as a store.** The implementation here
// is a map; `PostgresColonyRepository` is three tables and a JDBC driver, and the shape below is
// chosen so that swap is a class rather than a redesign — every method is one indexed statement
// against `colonies (player_id, …)` or `applied_verbs (player_id, idempotency_key, …)`.
//
// Three of the four exist in the shape they do only because of what SQL makes of them:
//
// - `appliedAmong` asks *"which of these keys has this player already applied?"* rather than
//   handing back everything they ever applied. The table is append-only and prunable, so the second
//   would be an unbounded read on every sync; the first is one `WHERE idempotency_key = ANY (…)`.
// - `write` takes the colony **and** the keys, because the two have to land or fail together. A
//   snapshot written without its keys is a colony that will re-apply every verb in it on the next
//   retry, and keys written without the snapshot are verbs the player paid for and did not get.
//   One method is what lets the store make it one transaction; two would make the atomicity the
//   caller's problem and the caller cannot solve it.
// - `write` takes `expected` because a store cannot know what the caller read. `#108` landed this
//   interface with `ApiError.StaleColony` in the taxonomy and nothing able to produce it, and the
//   comment that said the swap was "a class rather than a redesign" was true of the other three
//   methods and not of this one: a compare-and-set needs the read to hand back a token, the write
//   to carry it, and the caller to do something when it loses. All three are `#109`'s.
internal interface ColonyRepository {

    // Stores `snapshot` as this player's colony if they have none. See `Founding`: a second call is
    // not an error and does not overwrite.
    suspend fun found(player: PlayerId, snapshot: GameSnapshot): Founding

    // Null is `ApiError.NoColony` and is not a failure — it is what a first launch of the online
    // build meets before the one-time upload.
    suspend fun colonyOf(player: PlayerId): StoredColony?

    suspend fun appliedAmong(player: PlayerId, keys: Set<IdempotencyKey>): Set<IdempotencyKey>

    // Replaces the colony **only if it is still at `expected`**, records the keys with it, and — if
    // there is one — credits the pool in the same transaction.
    // `WriteResult.STALE` is not an error: it is the caller being told to read and replay again,
    // which `Replay.kt` is a pure function so that it can.
    suspend fun write(
        player: PlayerId,
        snapshot: GameSnapshot,
        applied: Set<IdempotencyKey>,
        expected: ColonyVersion,
        credit: PoolCredit? = null,
    ): WriteResult
}

// **The first write in this game that touches two rows**, and it is here rather than on
// `AllianceRepository` because the atomicity is the whole reason it exists. `alliance-sheet.md` §1.2
// is right that writing a second row synchronously is what the colony's concurrency scheme was built
// not to do — **for colonies**. A colony is one document with exactly one writer and takes an
// optimistic compare-and-set; a pool is columns with every member writing, and
// `pool_metal = pool_metal + ?` is atomic under the row lock and needs no version at all.
//
// What they need is to land or fail together, and `write` is already the method that means that:
// *"`write` takes the colony **and** the keys, because the two have to land or fail together… One
// method is what lets the store make it one transaction; two would make the atomicity the caller's
// problem and the caller cannot solve it."* The credit is the third thing in that sentence. If the
// compare-and-set loses, the whole transaction rolls back and nothing was contributed — which is
// exactly right, because `STALE` means read and replay again.
//
// **The rejected alternative is the outbox shape** — a `pending_contributions` row written with the
// colony and drained when the alliance is next read. That is §1.2's own recommendation and it is
// right for a cross-*colony* write, where the receiver's row has another owner. Here both rows are
// in the same Postgres and one transaction covers them, so at-least-once delivery buys nothing and
// costs a window in which the resources have left the colony and have not arrived in the pool — a
// player watching two numbers that do not add up.
// **The second cross-row write goes the opposite way to this one**, and it is worth knowing about
// from here: founding an alliance charges a colony and inserts an alliance, and `AllianceRepository
// .found` owns that pair the way `write` owns this one — see `lockedColony` and `writeColony`, which
// are the two statements it borrows.
//
// The direction is chosen by which side holds the larger piece of logic rather than by symmetry. A
// pool credit is two `x = x + ?` statements, so it rides along with the colony; founding is three
// verdict checks, a reap, an occupancy re-read and two inserts, so the colony's read-and-write rides
// along with *it*. Moving the bigger half would have meant a second copy of `AllianceRules` living
// next to the colony's SQL, which is how the two ends of a rule drift apart.
internal data class PoolCredit(
    val alliance: AllianceId,
    // Which seat gets the standing. `alliance_members.contributed` is the column the succession rule
    // reads when it looks for the best active contributor, and until this slice it was zero for
    // everybody — so that step of the rule has had no signal to run on since the day it shipped.
    val member: AllianceMemberId,
    val amount: Resources,
    // Priced on the game's 1 : 2 : 3 by the caller, so the SQL adds a number rather than knowing a
    // ratio. See `AllianceBalance.award`.
    val experience: Long,
)
