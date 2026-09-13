package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.notifications.data.LocalNotification
import dev.fardavide.oltre.core.AdaptationJob
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.AlertDelivery
import dev.fardavide.oltre.core.AlertSettings
import dev.fardavide.oltre.core.BuildJob
import dev.fardavide.oltre.core.BuildShipsResult
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResearchJob
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.YardJob
import dev.fardavide.oltre.core.buildShips
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.hours

class NotificationDeliveryAppBehaviourTest {

    @Test
    fun `one per category preserves the hull cards whole order choice`() {
        // given
        val ordered = assertIs<BuildShipsResult.Started>(
            buildShips(
                GameState.initial(GalaxySeed(20_260_807L)).copy(
                    alerts = AlertSettings.CARRIED_FORWARD,
                    resources = Resources.of(metal = 100_000, crystal = 100_000),
                ),
                Ships.of(ShipType.SKIFF, 3),
                at = TEST_NOW,
            ),
        ).state
        val lastHullAt = ordered.yard.last().completesAt

        app(saved = GameSnapshot(lastUpdatedAt = TEST_NOW, state = ordered)) {
            open(OltreTab.SHIPYARD)
            tapTheAlertOn(ShipType.SKIFF)
            openTheSettings()

            // when
            chooseDelivery(AlertDelivery.PER_CATEGORY)

            // then
            assertNotificationsBooked(
                LocalNotification(
                    id = "order-SKIFF",
                    collapseId = "order-SKIFF",
                    title = "3 hulls have left the yard",
                    body = "Your Skiff order is complete — they are in your fleet and ready to send.",
                    at = lastHullAt,
                ),
            )
        }
    }

    @Test
    fun `choosing one per category waits for all hulls and names their type once`() {
        // given
        val lastHullAt = TEST_NOW + 6.hours
        val state = GameState.initial(GalaxySeed(20_260_807L)).copy(
            alerts = AlertSettings.CARRIED_FORWARD,
            yard = (1..3).map { hull ->
                YardJob(
                    ship = ShipType.SKIFF,
                    startedAt = TEST_NOW + ((hull - 1) * 2).hours,
                    completesAt = TEST_NOW + (hull * 2).hours,
                )
            },
        )

        app(saved = GameSnapshot(lastUpdatedAt = TEST_NOW, state = state)) {
            openTheSettings()
            chooseByCategory()
            assertAlertsBooked(3)

            // when
            chooseDelivery(AlertDelivery.PER_CATEGORY)

            // then
            assertNotificationsBooked(
                LocalNotification(
                    id = "group-HULLS-${lastHullAt.toEpochMilliseconds()}",
                    collapseId = "group-HULLS-${lastHullAt.toEpochMilliseconds()}",
                    title = "3 hulls have left the yard",
                    body = "Skiff",
                    at = lastHullAt,
                ),
            )
        }
    }

    @Test
    fun `choosing one in total books simultaneous completions as one summary`() {
        // given
        val completesAt = TEST_NOW + 2.hours
        val state = GameState.initial(GalaxySeed(20_260_807L)).copy(
            alerts = AlertSettings.CARRIED_FORWARD,
            builds = mapOf(
                BuildingType.METAL_MINE to BuildJob(
                    building = BuildingType.METAL_MINE,
                    toLevel = BuildingLevel(2),
                    startedAt = TEST_NOW,
                    completesAt = completesAt,
                ),
            ),
            activeResearch = ResearchJob(
                technology = Technology.EXTRACTION,
                toLevel = TechLevel(1),
                startedAt = TEST_NOW,
                completesAt = completesAt,
            ),
            activeAdaptation = AdaptationJob(
                technology = AdaptationTechnology.THERMAL,
                toLevel = TechLevel(1),
                startedAt = TEST_NOW,
                completesAt = completesAt,
            ),
        )

        app(saved = GameSnapshot(lastUpdatedAt = TEST_NOW, state = state)) {
            openTheSettings()
            chooseByCategory()

            // when
            chooseDelivery(AlertDelivery.TOTAL)

            // then
            assertNotificationsBooked(
                LocalNotification(
                    id = "total-${completesAt.toEpochMilliseconds()}",
                    collapseId = "total",
                    title = "1 facility · 1 project · +1",
                    body = "Metal Mine · Extraction · Thermal Adaptation",
                    at = completesAt,
                ),
            )
        }
    }
}
