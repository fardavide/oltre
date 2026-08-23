package dev.fardavide.oltre.client.fleets.presentation

import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.fleets.ui.FleetsUiState
import dev.fardavide.oltre.client.fleets.ui.PHONE_WIDTH
import dev.fardavide.oltre.client.fleets.ui.SLIDE_OVER_WIDTH
import dev.fardavide.oltre.client.fleets.ui.fleets
import dev.fardavide.oltre.core.AlertSettings
import dev.fardavide.oltre.core.Event
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.FleetRun
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.StartRunResult
import dev.fardavide.oltre.core.startRun
import dev.fardavide.oltre.core.worldNameAt
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import org.junit.Test

// **The whole tab against a real `GameState`, mapper included.** `FleetsUiStateTest` drives the
// mapper with no screen and `TestFleetsUiState` drives the screen with no mapper — right for a
// baseline, and blind to the seam between them. A mapper computing the wrong phase and a screen
// drawing the right one from a fixture would both pass.
class FleetsFromStateBehaviourTest {

    @Test
    fun `a colony with a run out lists it with what it is bringing back`() {
        val state = dispatched(hulls = 1)

        fleets(uiState = state.toFleetsUiState(now = EPOCH, timeZone = TimeZone.UTC)) {
            assertShowsRun(0)
            assertReads("1 of 1 away")
            assertRunReads(0, "1 skiff")
            assertRunReads(0, "on station")
        }
    }

    @Test
    fun `the card counts down to the landing while the run is still outbound`() {
        val state = dispatched(hulls = 1)

        fleets(uiState = state.toFleetsUiState(now = EPOCH, timeZone = TimeZone.UTC)) {
            // Outbound, so the nearer of the run's two moments is the arrival rather than the
            // return — a 3h window that read 03:00:00 here would be counting the wrong one.
            assertRunDoesNotRead(0, "03:00:00")
        }
    }

    @Test
    fun `a colony with nothing out says so through the real mapper`() {
        // **The genesis reading, and since 0.11.3 it is "0 of 0" rather than "0 of 1"** — the colony
        // has bought no hull yet, so the tab's counter is honest about a fleet that does not exist
        // rather than about one that is merely at home. The empty-state copy is unchanged and does
        // the work either way: what is out is nothing, whatever is owned.
        val fresh = GameState.initial(SEED)

        fleets(uiState = fresh.toFleetsUiState(now = EPOCH, timeZone = TimeZone.UTC)) {
            assertHasNoRun(0)
            assertReads("Nothing is out.")
            assertReads("0 of 0 away")
        }
    }

    @Test
    fun `a landing with no world reaches the foot line rather than the list`() {
        // A schema-8 fold recorded no coordinate, so in a list of worlds it cannot be a row — and the
        // missing disc is what says so. The metal is still yours and the line says that too.
        val state = GameState.initial(SEED).copy(
            eventLog = listOf(
                Event.FleetReturned(
                    from = null,
                    ships = Ships.of(ShipType.SKIFF, 1),
                    cargo = Resources.of(crystal = 52),
                    at = EPOCH,
                ),
            ),
        )

        fleets(uiState = state.toFleetsUiState(now = EPOCH, timeZone = TimeZone.UTC)) {
            assertReads("WORLDS WORKED")
            assertTheUnrecordedLineReads("1 earlier run · 52 crystal · no target recorded")
        }
    }

    @Test
    fun `runs to one world fold into a row that names it and counts them`() {
        // The seam this test exists for: the mapper's fold and the screen's row agreeing about one
        // world. Two landings, one row, and the count is the thing neither half could get right
        // alone.
        val state = GameState.initial(SEED).copy(
            eventLog = listOf(
                landing(Resources.of(metal = 132), from = worked),
                landing(Resources.of(metal = 149), from = worked),
            ),
        )

        fleets(uiState = state.toFleetsUiState(now = EPOCH, timeZone = TimeZone.UTC)) {
            assertWorkedReads(worked, worldNameAt(state.galaxy.seed, worked))
            assertWorkedReads(worked, "2 runs")
            assertWorkedReads(worked, "281 metal")
        }
    }

