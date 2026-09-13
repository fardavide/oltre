package dev.fardavide.oltre.client

import dev.fardavide.oltre.core.AlertSettings
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Resources
import kotlin.test.Test

class ResearchPriceAlertAppBehaviourTest {

    @Test
    fun `watching an unaffordable project books its price rather than a completion`() {
        app(saved = unfundedLab()) {
            open(OltreTab.RESEARCH)
            assertAlertsBooked(0)

            tapTheWatchOnTheFirstProject()

            assertPriceAlertBooked("affordable-PHOTOVOLTAICS", "You can afford Photovoltaics")
        }
    }

    @Test
    fun `watching an unaffordable ladder books the adaptation price alert`() {
        app(saved = unfundedLab()) {
            open(OltreTab.RESEARCH)
            assertAlertsBooked(0)

            tapTheWatchOnTheThermalLadder()

            assertPriceAlertBooked("affordable-THERMAL", "You can afford Thermal Adaptation")
        }
    }

    private fun unfundedLab(): GameSnapshot = GameSnapshot(
        lastUpdatedAt = TEST_NOW,
        state = GameState.initial(GalaxySeed(20_260_807)).let { state ->
            state.copy(
                resources = Resources.of(),
                buildings = state.buildings
                    .withLevel(BuildingType.ROBOTICS_FACTORY, BuildingLevel(2))
                    .withLevel(BuildingType.DEUTERIUM_SYNTHESIZER, BuildingLevel(1)),
                alerts = AlertSettings.CARRIED_FORWARD,
            )
        },
    )
}
