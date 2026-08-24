package dev.fardavide.oltre.client.changelog.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.swipeLeft
import dev.fardavide.oltre.client.changelog.domain.ReleaseVersion
import dev.fardavide.oltre.client.design.core.OltreTheme

// The sheet's contents, driven through a robot rather than raw node queries — the taxonomy's rule.
// Everything here is a gesture a finger can make: a swipe, a tap on the rail, a tap on the row.
@OptIn(ExperimentalTestApi::class)
internal fun changelogSheet(
    compact: Boolean = false,
    block: ChangelogRobot.() -> Unit,
) {
    runDesktopComposeUiTest(width = if (compact) 320 else 393, height = 741) {
        setContent {
            OltreTheme {
                Surface {
                    ChangelogSheetContent(uiState = testChangelogUiState(), compact = compact)
                }
            }
        }
        ChangelogRobot(this).block()
    }
}

@OptIn(ExperimentalTestApi::class)
internal class ChangelogRobot(private val test: ComposeUiTest) {

    fun swipeToNextPage(): ChangelogRobot = apply {
        test.onNodeWithTag(ChangelogTestTags.SHEET).performTouchInput { swipeLeft() }
        test.waitForIdle()
    }

    // **The rail is a control, and this is the gesture that makes it one.** Sixty-four swipes is not
    // a way to reach the first week, so a tap lands on the nearest release — the galaxy map's own
    // caption gesture.
    fun tapRailAtEnd(): ChangelogRobot = apply {
        test.onNodeWithTag(ChangelogTestTags.RAIL).performTouchInput {
            click(centerRight)
        }
        test.waitForIdle()
    }

    fun assertShowing(version: String): ChangelogRobot = apply {
        test.onNodeWithTag(page(version)).assertIsDisplayed()
    }

    // **A page that is not composed is a page nobody can swipe to.** With `beyondViewportPageCount`
    // at one the pager keeps a neighbour either side and nothing else, so the far end of a
    // sixty-five-page run must genuinely be absent until it is reached.
    fun assertNotShowing(version: String): ChangelogRobot = apply {
        test.onNodeWithTag(page(version)).assertDoesNotExist()
    }

    fun assertDepthShowing(): ChangelogRobot = apply {
        test.onNodeWithTag(ChangelogTestTags.DEPTH).assertIsDisplayed()
    }

    private fun page(version: String): String =
        ChangelogTestTags.page(requireNotNull(ReleaseVersion.parse(version)))
}

// The build row, on its own, because what it does is call back and that is all it does.
@OptIn(ExperimentalTestApi::class)
internal fun buildRow(onOpenChangelog: () -> Unit, block: BuildRowRobot.() -> Unit) {
    runDesktopComposeUiTest(width = 393, height = 120) {
        setContent {
            OltreTheme {
                Surface {
                    BuildRow(uiState = testBuildRowUiState(), onOpenChangelog = onOpenChangelog)
                }
            }
        }
        BuildRowRobot(this).block()
    }
}

@OptIn(ExperimentalTestApi::class)
internal class BuildRowRobot(private val test: ComposeUiTest) {

    fun tap(): BuildRowRobot = apply {
        test.onNodeWithTag(ChangelogTestTags.BUILD_ROW).performClick()
        test.waitForIdle()
    }

    fun assertShowing(): BuildRowRobot = apply {
        test.onNodeWithTag(ChangelogTestTags.BUILD_ROW).assertIsDisplayed()
    }
}