    @Test
    fun `tapping a worked row raises the sheet on that world`() {
        // **Issue #62 in one assertion.** The ledger stops being a receipt and becomes a door back to
        // a world you liked — and the sheet it opens is the one the Galaxy tab raises, at its own
        // defaults rather than pre-filled from the run that was tapped.
        val state = GameState.initial(SEED).copy(
            ships = Ships.of(ShipType.SKIFF, 1),
            eventLog = listOf(landing(Resources.of(metal = 132), from = worked)),
        )

        fleetsScreen(state = state) {
            assertNoSheet()

            tapTheWorld(worked)

            assertTheSheetIsUp()
            assertTheSheetReads(worldNameAt(state.galaxy.seed, worked))
        }
    }

    @Test
    fun `touching a control on the sheet changes the run and commits nothing`() {
        // The same three-controls-one-verb claim the Galaxy side makes, asserted here because the
        // state behind them is a different screen's: `FleetsScreen` holds its own `DispatchSelection`
        // and nothing shares one with the Galaxy tab.
        val state = colonyWithARun()
        val sent = mutableListOf<GalaxyCoordinate>()

        fleetsScreen(state = state, onDispatchRun = { at, _, _, _ -> sent += at }) {
            tapTheWorld(worked)
            bringBack(ResourceKind.METAL)
            sendOneMore()
            homeIn(6.hours)
            assertTrue(sent.isEmpty(), "a control is a choice, not a commitment")
            assertTheSheetIsUp()
        }
    }

    @Test
    fun `the bell is live from this door too`() {
        // **The sheet has two doors and the ask must reach it from both**, or which tab a player
        // came through would decide whether they hear about the landing. Asserted here rather than
        // inferred from the Galaxy side, because the two screens hold their own sheet state and
        // module rule 5 stops either seeing the other — a callback dropped on this door is invisible
        // from over there.
        var asked = 0

        fleetsScreen(state = colonyWithARun(), onToggleAnnounce = { asked++ }) {
            tapTheWorld(worked)
            tapTheBell()
        }

        assertEquals(1, asked)
    }

    @Test
    fun `a fleet with nothing left to send offers no bell either`() {
        // The control and the verb appear and vanish together — a bell over a refusal would be
        // booking an alert for a flight that is not going anywhere.
        fleetsScreen(state = colonyWithARun().copy(ships = Ships.NONE)) {
            tapTheWorld(worked)
            assertOffersNoRun()
            assertHasNoBell()
        }
    }

    @Test
    fun `the run that leaves carries every control the player touched`() {
        // All three, together: the sheet's state is this screen's own, so a hull count and a window
        // chosen here have to survive to the verb. Two skiffs, so the stepper has somewhere to go.
        //
        // **The stepper is touched last since 0.13.1, and the order is the assertion rather than an
        // accident.** A currency and a rung both change what a fleet would lift, so both put the
        // manifest back to the fleet that empties the vein — see `homingIn`. Stepping before them
        // would be asserting that a count survives the two controls designed to overrule it, which is
        // the opposite of what this screen now does.
        val state = colonyWithARun().copy(ships = Ships.of(ShipType.SKIFF, 2))
        val sent = mutableListOf<Quadruple>()

        fleetsScreen(
            state = state,
            onDispatchRun = { at, gathering, ships, window -> sent += Quadruple(at, gathering, ships, window) },
        ) {
            tapTheWorld(worked)
            bringBack(ResourceKind.CRYSTAL)
            homeIn(6.hours)
            sendOneFewer()
            send()
        }

        val run = sent.single()
        assertEquals(ResourceKind.CRYSTAL, run.gathering)
        assertEquals(6.hours, run.window)
        // One fewer than the two the vein can absorb here, so `−` is a real move from the suggestion
        // rather than a step the clamp would have made anyway.
        assertEquals(Ships.of(ShipType.SKIFF, 1), run.ships)
    }

