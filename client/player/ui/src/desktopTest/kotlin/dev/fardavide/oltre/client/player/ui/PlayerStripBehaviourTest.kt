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
import dev.fardavide.oltre.protocol.MarkPreset
import dev.fardavide.oltre.protocol.PlayerMark
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// **What the two controls do, which is everything in this strip that does anything.** The rest of it
// is four readings, and a baseline is the right instrument for those.
//
// What they do *here* is report. The gear's answer moved above the tab bar at 0.18 and the profile's
// is a sheet the shell raises — see `MainScaffoldBehaviourTest`, which owns the four seconds. So
// every test below asks the same question in two halves: did the right callback fire, and did the
// other one stay quiet.
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
    fun `should ask for the profile when the name and mark are tapped`() {
        var asked = 0
        playerStrip(profile = { asked++ }) {
            tapTheNameAndMark()
        }
        assertEquals(1, asked)
    }

    @Test
    fun `should ask for nothing until the name and mark are tapped`() {
        // The same argument the gear's own version of this makes: the strip is chrome and is composed
        // on every launch, so a cluster that reported on arrival would open the profile sheet over
        // whatever screen the player actually asked for.
        var asked = 0
        playerStrip(profile = { asked++ }) {
            assertShowing(English.resolve(Strings.playerDefaultName()))
        }
        assertEquals(0, asked)
    }

    @Test
    fun `should tell the gear apart from the name beside it`() {
        // **The one that earns the tag.** The cluster is clickable, so mark, name and badge merge
        // into a single semantics node 7dp from a gear that is also clickable — and two adjacent
        // targets on a 38dp bar is exactly the shape in which a tap lands on the wrong one and every
        // other test in this file still passes.
        var settings = 0
        var profile = 0
        playerStrip(settings = { settings++ }, profile = { profile++ }) {
            tapSettings()
            assertEquals(0, profile, "the gear opened the profile")

            tapTheNameAndMark()
        }
        assertEquals(1, settings)
        assertEquals(1, profile)
    }

    @Test
    fun `should read the name and the level it was handed`() {
        playerStrip {
            assertShowing(English.resolve(Strings.playerDefaultName()))
            assertShowing(English.resolve(Strings.levelBadge(0)))
        }
    }

    @Test
    fun `should keep its badge findable as the strip's rather than as a row's`() {
        // **The shell reads this strip by subtraction and nothing in the shell would notice it
        // break.** `AppRobot` counts rows reading `LV 0` by excluding everything under
        // `PlayerTestTags.CONTENT`, and reads the player's level by including only what is under it —
        // a facility row says `LV 4` too, so an unscoped assertion about the research branch would
        // pass on a mine. Making the cluster clickable merged the badge into a node that did not
        // exist before, which is exactly the kind of change that keeps both halves compiling and
        // moves what they count.
        playerStrip {
            assertOnlyTheStripReads(English.resolve(Strings.levelBadge(0)))
        }
    }

    @Test
    fun `should draw the mark it was handed rather than one mark for everybody`() {
        // **The reading a baseline cannot make on its own.** Six presets and forty compositions share
        // one 20dp square, and a strip that ignored its state and always drew `THRESHOLD` would be
        // photographed correctly by the frame recorded against `THRESHOLD` — which is every frame in
        // this module. So the assertion is that two different marks put different amounts of ink on
        // the bar, which is only true if the state reaches the drawing.
        var threshold = 0
        playerStrip(uiState = newColonyPlayerStrip) { threshold = inkOnTheMark() }

        var sounding = 0
        playerStrip(uiState = newColonyPlayerStrip.copy(mark = PlayerMark.Preset(MarkPreset.SOUNDING))) {
            sounding = inkOnTheMark()
        }

        assertTrue(threshold > 0, "the strip drew no mark at all")
        assertNotEquals(threshold, sounding, "a vertical plumb line landed exactly as much ink as a departure")
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
                                mark = PlayerMark.Preset(MarkPreset.THRESHOLD),
                                level = level,
                                experiencePercent = 0,
                            ),
                            onOpenSettings = {},
                            onOpenProfile = {},
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
