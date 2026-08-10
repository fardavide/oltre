package dev.fardavide.oltre.client.colony.presentation

import androidx.compose.material3.Surface
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.core.BuildingType
import kotlin.test.assertEquals

// The colony's first Robot. `MainScaffoldBehaviourTest` and the layout assertions still query nodes
// directly — they predate the rule — but nothing new should, and the completion sweep is new.
//
// It drives a `FacilityList` rather than the whole screen, because what the sweep is about is one
// row: the energy card and the fleet strip above it have no part in it and only add strings an
// assertion could collide with.
@OptIn(ExperimentalTestApi::class)
internal class ColonyRobot(
    private val test: ComposeUiTest,
    private val announced: MutableState<Boolean>,
    private val watchTaps: MutableList<BuildingType>,
) {

    // The clock is stopped in this harness, so time only moves when a test says so. Every assertion
    // below is therefore about a named instant rather than about whatever frame the runner reached.
    fun atMillis(millis: Long) = apply { test.mainClock.advanceTimeBy(millis) }

    // What the shell does the moment a destination has shown an announcement: it forgets it, so a
    // return to the tab does not replay it. The row must not notice.
    fun withdrawTheAnnouncement() = apply {
        announced.value = false
        test.mainClock.advanceTimeByFrame()
    }

    fun assertReads(text: String) = apply {
        test.onNodeWithText(text, substring = true).assertIsDisplayed()
    }

    // The square carries no text, so this is the one control the Robot reaches by tag.
    fun tapTheWatchOn(building: BuildingType) = apply {
        test.onNodeWithTag(ColonyTestTags.watch(building)).performClick()
        test.mainClock.advanceTimeByFrame()
    }

    fun assertAskedToWatch(vararg buildings: BuildingType) = apply {
        assertEquals(buildings.toList(), watchTaps)
    }

    fun assertHasNoWatch(building: BuildingType) = apply {
        test.onNodeWithTag(ColonyTestTags.watch(building)).assertDoesNotExist()
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun facilityRow(row: FacilityRowUiState, block: ColonyRobot.() -> Unit) {
    facilityList(listOf(row), block)
}

// Several rows, for the one thing a single row cannot show: the watch is one slot, so what a tap on
// the second row means is only legible next to the first.
@OptIn(ExperimentalTestApi::class)
internal fun facilityList(rows: List<FacilityRowUiState>, block: ColonyRobot.() -> Unit) {
    runDesktopComposeUiTest(width = 393, height = 120 * rows.size) {
        val announced = mutableStateOf(rows.any { it.finishedWhileAway })
        val watchTaps = mutableListOf<BuildingType>()
        mainClock.autoAdvance = false
        setContent {
            OltreTheme {
                Surface {
                    FacilityList(
                        facilities = rows.map { it.copy(finishedWhileAway = it.finishedWhileAway && announced.value) },
                        onUpgrade = {},
                        compact = false,
                        onToggleWatch = { watchTaps += it },
                    )
                }
            }
        }
        ColonyRobot(this, announced, watchTaps).block()
    }
}
