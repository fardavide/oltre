package dev.fardavide.oltre.client.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Italian
import dev.fardavide.oltre.client.design.text.Strings
import org.junit.Test
import kotlin.test.assertEquals

// **What the gear does, which is the only thing in this strip that does anything.** The rest of it
// is four readings, and a baseline is the right instrument for those.
//
// The notice has a lifetime, so every test here runs on a paused clock and advances it deliberately.
// A test that let the clock run would be asserting against whichever frame the machine got to.
@OptIn(ExperimentalTestApi::class)
class PlayerStripBehaviourTest {

    @Test
    fun `should say the settings are coming when the gear is tapped`() {
        playerStrip {
            tapSettings()
            assertShowing(English.resolve(Strings.settingsComingSoon()))
        }
    }

    @Test
    fun `should say nothing before the gear is tapped`() {
        // The strip is chrome and is composed on every launch, so a notice that were there from the
        // first frame would greet every player with an apology for a screen they had not asked for.
        playerStrip {
            assertGone(English.resolve(Strings.settingsComingSoon()))
        }
    }

    @Test
    fun `should clear the notice once its window has passed`() {
        playerStrip {
            tapSettings()
            afterTheNotice()
            assertGone(English.resolve(Strings.settingsComingSoon()))
        }
    }

    @Test
    fun `should hold the notice for the whole of its window`() {
        // The other half of the one above, and the half that would not fail if the window were a
        // single frame long.
        playerStrip {
            tapSettings()
            justBeforeTheNoticeClears()
            assertShowing(English.resolve(Strings.settingsComingSoon()))
        }
    }

    @Test
    fun `should not stack a second notice when the gear is tapped twice`() {
        // Transience as state rather than as a queue — the resource rail's arrival roll is the shape
        // this copies. A second tap restarts the window; it does not add a second notice.
        playerStrip {
            tapSettings()
            tapSettings()
            assertEquals(1, noticeCount())
        }
    }

    @Test
    fun `should give the level and the gauge back after the notice clears`() {
        // The notice displaces the badge and the gauge rather than overlaying them, so this is what
        // says the displacement is temporary.
        playerStrip {
            tapSettings()
            assertGone(English.resolve(Strings.levelBadge(0)))
            afterTheNotice()
            assertShowing(English.resolve(Strings.levelBadge(0)))
        }
    }

    @Test
    fun `should read the name and the level it was handed`() {
        playerStrip {
            assertShowing(English.resolve(Strings.playerDefaultName()))
            assertShowing(English.resolve(Strings.levelBadge(0)))
        }
    }

    @Test
    fun `should re-read the state it is handed when that state changes`() {
        // **Nothing in the app can change this state yet**, which is exactly why it is worth
        // pinning: the strip is composed once per launch and every test above hands it a value that
        // never moves, so a strip that cached its first render would pass all of them. The slice
        // that makes experience real will change this state on a tick, and this is the test that
        // will already be there when it does.
        var level by mutableStateOf(Strings.levelBadge(0))
        runDesktopComposeUiTest(width = PHONE_WIDTH, height = 80) {
            setContent {
                OltreTheme {
                    Surface {
                        PlayerStripContent(
                            uiState = PlayerStripUiState(
                                name = Strings.playerDefaultName(),
                                level = level,
                                experiencePercent = 0,
                            ),
                            noticeShown = false,
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

    @Test
    fun `should say the settings are coming in Italian too`() {
        // The notice is the one string in the strip whose two languages differ in width, and it is
        // measured against the cluster it replaces at the narrowest window the app supports.
        playerStrip(width = SLIDE_OVER_WIDTH, translations = Italian) {
            tapSettings()
            assertShowing(Italian.resolve(Strings.settingsComingSoon()))
        }
    }
}
