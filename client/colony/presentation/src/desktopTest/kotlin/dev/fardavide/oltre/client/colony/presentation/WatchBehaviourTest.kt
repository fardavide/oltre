package dev.fardavide.oltre.client.colony.presentation

import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.client.design.component.WatchUiState
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.ResourceKind
import org.junit.Test

// What the square does, from the outside. The rules it enforces are core's — one watch, and the
// undo is the same tap — so what a screen test can say is narrower and still the whole of what the
// player touches: the square is present exactly where there is an instant to book, and every tap on
// one asks for the watch by name.
class WatchBehaviourTest {

    @Test
    fun `tapping the square asks for the watch on that row`() {
        facilityList(listOf(waiting(BuildingType.METAL_MINE), waiting(BuildingType.CRYSTAL_MINE))) {
            tapTheWatchOn(BuildingType.CRYSTAL_MINE)

            assertAskedToWatch(BuildingType.CRYSTAL_MINE)
        }
    }

    // There is no separate "unwatch": the lit square is the same control, and tapping it is how you
    // take the watch back. So the screen sends the same message either way and the model decides.
    @Test
    fun `tapping the lit square sends the same message as tapping an unlit one`() {
        facilityList(
            listOf(
                waiting(BuildingType.METAL_MINE, watch = WatchUiState.Booked("→ affordable 19:51")),
                waiting(BuildingType.CRYSTAL_MINE),
            ),
        ) {
            tapTheWatchOn(BuildingType.METAL_MINE)
            tapTheWatchOn(BuildingType.CRYSTAL_MINE)

            assertAskedToWatch(BuildingType.METAL_MINE, BuildingType.CRYSTAL_MINE)
        }
    }

    @Test
    fun `the watched row says the instant it named`() {
        facilityRow(waiting(BuildingType.METAL_MINE, watch = WatchUiState.Booked("→ affordable 19:51"))) {
            assertReads("→ affordable 19:51")
        }
    }

    @Test
    fun `a row with nothing to book has no square at all`() {
        facilityRow(waiting(BuildingType.METAL_MINE, watch = null)) {
            assertHasNoWatch(BuildingType.METAL_MINE)
        }
    }

    private fun waiting(
        building: BuildingType,
        watch: WatchUiState? = WatchUiState.Offered,
    ) = FacilityRowUiState(
        building = building,
        name = "Metal Mine",
        compactName = "Metal Mine",
        level = BuildingLevel(12),
        costs = listOf(CostChipUiState(kind = ResourceKind.METAL, amount = "12,458", short = true)),
        duration = "6h 12m",
        action = FacilityActionUiState.AffordableIn("in 8h 13m"),
        power = null,
        fix = null,
        watch = watch,
        finishedWhileAway = false,
    )
}
