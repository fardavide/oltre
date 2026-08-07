package dev.fardavide.oltre.client

import androidx.compose.material3.Text
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

// The rail is full-bleed but its cells sit on the same centred column as every screen's content,
// so an iPad does not push the three stocks out to the screen edges. This assertion moved here
// with the rail itself at 0.0.12 — it used to live in ColonyScreenLayoutBehaviourTest, back when
// the Colony screen was the only thing that drew one.
@OptIn(ExperimentalTestApi::class)
class MainScaffoldLayoutBehaviourTest {

    @Test
    fun `the rail caps and centres in a window far wider than a phone`() {
        assertRailColumn(windowWidth = 1400, windowHeight = 900)
    }

    @Test
    fun `the rail caps and centres in an iPad landscape window`() {
        assertRailColumn(windowWidth = 1194, windowHeight = 834)
    }

    // 570dp clears the 560dp cap by 10dp, so it is the tightest case the rule has to get right.
    @Test
    fun `the rail caps and centres in a Split View pane`() {
        assertRailColumn(windowWidth = 570, windowHeight = 834)
    }

    @Test
    fun `the rail fills a Slide Over window`() {
        assertRailColumn(windowWidth = 320, windowHeight = 834)
    }

    @Test
    fun `the rail fills a phone-sized window`() {
        assertRailColumn(windowWidth = 393, windowHeight = 852)
    }

    private fun assertRailColumn(windowWidth: Int, windowHeight: Int) {
        runDesktopComposeUiTest(width = windowWidth, height = windowHeight) {
            setContent {
                OltreTheme {
                    MainScaffold(
                        resources = testResourceRailUiState,
                        colony = { Text("colony-under-test") },
                        research = { Text("research-under-test") },
                    )
                }
            }

            val root = onRoot().getBoundsInRoot()
            val rootWidth = root.right - root.left
            val expected = minOf(rootWidth, OltreLayout.maxContentWidth)
            val bounds = onNodeWithTag(ShellTestTags.RESOURCE_RAIL_CONTENT, useUnmergedTree = true).getBoundsInRoot()
            val width = bounds.right - bounds.left
            assertTrue(
                abs((width - expected).value) <= TOLERANCE.value,
                "the rail is $width wide in a $rootWidth window, expected $expected",
            )
            val leftGap = bounds.left - root.left
            val rightGap = root.right - bounds.right
            assertTrue(
                abs((leftGap - rightGap).value) <= TOLERANCE.value,
                "the rail is off-centre in a $rootWidth window: $leftGap left, $rightGap right",
            )
        }
    }

    private companion object {

        // Layout rounds to whole pixels; a Dp of slack keeps the assertion about the layout rule
        // rather than about rounding.
        val TOLERANCE = 1.dp
    }
}
