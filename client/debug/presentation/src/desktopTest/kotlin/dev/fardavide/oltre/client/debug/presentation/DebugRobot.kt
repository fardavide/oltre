package dev.fardavide.oltre.client.debug.presentation

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
                    DebugSheet(
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

internal const val PHONE_WIDTH = 393

@OptIn(ExperimentalTestApi::class)
internal class DebugRobot(private val test: ComposeUiTest) {

    fun skipAhead() = apply { test.onNodeWithTag(DebugTestTags.SKIP).performClick() }

    fun tapReset() = apply { test.onNodeWithTag(DebugTestTags.RESET).performClick() }

    fun close() = apply { test.onNodeWithTag(DebugTestTags.CLOSE).performClick() }

    fun assertIsOpen() = apply { test.onNodeWithTag(DebugTestTags.SHEET).assertIsDisplayed() }

    // Exact, not substring: every one of these targets a single Text, so there is no container to
    // be ambiguous about and no reason to accept a label that grew a word.
    //
    // **`useUnmergedTree` is load-bearing on the two action rows.** `Modifier.clickable` sets
    // `mergeDescendants = true` — that is how a Button's label is readable on the Button — so in
    // the default merged tree the row's two Texts are folded *into* the row and their own tags stop
    // resolving. The reading rows are not clickable and so do not merge, but they are queried the
    // same way here: a robot where half the lookups silently depend on a modifier the sheet happens
    // to use is one bad refactor from failing for a reason nobody can see.
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
