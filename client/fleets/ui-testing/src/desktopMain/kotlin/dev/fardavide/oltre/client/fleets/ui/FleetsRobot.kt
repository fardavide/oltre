package dev.fardavide.oltre.client.fleets.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme

// A behaviour test says what the player sees; the Robot owns how it is looked for. There is nothing
// to press on this screen — no cancel and no recall anywhere in the app — so every method here is an
// assertion, which is itself the honest shape of a read-only destination.
@OptIn(ExperimentalTestApi::class)
fun fleets(
    uiState: FleetsUiState,
    width: Int = PHONE_WIDTH,
    block: FleetsRobot.() -> Unit,
) {
    runDesktopComposeUiTest(width = width, height = 852) {
        setContent {
            OltreTheme {
                Surface {
                    FleetsScreen(uiState = uiState)
                }
            }
        }
        FleetsRobot(this).block()
    }
}

const val PHONE_WIDTH = 393
const val SLIDE_OVER_WIDTH = 320

@OptIn(ExperimentalTestApi::class)
class FleetsRobot(private val test: ComposeUiTest) {

    // Scoped to a card, because three cards of the same shape say many of the same things — an
    // unscoped query would match several and fail on the ambiguity rather than on the assertion.
    fun assertRunReads(index: Int, text: String) = apply {
        test.onNodeWithTag(FleetsTestTags.card(index), useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText(text, substring = true)))
    }

    fun assertRunDoesNotRead(index: Int, text: String) = apply {
        test.onNodeWithTag(FleetsTestTags.card(index), useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText(text, substring = true)).not())
    }

    fun assertShowsRun(index: Int) = apply {
        test.onNodeWithTag(FleetsTestTags.card(index), useUnmergedTree = true).assertIsDisplayed()
    }

    fun assertHasNoRun(index: Int) = apply {
        test.onNodeWithTag(FleetsTestTags.card(index), useUnmergedTree = true).assertDoesNotExist()
    }

    fun assertLandingReads(index: Int, text: String) = apply {
        test.onNodeWithTag(FleetsTestTags.landing(index), useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText(text, substring = true)))
    }

    fun assertHasNoLanding(index: Int) = apply {
        test.onNodeWithTag(FleetsTestTags.landing(index), useUnmergedTree = true).assertDoesNotExist()
    }

    // Substring, because a line the screen composes from several Texts is still one line to the
    // player: the section rule renders as " · 5 of 6 away" next to its label.
    fun assertReads(text: String) = apply {
        test.onNodeWithText(text, substring = true, useUnmergedTree = true).assertIsDisplayed()
    }

    fun assertNothingReads(text: String) = apply {
        test.onNodeWithText(text, substring = true, useUnmergedTree = true).assertDoesNotExist()
    }
}
