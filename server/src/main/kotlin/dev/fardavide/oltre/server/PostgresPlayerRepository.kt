package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.CommanderName
import dev.fardavide.oltre.protocol.PlayerMark
import dev.fardavide.oltre.protocol.PlayerProfile
import dev.fardavide.oltre.protocol.Protocol
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

    // The second half of `resolve`'s statement pair, on its own and without the insert in front of
    // it. Null is "nobody has ever signed in as that", which is a real answer here rather than a
    // failure — see `PlayerRepository.find`.
    override suspend fun find(identity: ProviderIdentity): PlayerId? = dataSource.transaction { connection ->
        connection.query(
            SELECT_PLAYER,
            bind = {
                setString(1, identity.provider.value)
                setString(2, identity.subject)
            },
            read = { rows -> if (rows.next()) PlayerId(rows.getString(1)) else null },
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

    // **No row is no player, and both columns null is a player who has chosen nothing.** The two are
    // different answers and the caller acts differently on each — `Unauthenticated` against a strip
    // that goes on saying `Dead Reckoning` — so the null-ness of the *row* and the null-ness of the
    // *columns* are read separately rather than collapsed into one nullable name.
    override suspend fun profileOf(player: PlayerId): PlayerProfile? = dataSource.transaction { connection ->
        connection.query(
            SELECT_PROFILE,
            bind = { setString(1, player.value) },
            read = { rows -> if (rows.next()) profileFrom(rows.getString(1), rows.getString(2)) else null },
        )
    }

    override suspend fun setProfile(player: PlayerId, profile: PlayerProfile): Boolean =
        dataSource.transaction { connection ->
            connection.update(UPDATE_PROFILE) {
                // `setString` with null writes SQL NULL, which is what clearing is. Not `setNull`,
                // which would need the type code spelled out for nothing.
                setString(1, profile.name?.value)
                // `setObject` with the `jsonb` cast in the statement rather than `setString`: the
                // driver will not widen `text` to `jsonb` on its own, and the cast is in the SQL so
                // that a null still types.
                setString(2, profile.mark?.let { Protocol.json.encodeToString(PlayerMark.serializer(), it) })
                setString(3, player.value)
            } == 1
        }
}

// **Neither column can throw on the way out, and that is one rule rather than two.** A value written
// by a *newer* deploy is a state the service can genuinely be in — a rollback is one command and
// `#111` exercised one — and a read path that raised on it would turn a routine downgrade into
// `ApiError.Internal` for every request that account makes, including the sync. Degrading is the
// only answer that leaves the player with a game.
//
// It also catches the row an operator edited by hand, which is the other way columns like these go
// wrong and the reason the `catch` is on `Exception` rather than on the serializer's own type.
//
// **What the player sees when it degrades**, which is the half worth writing down: exactly what an
// account that has chosen nothing sees. A mark that will not read draws `THRESHOLD` and a name that
// will not read reads `Dead Reckoning` — the strip's own substitution for null, not a placeholder
// invented here — and the next save from the editor writes a pair this build can hold. Nothing on
// screen says *error*, because from the player's side nothing has gone wrong that they can act on.
//
// **The name half was missing and the argument above applied to it unchanged.** `CommanderName`'s
// guards are what a *request* is checked against — `readRequest` turns them into `ApiError.Malformed`
// — but `display_name` is a `text` column holding whatever any deploy ever wrote, and a bound that
// moves is exactly the rollback this comment was written for.
private fun profileFrom(name: String?, mark: String?): PlayerProfile = PlayerProfile(
    name = name?.let { held -> runCatching { CommanderName(held) }.getOrNull() },
    mark = mark?.let { document ->
        runCatching { Protocol.json.decodeFromString(PlayerMark.serializer(), document) }.getOrNull()
    },
)

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

private const val SELECT_PROFILE = "SELECT display_name, mark FROM players WHERE id = ?"

// Replaces both columns every time — see `PlayerRepository.setProfile` for why a merge would make
// `null` mean two things. `= 1` is "there was a row", which is the same shape `forget` reads.
//
// The `::jsonb` cast is the driver's requirement rather than decoration: a parameter bound as a
// string is `text`, and Postgres will not widen `text` to `jsonb` implicitly.
private const val UPDATE_PROFILE = "UPDATE players SET display_name = ?, mark = ?::jsonb WHERE id = ?"
