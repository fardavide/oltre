package dev.fardavide.oltre.client

import androidx.compose.material3.Text
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Translations
import dev.fardavide.oltre.client.player.ui.PlayerTestTags
import dev.fardavide.oltre.client.player.ui.SETTINGS_NOTICE_MILLIS
import dev.fardavide.oltre.client.tilt.domain.Tilt
import kotlin.test.assertTrue

// The gear and the answer it raises, driven the way a finger drives them. `useUnmergedTree = true`
// throughout: the gear sits inside a `clickable` node and a merged tree collapses it into the
// strip's row.
//
// What it owns is the timing. The notice has a lifetime, and every test that asks whether it is
// still up has to agree about which frame it means — so the window is stated in terms of the
// constant rather than as a number copied out of it.
@OptIn(ExperimentalTestApi::class)
internal class SettingsNoticeRobot(private val test: ComposeUiTest) {

    // **Two frames, and the second one is not padding.** The tap does not raise the notice — it
    // increments the count the frame's `LaunchedEffect` is keyed on, so the first frame recomposes
    // with the new count and restarts the effect, and the notice is up on the one after it. That
    // indirection is what makes a second tap restart the window instead of doing nothing, and one
    // frame of latency on a surface that stays for four seconds is the price of it.
    fun tapSettings(): SettingsNoticeRobot = apply {
        test.onNodeWithTag(PlayerTestTags.SETTINGS, useUnmergedTree = true).performClick()
        test.mainClock.advanceTimeByFrame()
        test.mainClock.advanceTimeByFrame()
    }

    // Far enough past the notice's own window that the effect clearing it has certainly run.
    fun afterTheNotice(): SettingsNoticeRobot = apply {
        test.mainClock.advanceTimeBy(SETTINGS_NOTICE_MILLIS + 100L)
    }

    fun justBeforeTheNoticeClears(): SettingsNoticeRobot = apply {
        test.mainClock.advanceTimeBy(SETTINGS_NOTICE_MILLIS - 100L)
    }

    fun assertShowing(text: String): SettingsNoticeRobot = apply {
        test.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
    }

    fun assertGone(text: String): SettingsNoticeRobot = apply {
        test.onNodeWithText(text, useUnmergedTree = true).assertDoesNotExist()
    }

    // One after a tap, and still one after a second tap — which is the assertion that the notice is
    // a state rather than something that stacks.
    fun assertNoticeCount(expected: Int): SettingsNoticeRobot = apply {
        val actual = test.onAllNodesWithTag(PlayerTestTags.NOTICE, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .size
        assertTrue(actual == expected, "$actual notices are up, expected $expected")
    }

    fun assertTheNoticeClearsTheTabBar(): SettingsNoticeRobot = apply {
        val notice = test.onNodeWithTag(PlayerTestTags.NOTICE, useUnmergedTree = true).getBoundsInRoot()
        val bar = test.onNodeWithTag(ShellTestTags.tab(OltreTab.COLONY)).getBoundsInRoot()
        assertTrue(
            notice.bottom <= bar.top,
            "the notice ends at ${notice.bottom} and the tab bar starts at ${bar.top}, so it covers the bar",
        )
    }
}

// The whole frame at a phone's size, with the five destinations standing in for themselves: the
// notice is placed relative to the tab bar, so nothing smaller than the scaffold can be asked where
// it sits. The clock is paused before `setContent`, because the notice has a lifetime and a test
// that let the clock run would be racing it.
@OptIn(ExperimentalTestApi::class)
internal fun frameWithTheGear(
    translations: Translations = English,
    block: SettingsNoticeRobot.() -> Unit,
) {
    runDesktopComposeUiTest(width = PHONE_WIDTH, height = PHONE_HEIGHT) {
        mainClock.autoAdvance = false
        setContent {
            OltreTheme(translations = translations) {
                MainScaffold(
                    // Desktop has no motion sensor, so this is also what the app itself passes here.
                    tilt = { Tilt.NONE },
                    player = testPlayerStripUiState,
                    resources = testResourceRailUiState,
                    colony = { Text("colony-under-test") },
                    research = { Text("research-under-test") },
                    galaxy = { _, _ -> Text("galaxy-under-test") },
                    shipyard = { Text("shipyard-under-test") },
                    fleets = { Text("fleets-under-test") },
                )
            }
        }
        mainClock.advanceTimeByFrame()
        SettingsNoticeRobot(this).block()
    }
}

private const val PHONE_WIDTH = 393
private const val PHONE_HEIGHT = 852
