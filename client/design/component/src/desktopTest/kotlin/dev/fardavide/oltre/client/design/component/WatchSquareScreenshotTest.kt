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

// **The one baseline that can see a bell.** A glyph is a `Canvas`, so it carries no semantics at all
// — no behaviour test in the repository can tell one bell from two, and the screens that draw the
// square photograph it at 17dp inside a card, where a stroke that moved by a unit is a pixel nobody
// would query. This frame is where the drawing is actually held.
//
// It lives beside `PressableScreenshotTest` for its reason: the design system's own components are
// worth photographing on their own bench, because a change to one of them moves every screen that
// spends it and a per-screen baseline says only that *something* moved.
@OptIn(ExperimentalTestApi::class)
class WatchSquareScreenshotTest {

    @Test
    fun `the square in every state it has`() {
        runDesktopComposeUiTest(width = WATCH_BENCH_WIDTH, height = WATCH_BENCH_HEIGHT) {
            // The square settles its colours when it lights — see `settlingColor` — so a frame taken
            // before the animation lands would photograph a colour halfway between the two states
            // and round differently on two machines.
            mainClock.autoAdvance = false
            setContent { OltreTheme { Surface { WatchSquareBench() } } }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/watch_square.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }
}