    @Test
    fun `sending from a worked row dispatches the run the sheet described`() {
        // Read off the *rendered* offer rather than off the selection: the mapper resolved the three
        // defaults and clamped the hull count, so dispatching the raw selection would send a run the
        // sheet never described. And the sheet closes, because the state after the tap is its own
        // receipt — a card appears in In flight above it.
        val state = colonyWithARun()
        val sent = mutableListOf<Quadruple>()

        fleetsScreen(
            state = state,
            onDispatchRun = { at, gathering, ships, window -> sent += Quadruple(at, gathering, ships, window) },
        ) {
            tapTheWorld(worked)
            bringBack(ResourceKind.METAL)
            send()

            assertNoSheet()
        }

        val run = sent.single()
        assertEquals(worked, run.at)
        assertEquals(ResourceKind.METAL, run.gathering)
        // The whole idle pool by default, which here is the one skiff the colony has at home.
        assertEquals(Ships.of(ShipType.SKIFF, 1), run.ships)
        assertEquals(3.hours, run.window)
    }

    @Test
    fun `a fleet that is entirely away is refused rather than offered a run`() {
        // **The state a player reading this list is usually in**, which is Design's sixth point and
        // the strongest argument for the row's quiet: had the row been a button, four times out of
        // five it would open a countdown.
        val state = GameState.initial(SEED).copy(
            ships = Ships.NONE,
            eventLog = listOf(landing(Resources.of(metal = 132))),
        )

        fleetsScreen(state = state) {
            tapTheWorld(worked)

            assertTheSheetIsUp()
            assertOffersNoRun()
            // **Nothing is away here — the colony owns nothing**, which is a different sentence and
            // the fixture always was that state. "Every hull is away" is now kept for a pool that
            // really does have something in flight.
            assertTheSheetReads("Nothing here can gather.")
        }
    }

    @Test
    fun `a fleet that really is away is told so rather than told it owns nothing`() {
        // The other side of the refusal's split, through the screen: something *is* out, so the
        // sentence is about the run and the footer counts the first hull home. Until 0.15.2 both
        // states shared one sentence, and the one they shared was true of only this one.
        val state = GameState.initial(SEED).copy(
            ships = Ships.NONE,
            // The row is drawn from the log, so the colony has worked this world before; the run is
            // what makes the refusal say "away" rather than "nothing here can gather".
            eventLog = listOf(landing(Resources.of(metal = 132))),
            runs = listOf(
                FleetRun(
                    target = worked,
                    ships = Ships.of(ShipType.SKIFF, 1),
                    gathering = ResourceKind.METAL,
                    cargo = Resources.of(metal = 400),
                    dispatchedAt = Instant.fromEpochMilliseconds(0),
                    returnsAt = Instant.fromEpochMilliseconds(0) + 3.hours,
                    announced = false,
                ),
            ),
        )

        fleetsScreen(state = state) {
            tapTheWorld(worked)

            assertTheSheetIsUp()
            assertOffersNoRun()
            assertTheSheetReads("Every hull is away.")
        }
    }

    @Test
    fun `a colony whose only hull is a scout is told what it is missing`() {
        // The 0.15 first check-in, through the screen: a scout charts a world and gathers nothing, so
        // the gathering pool is empty while a hull sits idle at home. The old sentence claimed both
        // that every hull was away and that nothing was idle.
        val state = GameState.initial(SEED).copy(
            ships = Ships.of(ShipType.SCOUT, 1),
            eventLog = listOf(landing(Resources.of(metal = 132))),
        )

        fleetsScreen(state = state) {
            tapTheWorld(worked)

            assertTheSheetIsUp()
            assertOffersNoRun()
            assertTheSheetReads("Nothing here can gather.")
        }
    }

