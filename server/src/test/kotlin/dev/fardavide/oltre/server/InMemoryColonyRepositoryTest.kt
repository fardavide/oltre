package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.IdempotencyKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours

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

        assertEquals(Founding.Founded(colony), founding)
        assertEquals(colony, repository.colonyOf(davide))
        assertNull(repository.colonyOf(someoneElse))
    }

    @Test
    fun `founding twice hands back the colony already there rather than a second galaxy`() = runTest {
        val first = freshColony(seed = 1)
        repository.found(davide, first)

        val founding = repository.found(davide, freshColony(seed = 2))

        assertEquals(Founding.AlreadyThere(first), founding)
        assertEquals(first, repository.colonyOf(davide))
    }

    @Test
    fun `writing replaces the colony and remembers what was applied to it`() = runTest {
        val key = IdempotencyKey("first-mine")
        repository.found(davide, freshColony())
        val advanced = freshColony(at = TEST_NOW + 2.hours)

        repository.write(davide, advanced, applied = setOf(key))

        assertEquals(advanced, repository.colonyOf(davide))
        assertEquals(setOf(key), repository.appliedAmong(davide, setOf(key)))
    }

    @Test
    fun `only the keys asked about come back`() = runTest {
        val landed = IdempotencyKey("landed")
        val neverSent = IdempotencyKey("never-sent")
        repository.write(davide, freshColony(), applied = setOf(landed))

        assertEquals(setOf(landed), repository.appliedAmong(davide, setOf(landed, neverSent)))
    }

    @Test
    fun `one player's applied keys are not another's`() = runTest {
        val key = IdempotencyKey("shared-string")
        repository.write(davide, freshColony(), applied = setOf(key))

        assertEquals(emptySet(), repository.appliedAmong(someoneElse, setOf(key)))
    }

    @Test
    fun `a write adds to what was applied rather than replacing it`() = runTest {
        val first = IdempotencyKey("first")
        val second = IdempotencyKey("second")
        repository.write(davide, freshColony(), applied = setOf(first))

        repository.write(davide, freshColony(at = TEST_NOW + 1.hours), applied = setOf(second))

        assertEquals(setOf(first, second), repository.appliedAmong(davide, setOf(first, second)))
    }
}
