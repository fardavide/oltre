package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.net.data.FakeOltreApi
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.DecodeResult
import dev.fardavide.oltre.core.Event
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameSave
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Research
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.SurveyJob
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.Technology
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.hours

class LegacySaveAppBehaviourTest {

    @Test
    fun `a colony saved before research existed keeps its facilities and gains usable destinations offline`() {
        // given a levelled facility in a save that predates both research and the galaxy
        val played = GameState.initial(GalaxySeed(20_260_807)).let { state ->
            state.copy(buildings = state.buildings.withLevel(BuildingType.METAL_MINE, BuildingLevel(7)))
        }
        val legacy = legacySave(GameSnapshot(lastUpdatedAt = TEST_NOW, state = played), schema = 2)
        assertIs<DecodeResult.Success>(GameSave.decode(legacy))

        // when the app restores the device's old colony
        app(saved = null, legacy = legacy, api = FakeOltreApi().apply { offline = true }) {
            // then its facility remains and both newly added destinations render their real state
            assertReads("LV 7")
            open(OltreTab.RESEARCH)
            assertRowsReading("LV 0", count = 8)
            open(OltreTab.GALAXY)
            assertReads("OF 250 CHARTED")
        }
    }

    @Test
    fun `a colony from before adaptation keeps its earned research when opened offline`() {
        // given a real applied branch from before the adaptation and fleet features existed
        val played = GameState.initial(GalaxySeed(20_260_807)).copy(
            research = Research.initial()
                .withLevel(Technology.PHOTOVOLTAICS, TechLevel(2))
                .withLevel(Technology.EXTRACTION, TechLevel(3)),
        )
        val legacy = legacySave(GameSnapshot(lastUpdatedAt = TEST_NOW, state = played), schema = 4)
        assertIs<DecodeResult.Success>(GameSave.decode(legacy))

        // when the upgrade launches with only this device's colony available
        app(saved = null, legacy = legacy, api = FakeOltreApi().apply { offline = true }) {
            open(OltreTab.RESEARCH)

            // then both earned levels survive alongside the newly introduced level-zero branch
            assertReads("Photovoltaics")
            assertReads("Extraction")
            assertRowsReading("LV 2", count = 1)
            assertRowsReading("LV 3", count = 1)
            assertRowsReading("LV 0", count = 6)
        }
    }

    @Test
    fun `a probe saved before scouts existed returns one hull on an offline launch`() {
        // given one old probe in flight and no scout in the idle pool
        val played = GameState.initial(GalaxySeed(20_260_807)).let { state ->
            state.copy(
                ships = Ships.NONE,
                surveys = listOf(
                    SurveyJob(
                        SystemAddress(state.galaxy.home.galaxy, 1),
                        startedAt = TEST_NOW - 3.hours,
                        completesAt = TEST_NOW - 2.hours,
                        announced = false,
                    ),
                ),
            )
        }
        val legacy = legacySave(
            GameSnapshot(lastUpdatedAt = TEST_NOW - 3.hours, state = played),
            schema = 12,
        )
        assertIs<DecodeResult.Success>(GameSave.decode(legacy))

        // when the launch both migrates and settles the overdue probe
        app(saved = null, legacy = legacy, api = FakeOltreApi().apply { offline = true }) {
            open(OltreTab.SHIPYARD)

            // then the scout card sees one returned hull rather than a second migration grant
            assertReads("Scout")
            assertReads("1 owned · 1 idle")
            assertAlertsBooked(0)
        }
    }

    @Test
    fun `a colony saved before experience existed opens at its earned level with its old alert controls`() {
        // given a played colony whose pre-experience save only recorded its accomplishments
        val history = (2..21).map { level ->
            Event.BuildCompleted(BuildingType.METAL_MINE, BuildingLevel(level), at = TEST_NOW - level.hours)
        } + (1..4).map { system ->
            Event.SurveyCompleted(SystemAddress(1, system), worldsFound = 4, at = TEST_NOW - system.hours)
        }
        val legacy = legacySave(
            GameSnapshot(
                lastUpdatedAt = TEST_NOW,
                state = GameState.initial(GalaxySeed(20_260_807)).copy(
                    resources = Resources.of(),
                    eventLog = history,
                ),
            ),
            schema = 15,
        )
        assertIs<DecodeResult.Success>(GameSave.decode(legacy))

        // when the saved history is the only colony the launch can restore
        app(saved = null, legacy = legacy, api = FakeOltreApi().apply { offline = true }) {
            // then the once-only experience fold reaches the player strip and the old per-item UI
            assertThePlayerStripReads("LV 4")
            assertColonyOffersSquares(offered = true)
        }
    }

    // Reverse only the additive fields introduced after the requested release. Encoding first
    // preserves the real wire shapes; the pre-launch decode above prevents a reset passing a test.
    private fun legacySave(snapshot: GameSnapshot, schema: Int): String {
        val current = Json.parseToJsonElement(GameSave.encode(snapshot)).jsonObject
        var state = current.getValue("state").jsonObject
        var galaxy = state.getValue("galaxy").jsonObject - "charted"
        var research = state.getValue("research").jsonObject
        state = JsonObject(state - setOf("alerts", "experience"))
        if (schema < 15) {
            state = JsonObject(state - "announceFlights" + listOf("runs", "surveys").associateWith { key ->
                JsonArray(state.getValue(key).jsonArray.map { JsonObject(it.jsonObject - "announced") })
            })
        }
        if (schema < 14) state = JsonObject(state - "hullAlerts")
        if (schema < 13) research = JsonObject(research - "propulsion")
        if (schema < 12) galaxy = galaxy - "pinned"
        if (schema < 11) {
            galaxy = galaxy - "deposits"
            research = JsonObject(research - "prospecting")
        }
        if (schema < 10) state = JsonObject(state - "yard")
        if (schema < 9) state = JsonObject(state - setOf("watching", "subscribed"))
        if (schema < 8) state = JsonObject(state - setOf("ships", "runs") + ("returningFleet" to JsonNull))
        if (schema < 6) state = JsonObject(state - "surveys")
        if (schema < 5) {
            state = JsonObject(state - "activeAdaptation")
            research = JsonObject(research - setOf("thermal", "gravitic", "atmospheric"))
        }
        state = JsonObject(state + ("galaxy" to JsonObject(galaxy)) + ("research" to research))
        if (schema < 4) state = JsonObject(state - "galaxy")
        if (schema < 3) state = JsonObject(state - setOf("research", "activeResearch"))
        val envelope = if (schema < 7) current - "debugUsed" else current
        return JsonObject(envelope + ("schemaVersion" to JsonPrimitive(schema)) + ("state" to state)).toString()
    }
}
