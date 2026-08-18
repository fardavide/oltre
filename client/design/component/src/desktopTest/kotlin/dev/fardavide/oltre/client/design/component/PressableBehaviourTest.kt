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

    // The split target, where the corner is not the interesting failure: this node claims 44dp of
    // height and draws about 30, so the ripple's escape route is the *band* above and below the face
    // rather than the four corners. `middleOf` is inside the face and `cornerOf` is in the claimed
    // area that no button was ever drawn in.
    @Test
    fun `a pressed wide target lights up its face`() {
        bench {
            val resting = middleOf(PressBenchTags.FACE)
            press(PressBenchTags.FACE)
            assertNotEquals(resting, middleOf(PressBenchTags.FACE))
        }
    }

    @Test
    fun `a pressed wide target leaves the area it claims but does not draw alone`() {
        bench {
            val resting = cornerOf(PressBenchTags.FACE)
            press(PressBenchTags.FACE)
            assertEquals(resting, cornerOf(PressBenchTags.FACE))
        }
    }

    private fun bench(assertions: PressRobot.() -> Unit) {
        runDesktopComposeUiTest(width = BENCH_WIDTH, height = BENCH_HEIGHT) {
            PressRobot(this).bench().assertions()
        }
    }
}
