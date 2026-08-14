package dev.fardavide.oltre.client.shipyard.presentation

import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.shipyard.ui.BuildActionUiState
import dev.fardavide.oltre.client.shipyard.ui.HullUiState
import dev.fardavide.oltre.client.shipyard.ui.ShipyardUiState
import dev.fardavide.oltre.core.BuildShipsResult
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.Buildings
import dev.fardavide.oltre.core.buildShips
import dev.fardavide.oltre.core.FleetBalance
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.StartRunResult
import dev.fardavide.oltre.core.YardJob
import dev.fardavide.oltre.core.startRun
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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
        assertEquals("3 hulls", state.toShipyardUiState(now = t0, timeZone = TimeZone.UTC).fleet)
    }

    @Test
    fun `one hull is a hull rather than one hulls`() {
        assertEquals("1 hull", GameState.initial(SEED).toShipyardUiState(now = t0, timeZone = TimeZone.UTC).fleet)
    }

    @Test
    fun `the pool names what is owned and what is idle and what is away`() {
        val state = fleetOf(3).dispatchOne()

        assertEquals("3 owned · 2 idle · 1 away", state.toShipyardUiState(now = t0, timeZone = TimeZone.UTC).skiff().pool)
    }

    @Test
    fun `nothing away drops the clause rather than printing a zero`() {
        assertEquals("3 owned · 3 idle", fleetOf(3).toShipyardUiState(now = t0, timeZone = TimeZone.UTC).skiff().pool)
    }

    @Test
    fun `the price is the same whatever the fleet is and wherever it is`() {
        // Three hulls owned and one of them out. This test read *"the price is the next rung of the
        // curve and counts the hulls in flight"* until 0.10.1, when the price went flat on Davide's
        // call — so what it now pins is that neither the fleet nor where it happens to be is an
        // input to the card, which is the same assertion with the answer changed.
        val state = fleetOf(3).dispatchOne()

        val cost = FleetBalance.shipCost(ShipType.SKIFF)
        assertEquals(
            listOf(cost.metal.groupedByThousands(), cost.crystal.groupedByThousands()),
            state.toShipyardUiState(now = t0, timeZone = TimeZone.UTC).skiff().costs.map { it.amount },
        )
    }

    @Test
    fun `a hull the colony can pay for offers the verb`() {
        assertEquals(BuildActionUiState.Build, wealthy().toShipyardUiState(now = t0, timeZone = TimeZone.UTC).skiff().action)
    }

    @Test
    fun `a hull the colony cannot pay for reddens the chip it is short of`() {
        // given a colony with the crystal and none of the metal
        val cost = FleetBalance.shipCost(ShipType.SKIFF)
        val state = GameState.initial(SEED).copy(resources = Resources.of(crystal = cost.crystal))

        // then the chip that reddened is the one that is short and the other is not
        val chips = state.toShipyardUiState(now = t0, timeZone = TimeZone.UTC).skiff().costs.associate { it.kind to it.short }
        assertEquals(mapOf(ResourceKind.METAL to true, ResourceKind.CRYSTAL to false), chips)
    }

    @Test
    fun `a hull the colony cannot pay for says when rather than saying no`() {
        val state = GameState.initial(SEED).copy(resources = Resources.of())

        val action = assertIs<BuildActionUiState.AvailableIn>(state.toShipyardUiState(now = t0, timeZone = TimeZone.UTC).skiff().action)
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
            stopped.toShipyardUiState(now = t0, timeZone = TimeZone.UTC).skiff().action,
        )
    }

    @Test
    fun `the hull that has no price yet is drawn and cannot be bought`() {
        // Design's sixth call: the Hauler ships from this slice as a dimmed card carrying its one
        // line. It has no cost chips and no verb, because `FleetBalance.shipCost` refuses to guess.
        val coming = wealthy().toShipyardUiState(now = t0, timeZone = TimeZone.UTC).comingHulls

        assertEquals(listOf(ShipType.HAULER), coming.map { it.type })
        assertTrue(coming.single().purpose.isNotEmpty())
    }

    @Test
    fun `the sentence on a hull names what it is for`() {
        // The line that has to be worth reading before a second hull exists — otherwise "four berths
        // at half the speed" arrives as a bigger number rather than as a trade.
        val skiff = wealthy().toShipyardUiState(now = t0, timeZone = TimeZone.UTC).skiff()

        assertTrue(skiff.purpose.contains("berth"), skiff.purpose)
    }

    // ── The yard ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `an idle yard says nothing at all rather than saying nothing is building`() {
        assertEquals(null, wealthy().toShipyardUiState(now = t0, timeZone = TimeZone.UTC).skiff().yard)
    }

    @Test
    fun `a hull on the slipway is a countdown and a bar on its own card`() {
        val ordered = wealthy().order(1)
        val job = ordered.yard.single()

        val yard = assertNotNull(ordered.toShipyardUiState(now = t0, timeZone = TimeZone.UTC).skiff().yard)

        assertEquals(0, yard.progressPercent)
        assertTrue(yard.countdown.isNotEmpty(), yard.countdown)
        assertEquals(null, yard.queued)
        assertTrue(job.completesAt > t0)
    }

    @Test
    fun `progress is how far through the hull is rather than how far through the queue is`() {
        // Halfway through the *head* job, with two more behind it. A bar measuring the whole queue
        // would sit near a tenth here and crawl, which is the opposite of what a bar is for.
        val ordered = wealthy().order(3)
        val head = ordered.yard.first()
        val halfway = head.startedAt + (head.completesAt - head.startedAt) / 2

        val yard = assertNotNull(ordered.toShipyardUiState(now = halfway, timeZone = TimeZone.UTC).skiff().yard)

        assertEquals(50, yard.progressPercent)
    }

    @Test
    fun `the hulls waiting behind the one being made are counted`() {
        val yard = assertNotNull(wealthy().order(3).toShipyardUiState(now = t0, timeZone = TimeZone.UTC).skiff().yard)

        assertEquals("2 queued", yard.queued)
    }

    @Test
    fun `the pool counts what is on the slipway separately from what exists`() {
        // A hull being made is not a hull you own — it cannot be sent and it is not in the fleet —
        // so it is its own clause rather than folded into either of the other two.
        val ordered = fleetOf(3).copy(resources = Resources.of(metal = 1_000_000, crystal = 1_000_000)).order(2)

        assertEquals("3 owned · 3 idle · 2 building", ordered.toShipyardUiState(now = t0, timeZone = TimeZone.UTC).skiff().pool)
    }

    @Test
    fun `the section rule still counts the fleet that exists rather than the one that is paid for`() {
        val ordered = fleetOf(3).copy(resources = Resources.of(metal = 1_000_000, crystal = 1_000_000)).order(2)

        assertEquals("3 hulls", ordered.toShipyardUiState(now = t0, timeZone = TimeZone.UTC).fleet)
    }

    @Test
    fun `a busy yard still offers the verb because the queue is what a check-in spends into`() {
        // The whole point of a serial queue rather than a single slot: a player with full stores can
        // commit all of it in one tap-through, and the yard serves it while they are away.
        val ordered = wealthy().order(1)

        assertEquals(BuildActionUiState.Build, ordered.toShipyardUiState(now = t0, timeZone = TimeZone.UTC).skiff().action)
    }

    @Test
    fun `a hull already paid for leaves the price of the next one alone`() {
        // The mirror of what this asserted until 0.10.1, when the card had to price against everything
        // *committed* or it would have offered a rung the verb would not sell. A flat price has no
        // rung to skip, so a hull on the slipway changes the wait and not the chips.
        val ordered = wealthy().order(1)

        val cost = FleetBalance.shipCost(ShipType.SKIFF)
        assertEquals(
            listOf(cost.metal.groupedByThousands(), cost.crystal.groupedByThousands()),
            ordered.toShipyardUiState(now = t0, timeZone = TimeZone.UTC).skiff().costs.map { it.amount },
        )
    }

    @Test
    fun `a card reports the slipway only when the hull on it is its own`() {
        // The guard the mapper reads off `yard.first()` rather than searching for: the yard is
        // serial, so the hull being made is the one at the front, and a Hauler ahead of a Skiff in
        // the queue must not put a countdown on the Skiff's card. Unreachable from a finger today —
        // `FOR_SALE` is the skiff alone — and reachable the day slice 4 lands, which is the point.
        val state = wealthy().copy(
            yard = listOf(YardJob(ship = ShipType.HAULER, startedAt = t0, completesAt = t0 + 2.hours)),
        )

        assertEquals(null, state.toShipyardUiState(now = t0, timeZone = TimeZone.UTC).skiff().yard)
    }

    @Test
    fun `the wall-clock instant is the one the hull is actually done at`() {
        // The other half of the countdown, and the half a countdown cannot say: a player who looks at
        // 23:50 wants "done 02:22", not "in 2h 32m" alone. Read in a fixed zone so the assertion is
        // about the mapper rather than about where the test runs.
        val ordered = wealthy().order(1)
        val job = ordered.yard.single()

        val yard = assertNotNull(ordered.toShipyardUiState(now = t0, timeZone = TimeZone.UTC).skiff().yard)

        val expected = job.completesAt.toLocalDateTime(TimeZone.UTC)
        assertEquals(
            "done ${expected.hour.toString().padStart(2, '0')}:${expected.minute.toString().padStart(2, '0')}",
            yard.doneAt,
        )
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────

    private fun ShipyardUiState.skiff(): HullUiState = hulls.single { it.type == ShipType.SKIFF }

    private fun GameState.order(hulls: Int): GameState =
        assertIs<BuildShipsResult.Started>(buildShips(this, Ships.of(ShipType.SKIFF, hulls), at = t0)).state

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
