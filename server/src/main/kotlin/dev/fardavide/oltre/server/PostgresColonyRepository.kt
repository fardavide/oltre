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
        read = { rows -> if (rows.next()) colonyFrom(rows.getString(1), rows.getLong(2)) else null },
    )

    // The six values a colony write carries, bound once for both statements that write one. That is
    // why `INSERT_COLONY` names `player_id` **last** rather than first as the table does: the two
    // statements then share parameters one to six exactly, and the only thing the update adds is the
    // version it is asserting, at seven. Two hand-kept copies of a positional binding is how a
    // column ends up in the wrong parameter with nothing to say so — which is what the first run of
    // this file did.
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
        setString(6, player.value)
    }
}

// **How long a spent key is remembered.** Thirty days, and the number is chosen against the client
// rather than against the table: an idempotency key matters for exactly as long as some device might
// still be holding the verb in an outbox, and a phone that has been off for a month has been
// through `#112`'s outbox draining on every launch since. Wide enough that the honest answer to
// "could a retry still arrive?" is no; narrow enough that the table is bounded by active play.
internal val APPLIED_RETENTION: Duration = 30.days

// `player_id` last and not first, which is the table's order — see `bindColony` for why: it is what
// lets the insert and the compare-and-set share one binding.
private const val INSERT_COLONY = """
    INSERT INTO colonies (schema_version, last_updated_at, snapshot_json, version, updated_at, player_id)
    VALUES (?, ?, ?::jsonb, ?, ?, ?)
    ON CONFLICT (player_id) DO NOTHING
"""

private const val SELECT_COLONY = "SELECT snapshot_json, version FROM colonies WHERE player_id = ?"

// The compare-and-set. The trailing `AND version = ?` is the whole of it: it updates one row or no
// rows, and which of the two is the answer.
private const val UPDATE_COLONY = """
    UPDATE colonies
    SET schema_version = ?, last_updated_at = ?, snapshot_json = ?::jsonb, version = ?, updated_at = ?
    WHERE player_id = ? AND version = ?
"""

// The one parameter `bindColony` does not bind — the version being asserted, which only the update
// has. Named because a bare `7` beside a call that fills one to six is the thing that breaks when a
// column moves.
private const val EXPECTED_VERSION_PARAMETER = 7

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
