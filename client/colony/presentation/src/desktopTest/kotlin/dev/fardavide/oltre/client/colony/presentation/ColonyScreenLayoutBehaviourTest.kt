package dev.fardavide.oltre.client.colony.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import dev.fardavide.oltre.client.design.OltreLayout
import dev.fardavide.oltre.client.design.OltreTheme
import kotlin.math.abs
import kotlin.test.assertTrue
import org.junit.Test

// The window is not a phone: iPad, Split View, Stage Manager and desktop all hand the UI an
// arbitrary width. The rule is the same at every size — the content is as wide as the window up
// to OltreLayout.maxContentWidth, and centred past it — because a 1000dp-wide facility row of
// 13.5sp text is unreadable.
@OptIn(ExperimentalTestApi::class)
class ColonyScreenLayoutBehaviourTest {

    @Test
    fun `content caps and centres in a window far wider than a phone`() {
        assertContentColumn(windowWidth = 1400, windowHeight = 900)
    }

    // The sizes an iPad actually hands the app, in 11" points: full screen either way up, the
    // widest Split View pane, and Slide Over. 570dp clears the 560dp cap by 10dp, so it is the
    // tightest case the rule has to get right; 320dp is below it and must simply fill.
    @Test
    fun `content caps and centres in an iPad landscape window`() {
        assertContentColumn(windowWidth = 1194, windowHeight = 834)
    }

    @Test
    fun `content caps and centres in an iPad portrait window`() {
        assertContentColumn(windowWidth = 834, windowHeight = 1194)
    }

    @Test
    fun `content caps and centres in a Split View pane`() {
        assertContentColumn(windowWidth = 570, windowHeight = 834)
    }

    @Test
    fun `content fills a Slide Over window`() {
        assertContentColumn(windowWidth = 320, windowHeight = 834)
    }

    @Test
    fun `content fills a phone-sized window`() {
        assertContentColumn(windowWidth = 393, windowHeight = 852)
    }

    // The window size is given in pixels and the bounds come back in Dp, so the expectation is
    // derived from the measured root rather than from the pixel count: the rule under test is
    // min(window, cap), whatever the density the test environment picks.
    private fun assertContentColumn(windowWidth: Int, windowHeight: Int) {
        runDesktopComposeUiTest(width = windowWidth, height = windowHeight) {
            setContent {
                OltreTheme {
                    ColonyScreen(uiState = testColonyUiState, onUpgrade = {})
                }
            }

            val root = onRoot().getBoundsInRoot()
            val rootWidth = root.right - root.left
            val expected = minOf(rootWidth, OltreLayout.maxContentWidth)
            CONSTRAINED_TAGS.forEach { tag ->
                val bounds = onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()
                val width = bounds.right - bounds.left
                assertTrue(
                    abs((width - expected).value) <= TOLERANCE.value,
                    "$tag is $width wide in a $rootWidth window, expected $expected",
                )
                val leftGap = bounds.left - root.left
                val rightGap = root.right - bounds.right
                assertTrue(
                    abs((leftGap - rightGap).value) <= TOLERANCE.value,
                    "$tag is off-centre in a $rootWidth window: $leftGap left, $rightGap right",
                )
            }
        }
    }

    private companion object {

        val CONSTRAINED_TAGS = listOf(
            ColonyTestTags.RESOURCE_RAIL_CONTENT,
            ColonyTestTags.POWER_STRIP_CONTENT,
            ColonyTestTags.CONTENT,
        )

        // Layout rounds to whole pixels; a Dp of slack keeps the assertions about the layout
        // rule rather than about rounding.
        val TOLERANCE = 1.dp
    }
}
