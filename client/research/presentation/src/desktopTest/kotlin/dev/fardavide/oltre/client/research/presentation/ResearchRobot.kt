package dev.fardavide.oltre.client.research.presentation

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
import dev.fardavide.oltre.client.design.OltreTheme
import dev.fardavide.oltre.core.Technology

// A behaviour test says what the player did and what they should see; the Robot owns how. Keeping
// the node queries here is what makes a testTag rename one file's problem rather than every test's
// — the convention the taxonomy asks for, and which MainScaffoldBehaviourTest still predates.
@OptIn(ExperimentalTestApi::class)
internal fun researchScreen(
    uiState: ResearchUiState,
    width: Int = PHONE_WIDTH,
    onStartResearch: (Technology) -> Unit = {},
    block: ResearchRobot.() -> Unit,
) {
    runDesktopComposeUiTest(width = width, height = 852) {
        setContent {
            OltreTheme {
                Surface {
                    ResearchScreen(uiState = uiState, onStartResearch = onStartResearch)
                }
            }
        }
        ResearchRobot(this).block()
    }
}

internal const val PHONE_WIDTH = 393
internal const val SLIDE_OVER_WIDTH = 320

@OptIn(ExperimentalTestApi::class)
internal class ResearchRobot(private val test: ComposeUiTest) {

    fun startResearching(technology: Technology) = apply {
        test.onNodeWithTag(ResearchTestTags.action(technology)).performClick()
    }

    fun assertBranchShows(technology: Technology) = apply {
        test.onNodeWithTag(ResearchTestTags.row(technology)).assertIsDisplayed()
    }

    fun assertOffersResearch(technology: Technology) = apply {
        test.onNodeWithTag(ResearchTestTags.action(technology)).assertTextEquals("Research")
    }

    // The ghost carries a time, never a dead button.
    fun assertWaits(technology: Technology, label: String) = apply {
        test.onNodeWithTag(ResearchTestTags.action(technology)).assertTextEquals(label)
    }

    fun assertCountsDown(technology: Technology, countdown: String) = apply {
        test.onNodeWithTag(ResearchTestTags.action(technology)).assertTextEquals(countdown)
    }

    // A locked row is name, level and requirement — no costs and nothing to press.
    fun assertOffersNothing(technology: Technology) = apply {
        test.onNodeWithTag(ResearchTestTags.action(technology)).assertDoesNotExist()
    }

    // Scoped to a row, because three rows of the same shape say many of the same things: two of
    // them carry "Requires Robotics 1" before the gate, and an unscoped query would match both and
    // fail on the ambiguity rather than on the assertion.
    fun assertRowReads(technology: Technology, text: String) = apply {
        test.onNodeWithTag(ResearchTestTags.row(technology))
            .assert(hasAnyDescendant(hasText(text, substring = true)))
    }

    // Substring, because a line the screen composes from several Texts is still one line to the
    // player: the section rule renders as " · one project at a time" next to its label.
    fun assertReads(text: String) = apply {
        test.onNodeWithText(text, substring = true).assertIsDisplayed()
    }

    fun assertNothingReads(text: String) = apply {
        test.onNodeWithText(text, substring = true).assertDoesNotExist()
    }
}
