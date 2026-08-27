package dev.fardavide.oltre.client.auth.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.component.RefusalUiState
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import dev.fardavide.oltre.client.design.text.AuthProviderName
import dev.fardavide.oltre.client.design.text.DeleteFactKind
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// **Two faces, and red arrives as an outline before it is ever filled.** Captured as the contents
// rather than through the sheet, for `AlertSheetScreenshotTest`'s reason: a picture of what a face
// says has no business also depending on a popup being reachable and an entrance settling.
//
// The colony is the design's own reference one, so the four fact rows read the numbers the design
// wrote them against.
@OptIn(ExperimentalTestApi::class)
class DeleteFaceScreenshotTest {

    // All reading and no consequence: four rows of what exists, then the fact the numbers cannot
    // teach. The action is a red **ghost**, which in this system is an action that is not yet the
    // action.
    @Test
    fun `the face the account row opens`() {
        capture(name = "delete_face_warn", state = warn())
    }

    // **The four fact rows are gone**, because they were for reading and this face is for deciding.
    // The only filled red button in the product, and `Keep it` first because dismissal is a no.
    @Test
    fun `the last step`() {
        capture(name = "delete_face_confirm", state = confirm())
    }

    // **Deleting an account needs the network, so it refuses exactly as a dispatch does.** Red for
    // the refusal and red for the action, and they do not collide because one is a sentence and the
    // other is a control.
    @Test
    fun `the face with no network behind it`() {
        capture(
            name = "delete_face_offline",
            state = warn().copy(
                refusal = RefusalUiState(
                    lead = Strings.refusedDeleteLead(),
                    body = Strings.refusedDeleteBody(),
                ),
            ),
        )
    }

    // **At 288dp of content nothing is cut**: the fact rows stack their value under their label, as
    // the ladder stacks, and the button row puts the destructive action last because in a column the
    // thumb is at the bottom.
    @Test
    fun `the last step in a Slide Over window`() {
        capture(name = "delete_face_confirm_slide_over", state = confirm(), width = SLIDE_OVER_WIDTH, compact = true)
    }

    private fun capture(
        name: String,
        state: DeleteFaceUiState,
        width: Int = PHONE_WIDTH,
        compact: Boolean = false,
    ) {
        runDesktopComposeUiTest(width = width, height = FACE_HEIGHT) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    Surface {
                        DeleteFaceContent(uiState = state, compact = compact, onKeep = {}, onAct = {})
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

    // Built here rather than through the mapper, which is rule 4: a `ui` module may not see its own
    // `presentation`. What the mapper decides — which face carries which rows — is asserted in
    // `DeleteFaceUiStateTest`, where it reads as words.
    private fun warn(): DeleteFaceUiState = DeleteFaceUiState(
        title = Strings.deleteFaceTitle(),
        intro = Strings.deleteFaceIntro(),
        facts = listOf(
            DeleteFactUiState(
                label = Strings.deleteFactLabel(DeleteFactKind.COLONY),
                value = Strings.deleteFactColony(NAME, facilities = 13, level = 7),
            ),
            DeleteFactUiState(
                label = Strings.deleteFactLabel(DeleteFactKind.FLEET),
                value = Strings.deleteFactFleet(TextRes("4 skiffs and 1 hauler"), runs = 3),
            ),
            DeleteFactUiState(
                label = Strings.deleteFactLabel(DeleteFactKind.MAP),
                value = Strings.deleteFactMap(surveyed = 84, pinned = 6),
            ),
            DeleteFactUiState(
                label = Strings.deleteFactLabel(DeleteFactKind.RESEARCH),
                value = Strings.deleteFactResearch(projects = 9, adaptations = 4),
            ),
        ),
        second = Strings.deleteFaceSecond(),
        refusal = null,
        keep = null,
        action = Strings.deleteFaceAction(),
        destructive = false,
    )

    private fun confirm(): DeleteFaceUiState = DeleteFaceUiState(
        title = Strings.deleteConfirmTitle(NAME),
        intro = Strings.deleteConfirmIntro(),
        facts = emptyList(),
        second = Strings.deleteConfirmSecond(AuthProviderName.APPLE),
        refusal = null,
        keep = Strings.deleteKeep(),
        action = Strings.deleteConfirmAction(),
        destructive = true,
    )
}

// The design's reference colony, so the frames read the numbers it was drawn against.
private val NAME = TextRes("Dead Reckoning")

private const val PHONE_WIDTH = 393
private const val SLIDE_OVER_WIDTH = 320

// **Taller than either face, deliberately** — `AlertSheetScreenshotTest`'s rule: a frame that clips
// cannot show a control that overflowed, which is exactly the failure a baseline of a sheet is for.
private const val FACE_HEIGHT = 620
