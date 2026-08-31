package dev.fardavide.oltre.client.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import dev.fardavide.oltre.client.design.component.SectionLabel
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Italian
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.Translations
import dev.fardavide.oltre.protocol.MarkBody
import dev.fardavide.oltre.protocol.MarkPath
import dev.fardavide.oltre.protocol.MarkPreset
import dev.fardavide.oltre.protocol.MarkTerminus
import dev.fardavide.oltre.protocol.PlayerMark
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// The two identity faces, in the states that differ from each other as *drawings* rather than as
// settings — captured as the bare contents rather than through the modal, because a
// `ModalBottomSheet` renders into a scene root of its own and `onRoot()` cannot reach it.
//
// **Every frame is taller than its face, deliberately.** The measured heights are 316, 367, 444 and
// 377 for the four identity frames and 457 / 474 for the composer, and each window here has room over
// that: a frame that clipped could not show a control that overflowed, which is the one thing these
// pictures are for.
//
// **No `performClick` anywhere.** A press bakes its own indication into a baseline and pins it there
// forever, so every state below is reached by handing the face a different `UiState` — the save button
// appears because the draft differs, not because anything was typed.
@OptIn(ExperimentalTestApi::class)
class IdentityScreenshotTest {

    // How it opens for an account that has never been here: `Threshold` lit, `Dead Reckoning` in the
    // field, and no save button because there is nothing to save.
    @Test
    fun `the face a new commander opens`() {
        capture(name = "identity_face", height = 360) {
            IdentityFace(uiState = identityFaceUiState(committed = "", draft = ""))
        }
    }

    // **The one state the save button exists in.** A draft that differs from what is committed is the
    // whole of the condition, and this is the frame that says the button is a 44dp accent face rather
    // than a greyed one.
    @Test
    fun `a name typed and not yet saved`() {
        capture(name = "identity_face_save", height = 400) {
            IdentityFace(uiState = identityFaceUiState(chosen = MarkPreset.SEXTANT, draft = "Ada Lovelac"))
        }
    }

    // Held: the grid and the row at 42%, the requirement above them at full strength, no save button
    // and nothing red. The sheet still opens — a player who taps their own name deserves to see what
    // they would be choosing.
    @Test
    fun `the face with no network behind it`() {
        capture(name = "identity_face_held", height = 490) {
            IdentityFace(
                uiState = identityFaceUiState(
                    requirement = Strings.profileHeldRequirement(hour = 9, minute = 41),
                ),
            )
        }
    }

    // 288dp of content will not hold six cells, so the grid stacks to three by two — the dispatch
    // ladder's move rather than a clip. Nothing is dropped and nothing is shortened.
    @Test
    fun `the grid stacks in a Slide Over window`() {
        capture(name = "identity_face_slide_over", height = 420, width = SLIDE_OVER_WIDTH) {
            IdentityFace(uiState = identityFaceUiState(), compact = true)
        }
    }

    // Italian at the narrow width, because that is where the longest language meets the least room —
    // `Componi un segno` in a 34dp ghost row and `Nome e segno` over a stacked grid.
    @Test
    fun `the stacked grid in Italian`() {
        capture(
            name = "identity_face_slide_over_it",
            height = 420,
            width = SLIDE_OVER_WIDTH,
            translations = Italian,
        ) {
            IdentityFace(uiState = identityFaceUiState(), compact = true)
        }
    }

    // The composer as it opens: on `Threshold`, which is the one preset the grammar can make. Three
    // ladders of whole marks, because a path on its own is four pixels of stroke.
    @Test
    fun `the composer on the one preset the grammar can make`() {
        capture(name = "mark_compose_face", height = 500) {
            MarkComposeFace(uiState = markComposeFaceUiState())
        }
    }

    // **A terminus is the end of a path**, so with no path the third ladder is not drawn at all —
    // not dimmed, not empty, absent. The face is 360dp here against 457 with the ladder.
    @Test
    fun `the composer with no path and therefore no terminus`() {
        capture(name = "mark_compose_face_no_path", height = 420) {
            MarkComposeFace(
                uiState = markComposeFaceUiState(
                    mark = PlayerMark.Composed(
                        body = MarkBody.LIMB,
                        path = MarkPath.NONE,
                        terminus = MarkTerminus.NONE,
                    ),
                ),
            )
        }
    }

    // Still four across at 320dp, which is the frame's own call: stacking a ladder would break the
    // one-row-per-slot reading that makes three of them legible at once.
    @Test
    fun `the composer in a Slide Over window`() {
        capture(name = "mark_compose_face_slide_over", height = 520, width = SLIDE_OVER_WIDTH) {
            MarkComposeFace(uiState = markComposeFaceUiState())
        }
    }

    // Held, and it is the identity face's frame said again on the other half of the one editor: the
    // card above at full strength, the preview and all three ladders at 42%, nothing red and no chip
    // left with a press behind it. There was no such frame because there was no such state — every
    // part stayed lit and answered a tap with nothing.
    @Test
    fun `the composer with no network behind it`() {
        capture(name = "mark_compose_face_held", height = 620) {
            MarkComposeFace(
                uiState = markComposeFaceUiState(
                    requirement = Strings.profileHeldRequirement(hour = 9, minute = 41),
                ),
            )
        }
    }

