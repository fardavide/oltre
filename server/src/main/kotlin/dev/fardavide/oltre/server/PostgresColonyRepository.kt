package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.GameSave
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.protocol.IdempotencyKey
import java.sql.Connection
import java.sql.PreparedStatement
import javax.sql.DataSource
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

// **The colony, in three tables.** `InMemoryColonyRepository` answers the same four questions from a
// map and stays because it is what the unit tests run against and what `./gradlew :server:run`
// serves with no database; this is what a deployed server holds, and the pair answering identically
// is what makes the unit suite above it worth anything.
//
// **The atomicity is the database's and could not be Kotlin's.** A `Mutex` is enough for one
// process; Cloud Run runs several, sharing nothing but this. So the compare-and-set is a `WHERE
// version = ?` that updates no row when it loses, and the colony and its spent keys land inside one
// transaction or not at all.
//
// This file and `PostgresDatabase.kt` hold every line that needs a connection to run and no line
// that decides anything — see the note there.
internal class PostgresColonyRepository(
    private val dataSource: DataSource,
    private val clock: Clock,
) : ColonyRepository {

    // **The player row is not written here any more** — `#110`. `#109` forged one from the header
    // value because there was nothing else to hang the foreign key off; now `PostgresPlayerRepository
    // .resolve` writes it at sign-in, which is the only moment anybody has actually said who they
    // are. What guarantees it is there by the time this runs is the authenticator: every request
    // that reaches a route has already had its player looked up, and one that names somebody the
    // table does not hold is `ApiError.Unauthenticated` long before here.
    override suspend fun found(player: PlayerId, snapshot: GameSnapshot): Founding =
        dataSource.transaction { connection ->
            val now = clock.now()
            // **`ON CONFLICT DO NOTHING` is what makes founding idempotent**, and the row count is
            // what tells the two apart: one row inserted is a colony that did not exist, zero is a
            // retry after a lost response. Both then read the row back, so the caller gets the
            // colony either way and the routes differ only in `201` against `200`.
            val founded = connection.update(INSERT_COLONY) {
                bindColony(snapshot, version = ColonyVersion.FIRST, now = now, player = player)
            } == 1
            val stored = connection.selectColony(player)
                ?: error("a colony was inserted or already there, and is neither")
            if (founded) Founding.Founded(stored) else Founding.AlreadyThere(stored)
        }

    override suspend fun colonyOf(player: PlayerId): StoredColony? =
        dataSource.transaction { connection -> connection.selectColony(player) }

    override suspend fun appliedAmong(player: PlayerId, keys: Set<IdempotencyKey>): Set<IdempotencyKey> {
        // The common sync carries an empty outbox — the app was opened, nothing was tapped — and
        // asking the database which of no keys it has seen is a round trip to Neon for an answer
        // that is known here.
        if (keys.isEmpty()) return emptySet()
        return dataSource.transaction { connection ->
            connection.query(
                SELECT_APPLIED,
                bind = {
                    setString(1, player.value)
                    setArray(2, connection.createArrayOf("text", keys.map { it.value }.toTypedArray()))
                },
                read = { rows ->
                    buildSet {
                        while (rows.next()) add(IdempotencyKey(rows.getString(1)))
                    }
                },
            )
        }
    }

    override suspend fun write(
        player: PlayerId,
        snapshot: GameSnapshot,
        applied: Set<IdempotencyKey>,
        expected: ColonyVersion,
        credit: PoolCredit?,
    ): WriteResult = dataSource.transaction { connection ->
        val now = clock.now()
        val updated = connection.update(UPDATE_COLONY) {
            bindColony(snapshot, version = expected.next(), now = now, player = player)
            setLong(EXPECTED_VERSION_PARAMETER, expected.value)
        }
        // **Nothing updated is the compare-and-set losing**, and it covers both ways of losing with
        // one comparison: another device moved the version on, or there is no colony to move. The
        // keys below are inside the same transaction and so are not written either — a verb the
        // player paid for and did not get is the one outcome worse than a retry.
        if (updated == 0) return@transaction WriteResult.STALE

        for (key in applied) {
            connection.update(INSERT_APPLIED) {
                setString(1, key.value)
                setString(2, player.value)
                setObject(3, now.atUtc())
            }
        }

        // **The cross-row write, and the whole reason it is here rather than on the alliance store.**
        // It is inside this transaction, after the compare-and-set and after the keys, so all three
        // land or none of them do. A lost CAS returned above without reaching this line, which is
        // exactly right: `STALE` means nothing was contributed and the caller replays.
        //
        // Two `UPDATE … SET x = x + ?` statements and no version on either. The colony keeps its
        // optimistic token because it is one document with one writer; the alliance rows take an
        // in-database increment because every member writes them, and an increment is atomic under
        // the row lock. `#144` §6 is the argument; the property that makes it safe is that
        // `applied_verbs` is written in the same transaction, so a retried envelope cannot credit
        // twice — provided the credit is computed from what was applied *now*, which is `Replayed
        // .contributed`'s job and is stated there.
        credit?.let { paid ->
            connection.update(CREDIT_POOL) {
                setLong(1, paid.amount.metal)
                setLong(2, paid.amount.crystal)
                setLong(3, paid.amount.deuterium)
                setLong(4, paid.experience)
                setString(5, paid.alliance.value)
            }
            connection.update(CREDIT_MEMBER) {
                setLong(1, paid.experience)
                setString(2, paid.member.value)
            }
        }
        WriteResult.WRITTEN
    }

    // **Sweeping `applied_verbs`, which is the one table with no ceiling.** A key is only ever asked
    // about by a client still retrying the verb that minted it, so a row older than any plausible
    // retry protects nothing and is paid for on every backup.
    //
    // Not on `ColonyRepository`: the interface is the four questions a request asks, and this is
    // maintenance the process does between them. `Main.kt` is what calls it.
    suspend fun prune(before: Instant): Int = dataSource.transaction { connection ->
        connection.update(PRUNE_APPLIED) { setObject(1, before.atUtc()) }
    }

    private fun Connection.selectColony(player: PlayerId): StoredColony? = query(
        SELECT_COLONY,
        bind = { setString(1, player.value) },
        // **`getObject` and not `getLong` on the third column**, because it is nullable and
        // `getLong` answers 0 for SQL null — which is a real alliance level rather than an absence,
        // and would hand every colony in no alliance the boon of a level-zero one. That is the same
        // figure by luck today (level 0 takes nothing off) and would stop being so the day the
        // opening level did anything.
        read = { rows ->
            if (rows.next()) {
                colonyFrom(rows.getString(1), rows.getLong(2), (rows.getObject(3) as? Number)?.toLong())
            } else {
                null
            }
        },
    )

    // The seven values a colony write carries, bound once for both statements that write one. That is
    // why `INSERT_COLONY` names `player_id` **last** rather than first as the table does: the two
    // statements then share parameters one to seven exactly, and the only thing the update adds is the
    // version it is asserting, at eight. Two hand-kept copies of a positional binding is how a
    // column ends up in the wrong parameter with nothing to say so — which is what the first run of
    // this file did.
}

