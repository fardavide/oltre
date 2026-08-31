package dev.fardavide.oltre.client.player.ui

import androidx.compose.ui.test.ExperimentalTestApi
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.protocol.CommanderName
import dev.fardavide.oltre.protocol.MarkBody
import dev.fardavide.oltre.protocol.MarkPath
import dev.fardavide.oltre.protocol.MarkPreset
import dev.fardavide.oltre.protocol.MarkTerminus
import dev.fardavide.oltre.protocol.PlayerMark
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

// **What the two faces do when they are pressed**, which is the half a baseline cannot photograph. A
// screenshot shows a Save button beautifully whether or not anything is behind it; every test here
// asserts the effect instead — what reached the callback, what stopped reaching it, and what is not on
// screen at all.
@OptIn(ExperimentalTestApi::class)
class IdentityBehaviourTest {

    @Test
    fun `should report the preset a tapped cell draws`() {
        var chosen: MarkPreset? = null

        identityFace(onChooseMark = { chosen = it }) {
            tapCell(MarkPreset.SEXTANT)
        }

        assertEquals(MarkPreset.SEXTANT, chosen)
    }

    @Test
    fun `should report every one of the six cells`() {
        // Six assertions in one test because what would be wrong here is a cell wired to its
        // neighbour — and a single cell tapped on its own cannot see that.
        val chosen = mutableListOf<MarkPreset>()

        MarkPreset.entries.forEach { preset ->
            identityFace(onChooseMark = { chosen += it }) { tapCell(preset) }
        }

        assertEquals(MarkPreset.entries.toList(), chosen.toList())
    }

    @Test
    fun `should report the cell that is already lit`() {
        // A grid is not a toggle: every silhouette is on screen, so a tap means *this one* rather than
        // *the other one*. Tapping the mark you are already wearing writes the mark you are already
        // wearing, and the thing a player must never see is the grid moving under their finger.
        var chosen: MarkPreset? = null

        identityFace(identityFaceUiState(chosen = MarkPreset.THRESHOLD), onChooseMark = { chosen = it }) {
            tapCell(MarkPreset.THRESHOLD)
        }

        assertEquals(MarkPreset.THRESHOLD, chosen)
    }

    @Test
    fun `should open the composer when the compose row is tapped`() {
        var opened = false

        identityFace(onComposeMark = { opened = true }) {
            tapCompose()
        }

        assertEquals(true, opened)
    }

    @Test
    fun `should offer no save until the draft differs from what is committed`() {
        identityFace {
            assertNoSave()
            type("!")
            assertSaveShowing()
        }
    }

    @Test
    fun `should take the save back once the draft matches what is committed again`() {
        // Typing and then undoing is the state a `saveable` flag would get wrong, and it is one keystroke
        // away at all times.
        identityFace(identityFaceUiState(committed = "Ada", draft = "Ada")) {
            type("m")
            assertSaveShowing()
            tapClear()
            type("Ada")
            assertNoSave()
        }
    }

    @Test
    fun `should report the name when save is tapped`() {
        var saved = false

        identityFace(onSaveName = { saved = true }) {
            type("!")
            tapSave()
        }

        assertEquals(true, saved)
    }

    @Test
    fun `should show the placeholder again once the field is cleared`() {
        identityFace {
            focusName()
            tapClear()
            assertEquals("", name())
            assertPlaceholderShowing()
        }
    }

    @Test
    fun `should keep the counter silent at seventeen and show it at eighteen`() {
        identityFace(identityFaceUiState(committed = "", draft = "")) {
            type(NAME_AT_THE_BOUND.take(SILENT_LENGTH))
            assertNoCounter()
            type("x")
            assertCounter(SILENT_LENGTH + 1)
        }
    }

    @Test
    fun `should stop accepting at the bound rather than refusing`() {
        assertEquals(CommanderName.MAX_LENGTH, NAME_AT_THE_BOUND.length, "the fixture is not at the bound")

        identityFace(identityFaceUiState(committed = "", draft = "")) {
            type(NAME_AT_THE_BOUND)
            type(" and more")

            assertCounter(CommanderName.MAX_LENGTH)
            assertEquals(NAME_AT_THE_BOUND, name())
        }
    }

