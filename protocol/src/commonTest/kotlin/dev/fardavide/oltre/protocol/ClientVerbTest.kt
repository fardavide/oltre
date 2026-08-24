package dev.fardavide.oltre.protocol

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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours

// **The registry the wire has and `core` deliberately does not.** `Advance.kt` states the same
// lesson about time — *"there is no registry here, and a job kind missing from this expression never
// completes"* — and the failure mode here is one step quieter: a verb `core` can apply and the
// protocol cannot carry is a control that works offline on the device it was written on and does
// nothing once the engine moves.
//
// Two holes have to be closed for that to be caught, and one test closes only one of them:
//
//   a member added to `ClientVerb` and to no sample   ->  `idOf` below stops compiling
//   an id added to `VerbId` and to no sample          ->  `every verb has a sample` fails
//
// So `VerbId` is a hand-written mirror of the sealed interface rather than something derived from
// it. Deriving it — `sealedSubclasses`, an `entries` list in the main source set — would make the
// two agree by construction, which is exactly the property that stops the pair catching anything.
private enum class VerbId {
    START_UPGRADE,
    START_RESEARCH,
    START_ADAPTATION,
    BUILD_SHIPS,
    START_RUN,
    START_SURVEY,
    TOGGLE_ALERT,
    CYCLE_HULL_ALERT,
    TOGGLE_FLIGHT_ALERTS,
    SET_ALERT_MODE,
    TOGGLE_ALERT_CATEGORY,
    SET_ALERT_DELIVERY,
}

private fun idOf(verb: ClientVerb): VerbId = when (verb) {
    is ClientVerb.StartUpgrade -> VerbId.START_UPGRADE
    is ClientVerb.StartResearch -> VerbId.START_RESEARCH
    is ClientVerb.StartAdaptation -> VerbId.START_ADAPTATION
    is ClientVerb.BuildShips -> VerbId.BUILD_SHIPS
    is ClientVerb.StartRun -> VerbId.START_RUN
    is ClientVerb.StartSurvey -> VerbId.START_SURVEY
    is ClientVerb.ToggleAlert -> VerbId.TOGGLE_ALERT
    is ClientVerb.CycleHullAlert -> VerbId.CYCLE_HULL_ALERT
    ClientVerb.ToggleFlightAlerts -> VerbId.TOGGLE_FLIGHT_ALERTS
    is ClientVerb.SetAlertMode -> VerbId.SET_ALERT_MODE
    is ClientVerb.ToggleAlertCategory -> VerbId.TOGGLE_ALERT_CATEGORY
    is ClientVerb.SetAlertDelivery -> VerbId.SET_ALERT_DELIVERY
}

// One of each, carrying values that are not the type's default — a sample that happened to encode
// the first enum constant everywhere would round-trip through a broken serializer.
internal val VERB_SAMPLES: List<ClientVerb> = listOf(
    ClientVerb.StartUpgrade(BuildingType.ROBOTICS_FACTORY),
    ClientVerb.StartResearch(Technology.PROPULSION),
    ClientVerb.StartAdaptation(AdaptationTechnology.GRAVITIC),
    ClientVerb.BuildShips(Ships.of(ShipType.SKIFF, 3)),
    ClientVerb.StartRun(
        target = GalaxyCoordinate(galaxy = 2, system = 118, slot = 4),
        gathering = ResourceKind.CRYSTAL,
        ships = Ships.of(ShipType.SKIFF, 2),
        window = 8.hours,
    ),
    ClientVerb.StartSurvey(SystemAddress(galaxy = 3, system = 47)),
    ClientVerb.ToggleAlert(WatchTarget.Ladder(AdaptationTechnology.ATMOSPHERIC)),
    ClientVerb.CycleHullAlert(ShipType.HAULER),
    ClientVerb.ToggleFlightAlerts,
    ClientVerb.SetAlertMode(AlertMode.BY_CATEGORY),
    ClientVerb.ToggleAlertCategory(AlertCategory.PROBES),
    ClientVerb.SetAlertDelivery(AlertDelivery.TOTAL),
)

class ClientVerbTest {

    @Test
    fun `every verb has a sample`() {
        assertEquals(VerbId.entries.toSet(), VERB_SAMPLES.mapTo(mutableSetOf(), ::idOf))
    }

    @Test
    fun `no two samples are the same verb`() {
        assertEquals(VERB_SAMPLES.size, VERB_SAMPLES.mapTo(mutableSetOf(), ::idOf).size)
    }

    @Test
    fun `every verb survives a round trip`() {
        VERB_SAMPLES.forEach { verb ->
            val text = Protocol.json.encodeToString(ClientVerb.serializer(), verb)
            assertEquals(verb, Protocol.json.decodeFromString(ClientVerb.serializer(), text), text)
        }
    }

    // The discriminator is a wire identifier from the first deploy — `#106` §8: this repo publishes
    // on merge, so a server has to keep answering the build already on somebody's phone. Renaming a
    // member is free; renaming what it encodes as is a wire break, and this is what makes the
    // difference visible in the diff rather than in a support message.
    @Test
    fun `the wire names are pinned`() {
        val encoded = VERB_SAMPLES.map { verb ->
            val json = Protocol.json.encodeToJsonElement(ClientVerb.serializer(), verb) as JsonObject
            (json["type"] as JsonPrimitive).content
        }
        assertEquals(
            listOf(
                "StartUpgrade",
                "StartResearch",
                "StartAdaptation",
                "BuildShips",
                "StartRun",
                "StartSurvey",
                "ToggleAlert",
                "CycleHullAlert",
                "ToggleFlightAlerts",
                "SetAlertMode",
                "ToggleAlertCategory",
                "SetAlertDelivery",
            ),
            encoded,
        )
    }

    // `#106` §3's table read back off the type. The split is not a detail of this slice: it is what
    // the outbox in `#112` branches on and what `#113` has to explain to a player who tapped a world
    // row on a train.
    @Test
    fun `only the two galaxy touching verbs refuse to be queued`() {
        val looking = VERB_SAMPLES.filter { it.offlineRule == OfflineRule.LOOK_DONT_ACT }
        assertEquals(
            setOf(VerbId.START_RUN, VerbId.START_SURVEY),
            looking.mapTo(mutableSetOf(), ::idOf),
        )
    }

    @Test
    fun `every other verb is queued and validated`() {
        val queued = VERB_SAMPLES.filter { it.offlineRule == OfflineRule.QUEUE_AND_VALIDATE }
        assertEquals(VERB_SAMPLES.size - 2, queued.size)
    }
}
