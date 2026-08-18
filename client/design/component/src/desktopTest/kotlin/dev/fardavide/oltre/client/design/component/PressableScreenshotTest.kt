package dev.fardavide.oltre.client.design.component

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// **The only baselines in the repository taken with a finger down.** Every other frame here is of a
// screen at rest, because at rest is what a player spends a session looking at — and an indication
// draws nothing at all until it is touched, so no baseline in the app could see what a press does.
//
// **What these are and are not for.** They are a record of what a held press *looks* like, so a
// change to the ripple's colour, radius or opacity has to be looked at by somebody. They are not the
// guard on the clipping bug they were written for: the tolerance here allows 8% of pixels to change
// and four rounded corners are 0.2% of the frame, so the baseline recorded against the broken code
// verified clean against the fixed one. `PressableBehaviourTest` is the guard, and it reads a named
// pixel rather than a whole image. The two are worth having together for the reason the test-coverage
// skill gives — a screenshot says what a state looks like, a behaviour test says what touching it
// does — but only one of them can fail for the right reason here, and it is not this one.
@OptIn(ExperimentalTestApi::class)
class PressableScreenshotTest {

    @Test
    fun `a card under a finger`() {
        pressed(tag = PressBenchTags.CARD, file = "pressable_card")
    }

    // The 9dp verb — the colony's Upgrade, Research's Research, the shipyard's Build and the row
    // sheet's own footer are one button drawn four times, and this is it.
    @Test
    fun `a button under a finger`() {
        pressed(tag = PressBenchTags.BUTTON, file = "pressable_button")
    }

    // A tap area deliberately larger than the thing it presses, so a 30dp button can claim the 44dp
    // iOS minimum without drawing 44dp. What the frame shows is that the ripple is the size of the
    // button rather than the size of the claim.
    @Test
    fun `a wide target under a finger`() {
        pressed(tag = PressBenchTags.FACE, file = "pressable_face")
    }

    private fun pressed(tag: String, file: String) {
        runDesktopComposeUiTest(width = BENCH_WIDTH, height = BENCH_HEIGHT) {
            mainClock.autoAdvance = false
            setContent { OltreTheme { Surface { PressBench() } } }
            // Down and never up: a released ripple fades out, and a frame of a fade is a frame that
            // rounds differently on two machines. Held, it settles and stays.
            onNodeWithTag(tag).performTouchInput { down(center) }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/$file.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }
}
