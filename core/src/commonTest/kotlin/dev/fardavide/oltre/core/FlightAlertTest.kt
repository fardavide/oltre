package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// The third ask in the game, and **the first one that is made before the thing it is about exists.**
// A facility row is subscribed to after the build starts and a hull card is tapped while the queue
// holds one; a flight has no card until it is in the air, and by then the only control the design
// gives it is the one on the sheet that sent it. So the bell sits beside Dispatch, and dispatch is
// what stamps the answer onto the job.
//
// That shape is Davide's call, 2026-08-22, and it has two halves this file pins separately. **The
// ask is on the run** — so toggling the bell afterwards cannot reach a flight already out, which is
// what makes the promise the sheet made at the tap the promise that is kept. **And the bell
// remembers** — so a player who always wants to be told taps it once rather than once per dispatch.
class FlightAlertTest {

    @Test
    fun `a new colony is not waiting to hear about anything it sends`() {
        // Opt-in, like every other alert in the game since 0.5.0. The truthful default is off:
        // nothing has been asked for, so nothing is announced.
        assertFalse(GameState.initial().announceFlights)
    }

    @Test
    fun `one tap asks about the flights that follow`() {
        assertTrue(toggleFlightAlerts(GameState.initial()).announceFlights)
    }

    @Test
    fun `a second tap takes the ask back`() {
        // The undo is the same control, exactly as it is for the square on a facility row and for the
        // hull card's cycle. Nothing here asks for confirmation, because nothing here is expensive to
        // get wrong.
        assertFalse(toggleFlightAlerts(toggleFlightAlerts(GameState.initial())).announceFlights)
    }

    @Test
    fun `a run sent with the bell lit carries the ask`() {
        // given
        val asked = toggleFlightAlerts(fleetOf(1))

        // when
        val started = dispatch(asked)

        // then
        assertTrue(started.runs.single().announced)
    }

    @Test
    fun `a run sent with the bell unlit carries none`() {
        assertFalse(dispatch(fleetOf(1)).runs.single().announced)
    }

    @Test
    fun `a probe sent with the bell lit carries the ask`() {
        val asked = toggleFlightAlerts(scouted())

        val started = survey(asked)

        assertTrue(started.surveys.single().announced)
    }

    @Test
    fun `a probe sent with the bell unlit carries none`() {
        assertFalse(survey(scouted()).surveys.single().announced)
    }

    @Test
    fun `taking the ask back does not reach a flight already in the air`() {
        // **The whole of why the flag is stamped rather than read live.** A run is a promise made at
        // the tap — its cargo and its window are both fixed there — and the alert is part of that
        // promise. A player who lights the bell for one world and unlights it for the next is asking
        // two different questions, and the first answer must survive the second.
        val out = dispatch(toggleFlightAlerts(fleetOf(1)))

        val quietened = toggleFlightAlerts(out)

        assertFalse(quietened.announceFlights)
        assertTrue(quietened.runs.single().announced)
    }

    @Test
    fun `the bell keeps its position for the next dispatch`() {
        // The other half of Davide's call: the ask is per flight and the *control* is standing, so a
        // player who always wants to be told taps it once rather than once per sheet. The pair is
        // what makes a per-run ask cheap enough to be worth having.
        val asked = toggleFlightAlerts(fleetOf(2))
        val first = dispatch(asked)

        val second = dispatch(first)

        assertTrue(second.runs.all { it.announced })
    }

    @Test
    fun `the ask survives a round trip`() {
        val state = dispatch(toggleFlightAlerts(fleetOf(1)))
        val snapshot = GameSnapshot(lastUpdatedAt = EPOCH, state = state)

        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(GameSave.encode(snapshot))).snapshot

        assertTrue(decoded.state.announceFlights)
        assertTrue(decoded.state.runs.single().announced)
    }

    @Test
    fun `two flights alike in everything but the ask are two different flights`() {
        // **The property `advance`'s own tests rest on.** Its whole-state assertions compare
        // `GameState`s with `assertEquals`, so a field the generated `equals` ignored would let a
        // silenced run and an announced one compare equal — and every test that asserts a span
        // produces a particular colony would stop being able to see this one.
        val out = dispatch(toggleFlightAlerts(fleetOf(1))).runs.single()
        val probe = survey(toggleFlightAlerts(scouted())).surveys.single()

        assertNotEquals(out, out.copy(announced = false))
        assertNotEquals(probe, probe.copy(announced = false))
    }

    @Test
    fun `two colonies alike but for the bell are two different colonies`() {
        // The same property one level up, and it has the same owner: `advance`'s tests assert whole
        // `GameState`s with `assertEquals`, so a field its `equals` ignored would make a colony that
        // has asked and one that has not compare equal — and every span test would stop being able
        // to tell them apart.
        val state = fleetOf(1)

        assertNotEquals(state, toggleFlightAlerts(state))
    }

    @Test
    fun `nothing else about the colony moves`() {
        val state = fleetOf(2)

        val asked = toggleFlightAlerts(state)

        assertEquals(state.resources, asked.resources)
        assertEquals(state.ships, asked.ships)
        assertEquals(state.runs, asked.runs)
        assertEquals(state.eventLog, asked.eventLog)
        assertEquals(state.subscribed, asked.subscribed)
        assertEquals(state.hullAlerts, asked.hullAlerts)
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────

    private fun fleetOf(hulls: Int): GameState =
        GameState.initial().copy(ships = Ships.of(ShipType.SKIFF, hulls))

    private fun scouted(): GameState = GameState.initial().copy(
        ships = Ships.of(ShipType.SCOUT, 2),
        resources = Resources.of(metal = 100_000, crystal = 0, deuterium = 0),
    )

    private fun dispatch(state: GameState): GameState = assertIs<StartRunResult.Started>(
        startRun(
            state = state,
            target = neighbourOfHome(state),
            gathering = ResourceKind.METAL,
            ships = Ships.of(ShipType.SKIFF, 1),
            window = 3.hours,
            at = EPOCH,
        ),
    ).state

    private fun survey(state: GameState): GameState = assertIs<StartSurveyResult.Started>(
        startSurvey(state, unsurveyedSystem(state), at = EPOCH),
    ).state

    // Genesis surveys the whole home system, so its other worlds are legal targets on turn one.
    private fun neighbourOfHome(state: GameState): GalaxyCoordinate =
        state.galaxy.surveyed.filter { it != state.galaxy.home }.minByOrNull { it.slot }
            ?: error("the test seed's home system holds no world but home")

    private fun unsurveyedSystem(state: GameState): SystemAddress {
        val home = state.galaxy.home
        val system = (1..GalaxyBalance.SYSTEMS_PER_GALAXY).first { it != home.system }
        return SystemAddress(galaxy = home.galaxy, system = system)
    }

    private companion object {
        val EPOCH = Instant.fromEpochMilliseconds(0)
    }
}
