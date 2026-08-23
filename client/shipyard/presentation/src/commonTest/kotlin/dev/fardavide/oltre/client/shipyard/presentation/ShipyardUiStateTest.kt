package dev.fardavide.oltre.client.shipyard.presentation

import dev.fardavide.oltre.client.design.component.WatchSquareUiState
import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.design.text.StringId
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
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
import dev.fardavide.oltre.core.HullAlert
import dev.fardavide.oltre.core.NotificationCategory
import dev.fardavide.oltre.core.NotificationGrouping
import dev.fardavide.oltre.core.NotificationScope
import dev.fardavide.oltre.core.NotificationSettings
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
//
// **Every assertion below names a catalogue entry rather than a string, since #86.** That is not a
// mechanical consequence of the mapper's return type — it is the property the framework was bought
// for: `assertEquals(Strings.hullsInFleet(3), …)` goes on passing when Davide rewords the heading and
// still fails the day the mapper counts the wrong hulls. The words themselves are pinned once, in
// `EnglishTest`, where changing them is a deliberate act rather than a test repair.
class ShipyardUiStateTest {

    private val t0 = Instant.fromEpochMilliseconds(0)

    // **The seam that let the scout ship unbuyable, as a test.** The Shipyard's card list is
    // hand-maintained copy — a name and a purpose per hull, which cannot be derived — and
    // `buildShips` has its own list of what it will actually sell. Those are two statements of one
    // fact, and at 0.15 they disagreed: `FleetBalance.FOR_SALE` gained the scout and the screen did
    // not, so a colony that owns no hulls could not buy the one hull that surveys, and the Galaxy tab
    // was dead for the whole game rather than for the first day.
    //
    // Nothing in `core` could catch it: every test there calls the verb directly. This is the only
    // place the two lists can be held against each other.
    @Test
    fun `every hull the verb will sell has a card and every card is a hull the verb will sell`() {
        val onScreen = fleetOf(1).toShipyardUiState(now = t0, timeZone = TimeZone.UTC).hulls.map { it.type }

        assertEquals(FleetBalance.FOR_SALE, onScreen.toSet())
        assertEquals(FleetBalance.FOR_SALE.size, onScreen.size, "a hull is drawn twice: $onScreen")
    }

    @Test
    fun `the section rule counts the whole fleet rather than the idle pool`() {
        // given a colony with one skiff out and two in dock
        val state = fleetOf(3).dispatchOne()

        // then — three hulls, one of them away, and the heading says three
        assertEquals(Strings.hullsInFleet(3), state.toShipyardUiState(now = t0, timeZone = TimeZone.UTC).fleet)
    }

    @Test
    fun `one hull is a hull rather than one hulls`() {
        assertEquals(Strings.hullsInFleet(1), fleetOf(1).toShipyardUiState(now = t0, timeZone = TimeZone.UTC).fleet)
    }

    // **What the screen says to the player it is now written for.** Until 0.11.3 genesis granted a
    // skiff, so nobody could open this tab owning nothing and the zero was an unreachable branch of
    // the same rule the test above pins. It is the *opening* state now — the first thing a new colony
    // reads here — so it gets an assertion of its own rather than being left to the plural default.
    @Test
    fun `a colony that has not bought a hull yet reads zero rather than an empty header`() {
        assertEquals(
            Strings.hullsInFleet(0),
            GameState.initial(SEED).toShipyardUiState(now = t0, timeZone = TimeZone.UTC).fleet,
        )
    }

    @Test
    fun `the pool names what is owned and what is idle and what is away`() {
        val state = fleetOf(3).dispatchOne()

        assertEquals(
            Strings.clauses(listOf(Strings.shipsOwned(3), Strings.shipsIdle(2), Strings.shipsAway(1))),
            state.toShipyardUiState(now = t0, timeZone = TimeZone.UTC).skiff().pool,
        )
    }

