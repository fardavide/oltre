package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.IdempotencyKey
import io.zonky.test.db.postgres.junit.SingleInstancePostgresRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.ClassRule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// **The same three questions `InMemoryPlayerRepositoryTest` asks two maps, asked of the table.** The
// pair is the point, exactly as it is for the colony store one file over: the unit suite stands on
// the in-memory half, and a fake that answers differently from the store it doubles makes every test
// above it a lie.
//
// Where this file has more is where SQL can be wrong in ways a `mutableMapOf` cannot — a conflict
// clause, a unique index, and above all **the two `ON DELETE CASCADE`s**, which are the whole of
// account deletion and which no unit test can prove because in memory they are written out by hand.
class PostgresPlayerRepositoryIntegrationTest {

    private val database = postgres.embeddedPostgres.postgresDatabase
    private val clock = MovableClock(TEST_NOW)
    private val ids = sequentialPlayerIds()
    private val players = PostgresPlayerRepository(database, clock, ids)
    private val colonies = PostgresColonyRepository(database, clock)
    private val mine = ProviderIdentity(ProviderName("google"), "subject-a")
    private val theirs = ProviderIdentity(ProviderName("google"), "subject-b")

    @BeforeTest
    fun anEmptyIdentityStore() {
        database.applySchema()
        database.emptyEveryTable()
    }

    // ── Resolving ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `an identity nobody has seen becomes a row`() = runTest {
        val player = players.resolve(mine)

        assertEquals(PlayerId("player-1"), player)
        assertEquals(listOf("google" to "subject-a"), database.playerIdentities())
    }

    @Test
    fun `the same identity comes back to the same row rather than a second one`() = runTest {
        // What makes a second sign-in find the first colony, and it is `ON CONFLICT (provider,
        // subject) DO NOTHING` plus the select that follows doing it.
        val first = players.resolve(mine)

        assertEquals(first, players.resolve(mine))
        assertEquals(1, database.rowsIn("players"))
    }

    @Test
    fun `a different subject is a different row`() = runTest {
        assertNotEquals(players.resolve(mine), players.resolve(theirs))
        assertEquals(2, database.rowsIn("players"))
    }

    @Test
    fun `the same subject at a different provider is a different player`() = runTest {
        // The unique index is on the pair and not on the subject alone, because nothing about a
        // subject is globally unique — Apple and Google can both mint `1234` and mean two people.
        val google = players.resolve(ProviderIdentity(ProviderName("google"), "1234"))
        val apple = players.resolve(ProviderIdentity(ProviderName("apple"), "1234"))

        assertNotEquals(google, apple)
    }

    @Test
    fun `two devices signing in at once resolve to one player and not two`() = runTest {
        // **The reason `resolve` inserts before it selects.** Looked up first, both callers would
        // miss, both would insert, and the unique index would turn one of them into a 500 on
        // somebody's very first sign-in. Which of the two wins the insert is a race; that exactly
        // one row exists afterwards is not.
        val resolved = withContext(Dispatchers.IO) {
            (1..2).map { async { players.resolve(mine) } }.awaitAll()
        }

        assertEquals(1, resolved.distinct().size, "two devices opened two accounts: $resolved")
        assertEquals(1, database.rowsIn("players"))
    }

    // ── Finding ───────────────────────────────────────────────────────────────────────────────

    // **`find` is `resolve` with the insert taken away, and that is exactly what has to be proved
    // here.** `#111` added it for Apple's server-to-server notifications, where the caller is Apple
    // and the answer *"nobody"* is a real one — a notification about a subject that never signed in
    // here must not mint a row for it on the way to deleting it. Sharing the `SELECT` with `resolve`
    // is what makes that safe and is also how it could go wrong.
    @Test
    fun `finding an identity that signed in gives the row it made`() = runTest {
        val player = players.resolve(mine)

        assertEquals(player, players.find(mine))
    }

    @Test
    fun `finding an identity nobody has seen writes nothing`() = runTest {
        assertNull(players.find(mine))
        assertEquals(0, database.rowsIn("players"))
    }

    // The pair is provider-scoped, so the same subject under another provider is another person —
    // Apple and Google can both mint `1234` and mean two different people. A `find` that looked at
    // the subject alone would hand Apple somebody else's colony to delete.
    @Test
    fun `finding is scoped to the provider as well as the subject`() = runTest {
        players.resolve(mine)

        assertNull(players.find(ProviderIdentity(ProviderName("apple"), "subject-a")))
    }

    // ── Existing ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a player who signed in exists and one who never did does not`() = runTest {
        val player = players.resolve(mine)

        assertTrue(players.exists(player))
        assertFalse(players.exists(PlayerId("never-signed-in")))
    }

    // ── Deleting ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `deleting a player takes the colony and the spent keys with it`() = runTest {
        // **The cascade, which is the whole of account deletion and the one thing only a database can
        // prove.** App Review 5.1.1(v) asks for deletion rather than a flag, and `schema.sql` gets it
        // from two foreign keys — so this is the test that says those clauses are really there.
        val player = players.resolve(mine)
        val key = IdempotencyKey("spent")
        val founded = colonies.found(player, freshColony()).colony
        colonies.write(player, freshColony(), applied = setOf(key), expected = founded.version)

        assertTrue(players.forget(player))

        assertEquals(0, database.rowsIn("players"))
        assertEquals(0, database.rowsIn("colonies"))
        assertEquals(0, database.rowsIn("applied_verbs"))
        assertNull(colonies.colonyOf(player))
        assertEquals(emptySet(), colonies.appliedAmong(player, setOf(key)))
    }

    @Test
    fun `deleting somebody who is not there is done rather than an error`() = runTest {
        // A client that lost the response to its first attempt sends a second, and telling it that
        // its account does not exist is telling it the thing it asked for.
        assertFalse(players.forget(PlayerId("never-signed-in")))
    }

    @Test
    fun `one player's deletion leaves another's colony exactly where it was`() = runTest {
        val player = players.resolve(mine)
        val other = players.resolve(theirs)
        colonies.found(player, freshColony(seed = 1))
        colonies.found(other, freshColony(seed = 2))

        players.forget(player)

        assertNull(colonies.colonyOf(player))
        assertEquals(freshColony(seed = 2), colonies.colonyOf(other)?.snapshot)
    }

    @Test
    fun `signing in again after a deletion founds a fresh colony rather than resurrecting the old one`() = runTest {
        // **The property `#110`'s Done-means ends on**, proved against the tables rather than a map.
        // The surrogate `players.id` is what buys it: the same Apple subject comes back to a *new*
        // id, with nothing hanging off it and no way to reach what was there.
        val before = players.resolve(mine)
        val old = colonies.found(before, freshColony(seed = 1)).colony.snapshot
        players.forget(before)

        val after = players.resolve(mine)
        val fresh = colonies.found(after, freshColony(seed = 2)).colony.snapshot

        assertNotEquals(before, after)
        assertNotEquals(old.state.galaxy.seed, fresh.state.galaxy.seed)
        assertEquals(1, database.rowsIn("colonies"))
    }

    private companion object {

        @get:ClassRule
        @JvmStatic
        val postgres: SingleInstancePostgresRule = embeddedPostgres()
    }
}
