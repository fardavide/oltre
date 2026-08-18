package dev.fardavide.oltre.client.design.component

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

// **One property, stated three times because the app presses three different shapes.** A press puts
// ink inside the control and nowhere else — which sounds like a restatement of what a ripple is, and
// was false in every rounded thing this app draws until 0.13.1: `Modifier.clickable` clips its
// indication to the node's *rectangle*, so a circle expanding across a 14dp card painted the corners
// the card's own fill had already rounded off.
//
// Each shape is asserted twice and the pair is the test. On its own, "the corner is untouched" is
// satisfied by a press that draws nothing at all — by a broken interaction source, a control that
// stopped being clickable, a probe reading the wrong node. The second assertion is what makes the
// first mean something: the middle *does* change, so there was ink to keep out of the corner.
@OptIn(ExperimentalTestApi::class)
class PressableBehaviourTest {

    @Test
    fun `a pressed card lights up`() {
        bench {
            val resting = middleOf(PressBenchTags.CARD)
            press(PressBenchTags.CARD)
            assertNotEquals(resting, middleOf(PressBenchTags.CARD))
        }
    }

    @Test
    fun `a pressed card leaves the corners its own shape rounded off`() {
        bench {
            val resting = cornerOf(PressBenchTags.CARD)
            press(PressBenchTags.CARD)
            assertEquals(resting, cornerOf(PressBenchTags.CARD))
        }
    }

    @Test
    fun `a pressed button lights up`() {
        bench {
            val resting = middleOf(PressBenchTags.BUTTON)
            press(PressBenchTags.BUTTON)
            assertNotEquals(resting, middleOf(PressBenchTags.BUTTON))
        }
    }

    @Test
    fun `a pressed button leaves the corners its own shape rounded off`() {
        bench {
            val resting = cornerOf(PressBenchTags.BUTTON)
            press(PressBenchTags.BUTTON)
            assertEquals(resting, cornerOf(PressBenchTags.BUTTON))
        }
    }

    // **The split target has two ways to be wrong, and it needs both asked about.** It claims 44dp of
    // height and draws about 30, so one failure is a ripple smeared across the whole claim — and the
    // other is the same square corner as everywhere else, on the face it actually draws.
    //
    // The first version of this file asked only the first question, and that was a real hole rather
    // than a stylistic one: deleting the clip from `PressableFace` left all six tests green and the
    // baseline verifying, because the probe read the *claim* box and pixel (1,1) of that lands in the
    // 3dp band above the face, where no button is drawn and no arc passes. The component this change
    // introduced — the one carrying the fix to `WatchSquare`, `ProbeAction` and `MapCaption` — was
    // the one shape whose rounding nothing checked. Hence two tags and four tests.
    @Test
    fun `a pressed wide target lights up its face`() {
        bench {
            val resting = middleOf(PressBenchTags.FACE)
            press(PressBenchTags.FACE_CLAIM)
            assertNotEquals(resting, middleOf(PressBenchTags.FACE))
        }
    }

    @Test
    fun `a pressed wide target keeps its ripple inside the face it draws`() {
        bench {
            val resting = cornerOf(PressBenchTags.FACE)
            press(PressBenchTags.FACE_CLAIM)
            assertEquals(resting, cornerOf(PressBenchTags.FACE))
        }
    }

    @Test
    fun `a pressed wide target leaves the height it claims but does not draw`() {
        bench {
            val resting = cornerOf(PressBenchTags.FACE_CLAIM)
            press(PressBenchTags.FACE_CLAIM)
            assertEquals(resting, cornerOf(PressBenchTags.FACE_CLAIM))
        }
    }

    private fun bench(assertions: PressRobot.() -> Unit) {
        runDesktopComposeUiTest(width = BENCH_WIDTH, height = BENCH_HEIGHT) {
            PressRobot(this).bench().assertions()
        }
    }
}
