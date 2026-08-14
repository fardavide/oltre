package dev.fardavide.oltre.client.research.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import dev.fardavide.oltre.client.design.core.OltreLayout
import dev.fardavide.oltre.client.design.core.OltreTheme
import kotlin.math.abs
import kotlin.test.assertTrue
import org.junit.Test

// The same rule the Colony screen follows, because the window is never assumed to be a phone: the
// content is as wide as the window up to OltreLayout.maxContentWidth and centred past it. On this
// screen it also means 560dp adds air, not content — no columns, no second pane, nothing revealed
// at width. Three rows leave most of a phone empty, and that stays empty.
@OptIn(ExperimentalTestApi::class)
class ResearchScreenLayoutBehaviourTest {

    @Test
    fun `content caps and centres in a window far wider than a phone`() {
        assertContentColumn(windowWidth = 1400, windowHeight = 900)
    }

    @Test
    fun `content caps and centres in an iPad landscape window`() {
        assertContentColumn(windowWidth = 1194, windowHeight = 834)
    }

    @Test
    fun `content caps and centres in an iPad portrait window`() {
        assertContentColumn(windowWidth = 834, windowHeight = 1194)
    }

    // 570dp clears the 560dp cap by 10dp, so it is the tightest case the rule has to get right.
    @Test
    fun `content caps and centres in a Split View pane`() {
        assertContentColumn(windowWidth = 570, windowHeight = 834)
    }

    @Test
    fun `content fills a Slide Over window`() {
        assertContentColumn(windowWidth = SLIDE_OVER_WIDTH, windowHeight = 834)
    }

    @Test
    fun `content fills a phone-sized window`() {
        assertContentColumn(windowWidth = PHONE_WIDTH, windowHeight = 852)
    }

    private fun assertContentColumn(windowWidth: Int, windowHeight: Int) {
        runDesktopComposeUiTest(width = windowWidth, height = windowHeight) {
            setContent {
                OltreTheme {
                    ResearchScreen(
                        uiState = oneProjectInFlightUiState,
                        onStartResearch = {},
                        onStartAdaptation = {},
                        onToggleTechnologyWatch = {},
                        onToggleAdaptationWatch = {},
                    )
                }
            }

            val root = onRoot().getBoundsInRoot()
            val rootWidth = root.right - root.left
            val expected = minOf(rootWidth, OltreLayout.maxContentWidth)
            val bounds = onNodeWithTag(ResearchTestTags.CONTENT, useUnmergedTree = true).getBoundsInRoot()
            val width = bounds.right - bounds.left
            assertTrue(
                abs((width - expected).value) <= TOLERANCE.value,
                "the content is $width wide in a $rootWidth window, expected $expected",
            )
            val leftGap = bounds.left - root.left
            val rightGap = root.right - bounds.right
            assertTrue(
                abs((leftGap - rightGap).value) <= TOLERANCE.value,
                "the content is off-centre in a $rootWidth window: $leftGap left, $rightGap right",
            )
        }
    }

    private companion object {

        // Layout rounds to whole pixels; a Dp of slack keeps the assertion about the layout rule
        // rather than about rounding.
        val TOLERANCE = 1.dp
    }
}
