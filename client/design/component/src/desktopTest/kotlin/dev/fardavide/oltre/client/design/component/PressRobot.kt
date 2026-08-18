package dev.fardavide.oltre.client.design.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS

// What a finger does to a control, in the two places worth asking about: the middle of it, where the
// indication is supposed to land, and the corner of its bounding box, where it is not.
//
// **The corner probe is the whole point of this robot.** A screenshot cannot make this assertion:
// the repository's tolerance allows 8% of pixels to change, calibrated for glyph anti-aliasing
// across two machines, and four 14dp corner wedges on a 393dp card are 0.2% of the frame. So the
// baseline that was recorded while the ripple *was* spilling square out of every rounded button in
// the app passed verification against the fixed one. A tolerance that can absorb the defect is the
// wrong instrument; a named pixel is the right one.
@OptIn(ExperimentalTestApi::class)
internal class PressRobot(private val test: ComposeUiTest) {

    fun bench(): PressRobot = apply {
        test.mainClock.autoAdvance = false
        test.setContent { OltreTheme { PressBench() } }
        settle()
    }

    // Down and never up, because a released ripple fades and this robot reads pixels rather than
    // waiting on them. Held, the indication settles to a constant radius and a constant alpha.
    fun press(tag: String): PressRobot = apply {
        test.onNodeWithTag(tag).performTouchInput { down(center) }
        settle()
    }

    fun middleOf(tag: String): Color = pixelOf(tag) { width, height -> width / 2 to height / 2 }

    // **Inside the node's rectangle and outside its rounded shape**, which is the only place the two
    // disagree and therefore the only place the bug was ever visible. One pixel in from each edge: at
    // the 9dp radius of the app's smallest rounded control that sits 2.3px clear of the arc, which is
    // wider than the anti-aliasing on it.
    fun cornerOf(tag: String): Color = pixelOf(tag) { _, _ -> 1 to 1 }

    private fun pixelOf(tag: String, at: (width: Int, height: Int) -> Pair<Int, Int>): Color {
        val pixels = test.onNodeWithTag(tag).captureToImage().toPixelMap()
        val (x, y) = at(pixels.width, pixels.height)
        return pixels[x, y]
    }

    // The same wind-forward every baseline in the repository takes, and for the same reason: the
    // app's one-shot transitions have to be over before anything reads what is on the screen.
    private fun settle() {
        test.mainClock.advanceTimeBy(SETTLED_MILLIS)
    }
}
