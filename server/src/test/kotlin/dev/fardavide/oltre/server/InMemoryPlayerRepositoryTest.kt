package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.IdempotencyKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// **The same three questions `PostgresPlayerRepositoryIntegrationTest` asks the table, asked of two
// maps.** The pair is the point, exactly as it is for the colony store: the unit suite above this
// stands on the in-memory half, and a fake that answers differently from the store it doubles makes
// every test above it a lie.
//
// The one place the two genuinely differ is the cascade — a foreign key over there, written out by
// hand here — which is why "deleting takes the colony with it" is asserted on both sides.
class InMemoryPlayerRepositoryTest {

    private val colonies = InMemoryColonyRepository()
    private val players = InMemoryPlayerRepository(colonies, ids = sequentialPlayerIds())
    private val mine = ProviderIdentity(ProviderName("google"), "subject-a")
    private val theirs = ProviderIdentity(ProviderName("google"), "subject-b")

    @Test
    fun `an identity nobody has seen becomes a player`() = runTest {
        assertEquals(PlayerId("player-1"), players.resolve(mine))
    }

    @Test
    fun `the same identity comes back to the same player`() = runTest {
        val first = players.resolve(mine)

        assertEquals(first, players.resolve(mine))
    }

    @Test
    fun `a different subject is a different player`() = runTest {
        assertNotEquals(players.resolve(mine), players.resolve(theirs))
    }

    @Test
    fun `the same subject at a different provider is a different player`() = runTest {
        // Nothing about a subject is globally unique, which is why the table's index is on the pair.
        val google = players.resolve(ProviderIdentity(ProviderName("google"), "1234"))
        val apple = players.resolve(ProviderIdentity(ProviderName("apple"), "1234"))

        assertNotEquals(google, apple)
    }

    @Test
    fun `finding an identity that signed in gives the same player resolve did`() = runTest {
        val player = players.resolve(mine)

        assertEquals(player, players.find(mine))
    }

    // **`find` is `resolve` with the creating taken away**, which is the whole reason it is a second
    // method — `#111` added it for Apple's notifications, where the caller is Apple and *"nobody"* is
    // a real answer. A `find` that fell through to `resolve` would mint a player for a subject that
    // has never held a colony, on the way to deleting it.
    @Test
    fun `finding an identity nobody has seen creates nobody`() = runTest {
        assertNull(players.find(mine))
        assertFalse(players.exists(PlayerId("player-1")))
    }

    @Test
    fun `finding is scoped to the provider as well as the subject`() = runTest {
        players.resolve(mine)

        assertNull(players.find(ProviderIdentity(ProviderName("apple"), "subject-a")))
    }

    @Test
    fun `a player who has signed in exists and one who never has does not`() = runTest {
        val player = players.resolve(mine)

        assertTrue(players.exists(player))
        assertFalse(players.exists(PlayerId("never-signed-in")))
    }

    @Test
    fun `forgetting a player takes the colony and the spent keys with it`() = runTest {
        // The cascade `schema.sql` gets from two foreign keys. **Forgetting the keys is the half
        // that is easy to miss**: `applied_verbs` outlives a colony on purpose, so a delete that
        // took only the colony would leave the old account's keys to refuse the new one's verbs.
        val player = players.resolve(mine)
        val key = IdempotencyKey("spent")
        colonies.found(player, freshColony())
        colonies.write(player, freshColony(), applied = setOf(key), expected = ColonyVersion.FIRST)

        assertTrue(players.forget(player))

        assertFalse(players.exists(player))
        assertNull(colonies.colonyOf(player))
        assertEquals(emptySet(), colonies.appliedAmong(player, setOf(key)))
    }

    @Test
    fun `forgetting somebody who is not there is done rather than an error`() = runTest {
        // Deleting twice is what a client that lost the first response does, and telling it that its
        // account does not exist is telling it the thing it asked for.
        assertFalse(players.forget(PlayerId("never-signed-in")))
    }

    @Test
    fun `one player's deletion leaves another's colony alone`() = runTest {
        val player = players.resolve(mine)
        val other = players.resolve(theirs)
        colonies.found(player, freshColony(seed = 1))
        colonies.found(other, freshColony(seed = 2))

        players.forget(player)

        assertNull(colonies.colonyOf(player))
        assertEquals(freshColony(seed = 2), colonies.colonyOf(other)?.snapshot)
    }

    @Test
    fun `the real mint hands out an id nobody has had before`() = runTest {
        // Every test in this file runs on a counter so its assertions can be read; this is the one
        // that says the shipping mint is a surrogate key rather than anything derived. Two calls, two
        // ids — which is what makes "signing in again after a deletion is a new player" true in
        // production and not only in a fixture.
        val real = InMemoryPlayerRepository(colonies, ids = PlayerIds.RANDOM)

        assertNotEquals(real.resolve(mine), real.resolve(theirs))
    }

    @Test
    fun `signing in again after a deletion is a new player and not the old one`() = runTest {
        // **The property that makes account deletion mean deletion.** The id is a surrogate key, so
        // the same subject comes back to a new one — with nothing hanging off it, and no way to
        // reach the colony that was there.
        val before = players.resolve(mine)
        players.forget(before)

        val after = players.resolve(mine)

        assertNotEquals(before, after)
    }
}
