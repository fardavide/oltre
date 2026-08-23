package dev.fardavide.oltre.core

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class NotificationSettingsTest {

    // The property the whole feature rests on: a switch exists for every kind of news, so nothing
    // the game can say is ungovernable. Written as a set comparison rather than seven assertions
    // because what is being asserted is *coverage* — if a ninth `FutureEvent` lands and is mapped
    // onto an existing category, this is the test that notices.
    @Test
    fun `every kind of news the game can deliver has a category of its own`() {
        // given one of each member core can predict
        val events = listOf(
            FutureEvent.BuildCompletes(BuildingType.METAL_MINE, BuildingLevel(2), at = LATER),
            FutureEvent.ResearchCompletes(Technology.PHOTOVOLTAICS, TechLevel(1), at = LATER),
            FutureEvent.AdaptationCompletes(AdaptationTechnology.THERMAL, TechLevel(1), at = LATER),
            FutureEvent.ShipsComplete(ShipType.SKIFF, at = LATER),
            FutureEvent.SurveyLands(
                target = SystemAddress(galaxy = 1, system = 2),
                worldsFound = 3,
                settleable = 0,
                announced = true,
                at = LATER,
            ),
            FutureEvent.FleetReturns(
                target = GalaxyCoordinate(galaxy = 1, system = 2, slot = 3),
                ships = Ships.NONE,
                cargo = Resources.of(metal = 100),
                dispatchedAt = EPOCH,
                announced = true,
                at = LATER,
            ),
            FutureEvent.AffordableAt(
                purchase = WatchedPurchase.Facility(
                    building = BuildingType.SOLAR_PLANT,
                    toLevel = BuildingLevel(3),
                    cost = Resources.of(metal = 100),
                ),
                at = LATER,
            ),
        )

        // when
        val categories = events.map { it.category() }

        // then every category is claimed and no two events share one
        assertEquals(NotificationCategory.entries.toSet(), categories.toSet())
        assertEquals(events.size, categories.toSet().size, "two kinds of news share a category")
    }

    // The two branches were split into two research slots at 0.12.2 because they are not the same
    // decision. A settings screen that re-merged them would undo that in the one place a player
    // looks to say what they care about.
    @Test
    fun `research and adaptations are governed separately`() {
        assertEquals(
            NotificationCategory.RESEARCH,
            FutureEvent.ResearchCompletes(Technology.PHOTOVOLTAICS, TechLevel(1), at = LATER).category(),
        )
        assertEquals(
            NotificationCategory.ADAPTATIONS,
            FutureEvent.AdaptationCompletes(AdaptationTechnology.THERMAL, TechLevel(1), at = LATER).category(),
        )
    }

    // Nothing about an existing colony changes on the launch that ships this: no alert starts
    // firing that was not asked for on the row it is about.
    @Test
    fun `a colony that has never opened the settings screen is asked per item`() {
        assertEquals(NotificationScope.AD_HOC, NotificationSettings.DEFAULT.scope)
        assertEquals(NotificationGrouping.SINGLE, NotificationSettings.DEFAULT.grouping)
    }

    // The difference between a mode and a mode that looks broken: the first switch into
    // by-category is a working state rather than silence.
    @Test
    fun `every category starts on so the first switch into by-category is not silence`() {
        assertEquals(NotificationCategory.entries.toSet(), NotificationSettings.DEFAULT.categories)
    }

    // It is written to a preferences file that carries no schema version and cannot migrate, so
    // what it encodes to is a shape a later build has to keep reading.
    @Test
    fun `settings survive a round trip through JSON`() {
        // given a colony that has changed both settings and muted two categories
        val settings = NotificationSettings(
            scope = NotificationScope.BY_CATEGORY,
            grouping = NotificationGrouping.SUMMARY,
            categories = setOf(NotificationCategory.FACILITIES, NotificationCategory.FLEET_RETURNS),
        )

        // when
        val encoded = Json.encodeToString(NotificationSettings.serializer(), settings)
        val decoded = Json.decodeFromString(NotificationSettings.serializer(), encoded)

        // then
        assertEquals(settings, decoded)
    }

    private companion object {

        val EPOCH: Instant = Instant.fromEpochSeconds(0)
        val LATER: Instant = EPOCH + 3.hours
    }
}
