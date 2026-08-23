package dev.fardavide.oltre.client

import androidx.compose.ui.test.ExperimentalTestApi
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Italian
import dev.fardavide.oltre.client.design.text.Strings
import org.junit.Test

// **The one control on the frame that answers rather than navigates**, and the whole of what it
// answers is two words that go away by themselves.
//
// Tested here rather than in `:client:player:ui` because the answer is not on the strip. The gear
// reports a tap; where the notice sits and how long it stays are the frame's, for the same reason
// the resource rail's arrival window is `App`'s rather than the rail's.
@OptIn(ExperimentalTestApi::class)
class SettingsNoticeBehaviourTest {

    @Test
    fun `should say the settings are coming when the gear is tapped`() {
        frameWithTheGear {
            tapSettings()
            assertShowing(English.resolve(Strings.settingsComingSoon()))
        }
    }

    @Test
    fun `should say nothing before the gear is tapped`() {
        // The frame is composed on every launch, so a notice that were there from the first frame
        // would greet every player with an apology for a screen they had not asked for.
        frameWithTheGear {
            assertGone(English.resolve(Strings.settingsComingSoon()))
        }
    }

    @Test
    fun `should clear the notice once its window has passed`() {
        frameWithTheGear {
            tapSettings()
            afterTheNotice()
            assertGone(English.resolve(Strings.settingsComingSoon()))
        }
    }

    @Test
    fun `should hold the notice for the whole of its window`() {
        // The other half of the one above, and the half that would not fail if the window were a
        // single frame long.
        frameWithTheGear {
            tapSettings()
            justBeforeTheNoticeClears()
            assertShowing(English.resolve(Strings.settingsComingSoon()))
        }
    }

    @Test
    fun `should not stack a second notice when the gear is tapped twice`() {
        frameWithTheGear {
            tapSettings()
            tapSettings()
            assertNoticeCount(1)
        }
    }

    @Test
    fun `should restart the window when the gear is tapped again`() {
        // **The reason the frame counts taps rather than raising a flag.** A `Boolean` key would set
        // `true` to `true` on a second tap, the effect would not restart, and the notice would go
        // four seconds after the *first* one — so a player tapping again just before it cleared would
        // watch their own answer vanish. Nothing about the picture would say so.
        frameWithTheGear {
            tapSettings()
            justBeforeTheNoticeClears()
            tapSettings()
            justBeforeTheNoticeClears()
            assertShowing(English.resolve(Strings.settingsComingSoon()))
        }
    }

    @Test
    fun `should sit above the tab bar rather than over it`() {
        // The whole reason the notice is placed by the frame: a surface anchored to the window's
        // bottom would need the bar's height as a number, and the bar is measured rather than pinned.
        frameWithTheGear {
            tapSettings()
            assertTheNoticeClearsTheTabBar()
        }
    }

    @Test
    fun `should say the settings are coming in Italian too`() {
        frameWithTheGear(translations = Italian) {
            tapSettings()
            assertShowing(Italian.resolve(Strings.settingsComingSoon()))
        }
    }
}
