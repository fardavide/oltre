package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.net.data.FakeOltreApi
import dev.fardavide.oltre.core.AlertCategory
import dev.fardavide.oltre.core.AlertDelivery
import dev.fardavide.oltre.core.AlertSettings
import dev.fardavide.oltre.core.BuildShipsResult
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.buildShips
import dev.fardavide.oltre.core.startUpgrade
import dev.fardavide.oltre.protocol.ClientVerb
import dev.fardavide.oltre.protocol.IdempotencyKey
import dev.fardavide.oltre.protocol.VerbEnvelope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// **What a tap with no signal does**, driven through the whole app rather than asserted at a mapper.
//
// The failure this file exists against is the one the design's own §3 names: *the control that was
// tapped looks exactly like one that landed*. Every assertion below is about a card or a line saying
// which of the two it is — and about the queue, because a card that says *held* while nothing was
// written would be the same defect wearing the fix.
class HeldAppBehaviourTest {

    @Test
    fun `a colony with signal holds nothing and shows no chrome line`() {
        app(saved = colony()) {
            assertOfflineLine(showing = false)
            assertDoesNotRead("Upgrade held.")
        }
    }

    // **The whole of the offline era in one test**: the tap is kept, the card says so, and the line
    // above the destination counts it.
    @Test
    fun `an upgrade tapped with no signal is held and the card says so`() {
        app(saved = colony(), api = offlineServer()) {
            tapTheActionOn(BuildingType.METAL_MINE)

            waitUntilItReads("Upgrade held.")
            assertReads("It starts when the network is back.")
            assertOfflineLine(showing = true)
        }
    }

    // The chrome line carries the network fact and the count and **never the state of a control** —
    // which is exactly why the count is what is asserted here and the card is asserted above.
    @Test
    fun `the chrome line counts what is outstanding`() {
        app(saved = colony(), api = offlineServer()) {
            tapTheActionOn(BuildingType.METAL_MINE)
            waitUntilItReads("Upgrade held.")

            assertReads("1 action held")
        }
    }

    // **The amber ghost is still a target**, and this is what pressing it does: nothing has been
    // sent, so taking it back costs nobody an apology and needs no server to agree.
    @Test
    fun `pressing the held ghost withdraws the request`() {
        app(saved = colony(), api = offlineServer()) {
            tapTheActionOn(BuildingType.METAL_MINE)
            waitUntilItReads("Upgrade held.")

            tapTheActionOn(BuildingType.METAL_MINE)

            assertDoesNotRead("Upgrade held.")
            assertReads("0 actions held")
        }
    }

    // **A verb queued before the app was closed is still queued when it opens**, which is the whole
    // reason the outbox is a file. A queue that evaporated on a relaunch would be worse than one
    // that was refused: the player would have no way to know it had gone.
    @Test
    fun `a queue written before the launch is still held after it`() {
        app(saved = colony(), api = offlineServer(), queued = listOf(queuedUpgrade())) {
            // **The card is amber before the line is**, and the order is the point rather than a
            // race to work around: what is held is read off the file at launch, so the row is right
            // on the first frame — where *"no network"* is not known until a sync has actually
            // failed, which takes three attempts over four seconds.
            assertReads("Upgrade held.")
            waitUntilItReads("1 action held")
        }
    }

    // **And it goes up on the next sync.** The card stops being amber because the server has
    // answered, which is the only thing that can make it stop.
    @Test
    fun `a queue written before the launch is sent when the server answers`() {
        app(saved = colony(), queued = listOf(queuedUpgrade())) {
            assertDoesNotRead("Upgrade held.")
            assertEquals(1, server.syncs().sumOf { it.envelopes.size })
        }
    }

    // **The amber says *it starts when the network is back*, and this is what makes that true.**
    // Until the tick loop learned to retry, nothing in the app noticed the network coming back: the
    // queue drained on a launch and on a tap and at no other moment, so a player who regained signal
    // mid-session sat with three amber cards until they touched one.
    //
    // The server is offline when the app opens and answering a minute later, and nothing is tapped in
    // between — which is the whole of the assertion.
    @Test
    fun `a held verb goes up on its own when the network comes back`() {
        val server = offlineServer()
        app(saved = colony(), api = server, queued = listOf(queuedUpgrade())) {
            assertReads("Upgrade held.")
            waitUntilItReads("1 action held")

            server.offline = false
            server.colony = colony()

            waitUntilItDoesNotRead("Upgrade held.")
            assertOfflineLine(showing = false)
        }
    }

