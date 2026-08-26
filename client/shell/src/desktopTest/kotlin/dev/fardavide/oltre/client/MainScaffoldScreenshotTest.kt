package dev.fardavide.oltre.client

import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.player.ui.PlayerTestTags
import dev.fardavide.oltre.client.tilt.domain.Tilt
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// The frame as a whole, and the only baseline that sees the starfield at all: it is drawn inside
// the destination box, so every per-screen baseline in the repo renders the screen without the
// scaffold around it and never captures a single star.
//
// That gap is worth closing rather than noting. The reason the field is twenty-six hard-coded
// positions instead of a seeded generator is precisely that a screenshot test has to be able to
// hold it still — which is an argument for a baseline that would notice, and there was none.
//
// The destination is deliberately a bare marker rather than a real screen. What this baseline is
// for is the chrome and the layer behind it: the rail above, the tab bar below, and the field in
// between with nothing occluding it. A real colony here would cover the stars with cards and
// re-assert what the colony's own baselines already say.
@OptIn(ExperimentalTestApi::class)
class MainScaffoldScreenshotTest {

    // The parallax's only witness. The field behind a destination is a function of that
    // destination's scroll offset and of nothing else, so the way to photograph it is to hand it an
    // offset — no gesture, no clock, no scrollable content to measure against.
    //
    // 620 is past a phone's own height, which is the case the un-wrapped first draft got wrong: the
    // near plane keeps 58% of the list's speed, so at this offset an unwrapped field would have
    // carried its stars 360dp up and left the bottom of the frame empty. What this baseline holds is
    // that the sky still reaches both edges.
    @Test
    fun `the field behind a destination that has been scrolled`() {
        runDesktopComposeUiTest(width = 393, height = 600) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    Surface {
                        Starfield(scrollOffset = { 620f })
                    }
                }
            }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/starfield_scrolled.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    @Test
    fun `the frame and the field behind it in a phone-sized window`() {
        captureFrame(name = "main_scaffold")
    }

    // **The frame at the narrowest window the app supports, and it has never had one.** Every screen
    // *inside* the frame has a Slide Over baseline; the chrome that holds them — the strip, the rail
    // and the tab bar, all three of which reflow at 320 — has only ever been photographed at 393.
    //
    // It arrives with this slice rather than for it: 0.18 is what took the notice out of this file and
    // put a slot in, so it is the release that reads `MainScaffold` closely, and 320 is the width the
    // design measured the sheet against. What the frame holds is five tabs sharing 320dp and a rail
    // that has to stack.
    @Test
    fun `the frame in a Slide Over window`() {
        captureFrame(name = "main_scaffold_slide_over", width = SLIDE_OVER_WIDTH)
    }

    // **The one new piece of chrome the offline era adds**, and the frame that holds where it sits:
    // under the rail, above the destination, and 22dp tall — which is exactly the height every
    // destination loses and `GalaxyRobot.DESTINATION_HEIGHT` had to move by.
    @Test
    fun `the frame with no network`() {
        captureFrame(
            name = "main_scaffold_offline",
            offline = OfflineLineUiState(
                text = Strings.offlineSince(hour = 11, minute = 31, held = 3, compact = false),
            ),
        )
    }

    // The same line at 320, where the noun goes and both numbers stay — the design's rule for this
    // width, and the one string in the frame that is authored twice.
    @Test
    fun `the frame with no network in a Slide Over window`() {
        captureFrame(
            name = "main_scaffold_offline_slide_over",
            width = SLIDE_OVER_WIDTH,
            offline = OfflineLineUiState(
                text = Strings.offlineSince(hour = 11, minute = 31, held = 3, compact = true),
            ),
        )
    }

    private fun captureFrame(
        name: String,
        width: Int = PHONE_WIDTH,
        offline: OfflineLineUiState? = null,
    ) {
        runDesktopComposeUiTest(width = width, height = 852) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    Surface {
                        MainScaffold(
                            // Desktop has no motion sensor, so this is also what the app itself passes here.
                            tilt = { Tilt.NONE },
                            player = testPlayerStripUiState,
                            resources = testResourceRailUiState,
                            colony = { Text("colony-under-test") },
                            research = { Text("research-under-test") },
                            galaxy = { _, _ -> Text("galaxy-under-test") },
                            shipyard = { Text("shipyard-under-test") },
                            fleets = { Text("fleets-under-test") },
                            offline = offline,
                            // The gear is drawn and never tapped: what it opens is `App`'s, and a
                            // sheet is a popup that `onRoot()` could not photograph anyway.
                            onOpenSettings = {},
                        )
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

    private companion object {
        const val PHONE_WIDTH = 393
        const val SLIDE_OVER_WIDTH = 320
    }

    // **The frame's one after-a-tap baseline left with 0.18**, and so did the reason for it. It
    // photographed where the `Coming soon` notice sat — a claim only a picture could carry, because
    // the notice was the frame's own and took no parameter, so nothing else could render it.
    //
    // What the gear opens is a `ModalBottomSheet` now, and a sheet is a popup: it is composed into a
    // window of its own, so `onRoot()` photographs the frame with nothing on it whatever the gear
    // has been asked. `AlertSheetScreenshotTest` captures the contents, `AlertSheetBehaviourTest`
    // drives the chrome, and `MainScaffoldBehaviourTest` holds the one claim that was ever this
    // file's — that the gear opens something and that a second tap closes it.
}
