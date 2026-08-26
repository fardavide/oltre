package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.net.data.FakeOltreApi
import dev.fardavide.oltre.core.AlertSettings
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.WorldVerdict
import dev.fardavide.oltre.core.verdictFor
import dev.fardavide.oltre.core.worldAt
import kotlin.test.Test
import kotlin.test.assertEquals

// **A verb that cannot be held**, driven through the app.
//
// Everything else offline is a bet against your own colony, which is a closed system the server can
// settle. A run is aimed at a world in a galaxy other players are also in, so queueing one would be
// aiming at a snapshot — the target may be surveyed, claimed, emptied or defended by the time the
// phone reconnects. It refuses at the tap and says which fact makes it impossible.
//
// **What is asserted is that the control answered.** A button that silently did nothing is the
// failure the whole rule exists to prevent, and it is the one failure that looks identical to a
// working control in a screenshot.
//
// The colony is `FlightAlertAppBehaviourTest`'s, for its reasons: two hulls so there is something to
// send, and `CARRIED_FORWARD` so the sheet carries the controls the design drew.
class RefusedAppBehaviourTest {

    @Test
    fun `a run tapped with no signal is refused and names the target`() {
        app(saved = snapshot(), api = offlineServer()) {
            openTheSheet()
            sendTheRun()

            waitUntilItReads("A run cannot be held.")
            assertReads("is in a shared galaxy")
        }
    }

    // **Nothing was kept**, which is the whole difference between this and a held upgrade: the queue
    // is empty afterwards, so there is nothing to send when the signal comes back and nothing that
    // will happen later without being asked for again.
    @Test
    fun `a refused run queues nothing`() {
        app(saved = snapshot(), api = offlineServer()) {
            openTheSheet()
            sendTheRun()
            waitUntilItReads("A run cannot be held.")

            assertReads("0 actions held")
        }
    }

    // **Press it twice and it says the same thing twice**, which is correct: nothing has changed and
    // the app is not going to pretend the second tap was different. What it never does is silently do
    // nothing.
    @Test
    fun `a refused run answers every tap`() {
        app(saved = snapshot(), api = offlineServer()) {
            openTheSheet()
            sendTheRun()
            waitUntilItReads("A run cannot be held.")

            sendTheRun()

            assertReads("A run cannot be held.")
        }
    }

    // With signal the same tap goes through and the refusal never appears — the other half of the
    // pair, and the one that would fail silently.
    @Test
    fun `a run that reaches the server is never refused`() {
        app(saved = snapshot()) {
            openTheSheet()
            sendTheRun()

            assertDoesNotRead("A run cannot be held.")
            assertEquals(1, server.syncs().sumOf { it.envelopes.size })
        }
    }

    private fun AppRobot.openTheSheet() = apply {
        open(OltreTab.GALAXY)
        openTheWorldsList()
        openTheWorld(runnable)
    }

    // **Holding a colony as well as being unreachable**, so the app opens on the save rather than on
    // the gate: what is under test is a tap inside the game, and a gate is a different screen.
    private fun offlineServer(): FakeOltreApi = FakeOltreApi().apply { offline = true }

    private fun snapshot(): GameSnapshot = GameSnapshot(
        lastUpdatedAt = TEST_NOW,
        state = seeded.copy(
            resources = Resources.of(metal = 10_000, crystal = 10_000),
            ships = Ships.of(ShipType.SKIFF, 2),
        ),
    )

    private companion object {

        // `CARRIED_FORWARD` rather than genesis's own settings, for `FlightAlertAppBehaviourTest`'s
        // reason: under `BY_CATEGORY` the sheet's bell is absent, and a fixture that removed a
        // control it is not testing would be one more thing between the tap and the sentence.
        val seeded: GameState = GameState.initial(GalaxySeed(20_260_807L))
            .copy(alerts = AlertSettings.CARRIED_FORWARD)

        // A world the ledger lists and a run may actually be sent to. Read off the seed rather than
        // written down, because which of them is neither home nor held is the generator's answer.
        val runnable: GalaxyCoordinate = seeded.galaxy.home.let { home ->
            seeded.galaxy.surveyed
                .filter { it.galaxy == home.galaxy && it.system == home.system }
                .sortedBy { it.slot }
                .first { at ->
                    val world = worldAt(seeded.galaxy.seed, at)
                    world != null &&
                        verdictFor(world, seeded).let { it !is WorldVerdict.Home && it !is WorldVerdict.Occupied }
                }
        }
    }
}
