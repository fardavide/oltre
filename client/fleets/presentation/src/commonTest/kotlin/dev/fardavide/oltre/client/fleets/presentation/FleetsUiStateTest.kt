package dev.fardavide.oltre.client.fleets.presentation

import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.fleets.ui.FleetsUiState
import dev.fardavide.oltre.client.fleets.ui.RunCardUiState
import dev.fardavide.oltre.client.fleets.ui.RunPhase
import dev.fardavide.oltre.core.Event
import dev.fardavide.oltre.core.FleetBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// **The phase is derived here and stored nowhere**, which is Design's seventh call and the reason
// `core` keeps one instant per end rather than three. So the thing to pin is the derivation: which
// of three phases a run is in at each moment of its window, and where the two ticks sit on the bar.
class FleetsUiStateTest {

    @Test
    fun `a run that has just left is outbound`() {
        val state = dispatch(window = 3.hours)

        assertEquals(RunPhase.OUTBOUND, state.cardAt(EPOCH).phase)
    }

    @Test
    fun `a run whose flight has ended is on station`() {
        val state = dispatch(window = 3.hours)
        val run = state.runs.single()
        val flight = FleetBalance.flight(from = state.galaxy.home, to = run.target)

        assertEquals(RunPhase.ON_STATION, state.cardAt(EPOCH + flight + 1.minutes).phase)
    }

    @Test
    fun `a run that has turned for home is inbound`() {
        val state = dispatch(window = 3.hours)
        val run = state.runs.single()
        val flight = FleetBalance.flight(from = state.galaxy.home, to = run.target)

        assertEquals(RunPhase.INBOUND, state.cardAt(run.returnsAt - flight + 1.minutes).phase)
    }

    @Test
    fun `the boundary belongs to the phase it opens`() {
        // Exactly at the instant the flight ends the run is on station rather than still outbound —
        // the same convention `advance` uses for a job completing at its own boundary.
        val state = dispatch(window = 3.hours)
        val run = state.runs.single()
        val flight = FleetBalance.flight(from = state.galaxy.home, to = run.target)

        assertEquals(RunPhase.ON_STATION, state.cardAt(EPOCH + flight).phase)
    }

    @Test
    fun `the two ticks sit where the legs end and begin`() {
        // A 3h window to a neighbour: the round trip is a fraction of it at each end, and the ticks
        // are fractions of the **whole window** rather than of a leg — the bar is one length.
        val state = dispatch(window = 3.hours)
        val bar = state.cardAt(EPOCH).bar

        assertTrue(bar.outboundEndsAt > 0f && bar.outboundEndsAt < 0.5f, "${bar.outboundEndsAt}")
        assertTrue(bar.inboundBeginsAt > 0.5f && bar.inboundBeginsAt < 1f, "${bar.inboundBeginsAt}")
        // Symmetric, because the two legs are the same flight in two directions.
        assertEquals(bar.outboundEndsAt, 1f - bar.inboundBeginsAt, absoluteTolerance = 0.0001f)
    }

    @Test
    fun `the bar fills across the whole window rather than across a leg`() {
        val state = dispatch(window = 3.hours)

        assertEquals(0f, state.cardAt(EPOCH).bar.progress)
        assertEquals(0.5f, state.cardAt(EPOCH + 90.minutes).bar.progress, absoluteTolerance = 0.0001f)
        assertEquals(1f, state.cardAt(EPOCH + 3.hours).bar.progress)
    }

    @Test
    fun `the countdown names the next moment rather than always the return`() {
        // A run has two moments a player waits on and for the first half of it the nearer one is the
        // arrival — the same change of scope the Colony strip took at 0.7.0.
        val state = dispatch(window = 3.hours)
        val run = state.runs.single()
        val flight = FleetBalance.flight(from = state.galaxy.home, to = run.target)

        // Outbound: counting down to the landing, which is sooner than the whole window.
        val outbound = state.cardAt(EPOCH).countdown
        assertEquals(secondsAsCountdown(flight.inWholeSeconds), English.resolve(outbound))
    }

    @Test
    fun `the card names the manifest and what it is bringing back`() {
        val state = dispatch(window = 3.hours, hulls = 2)
        val card = state.cardAt(EPOCH)

        assertTrue(English.resolve(card.manifest).startsWith("2 skiffs · "), English.resolve(card.manifest))
        assertTrue(English.resolve(card.manifest).endsWith(" metal"), English.resolve(card.manifest))
    }

