package dev.fardavide.oltre.client.colony.presentation

import androidx.compose.material3.Surface
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.component.RowSheetContent
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

    // **Unmerged throughout, since the card became tappable.** `Modifier.clickable` merges its
    // descendants — that is how a button's label is readable on the button — so on the merged tree a
    // row is one node carrying every string it draws, and "does the card say this" stops being
    // distinguishable from "does any line on it say this". The unmerged tree still has the Texts.
    fun assertReads(text: String) = apply {
        test.onNodeWithText(text, substring = true, useUnmergedTree = true).assertIsDisplayed()
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

    fun assertNothingReads(text: String) = apply {
        test.onNodeWithText(text, substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    // Everything on the card that is not the action or the square. The tag is on the card itself, so
    // this is the same gesture the player makes anywhere on the row.
    fun tapTheCardOn(building: BuildingType) = apply {
        test.onNodeWithTag(ColonyTestTags.card(building)).performClick()
        test.waitForIdle()
    }

    fun assertTheSheetIsOpen() = apply {
        test.onNodeWithTag(ColonyTestTags.SHEET).assertIsDisplayed()
    }

    fun assertTheSheetIsClosed() = apply {
        test.onNodeWithTag(ColonyTestTags.SHEET).assertDoesNotExist()
    }

    // Scoped to the sheet rather than to the window: the sheet repeats what the row said, so an
    // unscoped query for that sentence would be answered by the row behind it.
    fun assertTheSheetReads(text: String) = apply {
        test.onNodeWithTag(ColonyTestTags.SHEET, useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText(text, substring = true)))
    }

    fun tapTheSheetAction() = apply {
        test.onNodeWithTag(ColonyTestTags.SHEET_ACTION).performClick()
        test.waitForIdle()
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun facilityRow(row: FacilityRowUiState, compact: Boolean = false, block: ColonyRobot.() -> Unit) {
    facilityList(listOf(row), compact = compact, block = block)
}

// Several rows, for the one thing a single row cannot show: the watch is one slot, so what a tap on
// the second row means is only legible next to the first.
//
// `compact` is the window's width as far as the list is concerned. The screen derives it from
// `BoxWithConstraints`; here it is stated, because what a narrow render changes about a row — the
// stacked square, the short name — is worth asking about without building a 320dp window round it.
//
// 140dp a row rather than the 120 it was: every row that is not in flight gained a verdict, which is
// one 10.5sp line over a 15sp leading plus the card's own 4dp between lines.
@OptIn(ExperimentalTestApi::class)
internal fun facilityList(
    rows: List<FacilityRowUiState>,
    compact: Boolean = false,
    block: ColonyRobot.() -> Unit,
) {
    runDesktopComposeUiTest(width = 393, height = ROW_HEIGHT * rows.size) {
        val announced = mutableStateOf(rows.any { it.finishedWhileAway })
        val watchTaps = mutableListOf<BuildingType>()
        mainClock.autoAdvance = false
        setContent {
            OltreTheme {
                Surface {
                    FacilityList(
                        facilities = rows.map { it.copy(finishedWhileAway = it.finishedWhileAway && announced.value) },
                        onUpgrade = {},
                        compact = compact,
                        onToggleWatch = { watchTaps += it },
                        onOpenDetail = {},
                    )
                }
            }
        }
        ColonyRobot(this, announced, watchTaps).block()
    }
}

private const val ROW_HEIGHT = 140

// The whole screen, for the one thing a list cannot show: which row has its arithmetic open is the
// screen's state, so a tap that opens a sheet is only a tap that opens a sheet from here. The clock
// runs, because a modal sheet has an entrance to settle.
@OptIn(ExperimentalTestApi::class)
internal fun colonyScreen(rows: List<FacilityRowUiState>, block: ColonyRobot.() -> Unit) {
    runDesktopComposeUiTest(width = 393, height = 852) {
        setContent {
            OltreTheme {
                Surface {
                    ColonyScreen(
                        // The fleet strip and the watch have no part in this, and both would push
                        // the rows the tests tap further down a 852dp window.
                        uiState = testColonyUiState.copy(facilities = rows, returningFleet = null),
                        onUpgrade = {},
                        onToggleWatch = {},
                    )
                }
            }
        }
        ColonyRobot(this, mutableStateOf(false), mutableListOf()).block()
    }
}

// The sheet's contents with no sheet around them, exactly as `DebugRobot` drives the debug panel: an
// assertion about what the sheet *says* has no business also depending on a popup being reachable
// and an enter animation settling.
@OptIn(ExperimentalTestApi::class)
internal fun facilitySheet(
    row: FacilityRowUiState,
    onAct: () -> Unit = {},
    block: ColonyRobot.() -> Unit,
) {
    runDesktopComposeUiTest(width = 393, height = 852) {
        setContent {
            OltreTheme {
                Surface {
                    RowSheetContent(
                        uiState = row.toRowSheetUiState(),
                        onAct = onAct,
                        modifier = Modifier.testTag(ColonyTestTags.SHEET),
                        actionModifier = Modifier.testTag(ColonyTestTags.SHEET_ACTION),
                    )
                }
            }
        }
        ColonyRobot(this, mutableStateOf(false), mutableListOf()).block()
    }
}
