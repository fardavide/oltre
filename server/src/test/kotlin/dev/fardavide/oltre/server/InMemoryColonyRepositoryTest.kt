package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.IdempotencyKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours

// **The store contract, asked of the map.** `PostgresColonyRepositoryIntegrationTest` asks the same
// questions of three tables, and the pair is the point: a fake that answers differently from the
// store it stands in for is a fake that makes every unit test above it a lie.
class InMemoryColonyRepositoryTest {

    private val repository = InMemoryColonyRepository()
    private val davide = PlayerId("davide")
    private val someoneElse = PlayerId("someone-else")

    @Test
    fun `a player with no colony has none`() = runTest {
        assertNull(repository.colonyOf(davide))
    }

    @Test
    fun `founding a colony stores it under the player who founded it`() = runTest {
        val colony = freshColony()

        val founding = repository.found(davide, colony)

        assertEquals(Founding.Founded(StoredColony(colony, ColonyVersion.FIRST)), founding)
        assertEquals(colony, repository.colonyOf(davide)?.snapshot)
        assertNull(repository.colonyOf(someoneElse))
    }

    @Test
    fun `founding twice hands back the colony already there rather than a second galaxy`() = runTest {
        val first = freshColony(seed = 1)
        repository.found(davide, first)

        val founding = repository.found(davide, freshColony(seed = 2))

        assertEquals(Founding.AlreadyThere(StoredColony(first, ColonyVersion.FIRST)), founding)
        assertEquals(first, repository.colonyOf(davide)?.snapshot)
    }

    @Test
    fun `writing replaces the colony and remembers what was applied to it`() = runTest {
        val key = IdempotencyKey("first-mine")
        val founded = repository.found(davide, freshColony()).colony
        val advanced = freshColony(at = TEST_NOW + 2.hours)

        val written = repository.write(davide, advanced, applied = setOf(key), expected = founded.version)

        assertEquals(WriteResult.WRITTEN, written)
        assertEquals(advanced, repository.colonyOf(davide)?.snapshot)
        assertEquals(setOf(key), repository.appliedAmong(davide, setOf(key)))
    }

    @Test
    fun `a write moves the colony on to the next version`() = runTest {
        val founded = repository.found(davide, freshColony()).colony

        repository.write(davide, freshColony(at = TEST_NOW + 1.hours), applied = emptySet(), expected = founded.version)

        assertEquals(founded.version.next(), repository.colonyOf(davide)?.version)
    }

    @Test
    fun `only the keys asked about come back`() = runTest {
        val landed = IdempotencyKey("landed")
        val neverSent = IdempotencyKey("never-sent")
        val founded = repository.found(davide, freshColony()).colony
        repository.write(davide, freshColony(), applied = setOf(landed), expected = founded.version)

        assertEquals(setOf(landed), repository.appliedAmong(davide, setOf(landed, neverSent)))
    }

    @Test
    fun `one player's applied keys are not another's`() = runTest {
        val key = IdempotencyKey("shared-string")
        val founded = repository.found(davide, freshColony()).colony
        repository.write(davide, freshColony(), applied = setOf(key), expected = founded.version)

        assertEquals(emptySet(), repository.appliedAmong(someoneElse, setOf(key)))
    }

    @Test
    fun `a write adds to what was applied rather than replacing it`() = runTest {
        val first = IdempotencyKey("first")
        val second = IdempotencyKey("second")
        val founded = repository.found(davide, freshColony()).colony
        repository.write(davide, freshColony(), applied = setOf(first), expected = founded.version)

        repository.write(
            davide,
            freshColony(at = TEST_NOW + 1.hours),
            applied = setOf(second),
            expected = founded.version.next(),
        )

        assertEquals(setOf(first, second), repository.appliedAmong(davide, setOf(first, second)))
    }

    // ── The compare-and-set ───────────────────────────────────────────────────────────────────

    @Test
    fun `a write that asserts a version the colony has moved past changes nothing`() = runTest {
        val stale = repository.found(davide, freshColony(seed = 1)).colony
        val winner = freshColony(at = TEST_NOW + 1.hours, seed = 2)
        repository.write(davide, winner, applied = emptySet(), expected = stale.version)

        val written = repository.write(
            davide,
            freshColony(at = TEST_NOW + 9.hours, seed = 3),
            applied = setOf(IdempotencyKey("lost")),
            expected = stale.version,
        )

        assertEquals(WriteResult.STALE, written)
        assertEquals(winner, repository.colonyOf(davide)?.snapshot)
        // The loser's keys did not land either — the pair is one transaction or it is nothing.
        assertEquals(emptySet(), repository.appliedAmong(davide, setOf(IdempotencyKey("lost"))))
    }

    @Test
    fun `a write against a colony nobody founded is stale rather than a colony out of nowhere`() = runTest {
        val written = repository.write(
            davide,
            freshColony(),
            applied = emptySet(),
            expected = ColonyVersion.FIRST,
        )

        assertEquals(WriteResult.STALE, written)
        assertNull(repository.colonyOf(davide))
    }
}
