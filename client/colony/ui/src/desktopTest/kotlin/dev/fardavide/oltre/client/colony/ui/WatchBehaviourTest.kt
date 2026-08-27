package dev.fardavide.oltre.client.colony.ui

import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.client.design.component.HeldUiState
import dev.fardavide.oltre.client.design.component.VerdictUiState
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
                waiting(BuildingType.METAL_MINE, watch = WatchUiState.Booked(TextRes("→ affordable 19:51"))),
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
        facilityRow(waiting(BuildingType.METAL_MINE, watch = WatchUiState.Booked(TextRes("→ affordable 19:51")))) {
            assertReads("→ affordable 19:51")
        }
    }

    @Test
    fun `a row with nothing to book has no square at all`() {
        facilityRow(waiting(BuildingType.METAL_MINE, watch = null)) {
            assertHasNoWatch(BuildingType.METAL_MINE)
        }
    }

    // The other half of the square, and a second wiring of the same callback: a running row asks
    // about its completion. Two rows, so a tap that reported the wrong one would be visible.
    @Test
    fun `tapping the square on a running row asks about that row`() {
        facilityList(listOf(waiting(BuildingType.METAL_MINE), running(BuildingType.CRYSTAL_MINE))) {
            tapTheWatchOn(BuildingType.CRYSTAL_MINE)

            assertAskedToWatch(BuildingType.CRYSTAL_MINE)
        }
    }

    // A subscribed running row adds no line, because the row already prints when it lands.
    @Test
    fun `a subscribed running row says nothing it was not already saying`() {
        facilityRow(running(BuildingType.CRYSTAL_MINE, watch = WatchUiState.Subscribed)) {
            assertReads("→ LV 11 · done 11:23")
            assertNothingReads("→ affordable")
        }
    }

    // The one name the app authors twice. At a Slide Over's width the row calls the Robotics Factory
    // "Robotics", which is what "Requires Robotics 10" already calls it.
    @Test
    fun `a narrow window uses the row's short name`() {
        facilityRow(waiting(BuildingType.ROBOTICS_FACTORY), compact = true) {
            assertReads("Robotics")
            assertNothingReads("Robotics Factory")
        }
    }

    @Test
    fun `a phone-wide window uses the row's full name`() {
        facilityRow(waiting(BuildingType.ROBOTICS_FACTORY)) {
            assertReads("Robotics Factory")
        }
    }

    private fun running(
        building: BuildingType,
        watch: WatchUiState? = WatchUiState.Offered,
    ) = waiting(building, watch).copy(
        action = FacilityActionUiState.Upgrading(
            toLevel = BuildingLevel(11),
            countdown = TextRes("00:27:14"),
            progressPercent = 78,
            doneAt = TextRes("done 11:23"),
        ),
        level = BuildingLevel(10),
    )

    // Named for the facility it is about, so a test that renames one row can tell which it tapped.
    // The verdict says nothing about the row's own name, deliberately: the two tests about which
    // name a window uses read the card's words, and a verdict that repeated one would answer them.
    private fun waiting(
        building: BuildingType,
        watch: WatchUiState? = WatchUiState.Offered,
        held: HeldUiState = HeldUiState.NONE,
    ) = FacilityRowUiState(
        building = building,
        name = TextRes(if (building == BuildingType.ROBOTICS_FACTORY) "Robotics Factory" else "Metal Mine"),
        compactName = TextRes(if (building == BuildingType.ROBOTICS_FACTORY) "Robotics" else "Metal Mine"),
        level = BuildingLevel(12),
        costs = listOf(CostChipUiState(kind = ResourceKind.METAL, amount = TextRes("12,458"), short = true)),
        duration = TextRes("6h 12m"),
        action = FacilityActionUiState.AffordableIn(TextRes("in 8h 13m")),
        power = null,
        fix = null,
        watch = watch,
        verdict = VerdictUiState(label = TextRes("+281/h metal · back in 6h 40m"), compactLabel = TextRes("+281/h metal")),
        detail = FacilityDetailUiState(lines = emptyList(), ladder = emptyList(), pointer = null),
        finishedWhileAway = false,
        held = held,
    )
}
