package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// The yard's own control, and the first in the game with three states rather than two. A facility
// row can only be asked one question — tell me when this lands — because one row is one job; a hull
// card stands over a *queue*, so there are two questions worth asking and the second tap is what
// asks the other one.
//
// What these pin is the cycle, the per-type independence, and the rule that makes an ask about the
// hulls the player ordered rather than a standing preference about the card.
class HullAlertTest {

    @Test
    fun `a card nobody has tapped asks for nothing`() {
        assertEquals(emptyMap(), GameState.initial(SEED).hullAlerts)
    }

    @Test
    fun `one tap asks to be told when the whole order is done`() {
        // given a queue of three skiffs
        val queued = queueing(ShipType.SKIFF, hulls = 3)

        // when
        val asked = cycleHullAlert(queued, ShipType.SKIFF)

        // then
        assertEquals(HullAlert.WHEN_ALL_DONE, asked.hullAlerts[ShipType.SKIFF])
    }

    @Test
    fun `a second tap asks about every hull instead`() {
        // given — the design's second question, and the reason this control has three states: a
        // player who ordered four hulls may want four buzzes or one, and both are reasonable
        val asked = cycleHullAlert(queueing(ShipType.SKIFF, hulls = 3), ShipType.SKIFF)

        // when
        val each = cycleHullAlert(asked, ShipType.SKIFF)

        // then
        assertEquals(HullAlert.EACH_HULL, each.hullAlerts[ShipType.SKIFF])
    }

    @Test
    fun `a third tap takes the ask back`() {
        // given — the undo is the same control, exactly as it is for the square on a facility row.
        // Nothing here asks for confirmation, because nothing here is expensive to get wrong.
        var state = queueing(ShipType.SKIFF, hulls = 3)
        repeat(3) { state = cycleHullAlert(state, ShipType.SKIFF) }

        // then — absent rather than a third constant meaning "off": an entry that says nothing is
        // an entry the notification layer has to remember to ignore
        assertEquals(emptyMap(), state.hullAlerts)
    }

    @Test
    fun `the cycle comes back round rather than sticking at the end`() {
        val state = queueing(ShipType.SKIFF, hulls = 2)
        val cycle = (1..4).runningFold(state) { previous, _ -> cycleHullAlert(previous, ShipType.SKIFF) }
            .map { it.hullAlerts[ShipType.SKIFF] }

        assertEquals(
            listOf(null, HullAlert.WHEN_ALL_DONE, HullAlert.EACH_HULL, null, HullAlert.WHEN_ALL_DONE),
            cycle,
        )
    }

    @Test
    fun `each hull type is asked about on its own`() {
        // given — one control per card was Davide's call, 2026-08-22. The queue is shared and
        // serial, and the *question* is per hull: a player waiting on a hauler is not waiting on
        // the two skiffs ahead of it.
        val mixed = alsoQueueing(queueing(ShipType.SKIFF, hulls = 1), ShipType.HAULER, hulls = 1)

        // when
        val asked = cycleHullAlert(mixed, ShipType.HAULER)

        // then
        assertEquals(mapOf(ShipType.HAULER to HullAlert.WHEN_ALL_DONE), asked.hullAlerts)
    }

    @Test
    fun `an ask is spent when the last hull of its type leaves the yard`() {
        // given — the rule `withoutSpentWatch` already states for a subscription, and it is the same
        // rule for the same reason: an ask is about the hulls the player ordered, not a standing
        // preference about the card. Order another skiff tomorrow and the control is unlit, because
        // that is a second decision.
        val asked = cycleHullAlert(queueing(ShipType.SKIFF, hulls = 2), ShipType.SKIFF)
        val lastDue = asked.yard.last().completesAt

        // when
        val later = advance(asked, from = EPOCH, to = lastDue)

        // then
        assertEquals(emptyList(), later.yard)
        assertEquals(emptyMap(), later.hullAlerts)
    }

