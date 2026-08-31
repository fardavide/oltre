package dev.fardavide.oltre.client.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Translations
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// **The app's first name field, in the six states the frame names.** They are six pictures rather than
// six settings: the line changes on focus, the caret and the clear come and go with it, the counter
// arrives at eighteen characters, and held swaps the whole face for amber.
//
// **Focus is requested rather than tapped.** `requestFocus` performs the semantics action and no
// touch, so a focused frame carries no press indication — which a `performClick` would bake into the
// baseline and pin there forever.
//
// **The caret is why these frames advance past `SETTLED_MILLIS` rather than to it.** The platform
// blinks it on a one-second cycle and 2,000ms lands exactly on a phase change, where the state the
// animation snapped to and the frame that draws it fall on either side of the same instant. A little
// past that, the answer is the same on every machine and does not depend on which of the two ran
// first.
@OptIn(ExperimentalTestApi::class)
class NameFieldScreenshotTest {

    // A name already committed, nothing focused: 1dp white 9% around the card's own `#101218`.
    @Test
    fun `the field holding a name at rest`() {
        capture(name = "name_field_resting", draft = COMMITTED_NAME)
    }

    // **An empty field is a preview and not an error.** The placeholder is `Dead Reckoning` in the same
    // 13.5sp SemiBold as real text, so what is on screen is the value saving it would give you.
    @Test
    fun `the field focused with nothing in it`() {
        capture(name = "name_field_focused_empty", draft = "", focus = true)
    }

    // Focused with something typed: the accent line, the caret, and the clear glyph in the 44dp square
    // that eats the trailing padding.
    @Test
    fun `the field focused with a name in it`() {
        capture(name = "name_field_focused_holding", draft = COMMITTED_NAME, focus = true)
    }

    // Accent at 22% behind the selected run — the one alpha in this control that is not a border.
    @Test
    fun `a run of the name selected`() {
        capture(name = "name_field_selection", draft = COMMITTED_NAME, selection = TextRange(0, SELECTED_RUN))
    }

    // **Twenty-four characters, and the counter at full ink.** It never goes amber and never goes red:
    // amber means held and red means short, and running out of characters is neither. The field simply
    // stops accepting, which is a thing a picture cannot show and a behaviour test can.
    @Test
    fun `the field at the bound`() {
        capture(name = "name_field_at_the_bound", draft = NAME_AT_THE_BOUND, focus = true)
    }

    // Held: amber 22% around the fleet strip's own `#141111`, no caret, no clear, and the note under it
    // saying a rename cannot wait. Nothing is greyed and nothing is red.
    @Test
    fun `the field with no network behind it`() {
        capture(name = "name_field_held", draft = COMMITTED_NAME, held = true)
    }

    private fun capture(
        name: String,
        draft: String,
        held: Boolean = false,
        focus: Boolean = false,
        selection: TextRange? = null,
        width: Int = PHONE_WIDTH,
        translations: Translations = English,
    ) {
        runDesktopComposeUiTest(width = width, height = FIELD_FRAME_HEIGHT) {
            mainClock.autoAdvance = false
            setContent {
                // The draft is the caller's everywhere else; a frame that photographs a cleared field
                // would need somewhere to put the empty string, so the frame is that somewhere.
                var typed by remember { mutableStateOf(draft) }
                OltreTheme(translations = translations) {
                    Surface {
                        Box(modifier = Modifier.fillMaxSize().padding(FRAME_PAD)) {
                            NameField(draft = typed, held = held, onDraftChange = { typed = it })
                        }
                    }
                }
            }
            if (focus) onNodeWithTag(IdentityTestTags.NAME).requestFocus()
            // Focuses on its own, which is why the selected frame does not ask for focus as well.
            selection?.let { onNodeWithTag(IdentityTestTags.NAME).performTextInputSelection(it) }
            mainClock.advanceTimeBy(SETTLED_MILLIS + CARET_PHASE_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/$name.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    private companion object {

        // The 44dp field and its note row with a clear band of window either side, so the border is
        // photographed against background rather than against the frame's own edge.
        const val FIELD_FRAME_HEIGHT = 110

        // The face's own side padding, so the field is drawn at the width it is drawn at on the sheet
        // rather than edge to edge.
        val FRAME_PAD = 16.dp

        // Clear of the blink's own half-second boundary and of the settle before it. Any value in the
        // open interval works; this one is a tenth of the cycle.
        const val CARET_PHASE_MILLIS = 120L

        // "Ada" — enough of the name to be unmistakably a run rather than a caret.
        const val SELECTED_RUN = 3
    }
}