    // **A tap with signal is not held at all**, which is the other half of the pair and the one that
    // would fail silently: a card that went amber on a working connection would be the app reporting
    // on itself rather than on the network.
    @Test
    fun `a tap that reaches the server is never drawn as held`() {
        app(saved = colony()) {
            tapTheActionOn(BuildingType.METAL_MINE)

            assertDoesNotRead("Upgrade held.")
            assertOfflineLine(showing = false)
        }
    }

    // ── The other nine controls ─────────────────────────────────────────────────────────────
    //
    // **Ten controls can be held and one of them being right proves nothing about the other nine.**
    // Each answers a different `ClientVerb`, each is matched back to the queue by a different
    // question, and the match is what the amber ghost's tap depends on — so a control whose key
    // lookup named the wrong verb would go amber and then refuse to be taken back, silently. The
    // pattern below is deliberately identical for each: hold it, read the sentence, press the ghost,
    // watch it go.

    // The square only exists on a row that has a completion to be told about, so the fixture is a
    // colony with a mine already going up — an idle row has nothing to offer and correctly draws no
    // control at all.
    @Test
    fun `a bell tapped with no signal is held and can be taken back`() {
        app(saved = upgrading(), api = offlineServer()) {
            tapTheWatchOn(BuildingType.METAL_MINE)
            waitUntilItReads("Watch held")

            tapTheWatchOn(BuildingType.METAL_MINE)

            assertDoesNotRead("Watch held")
            assertReads("0 actions held")
        }
    }

    @Test
    fun `a hull bought with no signal is held and can be taken back`() {
        app(saved = wealthy(), api = offlineServer()) {
            open(OltreTab.SHIPYARD)
            buyAHull()
            waitUntilItReads("Build held.")

            buyAHull()

            assertDoesNotRead("Build held.")
            assertReads("0 actions held")
        }
    }

    // **A build and its alert are one sentence**, which is the one card in the app that can say two
    // things at once — and the reason `HeldUiState` carries both flags rather than each control
    // carrying its own.
    //
    // The fixture is a colony that already has a hull on the slipway and asks per item, because the
    // square is absent otherwise: under `BY_CATEGORY` every hull is announced by its kind and the
    // per-order control has nothing left to decide.
    @Test
    fun `a hull and its alert held together say so in one line`() {
        app(saved = withYard(), api = offlineServer()) {
            open(OltreTab.SHIPYARD)
            buyAHull()
            waitUntilItReads("Build held.")

            tapTheAlertOn(ShipType.SKIFF)

            waitUntilItReads("Build held, and the alert held off with it.")
        }
    }

    // **The hull card's own bell, taken back** — a third control on the one card that can hold two
    // things, and the one whose lookup is keyed by hull type rather than by row.
    @Test
    fun `a hull alert tapped with no signal is held and can be taken back`() {
        app(saved = withYard(), api = offlineServer()) {
            open(OltreTab.SHIPYARD)
            tapTheAlertOn(ShipType.SKIFF)
            waitUntilItReads("1 action held")

            tapTheAlertOn(ShipType.SKIFF)

            waitUntilItReads("0 actions held")
        }
    }

    @Test
    fun `a research project started with no signal is held and can be taken back`() {
        app(saved = withLab(), api = offlineServer()) {
            open(OltreTab.RESEARCH)
            startTheFirstProject()
            waitUntilItReads("1 action held")

            startTheFirstProject()

            waitUntilItReads("0 actions held")
        }
    }

