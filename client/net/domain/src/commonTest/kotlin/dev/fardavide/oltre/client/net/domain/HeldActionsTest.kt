package dev.fardavide.oltre.client.net.domain

import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.AlertCategory
import dev.fardavide.oltre.core.AlertDelivery
import dev.fardavide.oltre.core.AlertMode
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.WatchTarget
import dev.fardavide.oltre.protocol.ClientVerb
import dev.fardavide.oltre.protocol.IdempotencyKey
import dev.fardavide.oltre.protocol.VerbEnvelope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class HeldActionsTest {

    @Test
    fun `an empty queue holds nothing`() {
        val held = HeldActions(emptyList())

        assertEquals(0, held.count)
        assertNull(held.upgrade(BuildingType.METAL_MINE))
        assertNull(held.research(Technology.PROSPECTING))
        assertNull(held.adaptation(AdaptationTechnology.THERMAL))
        assertNull(held.build(ShipType.SKIFF))
        assertNull(held.watch(WatchTarget.Facility(BuildingType.METAL_MINE)))
        assertNull(held.hullAlert(ShipType.SKIFF))
        assertNull(held.flightAlert)
        assertNull(held.alertMode)
        assertNull(held.alertCategory(AlertCategory.FACILITIES))
        assertNull(held.alertDelivery)
    }

    @Test
    fun `NONE is the empty queue`() {
        assertEquals(0, HeldActions.NONE.count)
    }

    @Test
    fun `an upgrade is held for the facility it names and for no other`() {
        val held = held(ClientVerb.StartUpgrade(BuildingType.CRYSTAL_MINE) to "k1")

        assertEquals(IdempotencyKey("k1"), held.upgrade(BuildingType.CRYSTAL_MINE))
        assertNull(held.upgrade(BuildingType.METAL_MINE))
    }

    @Test
    fun `a project and an adaptation are held apart`() {
        val held = held(
            ClientVerb.StartResearch(Technology.PROPULSION) to "k1",
            ClientVerb.StartAdaptation(AdaptationTechnology.THERMAL) to "k2",
        )

        assertEquals(IdempotencyKey("k1"), held.research(Technology.PROPULSION))
        assertEquals(IdempotencyKey("k2"), held.adaptation(AdaptationTechnology.THERMAL))
        assertNull(held.research(Technology.EXTRACTION))
        assertNull(held.adaptation(AdaptationTechnology.GRAVITIC))
    }

    // The card is one hull type and the verb is a whole manifest, so the question a card asks is
    // whether the order it would place is in the queue — not whether the queue holds that exact order.
    @Test
    fun `a build is held for every hull type in its manifest`() {
        val held = held(ClientVerb.BuildShips(Ships(mapOf(ShipType.SKIFF to 2, ShipType.HAULER to 1))) to "k1")

        assertEquals(IdempotencyKey("k1"), held.build(ShipType.SKIFF))
        assertEquals(IdempotencyKey("k1"), held.build(ShipType.HAULER))
        assertNull(held.build(ShipType.SCOUT))
    }

    @Test
    fun `each watch target is held on its own`() {
        val held = held(
            ClientVerb.ToggleAlert(WatchTarget.Facility(BuildingType.SOLAR_PLANT)) to "k1",
            ClientVerb.ToggleAlert(WatchTarget.Ladder(AdaptationTechnology.ATMOSPHERIC)) to "k2",
        )

        assertEquals(IdempotencyKey("k1"), held.watch(WatchTarget.Facility(BuildingType.SOLAR_PLANT)))
        assertEquals(IdempotencyKey("k2"), held.watch(WatchTarget.Ladder(AdaptationTechnology.ATMOSPHERIC)))
        assertNull(held.watch(WatchTarget.Facility(BuildingType.METAL_MINE)))
        assertNull(held.watch(WatchTarget.Project(Technology.PROPULSION)))
    }

    @Test
    fun `a hull alert is held for the hull it names`() {
        val held = held(ClientVerb.CycleHullAlert(ShipType.HAULER) to "k1")

        assertEquals(IdempotencyKey("k1"), held.hullAlert(ShipType.HAULER))
        assertNull(held.hullAlert(ShipType.SKIFF))
    }

    @Test
    fun `the flight alert has one answer because there is one of it`() {
        val held = held(ClientVerb.ToggleFlightAlerts to "k1")

        assertEquals(IdempotencyKey("k1"), held.flightAlert)
    }

    @Test
    fun `a category bell is held for the category it names`() {
        val held = held(ClientVerb.ToggleAlertCategory(AlertCategory.PROBES) to "k1")

        assertEquals(IdempotencyKey("k1"), held.alertCategory(AlertCategory.PROBES))
        assertNull(held.alertCategory(AlertCategory.HULLS))
    }

    // A ladder is the one control where the queue carries a *destination*, and the whole reason the
    // design draws two lit chips: the server's stop stays accent and the asked one takes amber. So a
    // key alone would not be enough to draw it.
    @Test
    fun `a ladder answers with the stop that was asked for`() {
        val held = held(
            ClientVerb.SetAlertMode(AlertMode.PER_ITEM) to "k1",
            ClientVerb.SetAlertDelivery(AlertDelivery.TOTAL) to "k2",
        )

        assertEquals(HeldStop(AlertMode.PER_ITEM, IdempotencyKey("k1")), held.alertMode)
        assertEquals(HeldStop(AlertDelivery.TOTAL, IdempotencyKey("k2")), held.alertDelivery)
    }

    // A galaxy-touching verb refuses at the tap and is never written, so it cannot be in the file this
    // reads. Asserted rather than assumed: a queue that somehow held one must not make a world row
    // amber, because amber promises the tap will happen later and this game will not promise that.
    @Test
    fun `a verb that cannot be queued holds nothing`() {
        val held = held(
            ClientVerb.StartSurvey(SystemAddress(1, 1)) to "k1",
            ClientVerb.StartRun(
                target = GalaxyCoordinate(galaxy = 1, system = 1, slot = 3),
                gathering = ResourceKind.METAL,
                ships = Ships(mapOf(ShipType.SKIFF to 1)),
                window = 6.hours,
            ) to "k2",
        )

        assertEquals(0, held.count)
    }

    // The queue moves under the finger and the projection has to move with it. Two verbs for one
    // control cannot arise from a finger — a tap on a held control withdraws rather than stacking —
    // but a file written by an older build could hold them, and the newest is the one a withdrawal
    // has to reach.
    @Test
    fun `two verbs for one control answer with the newest key`() {
        val held = held(
            ClientVerb.StartUpgrade(BuildingType.METAL_MINE) to "old",
            ClientVerb.StartUpgrade(BuildingType.METAL_MINE) to "new",
        )

        assertEquals(IdempotencyKey("new"), held.upgrade(BuildingType.METAL_MINE))
    }

    // The chrome line counts *actions*, which is envelopes rather than controls: two facilities held
    // is two, and the player who reads "3 actions held" can count three ambers on the screens.
    @Test
    fun `the count is the number of queued verbs`() {
        val held = held(
            ClientVerb.StartUpgrade(BuildingType.METAL_MINE) to "k1",
            ClientVerb.StartUpgrade(BuildingType.CRYSTAL_MINE) to "k2",
            ClientVerb.ToggleFlightAlerts to "k3",
        )

        assertEquals(3, held.count)
    }

    private fun held(vararg queued: Pair<ClientVerb, String>): HeldActions = HeldActions(
        queued.map { (verb, key) ->
            VerbEnvelope(verb = verb, clientInstant = AT, idempotencyKey = IdempotencyKey(key))
        },
    )

    private companion object {

        val AT: Instant = Instant.fromEpochSeconds(1_700_000_000)
    }
}
