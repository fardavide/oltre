package dev.fardavide.oltre.client.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
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

    fun assertShowing(text: String): PlayerRobot = apply {
        test.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
    }

    fun assertGone(text: String): PlayerRobot = apply {
        test.onNodeWithText(text, useUnmergedTree = true).assertDoesNotExist()
    }

    fun assertTheEdgeIsDrawn(): PlayerRobot = apply {
        test.onNodeWithTag(PlayerTestTags.EXPERIENCE, useUnmergedTree = true).assertIsDisplayed()
    }
}

// The scene every behaviour test in this module runs in: the theme, a surface, and the strip at a
// stated width. The clock is paused before `setContent`, because the gauge fills once on entry and a
// test that let the clock run would be racing it.
//
// **The gear reports rather than answers now**, so the scene is handed what it should do about it —
// `settings` defaults to doing nothing, which is what a test about the four readings wants, and a
// test about the tap passes a recorder.
@OptIn(ExperimentalTestApi::class)
internal fun playerStrip(
    uiState: PlayerStripUiState = newColonyPlayerStrip,
    width: Int = PHONE_WIDTH,
    translations: Translations = English,
    settings: () -> Unit = {},
    block: PlayerRobot.() -> Unit,
) {
    runDesktopComposeUiTest(width = width, height = STRIP_SCENE_HEIGHT) {
        mainClock.autoAdvance = false
        setContent {
            OltreTheme(translations = translations) {
                Surface {
                    Box(modifier = Modifier.fillMaxSize()) {
                        PlayerStrip(uiState = uiState, onOpenSettings = settings)
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