    @Test
    fun `a deuterium landing keeps its own colour and its own word`() {
        // Unreachable through `startRun` — a run's `gathering` is guarded to metal and crystal — and
        // reachable through `Event.FleetReturned`, which is the wider type. The row draws it rather
        // than crashing on a deposit the world cannot have.
        val state = GameState.initial(SEED).copy(
            eventLog = listOf(landing(Resources.of(deuterium = 7))),
        )

        fleets(uiState = state.toFleetsUiState(now = EPOCH, timeZone = TimeZone.UTC)) {
            assertWorkedReads(worked, "7 deuterium")
        }
    }

    @Test
    fun `the line with no world is not a door`() {
        val state = GameState.initial(SEED).copy(
            ships = Ships.of(ShipType.SKIFF, 1),
            eventLog = listOf(
                Event.FleetReturned(
                    from = null,
                    ships = Ships.of(ShipType.SKIFF, 1),
                    cargo = Resources.of(metal = 134),
                    at = EPOCH,
                ),
            ),
        )

        fleetsScreen(state = state) {
            tapTheUnrecordedLine()

            assertNoSheet()
        }
    }

    @Test
    fun `the three legs lose their nouns in a Slide Over window and keep every figure`() {
        // The compact form the design's 320dp frame specifies. Both widths carry the same three
        // durations; what goes is "out", "on station" and "home", which the order already says.
        val state = dispatched(hulls = 1)
        val uiState = state.toFleetsUiState(now = EPOCH, timeZone = TimeZone.UTC)
        val card = uiState.runs.single()

        fleets(uiState = uiState, width = PHONE_WIDTH) {
            assertRunReads(0, English.resolve(card.legs))
        }
        fleets(uiState = uiState, width = SLIDE_OVER_WIDTH) {
            assertRunReads(0, English.resolve(card.compactLegs))
            assertRunDoesNotRead(0, "on station")
        }
    }

    // **A real world of the seed rather than an address**, which the per-run ledger did not need and
    // the fold does: a row is a world, so it has a name, a face and a deposit, and a coordinate the
    // generator puts nothing at cannot be a row at all.
    private fun landing(cargo: Resources, from: GalaxyCoordinate = worked): Event.FleetReturned =
        Event.FleetReturned(from = from, ships = Ships.of(ShipType.SKIFF, 1), cargo = cargo, at = EPOCH)

    // A colony with an idle skiff and one world already worked — the smallest state in which the
    // section is a door rather than a list.
    //
    // **`CARRIED_FORWARD`, because a new colony's settings take the bell off this sheet.** Under
    // `By category` a run is announced by its kind, so the control has nothing left to decide and
    // this app draws none — which would make the two tests below assertions about a control that no
    // longer exists rather than about the door they are named for.
    private fun colonyWithARun(): GameState = GameState.initial(SEED).copy(
        ships = Ships.of(ShipType.SKIFF, 1),
        eventLog = listOf(landing(Resources.of(metal = 132))),
        alerts = AlertSettings.CARRIED_FORWARD,
    )

    private fun dispatched(hulls: Int): GameState {
        val state = GameState.initial(SEED).copy(ships = Ships.of(ShipType.SKIFF, hulls))
        val target = state.galaxy.surveyed.filter { it != state.galaxy.home }.minByOrNull { it.slot }
            ?: error("the test seed's home system holds no world but home")
        return assertIs<StartRunResult.Started>(
            startRun(state, target, ResourceKind.METAL, Ships.of(ShipType.SKIFF, hulls), 3.hours, EPOCH),
        ).state
    }

    private companion object {
        val SEED = GalaxySeed(20_260_807L)

        // Genesis surveys the home system, so its other worlds are real targets on turn one — which
        // is what a worked row needs and what an invented coordinate could not give it.
        val worked: GalaxyCoordinate = GameState.initial(SEED).galaxy.let { galaxy ->
            galaxy.surveyed.filter { it != galaxy.home }.minBy { it.slot }
        }
        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)
    }
}

// The four subjects of a run, kept together so one assertion can name all four rather than four
// mutable lists agreeing by luck about which tap they came from.
private data class Quadruple(
    val at: GalaxyCoordinate,
    val gathering: ResourceKind,
    val ships: Ships,
    val window: kotlin.time.Duration,
)