    @Test
    fun `should report nothing and offer no save while held`() {
        var chosen: MarkPreset? = null
        var opened = false

        identityFace(
            uiState = identityFaceUiState(
                committed = COMMITTED_NAME,
                // A draft that differs, so what makes the button absent is being held rather than
                // having nothing to save.
                draft = "Ada",
                requirement = Strings.profileHeldRequirement(hour = 9, minute = 41),
            ),
            onChooseMark = { chosen = it },
            onComposeMark = { opened = true },
        ) {
            assertRequirementShowing()
            assertNoSave()
            // Focused first, because unfocused is when the clear is absent anyway: what has to be true
            // is that it stays absent on the one field state that would otherwise summon it.
            focusName()
            assertNoClear()
            assertShowing(Strings.profileHeldFieldNote())
            tapCell(MarkPreset.WAKE)
            tapCompose()
        }

        assertNull(chosen, "a held grid committed a mark")
        assertEquals(false, opened)
    }

    @Test
    fun `should still answer every cell at a Slide Over width`() {
        // The narrow window is where the grid stacks to three by two, so every cell is re-measured and
        // re-placed. What must not change is that they are still the same six controls.
        var chosen: MarkPreset? = null

        identityFace(compact = true, width = SLIDE_OVER_WIDTH, onChooseMark = { chosen = it }) {
            tapCell(MarkPreset.SOUNDING)
        }

        assertEquals(MarkPreset.SOUNDING, chosen)
    }

    @Test
    fun `should report the body a tapped chip swaps in`() {
        var chosen: MarkBody? = null

        markComposeFace(onChooseBody = { chosen = it }) {
            tapBody(MarkBody.ORBIT)
        }

        assertEquals(MarkBody.ORBIT, chosen)
    }

    @Test
    fun `should report the path a tapped chip swaps in`() {
        var chosen: MarkPath? = null

        markComposeFace(onChoosePath = { chosen = it }) {
            tapPath(MarkPath.TWIN)
        }

        assertEquals(MarkPath.TWIN, chosen)
    }

    @Test
    fun `should report the terminus a tapped chip swaps in`() {
        var chosen: MarkTerminus? = null

        markComposeFace(onChooseTerminus = { chosen = it }) {
            tapTerminus(MarkTerminus.RING)
        }

        assertEquals(MarkTerminus.RING, chosen)
    }

    @Test
    fun `should drop the terminus ladder when the path is none`() {
        markComposeFace {
            assertTerminusLadderShowing()
        }
        markComposeFace(
            markComposeFaceUiState(
                mark = PlayerMark.Composed(
                    body = MarkBody.LIMB,
                    path = MarkPath.NONE,
                    terminus = MarkTerminus.NONE,
                ),
            ),
        ) {
            assertNoTerminusLadder()
            // The path ladder itself has not moved: `None` is a stop on it like the other three, which
            // is why the ladder below vanishes rather than the chip going dark.
            assertShowing(Strings.markSlotPath())
        }
    }

    @Test
    fun `should report none when the path is set to none`() {
        var chosen: MarkPath? = null

        markComposeFace(onChoosePath = { chosen = it }) {
            tapPath(MarkPath.NONE)
        }

        assertEquals(MarkPath.NONE, chosen)
    }

    // **The composer's eleven chips answer to held exactly as the six cells do**, and it had no held
    // state at all: with no signal every part stayed lit and fully tappable, and each tap changed
    // nothing a player could perceive — while the identity face, for the very same tap, dimmed to 42%
    // and raised the amber card. All three ladders are driven here because what would be wrong is one
    // of them being left live, and a single chip cannot see that.
    @Test
    fun `should report nothing from any ladder while held`() {
        var body: MarkBody? = null
        var path: MarkPath? = null
        var terminus: MarkTerminus? = null

        markComposeFace(
            uiState = markComposeFaceUiState(
                requirement = Strings.profileHeldRequirement(hour = 9, minute = 41),
            ),
            onChooseBody = { body = it },
            onChoosePath = { path = it },
            onChooseTerminus = { terminus = it },
        ) {
            assertRequirementShowing()
            tapBody(MarkBody.ORBIT)
            tapPath(MarkPath.TWIN)
            tapTerminus(MarkTerminus.RING)
        }

        assertNull(body, "a held body ladder committed a part")
        assertNull(path, "a held path ladder committed a part")
        assertNull(terminus, "a held terminus ladder committed a part")
    }

    // The card is the state, so its absence is what makes the eleven chips live — asserted rather
    // than assumed, because every test above this one depends on it.
    @Test
    fun `should raise no card on the composer while the chips can commit`() {
        markComposeFace {
            assertNoRequirement()
        }
    }
}

// The last length the counter says nothing about. The frame's own number, and the whole of what
// "silent to 17, showing from 18" is.
private const val SILENT_LENGTH = 17