    @Test
    fun `one hull is a skiff rather than one skiffs`() {
        assertTrue(English.resolve(dispatch(window = 3.hours, hulls = 1).cardAt(EPOCH).manifest).startsWith("1 skiff · "))
    }

    @Test
    fun `the heading counts what is away against what is owned`() {
        val state = fleetOf(3).dispatchWith(hulls = 2, window = 3.hours)

        assertEquals("2 of 3 away", English.resolve(state.toFleetsUiState(now = EPOCH, timeZone = TimeZone.UTC).away))
    }

    @Test
    fun `runs are listed by the moment each is counting down to`() {
        // `runs` is unordered on `GameState` for the reason `advance` sorts its arrivals on an
        // intrinsic key — a list whose order depended on the sequence of taps is one a reloaded save
        // reproduces only by accident.
        val state = fleetOf(2)
            .dispatchWith(hulls = 1, window = 24.hours)
            .dispatchWith(hulls = 1, window = 3.hours)

        val cards = state.toFleetsUiState(now = EPOCH, timeZone = TimeZone.UTC).runs

        assertEquals(2, cards.size)
        assertTrue(
            English.resolve(cards[0].countdown) <= English.resolve(cards[1].countdown),
            cards.map { English.resolve(it.countdown) }.toString(),
        )
    }

    // ── The ledger ──────────────────────────────────────────────────────────────────────────
    //
    // **The per-run ledger's assertions left with it at 0.13** — the section is a fold over worlds
    // now, and every claim that used to live here (newest first, the stamp, the migrated run, the
    // cap) is either restated or retired in `WorkedWorldsTest`. What stays is the pair that is about
    // *this* mapper's own shape rather than about the fold.

    @Test
    fun `a colony with nothing out and nothing landed says so with an empty list and no section`() {
        val fresh = fleetOf(1).toFleetsUiState(now = EPOCH, timeZone = TimeZone.UTC)

        assertTrue(fresh.runs.isEmpty())
        assertNull(fresh.worked, "a heading over nothing claims a history that is not there")
        assertEquals("0 of 1 away", English.resolve(fresh.away))
    }

    @Test
    fun `no sheet is up until a world is tapped`() {
        assertNull(fleetOf(1).toFleetsUiState(now = EPOCH, timeZone = TimeZone.UTC).dispatch)
    }

    // ── The fixture ─────────────────────────────────────────────────────────────────────────

    private fun fleetOf(hulls: Int): GameState =
        GameState.initial(SEED).copy(ships = Ships.of(ShipType.SKIFF, hulls))

    private fun dispatch(window: kotlin.time.Duration, hulls: Int = 1): GameState =
        fleetOf(hulls).dispatchWith(hulls = hulls, window = window)

    // Genesis surveys the home system, so its other worlds are legal targets on turn one. Lowest
    // slot first, which keeps the choice stable for a given seed.
    private fun GameState.dispatchWith(hulls: Int, window: kotlin.time.Duration): GameState {
        val target = galaxy.surveyed.filter { it != galaxy.home }.minByOrNull { it.slot }
            ?: error("the test seed's home system holds no world but home")
        return assertIs<StartRunResult.Started>(
            startRun(this, target, ResourceKind.METAL, Ships.of(ShipType.SKIFF, hulls), window, EPOCH),
        ).state
    }

    private fun GameState.cardAt(now: Instant): RunCardUiState =
        toFleetsUiState(now = now, timeZone = TimeZone.UTC).runs.single()

    private fun landing(cargo: Resources, at: Instant): Event.FleetReturned = Event.FleetReturned(
        from = GalaxyCoordinate(galaxy = 3, system = 171, slot = 10),
        ships = Ships.of(ShipType.SKIFF, 1),
        cargo = cargo,
        at = at,
    )

    private fun secondsAsCountdown(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = seconds % 3600 / 60
        val rest = seconds % 60
        fun pad(value: Long) = value.toString().padStart(2, '0')
        return "${pad(hours)}:${pad(minutes)}:${pad(rest)}"
    }

    private companion object {
        // The seed every client test in the repository uses.
        val SEED = GalaxySeed(20_260_807L)

        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)
    }
}
