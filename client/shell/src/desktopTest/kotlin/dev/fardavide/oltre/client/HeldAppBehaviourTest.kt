package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.net.data.FakeOltreApi
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.protocol.ClientVerb
import dev.fardavide.oltre.protocol.IdempotencyKey
import dev.fardavide.oltre.protocol.VerbEnvelope
import kotlin.test.Test
import kotlin.test.assertEquals

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

private fun colony(): GameSnapshot = GameSnapshot(
    lastUpdatedAt = TEST_NOW,
    debugUsed = false,
    // The opening stock covers a mine outright — `OpeningBalanceTest` pins it — so the Metal Mine's
    // button is live on the first frame and a tap on it is a verb rather than a ghost.
    state = GameState.initial(GalaxySeed(TEST_NOW.toEpochMilliseconds())),
)
