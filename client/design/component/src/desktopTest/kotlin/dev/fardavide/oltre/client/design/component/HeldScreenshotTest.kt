package dev.fardavide.oltre.client.design.component

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// **The offline era's three carriers, on the design system's own bench**, beside
// `WatchSquareScreenshotTest` and `PressableScreenshotTest` and for their reason: these are spent by
// every screen in the app, so a change to one of them moves every card and a per-screen baseline says
// only that *something* moved.
//
// What no other frame holds is the wrap. `HeldNote` wraps where a verdict truncates — a verdict is
// authored to a width and drops its second clause in a Slide Over pane; a held line is a fact about
// the network with nothing optional in it. The bench is 288dp of content, which is that pane, and the
// longest line in the catalogue is on it.
@OptIn(ExperimentalTestApi::class)
class HeldScreenshotTest {

    @Test
    fun `the three ways a card says the network is not there`() {
        runDesktopComposeUiTest(width = HELD_BENCH_WIDTH, height = HELD_BENCH_HEIGHT) {
            // The ghost is a `pressable`, which settles its own press state — a frame taken before
            // that lands photographs a colour halfway between two and rounds differently on two
            // machines.
            mainClock.autoAdvance = false
            setContent { OltreTheme { Surface { HeldBench() } } }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/held.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }
}
