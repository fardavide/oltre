package dev.fardavide.oltre.client

import dev.fardavide.oltre.core.AlertCategory
import dev.fardavide.oltre.core.AlertDelivery
import dev.fardavide.oltre.core.AlertMode
import dev.fardavide.oltre.core.AlertSettings
import dev.fardavide.oltre.core.BuildJob
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResearchJob
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.Technology
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours

// **The sheet driven through the composition root, and the only place that can see what it is for.**
// `AlertSettingsTest` proves what `core` does with the three verbs; `AlertDeliveryTest` proves which
// alerts a state produces; `AlertSheetBehaviourTest` proves the chips call back. None of them can see
// the three links between — the frame that raises the sheet, the `prefer` lambda that commits, and
// the `notifications.sync` inside that commit which is the only thing that actually books anything.
//
// **Counting alerts is what makes this an assertion about the feature rather than about a control.**
// A ladder that moves and books nothing is exactly the failure an unconditional commit exists to
// prevent — nothing here writes an event, so `act` would have declined to save at all.
class AlertSheetAppBehaviourTest {

    @Test
    fun `choosing by category announces a job nobody ever tapped a bell for`() {
        // The whole of what this version is, from the outside. The colony has a mine running and
        // nothing in `subscribed`, so under the mode a 0.17 save carries it is silent.
        app(saved = snapshot(building())) {
            assertAlertsBooked(0)

            openTheSettings()
            chooseByCategory()

            assertAlertsBooked(1)
        }
    }

    @Test
    fun `choosing per item again takes it back`() {
        // The switches are not cleared when the mode leaves them, and neither is the colony: this
        // save has subscribed to nothing, so going back is going back to silence rather than to some
        // third answer.
        app(saved = snapshot(building())) {
            openTheSettings()
            chooseByCategory()
            assertAlertsBooked(1)

            chooseMode(AlertMode.PER_ITEM)

            assertAlertsBooked(0)
        }
    }

    @Test
    fun `turning a category off silences it without touching the rest`() {
        app(saved = snapshot(buildingAndResearching())) {
            openTheSettings()
            chooseByCategory()
            assertAlertsBooked(2)

            toggleCategory(AlertCategory.FACILITIES)

            assertAlertsBooked(1)
        }
    }

    @Test
    fun `one in total books one notification per instant and all of them under one tray entry`() {
        // Davide's call, 2026-08-23, end to end: the tray holds one notification, and each landing
        // brings it up to date rather than adding a second.
        app(saved = snapshot(buildingAndResearching())) {
            openTheSettings()
            chooseByCategory()
            chooseDelivery(AlertDelivery.TOTAL)

            assertAlertsBooked(2)
            assertOneTrayEntry()
        }
    }

    @Test
    fun `the colony behind the sheet stops carrying squares`() {
        // **The dead-control rule met head on.** Under `BY_CATEGORY` a facility row has nothing left
        // to ask, so the square is not disabled — it is gone, which is what a missing square has
        // meant on these rows since the watch slice. A row that kept a bell the scheduler no longer
        // consults would be the worst kind of defect this repository names.
        app(saved = snapshot(building())) {
            assertColonyOffersSquares(true)

            openTheSettings()
            chooseByCategory()
            dismissTheSettings()

            assertColonyOffersSquares(false)
        }
    }

    // A mine two hours out and nothing asked about it — the state a colony carried forward from 0.17
    // is in until somebody taps a square.
    private fun building(): GameState = perItem().copy(
        builds = mapOf(
            BuildingType.METAL_MINE to BuildJob(
                building = BuildingType.METAL_MINE,
                toLevel = BuildingLevel(2),
                startedAt = TEST_NOW,
                completesAt = TEST_NOW + 2.hours,
            ),
        ),
    )

    // **Two different kinds in flight, not two facilities**, and the distinction is the whole test:
    // one switch has to be shown reaching one kind and leaving the other alone, which two mines
    // cannot demonstrate. The project lands well outside the five-minute chain, so `One each` really
    // is two alerts and the counts below are measuring the gate rather than the grouping.
    private fun buildingAndResearching(): GameState = building().copy(
        activeResearch = ResearchJob(
            technology = Technology.EXTRACTION,
            toLevel = TechLevel(1),
            startedAt = TEST_NOW,
            completesAt = TEST_NOW + 6.hours,
        ),
    )

    private fun perItem(): GameState =
        GameState.initial(GalaxySeed(20_260_807L)).copy(alerts = AlertSettings.CARRIED_FORWARD)

    private fun snapshot(state: GameState): GameSnapshot = GameSnapshot(lastUpdatedAt = TEST_NOW, state = state)
}
