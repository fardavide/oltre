package dev.fardavide.oltre.client.shipyard.presentation

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.core.ShipType

// A behaviour test says what the player did and what they should see; the Robot owns how. Keeping
// the node queries here is what makes a testTag rename one file's problem rather than every test's.
@OptIn(ExperimentalTestApi::class)
internal fun shipyard(
    uiState: ShipyardUiState,
    width: Int = PHONE_WIDTH,
    onBuild: (ShipType) -> Unit = {},
    block: ShipyardRobot.() -> Unit,
) {
    runDesktopComposeUiTest(width = width, height = 852) {
        setContent {
            OltreTheme {
                Surface {
                    ShipyardScreen(uiState = uiState, onBuild = onBuild)
                }
            }
        }
        ShipyardRobot(this).block()
    }
}

internal const val PHONE_WIDTH = 393
internal const val SLIDE_OVER_WIDTH = 320

@OptIn(ExperimentalTestApi::class)
internal class ShipyardRobot(private val test: ComposeUiTest) {

    fun buy(type: ShipType) = apply {
        test.onNodeWithTag(ShipyardTestTags.action(type), useUnmergedTree = true).performClick()
    }

    fun assertOffersToBuild(type: ShipType) = apply {
        test.onNodeWithTag(ShipyardTestTags.action(type), useUnmergedTree = true).assertTextEquals("Build")
    }

    // The ghost carries a time, never a dead button — the same contract every other screen's
    // unaffordable row has.
    fun assertWaits(type: ShipType, label: String) = apply {
        test.onNodeWithTag(ShipyardTestTags.action(type), useUnmergedTree = true).assertTextEquals(label)
    }

    fun assertNothingToPress(type: ShipType) = apply {
        test.onNodeWithTag(ShipyardTestTags.action(type), useUnmergedTree = true).assertDoesNotExist()
    }

    // Scoped to the card, because two cards on this screen say some of the same words.
    fun assertCardReads(type: ShipType, text: String) = apply {
        test.onNodeWithTag(ShipyardTestTags.card(type), useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText(text, substring = true)))
    }

    fun assertCardDoesNotRead(type: ShipType, text: String) = apply {
        test.onNodeWithTag(ShipyardTestTags.card(type), useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText(text, substring = true)).not())
    }

    fun assertShowsCard(type: ShipType) = apply {
        test.onNodeWithTag(ShipyardTestTags.card(type), useUnmergedTree = true).assertIsDisplayed()
    }

    // Substring, because a line the screen composes from several Texts is still one line to the
    // player: the section rule renders as " · 6 hulls" next to its label.
    fun assertReads(text: String) = apply {
        test.onNodeWithText(text, substring = true, useUnmergedTree = true).assertIsDisplayed()
    }
}
