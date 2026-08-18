package dev.fardavide.oltre.client

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.fardavide.oltre.client.design.core.OltreMotion

// **A Robot for the switch, and only for the switch.** The rest of `MainScaffoldBehaviourTest`
// predates the Robot rule and queries the tree directly — the test-coverage skill names it as the
// migration target rather than the example — so this covers what 0.13.2 added and leaves the older
// tests where they are. Migrating them is worth doing and is not this change.
//
// What it owns is the timing, which is the part a test should not be restating: since the switch
// became a transition, "the tab is open" is a claim about a frame 210ms after the tap rather than
// about the next one, and every test that taps a tab has to agree about which frame it means.
@OptIn(ExperimentalTestApi::class)
internal class ScaffoldRobot(private val test: ComposeUiTest) {

    fun tap(tab: OltreTab): ScaffoldRobot = apply {
        test.onNodeWithTag(ShellTestTags.tab(tab)).performClick()
        test.mainClock.advanceTimeByFrame()
    }

    // Halfway through the crossing, where both destinations are on screen at once. Deliberately not
    // a settle: what it is for is the frame a settle would skip over.
    fun halfwayThrough(): ScaffoldRobot = apply {
        test.mainClock.advanceTimeBy(OltreMotion.SWITCH_MILLIS / 2L)
    }

    // Past the end rather than exactly on it: the switch starts on the frame after the tap, so
    // `SWITCH_MILLIS` from there lands a frame short, and a boundary is the one place two machines
    // round differently.
    fun afterTheSwitch(): ScaffoldRobot = apply {
        test.mainClock.advanceTimeBy(OltreMotion.SWITCH_MILLIS + 100L)
    }

    // `assertExists` rather than `assertIsDisplayed`: a destination mid-crossing is faded and offset,
    // which is exactly the state this has to be able to see.
    fun assertDrawn(marker: String): ScaffoldRobot = apply {
        test.onNodeWithText(marker).assertExists()
    }

    fun assertShowing(marker: String): ScaffoldRobot = apply {
        test.onNodeWithText(marker).assertIsDisplayed()
    }

    fun assertGone(marker: String): ScaffoldRobot = apply {
        test.onNodeWithText(marker).assertDoesNotExist()
    }
}
