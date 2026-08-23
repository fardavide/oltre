package dev.fardavide.oltre.client.player.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Strings
import org.junit.Test
import kotlin.test.assertEquals

// **What the gear does, which is the only thing in this strip that does anything.** The rest of it
// is four readings, and a baseline is the right instrument for those.
//
// What the gear does *here* is report. The answer moved above the tab bar at 0.18 and is the frame's
// to draw and to time — see `MainScaffoldBehaviourTest`, which owns the four seconds.
@OptIn(ExperimentalTestApi::class)
class PlayerStripBehaviourTest {

    @Test
    fun `should ask for the settings when the gear is tapped`() {
        var asked = 0
        playerStrip(settings = { asked++ }) {
            tapSettings()
        }
        assertEquals(1, asked)
    }

    @Test
    fun `should ask again when the gear is tapped twice`() {
        // The frame restarts the notice's window on a second tap rather than stacking a second bar,
        // and it can only do that if it hears about the second tap. A strip that swallowed the
        // repeat would make that rule untestable one layer up.
        var asked = 0
        playerStrip(settings = { asked++ }) {
            tapSettings()
            tapSettings()
        }
        assertEquals(2, asked)
    }

    @Test
    fun `should ask for nothing until the gear is tapped`() {
        // The strip is chrome and is composed on every launch, so a strip that asked on arrival
        // would greet every player with an apology for a screen they had not asked for.
        var asked = 0
        playerStrip(settings = { asked++ }) {
            assertShowing(English.resolve(Strings.playerDefaultName()))
        }
        assertEquals(0, asked)
    }

    @Test
    fun `should read the name and the level it was handed`() {
        playerStrip {
            assertShowing(English.resolve(Strings.playerDefaultName()))
            assertShowing(English.resolve(Strings.levelBadge(0)))
        }
    }

    @Test
    fun `should draw its edge at every width`() {
        // The gauge is the strip's bottom edge now, so it is the one part of the drawing that is not
        // in the row and cannot be found by its text. At LV 0 it is an unlit 2dp line, which is what
        // a fresh install sees and what a frame cannot tell from the hairline it replaced.
        playerStrip(width = SLIDE_OVER_WIDTH) {
            assertTheEdgeIsDrawn()
        }
    }

    @Test
    fun `should re-read the state it is handed when that state changes`() {
        // **The strip is composed once per launch** and every test above hands it a value that never
        // moves, so a strip that cached its first render would pass all of them. Since 0.17 the level
        // is folded off the save and does move on a tick, and this is what says the frame follows it.
        var level by mutableStateOf(Strings.levelBadge(0))
        runDesktopComposeUiTest(width = PHONE_WIDTH, height = 80) {
            setContent {
                OltreTheme {
                    Surface {
                        PlayerStrip(
                            uiState = PlayerStripUiState(
                                name = Strings.playerDefaultName(),
                                level = level,
                                experiencePercent = 0,
                            ),
                            onOpenSettings = {},
                        )
                    }
                }
            }
            onNodeWithText(English.resolve(Strings.levelBadge(0)), useUnmergedTree = true).assertIsDisplayed()

            level = Strings.levelBadge(3)
            waitForIdle()

            onNodeWithText(English.resolve(Strings.levelBadge(3)), useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText(English.resolve(Strings.levelBadge(0)), useUnmergedTree = true).assertDoesNotExist()
        }
    }
}