    @Test
    fun `an ask survives while a hull of its type is still on the slipway`() {
        val asked = cycleHullAlert(queueing(ShipType.SKIFF, hulls = 2), ShipType.SKIFF)
        val firstDue = asked.yard.first().completesAt

        val later = advance(asked, from = EPOCH, to = firstDue)

        assertEquals(1, later.yard.size)
        assertEquals(HullAlert.WHEN_ALL_DONE, later.hullAlerts[ShipType.SKIFF])
    }

    @Test
    fun `one type landing does not clear the ask on another`() {
        // The mirror of the test above at the map level: a fold that rebuilt the whole map from the
        // types still queued would be right, and one that cleared the map when *any* type emptied
        // would pass every other test in this file.
        val mixed = alsoQueueing(queueing(ShipType.SKIFF, hulls = 1), ShipType.HAULER, hulls = 1)
        val asked = cycleHullAlert(cycleHullAlert(mixed, ShipType.SKIFF), ShipType.HAULER)
        val skiffDue = asked.yard.first { it.ship == ShipType.SKIFF }.completesAt

        // when — walked to the instant the skiff lands and no further, so the hauler behind it is
        // still on the slipway
        val later = advance(asked, from = EPOCH, to = skiffDue)

        assertEquals(mapOf(ShipType.HAULER to HullAlert.WHEN_ALL_DONE), later.hullAlerts)
    }

    @Test
    fun `tapping a card with nothing of its type in the yard asks for nothing`() {
        // There is no control on an idle card — the absence of one, the way this app says "there is
        // nothing to wait for" everywhere else. Core states it anyway rather than trusting the
        // screen, for `toggleAlert`'s own reason: the screen renders a snapshot and the tap is
        // applied to a state that has been advanced since, so the last hull may have landed in
        // between.
        val empty = wealthy(GameState.initial(SEED))

        assertEquals(empty, cycleHullAlert(empty, ShipType.SKIFF))
    }

    @Test
    fun `an ask survives a round trip`() {
        val state = cycleHullAlert(cycleHullAlert(queueing(ShipType.SKIFF, hulls = 2), ShipType.SKIFF), ShipType.SKIFF)
        val snapshot = GameSnapshot(lastUpdatedAt = EPOCH, state = state)

        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(GameSave.encode(snapshot))).snapshot

        assertEquals(mapOf(ShipType.SKIFF to HullAlert.EACH_HULL), decoded.state.hullAlerts)
    }

    @Test
    fun `nothing else about the colony moves`() {
        val queued = queueing(ShipType.SKIFF, hulls = 2)

        val asked = cycleHullAlert(queued, ShipType.SKIFF)

        assertEquals(queued.yard, asked.yard)
        assertEquals(queued.resources, asked.resources)
        assertEquals(queued.subscribed, asked.subscribed)
        assertEquals(queued.eventLog, asked.eventLog)
        assertNull(asked.watching)
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────

    private fun queueing(ship: ShipType, hulls: Int): GameState =
        alsoQueueing(wealthy(GameState.initial(SEED)), ship, hulls)

    // A second order on top of an existing queue, placed through the verb rather than written into
    // `yard` by hand — which the fixtures here tried first and could not do: the serial rule in
    // `GameState.init` refuses a hand-chained queue the moment the durations move, and the hull
    // curve is nobody's business here.
    private fun alsoQueueing(state: GameState, ship: ShipType, hulls: Int): GameState =
        assertIs<BuildShipsResult.Started>(buildShips(state, Ships.of(ship, hulls), at = EPOCH)).state

    private fun wealthy(state: GameState): GameState =
        state.copy(resources = Resources.of(metal = 100_000, crystal = 100_000, deuterium = 100_000))

    private companion object {
        val EPOCH = Instant.fromEpochMilliseconds(0)
        val SEED = GalaxySeed(20_260_807)
    }
}
