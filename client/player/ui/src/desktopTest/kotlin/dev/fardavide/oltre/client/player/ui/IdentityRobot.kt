package dev.fardavide.oltre.client.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.design.text.Translations
import dev.fardavide.oltre.protocol.CommanderName
import dev.fardavide.oltre.protocol.MarkBody
import dev.fardavide.oltre.protocol.MarkPath
import dev.fardavide.oltre.protocol.MarkPreset
import dev.fardavide.oltre.protocol.MarkTerminus

// The two identity faces, driven the way a finger and a keyboard drive them.
//
// **The scene owns the draft, and that is what the shell will do too.** The face takes the draft as a
// parameter and reports every keystroke, like every other control in this app — so a test that wants
// to see what happens *after* a keystroke has to be the thing that remembers it. A face that held its
// own draft would be easier to test here and impossible to seed from a mapper, which is the wrong way
// round.
//
// The clock is paused before `setContent` and stepped a frame after every action, which is
// `playerStrip`'s idiom and buys one thing this scene needs more than that one did: a focused text
// field blinks a caret forever, and an auto-advancing clock has no idle to wait for.
@OptIn(ExperimentalTestApi::class)
internal fun identityFace(
    uiState: IdentityFaceUiState = identityFaceUiState(),
    compact: Boolean = false,
    width: Int = PHONE_WIDTH,
    translations: Translations = English,
    onChooseMark: (MarkPreset) -> Unit = {},
    onComposeMark: () -> Unit = {},
    onSaveName: () -> Unit = {},
    block: IdentityRobot.() -> Unit,
) {
    runDesktopComposeUiTest(width = width, height = FACE_SCENE_HEIGHT) {
        mainClock.autoAdvance = false
        setContent {
            var draft by remember { mutableStateOf(uiState.draft) }
            OltreTheme(translations = translations) {
                Surface {
                    Box(modifier = Modifier.fillMaxSize()) {
                        IdentityFaceContent(
                            uiState = uiState.copy(draft = draft),
                            compact = compact,
                            onChooseMark = onChooseMark,
                            onComposeMark = onComposeMark,
                            onNameChange = { typed -> draft = typed },
                            onSaveName = onSaveName,
                        )
                    }
                }
            }
        }
        mainClock.advanceTimeByFrame()
        IdentityRobot(this).block()
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun markComposeFace(
    uiState: MarkComposeFaceUiState = markComposeFaceUiState(),
    width: Int = PHONE_WIDTH,
    translations: Translations = English,
    onChooseBody: (MarkBody) -> Unit = {},
    onChoosePath: (MarkPath) -> Unit = {},
    onChooseTerminus: (MarkTerminus) -> Unit = {},
    block: IdentityRobot.() -> Unit,
) {
    runDesktopComposeUiTest(width = width, height = FACE_SCENE_HEIGHT) {
        mainClock.autoAdvance = false
        setContent {
            OltreTheme(translations = translations) {
                Surface {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MarkComposeFaceContent(
                            uiState = uiState,
                            onChooseBody = onChooseBody,
                            onChoosePath = onChoosePath,
                            onChooseTerminus = onChooseTerminus,
                        )
                    }
                }
            }
        }
        mainClock.advanceTimeByFrame()
        IdentityRobot(this).block()
    }
}

@OptIn(ExperimentalTestApi::class)
internal class IdentityRobot(private val test: ComposeUiTest) {

    fun tapCell(preset: MarkPreset): IdentityRobot = tap(IdentityTestTags.cell(preset))

    fun tapCompose(): IdentityRobot = tap(IdentityTestTags.COMPOSE_ROW)

    fun tapSave(): IdentityRobot = tap(IdentityTestTags.SAVE)

    fun tapClear(): IdentityRobot = tap(IdentityTestTags.CLEAR)

    fun tapBody(body: MarkBody): IdentityRobot = tap(IdentityTestTags.body(body))

    fun tapPath(path: MarkPath): IdentityRobot = tap(IdentityTestTags.path(path))

    fun tapTerminus(terminus: MarkTerminus): IdentityRobot = tap(IdentityTestTags.terminus(terminus))

    // **Focus without a touch**, which is what lets a frame photograph a focused field: a click would
    // land a press on the row and bake indication into a baseline that then never changes.
    fun focusName(): IdentityRobot = apply {
        test.onNodeWithTag(IdentityTestTags.NAME).requestFocus()
        test.mainClock.advanceTimeByFrame()
    }

    // **Appends rather than replaces**, which is what a keyboard does — and it is the only way to see
    // the field refuse: typing past the bound is a keystroke the field declines, where setting the
    // value would be the test writing the state it then asserts on.
    fun type(text: String): IdentityRobot = apply {
        test.onNodeWithTag(IdentityTestTags.NAME).performTextInput(text)
        test.mainClock.advanceTimeByFrame()
    }

    // What the field is holding, read off the node rather than off the scene's own copy: a draft the
    // face never drew would pass an assertion about the draft.
    fun name(): String = test.onNodeWithTag(IdentityTestTags.NAME)
        .fetchSemanticsNode()
        .config[SemanticsProperties.EditableText]
        .text

    fun assertSaveShowing(): IdentityRobot = apply {
        test.onNodeWithTag(IdentityTestTags.SAVE).assertIsDisplayed()
    }

    // **Absent, not disabled** — the design's own words, and the only assertion that can tell the two
    // apart is one about existence.
    fun assertNoSave(): IdentityRobot = apply {
        test.onNodeWithTag(IdentityTestTags.SAVE).assertDoesNotExist()
    }

    fun assertCounter(length: Int): IdentityRobot = apply {
        val expected = English.resolve(
            Strings.profileNameCounter(length = length, max = CommanderName.MAX_LENGTH),
        )
        test.onNodeWithTag(IdentityTestTags.COUNTER).assertTextEquals(expected)
    }

    fun assertNoCounter(): IdentityRobot = apply {
        test.onNodeWithTag(IdentityTestTags.COUNTER).assertDoesNotExist()
    }

    // The placeholder is drawn *behind* the field rather than instead of it, so it is a node of its own
    // and an emptied field is one that has it showing again.
    fun assertPlaceholderShowing(): IdentityRobot = assertShowing(Strings.playerDefaultName())

    fun assertNoClear(): IdentityRobot = apply {
        test.onNodeWithTag(IdentityTestTags.CLEAR).assertDoesNotExist()
    }

    fun assertRequirementShowing(): IdentityRobot = apply {
        test.onNodeWithTag(IdentityTestTags.REQUIREMENT).assertIsDisplayed()
    }

    // **The card is the held state**, so its absence is what says the controls under it can commit —
    // and existence is the only assertion that can tell that from a card drawn quietly.
    fun assertNoRequirement(): IdentityRobot = apply {
        test.onNodeWithTag(IdentityTestTags.REQUIREMENT).assertDoesNotExist()
    }

    fun assertTerminusLadderShowing(): IdentityRobot = apply {
        test.onNodeWithTag(IdentityTestTags.TERMINUS_LADDER).assertIsDisplayed()
    }

    // **Not drawn rather than dimmed**: a terminus is the end of a path, so with no path the row is
    // gone and there is nothing to be greyed.
    fun assertNoTerminusLadder(): IdentityRobot = apply {
        test.onNodeWithTag(IdentityTestTags.TERMINUS_LADDER).assertDoesNotExist()
    }

    fun assertShowing(text: TextRes): IdentityRobot = apply {
        test.onNodeWithText(English.resolve(text), useUnmergedTree = true).assertIsDisplayed()
    }

    fun assertNotShowing(text: TextRes): IdentityRobot = apply {
        test.onNodeWithText(English.resolve(text), useUnmergedTree = true).assertDoesNotExist()
    }

    // Every tap steps a frame, because the clock is paused: without it the tap has landed and nothing
    // has been recomposed to look at.
    private fun tap(tag: String): IdentityRobot = apply {
        test.onNodeWithTag(tag, useUnmergedTree = true).performClick()
        test.mainClock.advanceTimeByFrame()
    }
}

// Taller than either face at either width by a clear band, so nothing is clipped and a control that
// overflowed would be visible rather than scrolled out of reach.
private const val FACE_SCENE_HEIGHT = 900