    @Test
    fun `nothing away drops the clause rather than printing a zero`() {
        assertEquals(
            Strings.clauses(listOf(Strings.shipsOwned(3), Strings.shipsIdle(3))),
            fleetOf(3).toShipyardUiState(now = t0, timeZone = TimeZone.UTC).skiff().pool,
        )
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
        // The *entry*, not the wording: what this pins is that an unaffordable hull answers "when",
        // and the wait inside it is a duration the format module already has its own tests for.
        assertEquals(StringId.AvailableIn, action.label.entry())
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
            BuildActionUiState.AvailableIn(Strings.availableNever()),
            stopped.toShipyardUiState(now = t0, timeZone = TimeZone.UTC).skiff().action,
        )
    }

    @Test
    fun `the hauler is sold rather than promised`() {
        // The `NOT YET BUILT` section is gone with the promise it carried: Design named one card for
        // it and that hull has shipped. It comes back with the first hull that has a date — the
        // escort and the settler are still slices nobody has scheduled, and a card for either would
        // advertise one.
        val yard = wealthy().toShipyardUiState(now = t0, timeZone = TimeZone.UTC)

        assertTrue(ShipType.HAULER in yard.hulls.map { it.type })
    }

    @Test
    fun `the sentence on a hull names what it is for`() {
        // The line that has to be worth reading before a second hull exists — otherwise "four berths
        // at half the speed" arrives as a bigger number rather than as a trade.
        val skiff = wealthy().toShipyardUiState(now = t0, timeZone = TimeZone.UTC).skiff()

        assertEquals(Strings.skiffPurpose(), skiff.purpose)
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
        assertEquals(StringId.Countdown, yard.countdown.entry())
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

        assertEquals(Strings.shipsQueued(2), yard.queued)
    }

    @Test
    fun `the pool counts what is on the slipway separately from what exists`() {
        // A hull being made is not a hull you own — it cannot be sent and it is not in the fleet —
        // so it is its own clause rather than folded into either of the other two.
        val ordered = fleetOf(3).copy(resources = Resources.of(metal = 1_000_000, crystal = 1_000_000)).order(2)

        assertEquals(
            Strings.clauses(
                listOf(Strings.shipsOwned(3), Strings.shipsIdle(3), Strings.shipsBuilding(2)),
            ),
            ordered.toShipyardUiState(now = t0, timeZone = TimeZone.UTC).skiff().pool,
        )
    }

    @Test
    fun `the section rule still counts the fleet that exists rather than the one that is paid for`() {
        val ordered = fleetOf(3).copy(resources = Resources.of(metal = 1_000_000, crystal = 1_000_000)).order(2)

        assertEquals(Strings.hullsInFleet(3), ordered.toShipyardUiState(now = t0, timeZone = TimeZone.UTC).fleet)
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
        assertEquals(Strings.doneAt(hour = expected.hour, minute = expected.minute), yard.doneAt)
    }

    // ── The square, which is offered by the queue and answered by the map ────────────────────

    @Test
    fun `a card with nothing of its type in the yard has no square at all`() {
        // The absence of a control rather than a disabled one, which is what this app says everywhere
        // it has nothing to offer. An idle card has no completion to be told about.
        val idle = wealthy().toShipyardUiState(now = t0, timeZone = TimeZone.UTC)

        assertEquals(null, idle.skiff().alert)
    }

    @Test
    fun `an order nobody has tapped offers the square unlit`() {
        val ordered = wealthy().order(2).toShipyardUiState(now = t0, timeZone = TimeZone.UTC)

        assertEquals(WatchSquareUiState.UNASKED, ordered.skiff().alert)
    }

    @Test
    fun `the two ways of asking are two states of the same square`() {
        val ordered = wealthy().order(2)

        for ((ask, expected) in mapOf(
            HullAlert.WHEN_ALL_DONE to WatchSquareUiState.ASKED,
            HullAlert.EACH_HULL to WatchSquareUiState.ASKED_SEVERAL,
        )) {
            val asked = ordered.copy(hullAlerts = mapOf(ShipType.SKIFF to ask))
            assertEquals(expected, asked.toShipyardUiState(now = t0, timeZone = TimeZone.UTC).skiff().alert)
        }
    }

    @Test
    fun `a hull queued behind another type offers a square although it shows no countdown`() {
        // **The one case that separates this mapper from `yardLine` next door**, and the reason the
        // square is read off the whole queue rather than off its head. A footer reports the hull being
        // made, which has to be the one on the slipway; the square asks about an *order*, and a hauler
        // waiting behind two skiffs is an order the player is waiting on with no countdown on its card.
        val mixed = assertIs<BuildShipsResult.Started>(
            buildShips(wealthy().order(2), Ships.of(ShipType.HAULER, 1), at = t0),
        ).state

        val hauler = mixed.toShipyardUiState(now = t0, timeZone = TimeZone.UTC).hulls
            .single { it.type == ShipType.HAULER }

        assertEquals(null, hauler.yard, "the countdown belongs to the hull on the slipway")
        assertEquals(WatchSquareUiState.UNASKED, hauler.alert)
    }

    @Test
    fun `asking about one hull type leaves the other card's square unlit`() {
        val mixed = assertIs<BuildShipsResult.Started>(
            buildShips(wealthy().order(2), Ships.of(ShipType.HAULER, 1), at = t0),
        ).state.copy(hullAlerts = mapOf(ShipType.HAULER to HullAlert.EACH_HULL))

        val cards = mixed.toShipyardUiState(now = t0, timeZone = TimeZone.UTC).hulls.associateBy { it.type }

        assertEquals(WatchSquareUiState.ASKED_SEVERAL, cards.getValue(ShipType.HAULER).alert)
        assertEquals(WatchSquareUiState.UNASKED, cards.getValue(ShipType.SKIFF).alert)
    }

    // **The card's control goes entirely once the categories are in charge** — Davide, 2026-08-23.
    // The Hulls switch answers for the whole yard, and what goes with it is the choice between the
    // card's two ways of asking: in that mode the grouping setting decides packaging for every kind
    // of news at once.
    @Test
    fun `a card has no square at all while the categories are in charge`() {
        // given an order the player has already tapped twice
        val ordered = wealthy().order(2).copy(hullAlerts = mapOf(ShipType.SKIFF to HullAlert.EACH_HULL))
        val byCategory = NotificationSettings(
            scope = NotificationScope.BY_CATEGORY,
            grouping = NotificationGrouping.SINGLE,
            categories = NotificationCategory.entries.toSet(),
        )

        // then — absent rather than inert, and the tap that lit it is kept in the state
        assertEquals(
            null,
            ordered.toShipyardUiState(now = t0, timeZone = TimeZone.UTC, alerts = byCategory).skiff().alert,
        )
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────

    private fun ShipyardUiState.skiff(): HullUiState = hulls.single { it.type == ShipType.SKIFF }

    // Which catalogue entry a piece of text is, for the two assertions whose *argument* is derived
    // and therefore not worth restating. Safe because a mapper only ever produces a catalogue entry
    // here; a `Raw` would be the defect.
    private fun TextRes.entry(): StringId = assertIs<TextRes.Message>(this).id

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