    @Test
    fun `the composer in Italian in a Slide Over window`() {
        capture(
            name = "mark_compose_face_slide_over_it",
            height = 520,
            width = SLIDE_OVER_WIDTH,
            translations = Italian,
        ) {
            MarkComposeFace(uiState = markComposeFaceUiState())
        }
    }

    // **The whole vocabulary at 3×, which is the one frame nobody navigates to.** Six silhouettes and
    // eleven parts at three times the 20dp they ship at: the size at which a reader can tell a
    // terminator from an orbit and a ring from a dot, and the only picture in the suite that is about
    // the drawings rather than about a screen. The design budgets it by name.
    @Test
    fun `every mark and every part at three times size`() {
        capture(name = "mark_guidelines", height = 560) { GuidelineCard() }
    }

    private fun capture(
        name: String,
        height: Int,
        width: Int = PHONE_WIDTH,
        translations: Translations = English,
        content: @Composable () -> Unit,
    ) {
        runDesktopComposeUiTest(width = width, height = height) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme(translations = translations) {
                    Surface {
                        // Filling the window is what pins the capture: `captureRoboImage` on
                        // `onRoot()` photographs the root node's measured bounds rather than the
                        // window, so without this the image would be the face's own height and a
                        // one-pixel disagreement between two machines would fail on dimensions before
                        // it ever compared a pixel.
                        Box(modifier = Modifier.fillMaxSize()) { content() }
                    }
                }
            }
            // Past the settle every lit border runs on entry, so a face is photographed where its
            // colours land rather than wherever the first frame caught them.
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/$name.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }
}

// The two faces with their callbacks stubbed, so a frame reads as the state it is of rather than as
// eight arguments. Nothing here is tapped, which is why every lambda is empty.
@Composable
private fun IdentityFace(uiState: IdentityFaceUiState, compact: Boolean = false) {
    IdentityFaceContent(
        uiState = uiState,
        compact = compact,
        onChooseMark = {},
        onComposeMark = {},
        onNameChange = {},
        onSaveName = {},
    )
}

@Composable
private fun MarkComposeFace(uiState: MarkComposeFaceUiState) {
    MarkComposeFaceContent(
        uiState = uiState,
        onChooseBody = {},
        onChoosePath = {},
        onChooseTerminus = {},
    )
}

// **A sheet nobody can reach, and that is what it is for.** The parts are drawn *alone* here rather
// than composed into a mark, which is the one place in the suite that happens: on a chip each part
// arrives wearing the other two, and this is where somebody checking a drawing can see the stroke that
// belongs to it and no other.
@Composable
private fun GuidelineCard() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(GUIDELINE_PAD),
        verticalArrangement = Arrangement.spacedBy(GUIDELINE_BLOCK),
    ) {
        Section(label = { SectionLabel(text = Strings.profileMarkLabel()) }) {
            // Three across rather than six: at 3× a row of six is 360dp of glyph in 361dp of content,
            // and this frame is about the drawings rather than about the grid.
            MarkPreset.entries.chunked(GUIDELINE_COLUMNS).forEach { row ->
                GuidelineRow {
                    row.forEach { preset ->
                        GuidelineGlyph { unit, ink ->
                            drawMarkPreset(preset = preset, unit = unit, dx = 0f, dy = 0f, color = ink)
                        }
                    }
                }
            }
        }
        Section(label = { SectionLabel(text = Strings.markSlotBody()) }) {
            GuidelineRow {
                MarkBody.entries.forEach { body ->
                    GuidelineGlyph { unit, ink ->
                        drawMarkBody(body = body, unit = unit, dx = 0f, dy = 0f, color = ink)
                    }
                }
            }
        }
        Section(label = { SectionLabel(text = Strings.markSlotPath()) }) {
            GuidelineRow {
                MarkPath.entries.forEach { path ->
                    GuidelineGlyph { unit, ink ->
                        drawMarkPath(path = path, unit = unit, dx = 0f, dy = 0f, color = ink)
                    }
                }
            }
        }
        Section(label = { SectionLabel(text = Strings.markSlotTerminus()) }) {
            GuidelineRow {
                MarkTerminus.entries.forEach { end ->
                    GuidelineGlyph { unit, ink ->
                        drawMarkTerminus(terminus = end, unit = unit, dx = 0f, dy = 0f, color = ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(label: @Composable () -> Unit, rows: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GUIDELINE_GAP)) {
        label()
        rows()
    }
}

@Composable
private fun GuidelineRow(glyphs: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(GUIDELINE_GAP), content = { glyphs() })
}

// The ink is the strip's own accent, so what this frame shows is the mark as a player meets it rather
// than as an outline nobody sees.
@Composable
private fun GuidelineGlyph(draw: DrawScope.(unit: Float, ink: Color) -> Unit) {
    Canvas(modifier = Modifier.size(GUIDELINE_MARK)) {
        draw(size.width / MARK_VIEWBOX, OltreColors.accent)
    }
}

private val GUIDELINE_MARK = 60.dp
private val GUIDELINE_GAP = 7.dp
private val GUIDELINE_BLOCK = 13.dp
private val GUIDELINE_PAD = 16.dp
private const val GUIDELINE_COLUMNS = 3