    // **The settings sheet holds three shapes of control** and they are matched back to the queue
    // three different ways: a category row by its category, the mode and the delivery by a stop the
    // queue has to be searched *backwards* for — the last one asked wins, because a player who taps
    // twice meant the second tap.
    @Test
    fun `an alert category toggled with no signal is held and can be taken back`() {
        app(saved = colony(), api = offlineServer()) {
            openTheSettings()
            toggleCategory(AlertCategory.HULLS)
            waitUntilItReads("1 action held")

            toggleCategory(AlertCategory.HULLS)

            waitUntilItReads("0 actions held")
        }
    }

    @Test
    fun `an alert mode chosen with no signal is held and can be taken back`() {
        app(saved = colony(), api = offlineServer()) {
            openTheSettings()
            chooseByCategory()
            waitUntilItReads("1 action held")

            chooseByCategory()

            waitUntilItReads("0 actions held")
        }
    }

    @Test
    fun `a delivery chosen with no signal is held and can be taken back`() {
        app(saved = colony(), api = offlineServer()) {
            openTheSettings()
            chooseDelivery(AlertDelivery.PER_CATEGORY)
            waitUntilItReads("1 action held")

            chooseDelivery(AlertDelivery.PER_CATEGORY)

            waitUntilItReads("0 actions held")
        }
    }

    private fun offlineServer(): FakeOltreApi = FakeOltreApi().apply {
        colony = null
        founds = null
        offline = true
    }
}

// **A verb already on disk when the app opens**, written the way the outbox writes one rather than
// composed by hand: a fixture that invented its own key would still queue, and the one property that
// matters here is that the app reads the file the outbox wrote.
private fun queuedUpgrade(): VerbEnvelope = VerbEnvelope(
    verb = ClientVerb.StartUpgrade(BuildingType.METAL_MINE),
    clientInstant = TEST_NOW,
    idempotencyKey = IdempotencyKey("queued-before-the-launch"),
)

// **A colony that can afford whatever the test taps**, so a held assertion is never really an
// assertion about a price. The opening stock covers a mine and nothing else; a hull and a project
// both need more than a first launch has.
private fun wealthy(): GameSnapshot = colony().let { opening ->
    opening.copy(
        state = opening.state.copy(
            resources = Resources.of(metal = 5_000_000, crystal = 5_000_000, deuterium = 5_000_000),
        ),
    )
}

// **A Robotics Factory, because the applied branch is gated behind one.** Photovoltaics and
// Extraction both require level 1, and a fresh colony has none — so a colony that could pay for
// every project on the screen would still be offered none of them.
private fun withLab(): GameSnapshot = wealthy().let { rich ->
    rich.copy(
        state = rich.state.copy(
            buildings = rich.state.buildings.withLevel(BuildingType.ROBOTICS_FACTORY, BuildingLevel(1)),
        ),
    )
}

// **A facility already going up**, because the watch square is offered on a row that has a
// completion to be told about and on no other. Started through the real verb rather than composed,
// so the row the square hangs on is the one the app actually draws.
private fun upgrading(): GameSnapshot = wealthy().let { rich ->
    rich.copy(
        state = assertIs<StartUpgradeResult.Started>(
            startUpgrade(
                rich.state.copy(alerts = AlertSettings.CARRIED_FORWARD),
                BuildingType.METAL_MINE,
                at = TEST_NOW,
            ),
        ).state,
    )
}

// **A hull on the slipway and a colony that asks per item.** Both halves are needed for the hull
// card to carry a square at all: no order means nothing to announce, and `BY_CATEGORY` answers the
// question one level up and takes the control off the card.
private fun withYard(): GameSnapshot = wealthy().let { rich ->
    rich.copy(
        state = assertIs<BuildShipsResult.Started>(
            buildShips(
                rich.state.copy(alerts = AlertSettings.CARRIED_FORWARD),
                Ships.of(ShipType.SKIFF, 1),
                at = TEST_NOW,
            ),
        ).state,
    )
}

private fun colony(): GameSnapshot = GameSnapshot(
    lastUpdatedAt = TEST_NOW,
    debugUsed = false,
    // The opening stock covers a mine outright — `OpeningBalanceTest` pins it — so the Metal Mine's
    // button is live on the first frame and a tap on it is a verb rather than a ghost.
    state = GameState.initial(GalaxySeed(TEST_NOW.toEpochMilliseconds())),
)
