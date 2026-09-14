package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class ContributeTest {

    @Test
    fun `a contribution leaves the colony and is recorded in the event log`() {
        // given
        val funded = GameState.initial().copy(resources = Resources.of(metal = 1_000, crystal = 400, deuterium = 90))

        // when
        val paid = assertIs<ContributeResult.Started>(
            contribute(funded, Resources.of(metal = 600, crystal = 100, deuterium = 30), at = EPOCH),
        ).state

        // then
        assertEquals(400L, paid.resources.metal)
        assertEquals(300L, paid.resources.crystal)
        assertEquals(60L, paid.resources.deuterium)
        assertEquals(
            listOf(
                Event.ResourcesContributed(
                    amount = Resources.of(metal = 600, crystal = 100, deuterium = 30),
                    at = EPOCH,
                ),
            ),
            paid.eventLog,
        )
    }

    @Test
    fun `a colony one unit short of what it offered cannot pay`() {
        val funded = GameState.initial().copy(resources = Resources.of(metal = 1_000, crystal = 400))

        assertEquals(
            ContributeResult.InsufficientResources,
            contribute(funded, Resources.of(metal = 1_000, crystal = 401), at = EPOCH),
        )
    }

    @Test
    fun `a refused contribution changes nothing at all`() {
        val funded = GameState.initial().copy(resources = Resources.of(metal = 10))
        val before = funded.resources

        val result = contribute(funded, Resources.of(metal = 11), at = EPOCH)

        assertIs<ContributeResult.InsufficientResources>(result)
        assertEquals(before, funded.resources)
        assertTrue(funded.eventLog.isEmpty())
    }

    // **A contribution of nothing is the dead control in its purest form** — a chip that presses,
    // debits nothing and appends an entry saying so. Refusing it in `core` is what puts the same
    // answer in front of the client and in front of the server's replay.
    @Test
    fun `a basket of nothing is refused rather than logged`() {
        val funded = GameState.initial().copy(resources = Resources.of(metal = 1_000))

        assertEquals(
            ContributeResult.NothingOffered,
            contribute(funded, Resources.of(), at = EPOCH),
        )
    }

    @Test
    fun `a colony with nothing at all can still be asked and still refuses`() {
        // The two refusals are reachable in the same state and the order between them is stated: a
        // basket of nothing is refused as nothing rather than as unaffordable, because a colony with
        // no stock covers a basket of no stock perfectly well.
        val broke = GameState.initial().copy(resources = Resources.of())

        assertEquals(ContributeResult.NothingOffered, contribute(broke, Resources.of(), at = EPOCH))
    }

    @Test
    fun `one resource is enough to be something`() {
        val funded = GameState.initial().copy(resources = Resources.of(deuterium = 5))

        val paid = assertIs<ContributeResult.Started>(
            contribute(funded, Resources.of(deuterium = 1), at = EPOCH),
        ).state

        assertEquals(4L, paid.resources.deuterium)
    }

    // Davide's call, 2026-09-14, on the recommendation in `#144` §3: **zero**. A flat award is per
    // event, so ten contributions of one metal would pay ten times what one contribution of ten
    // metal pays; a scaled one is the *what you own* tap `experience-sheet.md` §3 exists to prevent.
    // The resources buy the alliance a level, and the player's own gauge is for what their colony
    // did.
    @Test
    fun `a contribution pays the player nothing`() {
        val funded = GameState.initial().copy(resources = Resources.of(metal = 1_000_000, crystal = 1_000_000))

        val paid = assertIs<ContributeResult.Started>(
            contribute(funded, Resources.of(metal = 900_000, crystal = 900_000), at = EPOCH),
        ).state

        assertEquals(Experience.NONE, paid.experience)
        assertTrue(paid.eventLog.isNotEmpty(), "the contribution was not logged at all")
    }

    // The whole of what `core` can judge, written as the exhaustive `when` the server's own
    // flattening has to write anyway. Membership, a vault ceiling and a seat are facts about other
    // people; `core` holds one colony and must not learn that alliances exist, so a third arm
    // appearing here is a rule that has crossed a boundary rather than a case that was missed.
    @Test
    fun `core judges affordability and emptiness and nothing else`() {
        val funded = GameState.initial().copy(resources = Resources.of(metal = 10))
        val seen = listOf(
            contribute(funded, Resources.of(metal = 5), at = EPOCH),
            contribute(funded, Resources.of(metal = 50), at = EPOCH),
            contribute(funded, Resources.of(), at = EPOCH),
        ).map { result ->
            when (result) {
                is ContributeResult.Started -> "paid"
                ContributeResult.InsufficientResources -> "short"
                ContributeResult.NothingOffered -> "empty"
            }
        }

        assertEquals(listOf("paid", "short", "empty"), seen)
    }

    private companion object {
        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)
    }
}
