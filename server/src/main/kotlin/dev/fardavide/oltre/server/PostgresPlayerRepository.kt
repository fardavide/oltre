package dev.fardavide.oltre.server

import javax.sql.DataSource
import kotlin.time.Clock

// **The `players` table, and the third file in `:server` that holds nothing but connections and
// statements.** It is deliberately shaped like `PostgresColonyRepository`: no branch on an identity,
// no decision about a provider, nothing a unit test would want to reach. Who a token says somebody
// is, is `IdTokens.kt`; whether a session is still good is `Sessions.kt`; what happens when a player
// is not there is `Authenticator.kt`. All three are plain `…Test`s.
//
// **Deletion is one statement because the schema already said what it means.** `colonies.player_id`
// and `applied_verbs.player_id` are both `REFERENCES players (id) ON DELETE CASCADE`, so removing
// the row removes the colony and every spent key with it, inside one transaction, with no ordering
// for this code to get wrong. That is `#106` §5.4's three tables paying for themselves:
// `InMemoryPlayerRepository` has to write the cascade out by hand and this does not.
internal class PostgresPlayerRepository(
    private val dataSource: DataSource,
    private val clock: Clock,
    private val ids: PlayerIds = PlayerIds.RANDOM,
) : PlayerRepository {

    // **Insert-then-select rather than select-then-insert**, and the order is the whole of why this
    // is safe under two devices signing in at once. `ON CONFLICT (provider, subject) DO NOTHING`
    // makes the insert a no-op for the loser, and the select that follows is inside the same
    // transaction — so both callers come back with the *same* id, and the one whose insert did
    // nothing never learns it lost. Looked up first, both would miss and both would insert, and the
    // unique index would turn one of them into a 500 on a first sign-in.
    override suspend fun resolve(identity: ProviderIdentity): PlayerId = dataSource.transaction { connection ->
        connection.update(INSERT_PLAYER) {
            setString(1, ids.mint().value)
            setString(2, identity.provider.value)
            setString(3, identity.subject)
            setObject(4, clock.now().atUtc())
        }
        connection.query(
            SELECT_PLAYER,
            bind = {
                setString(1, identity.provider.value)
                setString(2, identity.subject)
            },
            read = { rows ->
                check(rows.next()) { "a player was inserted or already there, and is neither" }
                PlayerId(rows.getString(1))
            },
        )
    }

    override suspend fun exists(player: PlayerId): Boolean = dataSource.transaction { connection ->
        connection.query(
            SELECT_EXISTS,
            bind = { setString(1, player.value) },
            read = { rows -> rows.next() },
        )
    }

    override suspend fun forget(player: PlayerId): Boolean = dataSource.transaction { connection ->
        connection.update(DELETE_PLAYER) { setString(1, player.value) } == 1
    }
}

// The id is minted here and thrown away when the row already exists, which costs a UUID and buys the
// single-statement upsert above.
private const val INSERT_PLAYER = """
    INSERT INTO players (id, provider, subject, created_at)
    VALUES (?, ?, ?, ?)
    ON CONFLICT (provider, subject) DO NOTHING
"""

private const val SELECT_PLAYER = "SELECT id FROM players WHERE provider = ? AND subject = ?"

// `SELECT 1` and not `SELECT id`: the answer is whether a row came back, and asking the database for
// a column nobody reads is a column it has to fetch on every authenticated request.
private const val SELECT_EXISTS = "SELECT 1 FROM players WHERE id = ?"

// **The whole of account deletion**, and it is one line because `schema.sql` cascades. Actually
// delete rather than soft-delete — App Review 5.1.1(v) is explicit, and a flagged row is a row that
// still holds a provider subject.
private const val DELETE_PLAYER = "DELETE FROM players WHERE id = ?"