private fun PreparedStatement.bindColony(
    snapshot: GameSnapshot,
    version: ColonyVersion,
    now: Instant,
    player: PlayerId,
) {
    setInt(1, snapshot.schemaVersion)
    setObject(2, snapshot.lastUpdatedAt.atUtc())
    // `GameSave.encode` verbatim, which is the whole of `#106` §5.4: the save format already
    // lives in `core` because client and server must agree on it byte for byte, so there is
    // nothing here to map and nothing to keep in step.
    setString(3, GameSave.encode(snapshot))
    setLong(4, version.value)
    setObject(5, now.atUtc())
    setLong(6, snapshot.state.experience.points)
    setString(7, player.value)
}

// ── Reading and writing a colony inside somebody else's transaction ─────────────────────────────
//
// **Founding an alliance charges the colony and inserts an alliance, and those have to land or fail
// together** — so `PostgresAllianceRepository.found` runs both as statements on its own connection
// rather than calling `write`, which would open a second one and give the pair no atomicity at all.
//
// The two functions are **here** rather than there, beside the SQL and the binding they share a
// parameter order with, which is the same reason `bindColony` exists at all: two hand-kept copies of
// a positional binding is how a column ends up in the wrong parameter with nothing to say so.

// `SELECT … FOR UPDATE`, and the lock is what makes founding need no retry loop.
//
// **The contrast with `write` is worth stating, because they answer the same race two ways.** A sync
// reads a colony, spends real time replaying against it and writes it back, so holding a row lock for
// that whole span would serialise two devices behind each other — it takes an optimistic
// compare-and-set instead and tells a loser to read again. Founding reads, subtracts a fixed price
// and writes, all inside one transaction with no client round trip in the middle, so the pessimistic
// lock is both cheap and complete: a concurrent sync blocks until the commit and then loses its own
// compare-and-set, which is exactly the answer it already knows how to handle.
internal fun Connection.lockedColony(player: PlayerId): StoredColony? = query(
    LOCK_COLONY,
    bind = { setString(1, player.value) },
    // Nullable third column, read as an object for `selectColony`'s reason.
    read = { rows ->
        if (rows.next()) {
            colonyFrom(rows.getString(1), rows.getLong(2), (rows.getObject(3) as? Number)?.toLong())
        } else {
            null
        }
    },
)

// The same compare-and-set `write` uses, on a connection somebody else opened. Under `lockedColony`
// it cannot lose, and it asserts the version anyway: the assertion is what bumps it, and a write that
// did not would leave every reader's token valid for ever.
internal fun Connection.writeColony(
    player: PlayerId,
    snapshot: GameSnapshot,
    expected: ColonyVersion,
    now: Instant,
): Boolean = update(UPDATE_COLONY) {
    bindColony(snapshot, version = expected.next(), now = now, player = player)
    setLong(EXPECTED_VERSION_PARAMETER, expected.value)
} == 1

