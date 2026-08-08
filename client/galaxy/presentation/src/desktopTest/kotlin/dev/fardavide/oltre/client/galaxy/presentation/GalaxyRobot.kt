package dev.fardavide.oltre.client.galaxy.presentation

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.core.AdaptationTechnology

internal const val PHONE_WIDTH = 393
internal const val SLIDE_OVER_WIDTH = 320

// The harness and the Robot, copying `ResearchRobot` — the worked example the taxonomy points at.
// A behaviour test drives the screen through this and never queries a node in its own body.
@OptIn(ExperimentalTestApi::class)
internal fun galaxyScreen(
    uiState: GalaxyUiState,
    width: Int = PHONE_WIDTH,
    onSelectGalaxy: (Int) -> Unit = {},
    onStepSystem: (Int) -> Unit = {},
    onGoHome: () -> Unit = {},
    onOpenResearch: () -> Unit = {},
    block: GalaxyRobot.() -> Unit,
) {
    runDesktopComposeUiTest(width = width, height = 852) {
        setContent {
            OltreTheme {
                Surface {
                    GalaxyPage(
                        uiState = uiState,
                        onSelectGalaxy = onSelectGalaxy,
                        onStepSystem = onStepSystem,
                        onGoHome = onGoHome,
                        onOpenResearch = onOpenResearch,
                    )
                }
            }
        }
        GalaxyRobot(this).block()
    }
}

@OptIn(ExperimentalTestApi::class)
internal class GalaxyRobot(private val test: ComposeUiTest) {

    fun openGalaxy(galaxy: Int) = apply {
        test.onNodeWithTag(GalaxyTestTags.galaxy(galaxy)).performClick()
    }

    fun stepToTheNextSystem() = apply {
        test.onNodeWithTag(GalaxyTestTags.STEP_FORWARD).performClick()
    }

    fun stepToThePreviousSystem() = apply {
        test.onNodeWithTag(GalaxyTestTags.STEP_BACK).performClick()
    }

    fun goHome() = apply {
        test.onNodeWithTag(GalaxyTestTags.HOME).performClick()
    }

    // The blocked row's remedy, which is a tap target again now that Research can sell it.
    fun tapTheRemedy(slot: Int, technology: AdaptationTechnology) = apply {
        test.onNodeWithTag(GalaxyTestTags.adaptation(slot, technology)).performScrollTo().performClick()
    }

    // The rest of the card, which is not one: the row belongs to the world.
    fun tapTheWorld(slot: Int) = apply {
        test.onNodeWithTag(GalaxyTestTags.row(slot)).performScrollTo().performClick()
    }

    fun assertShowsWorld(slot: Int) = apply {
        test.onNodeWithTag(GalaxyTestTags.row(slot)).assertIsDisplayed()
    }

    fun assertShowsNoWorld(slot: Int) = apply {
        test.onNodeWithTag(GalaxyTestTags.row(slot)).assertDoesNotExist()
    }

    // Scoped to the row, because a verdict word appears on several of them at once — an unscoped
    // query for "BLOCKED" on the home system would match three nodes and fail on the ambiguity
    // rather than on the assertion.
    fun assertRowReads(slot: Int, text: String) = apply {
        test.onNodeWithTag(GalaxyTestTags.row(slot))
            .assert(hasAnyDescendant(hasText(text, substring = true)))
    }

    fun assertReads(text: String) = apply {
        test.onNodeWithText(text, substring = true).assertIsDisplayed()
    }

    fun assertNothingReads(text: String) = apply {
        test.onNodeWithText(text, substring = true).assertDoesNotExist()
    }

    fun assertTheMapIsDrawn() = apply {
        test.onNodeWithTag(GalaxyTestTags.MAP).assertIsDisplayed()
    }
}
