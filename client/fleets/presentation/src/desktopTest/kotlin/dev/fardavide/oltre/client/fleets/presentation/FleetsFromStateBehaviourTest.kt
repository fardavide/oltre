package dev.fardavide.oltre.client.fleets.presentation

import dev.fardavide.oltre.client.fleets.ui.FleetsUiState
import dev.fardavide.oltre.client.fleets.ui.PHONE_WIDTH
import dev.fardavide.oltre.client.fleets.ui.SLIDE_OVER_WIDTH
import dev.fardavide.oltre.client.fleets.ui.fleets
import dev.fardavide.oltre.core.Event
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.StartRunResult
import dev.fardavide.oltre.core.startRun
import kotlin.test.assertIs
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
        val fresh = GameState.initial(SEED)

        fleets(uiState = fresh.toFleetsUiState(now = EPOCH, timeZone = TimeZone.UTC)) {
            assertHasNoRun(0)
            assertReads("Nothing is out.")
            assertReads("0 of 1 away")
        }
    }

    @Test
    fun `a landing in the log reaches the ledger`() {
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
            assertReads("LANDED")
            assertLandingReads(0, "+52 crystal")
        }
    }

    @Test
    fun `a landing keeps its resource's own colour whichever resource it was`() {
        // Pairs with the fix in `toLanding`: the ledger reads the cargo rather than assuming the two
        // kinds a run may gather, and this is that reaching the screen. A deuterium landing is not
        // reachable through `startRun` and is reachable through the event, which is the wider type.
        val state = GameState.initial(SEED).copy(
            eventLog = listOf(
                landing(Resources.of(metal = 132)),
                landing(Resources.of(crystal = 52)),
                landing(Resources.of(deuterium = 7)),
            ),
        )

        fleets(uiState = state.toFleetsUiState(now = EPOCH, timeZone = TimeZone.UTC)) {
            assertLandingReads(0, "+7 deuterium")
            assertLandingReads(1, "+52 crystal")
            assertLandingReads(2, "+132 metal")
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
            assertRunReads(0, card.legs)
        }
        fleets(uiState = uiState, width = SLIDE_OVER_WIDTH) {
            assertRunReads(0, card.compactLegs)
            assertRunDoesNotRead(0, "on station")
        }
    }

    private fun landing(cargo: Resources): Event.FleetReturned = Event.FleetReturned(
        from = GalaxyCoordinate(galaxy = 3, system = 171, slot = 10),
        ships = Ships.of(ShipType.SKIFF, 1),
        cargo = cargo,
        at = EPOCH,
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
        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)
    }
}
