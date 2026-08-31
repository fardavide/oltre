package dev.fardavide.oltre.client.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreColors
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

    fun tapTheNameAndMark(): PlayerRobot = apply {
        test.onNodeWithTag(PlayerTestTags.PROFILE, useUnmergedTree = true).performClick()
        test.mainClock.advanceTimeByFrame()
    }

    // **How much of the mark's square is not the bar behind it**, which is the only question a
    // semantics tree can be asked about a drawing: the mark has no text and no role, so a test that
    // wanted to know *which* mark was drawn could otherwise only ask whether one was.
    //
    // A count rather than a bitmap comparison, and rather than a baseline: two silhouettes that
    // differ enough to be a choice differ in how much ink they land, and a number says which frame
    // failed in the failure message. `MarkInk.inkedPixels` counts the same way over a `DrawScope`
    // render; this counts over the composed one, because what is under test here is that the strip
    // passes on what it was handed rather than that the drawing is right.
    fun inkOnTheMark(): Int {
        val pixels = test.onNodeWithTag(PlayerTestTags.MARK, useUnmergedTree = true).captureToImage().toPixelMap()
        var inked = 0
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                if (pixels[x, y] != OltreColors.surface) inked++
            }
        }
        return inked
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

    // **The one query in this file on the *merged* tree, and the merge is what it is about.** The
    // shell counts how many rows read `LV 0` by asking for every node with that text that is *not*
    // inside `PlayerTestTags.CONTENT`, and asks what the player's own level is by asking for the one
    // that is — `AppRobot.assertRowsReading` and `assertThePlayerStripReads`, which are two halves of
    // the same subtraction and are the only reason `PlayerTestTags` is public at all.
    //
    // Making the left cluster clickable merges the mark, the name and the badge into a single
    // semantics node, so the badge's own node stops existing in the tree those two read. Both still
    // hold — the merged node is still a descendant of `CONTENT` — but nothing in the shell would say
    // so before CI ran, and `LV 4` is a sentence a facility row can say too. This is that claim,
    // asserted in the module that would break it.
    fun assertOnlyTheStripReads(text: String): PlayerRobot = apply {
        val inside = hasText(text, substring = true) and hasAnyAncestor(hasTestTag(PlayerTestTags.CONTENT))
        test.onNode(inside).assertIsDisplayed()
        test.onAllNodes(hasText(text, substring = true) and hasAnyAncestor(hasTestTag(PlayerTestTags.CONTENT)).not())
            .assertCountEquals(0)
    }
}

// The scene every behaviour test in this module runs in: the theme, a surface, and the strip at a
// stated width. The clock is paused before `setContent`, because the gauge fills once on entry and a
// test that let the clock run would be racing it.
//
// **Both controls report rather than answer**, so the scene is handed what it should do about each —
// they default to doing nothing, which is what a test about the readings wants, and a test about a
// tap passes a recorder. Two recorders rather than one is the point of `profile` having its own
// default: what makes the pair a pair is that a tap on one is silence on the other.
@OptIn(ExperimentalTestApi::class)
internal fun playerStrip(
    uiState: PlayerStripUiState = newColonyPlayerStrip,
    width: Int = PHONE_WIDTH,
    translations: Translations = English,
    settings: () -> Unit = {},
    profile: () -> Unit = {},
    block: PlayerRobot.() -> Unit,
) {
    runDesktopComposeUiTest(width = width, height = STRIP_SCENE_HEIGHT) {
        mainClock.autoAdvance = false
        setContent {
            OltreTheme(translations = translations) {
                Surface {
                    Box(modifier = Modifier.fillMaxSize()) {
                        PlayerStrip(uiState = uiState, onOpenSettings = settings, onOpenProfile = profile)
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
