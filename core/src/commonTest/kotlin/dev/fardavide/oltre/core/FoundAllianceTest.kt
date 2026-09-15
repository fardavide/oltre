package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

// **What founding costs the colony, and nothing else about founding.** The alliance itself is the
// server's — `core` holds one colony and must not learn that alliances exist — so everything here is
// about a basket leaving and a line landing in the log.
class FoundAllianceTest {

    @Test
    fun `the price leaves the colony`() {
        val state = stateHolding(Resources.of(metal = 300_000, crystal = 200_000, deuterium = 100_000))

        val paid = assertIs<FoundAllianceResult.Paid>(foundAlliance(state, PRICE, TEST_NOW))

        assertEquals(Resources.of(metal = 100_000, crystal = 100_000, deuterium = 50_000), paid.state.resources)
    }

    // **A colony short of any one resource cannot found**, which is the comparison that is not the
    // obvious one: a pile of metal does not pay for deuterium.
    @Test
    fun `a colony short of one resource cannot found`() {
        val state = stateHolding(Resources.of(metal = 10_000_000, crystal = 10_000_000, deuterium = 49_999))

        assertEquals(FoundAllianceResult.InsufficientResources, foundAlliance(state, PRICE, TEST_NOW))
    }

    @Test
    fun `a colony holding exactly the price can found`() {
        val state = stateHolding(PRICE)

        val paid = assertIs<FoundAllianceResult.Paid>(foundAlliance(state, PRICE, TEST_NOW))

        assertEquals(Resources.of(), paid.state.resources)
    }

    // The debit is a thing that happened to this colony, so it is in the log — and it is its own
    // member rather than a contribution, because the alliance's ladder is paid by contributions and
    // a founding charge is not one.
    @Test
    fun `founding writes what it cost to the log`() {
        val state = stateHolding(Resources.of(metal = 300_000, crystal = 200_000, deuterium = 100_000))

        val paid = assertIs<FoundAllianceResult.Paid>(foundAlliance(state, PRICE, TEST_NOW))

        assertEquals(Event.AllianceFounded(price = PRICE, at = TEST_NOW), paid.state.eventLog.last())
    }

    // A refused founding writes nothing at all — no debit, and no line saying one happened.
    @Test
    fun `a refused founding leaves the colony exactly as it was`() {
        val state = stateHolding(Resources.of(metal = 1, crystal = 1, deuterium = 1))

        assertEquals(FoundAllianceResult.InsufficientResources, foundAlliance(state, PRICE, TEST_NOW))
    }

    // **The gauge does not move for it** — `ExperienceBalance.awardFor` gives it nothing, which is a
    // balance lever left unpulled rather than a number invented here.
    @Test
    fun `founding pays no experience`() {
        val state = stateHolding(Resources.of(metal = 300_000, crystal = 200_000, deuterium = 100_000))

        val paid = assertIs<FoundAllianceResult.Paid>(foundAlliance(state, PRICE, TEST_NOW))

        assertEquals(state.experience, paid.state.experience)
    }

    private fun stateHolding(resources: Resources): GameState =
        GameState.initial(GalaxySeed(TEST_NOW.toEpochMilliseconds())).copy(resources = resources)

    private companion object {
        val TEST_NOW: Instant = Instant.parse("2026-09-14T12:00:00Z")
        val PRICE: Resources = Resources.of(metal = 200_000, crystal = 100_000, deuterium = 50_000)
    }
}
