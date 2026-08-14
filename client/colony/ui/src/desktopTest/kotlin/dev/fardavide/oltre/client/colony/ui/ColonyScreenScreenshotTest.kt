package dev.fardavide.oltre.client.colony.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// The whole screen at the sizes an iPad actually hands it. The component baselines cover phone
// width; these cover what the window does past it, so a regression in the capped-and-centred
// layout is visible rather than only asserted. Sizes are 11" iPad points, and each one sits on a
// different side of OltreLayout.maxContentWidth (560dp) — see ColonyScreenLayoutBehaviourTest
// for the rule.
@OptIn(ExperimentalTestApi::class)
class ColonyScreenScreenshotTest {

    @Test
    fun `colony screen in an iPad landscape window`() {
        captureColonyScreen(width = 1194, height = 834, name = "colony_screen_ipad_landscape")
    }

    @Test
    fun `colony screen in an iPad portrait window`() {
        captureColonyScreen(width = 834, height = 1194, name = "colony_screen_ipad_portrait")
    }

    // Half of a landscape iPad, the widest Split View pane. At 570dp it clears the 560dp cap by
    // 10dp — the tightest case where centring is visible at all, and the one most likely to look
    // wrong if the cap ever drifts.
    @Test
    fun `colony screen in a Split View pane`() {
        captureColonyScreen(width = 570, height = 834, name = "colony_screen_ipad_split_view")
    }

    // Slide Over, and the narrow Stage Manager window with it: below the cap, so the content
    // fills the window exactly as it does on a phone.
    @Test
    fun `colony screen in a Slide Over window`() {
        captureColonyScreen(width = 320, height = 834, name = "colony_screen_ipad_slide_over")
    }

    // The watch set, at the width it has to survive: the square sits outside the ghost button, so
    // the 29dp comes off the name column — 320dp is where that is decided.
    @Test
    fun `colony screen watching a row in a Slide Over window`() {
        captureColonyScreen(
            width = 320,
            height = 834,
            name = "colony_screen_watching_slide_over",
            uiState = watchedColonyUiState,
        )
    }

    // A phone, which is where the three things the watch moves are read together: the heading names
    // the row, the square is lit, and the card says the instant.
    @Test
    fun `colony screen watching a row on a phone`() {
        captureColonyScreen(
            width = 393,
            height = 852,
            name = "colony_screen_watching_phone",
            uiState = watchedColonyUiState,
        )
    }

    private fun captureColonyScreen(
        width: Int,
        height: Int,
        name: String,
        uiState: ColonyUiState = testColonyUiState,
    ) {
        runDesktopComposeUiTest(width = width, height = height) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    Surface {
                        ColonyScreen(uiState = uiState, onUpgrade = {}, onToggleWatch = {})
                    }
                }
            }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/$name.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }
}
