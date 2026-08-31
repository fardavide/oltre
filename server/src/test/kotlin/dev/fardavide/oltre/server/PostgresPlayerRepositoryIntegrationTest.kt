package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.CommanderName
import dev.fardavide.oltre.protocol.IdempotencyKey
import dev.fardavide.oltre.protocol.MarkBody
import dev.fardavide.oltre.protocol.MarkPath
import dev.fardavide.oltre.protocol.MarkPreset
import dev.fardavide.oltre.protocol.MarkTerminus
import dev.fardavide.oltre.protocol.PlayerMark
import dev.fardavide.oltre.protocol.PlayerProfile
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

    // ── The profile ───────────────────────────────────────────────────────────────────────────

    // The two columns `ALTER TABLE … ADD COLUMN IF NOT EXISTS` puts on `players`. What this half of
    // the pair can be wrong about that a map cannot: whether the columns are there at all after the
    // schema is applied to a database that already had the table.
    @Test
    fun `a row that has never been written to reads two absences`() = runTest {
        val player = players.resolve(mine)

        assertEquals(PlayerProfile(name = null, mark = null), players.profileOf(player))
    }

    @Test
    fun `no row is no profile rather than an empty one`() = runTest {
        assertNull(players.profileOf(PlayerId("never-signed-in")))
        assertFalse(players.setProfile(PlayerId("never-signed-in"), chosen))
    }

    @Test
    fun `what was written to the columns is what comes back out of them`() = runTest {
        val player = players.resolve(mine)

        assertTrue(players.setProfile(player, chosen))

        assertEquals(chosen, players.profileOf(player))
        assertEquals("Ada di Notte", database.scalar("SELECT display_name FROM players"))
        // Read back through Postgres's own `jsonb` accessors rather than as text, which is the point
        // of the column type: the document is a value the database understands, not a string it
        // stores. Text would compare against whatever key order the driver happened to emit.
        assertEquals("Composed", database.scalar("SELECT mark ->> 'type' FROM players"))
        assertEquals("WAKE", database.scalar("SELECT mark ->> 'body' FROM players"))
        assertEquals("TWIN", database.scalar("SELECT mark ->> 'path' FROM players"))
        assertEquals("RING", database.scalar("SELECT mark ->> 'terminus' FROM players"))
    }

    @Test
    fun `a preset writes the preset and not a composition`() = runTest {
        val player = players.resolve(mine)
        val preset = PlayerProfile(name = null, mark = PlayerMark.Preset(MarkPreset.SOUNDING))

        players.setProfile(player, preset)

        assertEquals(preset, players.profileOf(player))
        assertEquals("Preset", database.scalar("SELECT mark ->> 'type' FROM players"))
        assertEquals("SOUNDING", database.scalar("SELECT mark ->> 'preset' FROM players"))
    }

    @Test
    fun `clearing writes SQL NULL rather than an empty string`() = runTest {
        val player = players.resolve(mine)
        players.setProfile(player, chosen)

        players.setProfile(player, PlayerProfile(name = null, mark = null))

        // An empty string would decode as a blank `CommanderName`, which the contract refuses on
        // construction — so the row would be unreadable rather than merely wrong.
        assertNull(database.scalar("SELECT display_name FROM players"))
        assertEquals(PlayerProfile(name = null, mark = null), players.profileOf(player))
    }

    @Test
    fun `one player's columns are not another's`() = runTest {
        val player = players.resolve(mine)
        val other = players.resolve(theirs)
        players.setProfile(player, chosen)

        assertEquals(PlayerProfile(name = null, mark = null), players.profileOf(other))
    }

    // **A mark written by a newer deploy and read back by an older one.** A rollback is one command
    // and `#111` exercised one, so this is a state the service can genuinely be in — and letting the
    // decode throw would turn it into an exception on a read path that the endpoint answers as
    // `Internal`. Degrading to "no mark" gives the strip `THRESHOLD`, the drawing every account
    // already has, and the player's next save writes something this build understands.
    @Test
    fun `a part this build has never heard of reads as no mark rather than as a failure`() = runTest {
        val player = players.resolve(mine)
        players.setProfile(player, chosen)
        database.writeMark(player, """{"type":"Composed","body":"LIMB","path":"SPIRAL","terminus":"DOT"}""")

        assertEquals(PlayerProfile(name = CommanderName("Ada di Notte"), mark = null), players.profileOf(player))
    }

    // The other way a document like this goes wrong, and the reason the guard is on `Exception`
    // rather than on the serializer's own type: an operator at a `psql` prompt.
    @Test
    fun `a mark that is not a mark at all reads as no mark`() = runTest {
        val player = players.resolve(mine)
        players.setProfile(player, chosen)
        database.writeMark(player, """{"nonsense":true}""")

        assertEquals(PlayerProfile(name = CommanderName("Ada di Notte"), mark = null), players.profileOf(player))
    }

    // **The same argument, applied to the column beside it.** `display_name` is a `text` and holds
    // whatever any deploy ever wrote; `CommanderName`'s guards are what a *request* is checked
    // against, and running them on a read path turns a row a newer build wrote — a longer bound, a
    // name a trim rule once allowed — into an `IllegalArgumentException` and a 500. Degrading gives
    // the strip `Dead Reckoning`, which is what every account that has chosen nothing already reads,
    // and the player's next save writes a name this build can hold.
    @Test
    fun `a name this build could not have produced reads as no name rather than as a failure`() = runTest {
        val player = players.resolve(mine)
        players.setProfile(player, chosen)
        database.writeName(player, "a name from a deploy that bounded them somewhere past twenty-four")

        assertEquals(PlayerProfile(name = null, mark = chosen.mark), players.profileOf(player))
    }

    // The other half of the same guard, and the one an operator at a `psql` prompt reaches first:
    // `CommanderName` refuses a blank as hard as it refuses a long one.
    @Test
    fun `a name that is only whitespace reads as no name`() = runTest {
        val player = players.resolve(mine)
        players.setProfile(player, chosen)
        database.writeName(player, "   ")

        assertEquals(PlayerProfile(name = null, mark = chosen.mark), players.profileOf(player))
    }

    // And the pair that the type refuses: a row can hold it, and reading it must not throw either.
    @Test
    fun `a terminus on a mark with no path reads as no mark`() = runTest {
        val player = players.resolve(mine)
        players.setProfile(player, chosen)
        database.writeMark(player, """{"type":"Composed","body":"LIMB","path":"NONE","terminus":"DOT"}""")

        assertEquals(PlayerProfile(name = CommanderName("Ada di Notte"), mark = null), players.profileOf(player))
    }

    // **The cascade the columns get for free by living on `players` itself**, which is the whole
    // reason they are there rather than in a table of their own. `InMemoryPlayerRepository` has to
    // remove them by hand; this proves the row taking them.
    @Test
    fun `deleting the account takes the name and the mark with the row`() = runTest {
        val before = players.resolve(mine)
        players.setProfile(before, chosen)

        players.forget(before)

        val after = players.resolve(mine)
        assertNotEquals(before, after)
        assertEquals(PlayerProfile(name = null, mark = null), players.profileOf(after))
    }

    private val chosen = PlayerProfile(
        name = CommanderName("Ada di Notte"),
        mark = PlayerMark.Composed(MarkBody.WAKE, MarkPath.TWIN, MarkTerminus.RING),
    )

    private companion object {

        @get:ClassRule
        @JvmStatic
        val postgres: SingleInstancePostgresRule = embeddedPostgres()
    }
}