// **How long a spent key is remembered.** Thirty days, and the number is chosen against the client
// rather than against the table: an idempotency key matters for exactly as long as some device might
// still be holding the verb in an outbox, and a phone that has been off for a month has been
// through `#112`'s outbox draining on every launch since. Wide enough that the honest answer to
// "could a retry still arrive?" is no; narrow enough that the table is bounded by active play.
internal val APPLIED_RETENTION: Duration = 30.days

// `player_id` last and not first, which is the table's order — see `bindColony` for why: it is what
// lets the insert and the compare-and-set share one binding.
private const val INSERT_COLONY = """
    INSERT INTO colonies (schema_version, last_updated_at, snapshot_json, version, updated_at, experience, player_id)
    VALUES (?, ?, ?::jsonb, ?, ?, ?, ?)
    ON CONFLICT (player_id) DO NOTHING
"""

// **Two left joins rather than a second query**, and the sync route's own comment is the reason:
// it reads a membership only when a request carries a `Contribute`, because *"an unconditional
// membership lookup would put a second query on the hot path of the one route every check-in
// calls"*. The alliance boon needs the level on every sync, so asking for it separately would be
// exactly the round trip that guard exists to avoid. Joined, it is free — `alliance_members` is keyed
// by `player_id`, so this is a primary-key lookup and the row count cannot change.
//
// `LEFT`, because nineteen colonies in twenty are in no alliance and an inner join would answer
// *this player has no colony* for every one of them — which founds a second galaxy over the top of
// the first. See `colonyFrom`.
private const val SELECT_COLONY = """
    SELECT c.snapshot_json, c.version, a.experience
    FROM colonies c
    LEFT JOIN alliance_members m ON m.player_id = c.player_id
    LEFT JOIN alliances a ON a.id = m.alliance_id
    WHERE c.player_id = ?
"""

// The same read, holding the row until the transaction ends. See `lockedColony`.
//
// **`FOR UPDATE OF c`, not a bare `FOR UPDATE`.** With the joins above, an unqualified `FOR UPDATE`
// locks a row in *every* table the select touched — so founding a colony would take a row lock on
// the whole alliance and on the member row, and two members of one alliance founding at the same
// moment would serialise behind each other for no reason. Naming the table keeps the lock exactly
// where the pessimistic-locking argument below put it: on the colony being written.
private const val LOCK_COLONY = """
    SELECT c.snapshot_json, c.version, a.experience
    FROM colonies c
    LEFT JOIN alliance_members m ON m.player_id = c.player_id
    LEFT JOIN alliances a ON a.id = m.alliance_id
    WHERE c.player_id = ?
    FOR UPDATE OF c
"""

// The compare-and-set. The trailing `AND version = ?` is the whole of it: it updates one row or no
// rows, and which of the two is the answer.
private const val UPDATE_COLONY = """
    UPDATE colonies
    SET schema_version = ?, last_updated_at = ?, snapshot_json = ?::jsonb, version = ?, updated_at = ?, experience = ?
    WHERE player_id = ? AND version = ?
"""

// The one parameter `bindColony` does not bind — the version being asserted, which only the update
// has. Named because a bare `8` beside a call that fills one to seven is the thing that breaks when a
// column moves.
private const val EXPECTED_VERSION_PARAMETER = 8

// `ON CONFLICT DO NOTHING` because a key that was already recorded is being recorded again on
// purpose: `replay` reports a key it found already spent as applied, and the write that follows
// carries it. That is the truth rather than a placation, and the store treats it as the no-op it is.
private const val INSERT_APPLIED = """
    INSERT INTO applied_verbs (idempotency_key, player_id, applied_at)
    VALUES (?, ?, ?)
    ON CONFLICT (player_id, idempotency_key) DO NOTHING
"""

// `= ANY (?)` and not an `IN` list built by hand, which is what makes this one prepared statement
// however many keys a sync carries — an `IN (?, ?, ?)` is a different statement per length, so the
// database plans it again every time the outbox is a different size.
private const val SELECT_APPLIED =
    "SELECT idempotency_key FROM applied_verbs WHERE player_id = ? AND idempotency_key = ANY (?)"

private const val PRUNE_APPLIED = "DELETE FROM applied_verbs WHERE applied_at < ?"

// **The credit, and `version = version + 1` is doing real work in it.** The alliance row needs no
// compare-and-set of its own — the increments are atomic — but every other alliance act *does* take
// one, so a pool that moved without moving the version would let a rename or a project purchase
// assert a version it read before the contribution and win.
private const val CREDIT_POOL = """
    UPDATE alliances
    SET pool_metal = pool_metal + ?,
        pool_crystal = pool_crystal + ?,
        pool_deuterium = pool_deuterium + ?,
        experience = experience + ?,
        version = version + 1
    WHERE id = ?
"""

// The member's own standing, priced on the same 1 : 2 : 3 as the alliance's experience. This is the
// column `AllianceRules.successor` reads when it looks for the best active contributor — and until
// this statement existed it was zero for everybody, so that step of the succession rule had no
// signal to run on at all.
private const val CREDIT_MEMBER = "UPDATE alliance_members SET contributed = contributed + ? WHERE id = ?"
