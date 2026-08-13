package dev.fardavide.oltre.client.shipyard.presentation

import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.Buildings
import dev.fardavide.oltre.core.FleetBalance
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.StartRunResult
import dev.fardavide.oltre.core.startRun
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// The Shipyard is a price list, so what a test has to pin is the price and the three readings around
// it: what the fleet is, what the hull is for, and when the player can have another one.
class ShipyardUiStateTest {

    private val t0 = Instant.fromEpochMilliseconds(0)

    @Test
    fun `the section rule counts the whole fleet rather than the idle pool`() {
        // given a colony with one skiff out and two in dock
        val state = fleetOf(3).dispatchOne()

        // then — three hulls, one of them away, and the heading says three
        assertEquals("3 hulls", state.toShipyardUiState().fleet)
    }

    @Test
    fun `one hull is a hull rather than one hulls`() {
        assertEquals("1 hull", GameState.initial(SEED).toShipyardUiState().fleet)
    }

    @Test
    fun `the pool names what is owned and what is idle and what is away`() {
        val state = fleetOf(3).dispatchOne()

        assertEquals("3 owned · 2 idle · 1 away", state.toShipyardUiState().skiff().pool)
    }

    @Test
    fun `nothing away drops the clause rather than printing a zero`() {
        assertEquals("3 owned · 3 idle", fleetOf(3).toShipyardUiState().skiff().pool)
    }

    @Test
    fun `the price is the next rung of the curve and counts the hulls in flight`() {
        // given three hulls owned and one of them out
        val state = fleetOf(3).dispatchOne()

        // then the fourth is priced as the fourth — a fleet that is away is still a fleet you bought
        val fourth = FleetBalance.shipCost(ShipType.SKIFF, alreadyOwned = 3)
        assertEquals(
            listOf(fourth.metal.toString(), fourth.crystal.toString()),
            state.toShipyardUiState().skiff().costs.map { it.amount },
        )
    }

    @Test
    fun `a hull the colony can pay for offers the verb`() {
        assertEquals(BuildActionUiState.Build, wealthy().toShipyardUiState().skiff().action)
    }

    @Test
    fun `a hull the colony cannot pay for reddens the chip it is short of`() {
        // given a colony with the crystal and none of the metal
        val cost = FleetBalance.shipCost(ShipType.SKIFF, alreadyOwned = 1)
        val state = GameState.initial(SEED).copy(resources = Resources.of(crystal = cost.crystal))

        // then the chip that reddened is the one that is short and the other is not
        val chips = state.toShipyardUiState().skiff().costs.associate { it.kind to it.short }
        assertEquals(mapOf(ResourceKind.METAL to true, ResourceKind.CRYSTAL to false), chips)
    }

    @Test
    fun `a hull the colony cannot pay for says when rather than saying no`() {
        val state = GameState.initial(SEED).copy(resources = Resources.of())

        val action = assertIs<BuildActionUiState.AvailableIn>(state.toShipyardUiState().skiff().action)
        assertTrue(action.label.startsWith("in "), action.label)
    }

    @Test
    fun `a wait that never ends is a dash rather than a number nobody can read`() {
        // given a colony whose mines produce nothing at all
        val stopped = GameState.initial(SEED).copy(
            resources = Resources.of(),
            buildings = Buildings.initial().copy(
                metalMine = BuildingLevel(0),
                crystalMine = BuildingLevel(0),
            ),
        )

        assertEquals(
            BuildActionUiState.AvailableIn("—"),
            stopped.toShipyardUiState().skiff().action,
        )
    }

    @Test
    fun `the hull that has no price yet is drawn and cannot be bought`() {
        // Design's sixth call: the Hauler ships from this slice as a dimmed card carrying its one
        // line. It has no cost chips and no verb, because `FleetBalance.shipCost` refuses to guess.
        val coming = wealthy().toShipyardUiState().comingHulls

        assertEquals(listOf(ShipType.HAULER), coming.map { it.type })
        assertTrue(coming.single().purpose.isNotEmpty())
    }

    @Test
    fun `the sentence on a hull names what it is for`() {
        // The line that has to be worth reading before a second hull exists — otherwise "four berths
        // at half the speed" arrives as a bigger number rather than as a trade.
        val skiff = wealthy().toShipyardUiState().skiff()

        assertTrue(skiff.purpose.contains("berth"), skiff.purpose)
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────

    private fun ShipyardUiState.skiff(): HullUiState = hulls.single { it.type == ShipType.SKIFF }

    private fun fleetOf(hulls: Int): GameState =
        GameState.initial(SEED).copy(ships = Ships.of(ShipType.SKIFF, hulls))

    private fun wealthy(): GameState =
        GameState.initial(SEED).copy(resources = Resources.of(metal = 100_000, crystal = 100_000))

    // Genesis surveys the home system, so a neighbour of home is a legal target on turn one.
    private fun GameState.dispatchOne(): GameState {
        val target = galaxy.surveyed.filter { it != galaxy.home }.minByOrNull { it.slot }
            ?: error("the test seed's home system holds no world but home")
        return assertIs<StartRunResult.Started>(
            startRun(this, target, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), 3.hours, t0),
        ).state
    }

    private companion object {
        // The seed every client test in the repository uses, so a state built here and a state built
        // on the Galaxy tab are the same map.
        val SEED = GalaxySeed(20_260_807L)
    }
}
