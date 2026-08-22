package dev.fardavide.oltre.client.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Translations

// The strip, driven the way a finger drives it. Every query carries `useUnmergedTree = true`,
// because the gear is inside a `clickable` node and a merged tree collapses it into the row.
@OptIn(ExperimentalTestApi::class)
internal class PlayerRobot(private val test: ComposeUiTest) {

    fun tapSettings(): PlayerRobot = apply {
        test.onNodeWithTag(PlayerTestTags.SETTINGS, useUnmergedTree = true).performClick()
        test.mainClock.advanceTimeByFrame()
    }

    // Far enough past the notice's own window that the effect that clears it has certainly run, and
    // stated in terms of the constant rather than a number copied out of it.
    fun afterTheNotice(): PlayerRobot = apply {
        test.mainClock.advanceTimeBy(NOTICE_MILLIS + 100L)
    }

    fun justBeforeTheNoticeClears(): PlayerRobot = apply {
        test.mainClock.advanceTimeBy(NOTICE_MILLIS - 100L)
    }

    fun assertShowing(text: String): PlayerRobot = apply {
        test.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
    }

    fun assertGone(text: String): PlayerRobot = apply {
        test.onNodeWithText(text, useUnmergedTree = true).assertDoesNotExist()
    }

    // How many nodes carry the notice's tag. One after a tap, and still one after a second tap —
    // which is the assertion that the notice is a state rather than something that stacks.
    fun noticeCount(): Int =
        test.onAllNodesWithTag(PlayerTestTags.NOTICE, useUnmergedTree = true).fetchSemanticsNodes().size
}

// The scene every behaviour test in this module runs in: the theme, a surface, and the strip at a
// stated width. The clock is paused before `setContent`, because the notice has a lifetime and a
// test that let the clock run would be racing it.
@OptIn(ExperimentalTestApi::class)
internal fun playerStrip(
    uiState: PlayerStripUiState = playerStripUiState(),
    width: Int = PHONE_WIDTH,
    translations: Translations = English,
    block: PlayerRobot.() -> Unit,
) {
    runDesktopComposeUiTest(width = width, height = STRIP_SCENE_HEIGHT) {
        mainClock.autoAdvance = false
        setContent {
            OltreTheme(translations = translations) {
                Surface {
                    Box(modifier = Modifier.fillMaxSize()) {
                        PlayerStrip(uiState = uiState)
                    }
                }
            }
        }
        mainClock.advanceTimeByFrame()
        PlayerRobot(this).block()
    }
}

internal const val PHONE_WIDTH = 393
internal const val SLIDE_OVER_WIDTH = 320

// Taller than the strip by a clear band of background: erring tall costs a strip of window, erring
// short would clip the thing under test.
private const val STRIP_SCENE_HEIGHT = 80
