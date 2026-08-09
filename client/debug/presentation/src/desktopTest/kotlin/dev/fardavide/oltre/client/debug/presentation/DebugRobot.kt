package dev.fardavide.oltre.client.debug.presentation

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.debug.domain.DebugReport
import dev.fardavide.oltre.client.design.core.OltreTheme

// A behaviour test says what was tapped and what should happen; the Robot owns how. The convention
// the `test-coverage` skill asks for — node queries live here and nowhere else.
@OptIn(ExperimentalTestApi::class)
internal fun debugSheet(
    report: DebugReport = idleReport,
    onSkipAhead: () -> Unit = {},
    onReset: () -> Unit = {},
    onDismiss: () -> Unit = {},
    block: DebugRobot.() -> Unit,
) {
    runDesktopComposeUiTest(width = PHONE_WIDTH, height = 852) {
        setContent {
            OltreTheme {
                Surface {
                    DebugSheetContent(
                        report = report,
                        onSkipAhead = onSkipAhead,
                        onReset = onReset,
                        onDismiss = onDismiss,
                    )
                }
            }
        }
        DebugRobot(this).block()
    }
}

// The contents *inside the real sheet*, for the one test that is about the chrome rather than about
// what the panel says. Everything else renders `DebugSheetContent` directly: an assertion about a
// label has no business also depending on a popup being reachable and an enter animation settling.
@OptIn(ExperimentalTestApi::class)
internal fun debugBottomSheet(
    report: DebugReport = idleReport,
    onDismiss: () -> Unit = {},
    block: DebugRobot.() -> Unit,
) {
    runDesktopComposeUiTest(width = PHONE_WIDTH, height = 852) {
        setContent {
            OltreTheme {
                Surface {
                    DebugSheet(
                        report = report,
                        onSkipAhead = {},
                        onReset = {},
                        onDismiss = onDismiss,
                    )
                }
            }
        }
        DebugRobot(this).block()
    }
}

internal const val PHONE_WIDTH = 393

@OptIn(ExperimentalTestApi::class)
internal class DebugRobot(private val test: ComposeUiTest) {

    // A hold rather than a click, and `longClick` rather than a hand-driven clock: the row confirms
    // from `onLongPress`, so the framework's own gesture injection is what drives it and no test
    // here has to know how long the platform thinks a long press is.
    fun holdSkip() = apply { test.onNodeWithTag(DebugTestTags.SKIP).performTouchInput { longClick() } }

    fun holdReset() = apply { test.onNodeWithTag(DebugTestTags.RESET).performTouchInput { longClick() } }

    // The gesture that must *not* act, which is the whole point of the change.
    fun tapSkip() = apply { test.onNodeWithTag(DebugTestTags.SKIP).performTouchInput { click() } }

    fun tapReset() = apply { test.onNodeWithTag(DebugTestTags.RESET).performTouchInput { click() } }

    fun assertShowsProgress(row: String) = apply {
        test.onNodeWithTag(DebugTestTags.fill(row), useUnmergedTree = true).assertExists()
    }

    fun close() = apply { test.onNodeWithTag(DebugTestTags.CLOSE).performClick() }

    fun assertIsOpen() = apply { test.onNodeWithTag(DebugTestTags.SHEET).assertIsDisplayed() }

    // Exact, not substring: every one of these targets a single Text, so there is no container to
    // be ambiguous about and no reason to accept a label that grew a word.
    //
    // **`useUnmergedTree` everywhere, deliberately.** It was load-bearing while the rows used
    // `Modifier.clickable`, which sets `mergeDescendants = true` — that is how a Button's label is
    // readable on the Button — and it folded each row's two Texts into the row so their own tags
    // stopped resolving. The rows now carry a raw `pointerInput`, which merges nothing, so it is no
    // longer strictly required; it stays because a robot where half the lookups depend on which
    // modifier the sheet happens to use is one refactor from failing for a reason nobody can see.
    fun assertSkipOffers(detail: String) = apply {
        test.onNodeWithTag(DebugTestTags.detail(DebugTestTags.SKIP), useUnmergedTree = true)
            .assertTextEquals(detail)
    }

    fun assertResetSays(label: String) = apply {
        test.onNodeWithTag(DebugTestTags.label(DebugTestTags.RESET), useUnmergedTree = true)
            .assertTextEquals(label)
    }

    fun assertResetWarns(detail: String) = apply {
        test.onNodeWithTag(DebugTestTags.detail(DebugTestTags.RESET), useUnmergedTree = true)
            .assertTextEquals(detail)
    }

    fun assertReads(name: String, value: String) = apply {
        test.onNodeWithTag(DebugTestTags.reading(name), useUnmergedTree = true)
            .assertTextEquals(value)
    }
}
