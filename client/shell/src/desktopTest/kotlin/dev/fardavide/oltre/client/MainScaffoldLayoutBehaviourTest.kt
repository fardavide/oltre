package dev.fardavide.oltre.client

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
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

    // The rate moved up onto the stock's baseline, which bought the rail 12dp and cost it the one
    // guarantee a stacked column gave for free: that the rate always had a line to itself. Six
    // figures in a 320dp Slide Over pane is where the pair stops fitting, and there the rate has
    // to fall *under* the stock rather than share the line with it.
    //
    // Measured as a width rather than as a bound, because the failure this guards is not the rate
    // escaping its cell — it never does. A Row hands the stock the whole line and measures the
    // rate into whatever is left, so the node stays inside the cell and inside the rail while the
    // string inside it is cut in half. The only honest witness is the width the same rate takes
    // when nothing is competing for the line.
    // Both widths are measured in ONE composition, and that is not tidiness — it is the difference
    // between a test and a coin toss. `oltreMono()` resolves its faces through compose-resources,
    // which loads them asynchronously; two separately-built scenes can therefore measure the same
    // string against two different typefaces, the fallback in whichever one rendered before the
    // font arrived. Comparing widths to a dp across that race failed once on a cold JVM and then
    // passed nine times running, which is the worst way for a test to be wrong. One scene sees one
    // typeface, whatever it is, so the comparison is about the layout and nothing else.
    @Test
    fun `the rail draws every rate at full width in a Slide Over window`() {
        runDesktopComposeUiTest(width = 1400, height = 834) {
            setContent {
                OltreTheme {
                    Column {
                        // Capped, so the cell has all the room the design ever gives it.
                        Box(modifier = Modifier.width(OltreLayout.maxContentWidth)) {
                            ResourceRail(uiState = sixFigureResourceRailUiState)
                        }
                        // A Slide Over pane, where a third of the width has to hold six figures
                        // and a rate.
                        Box(modifier = Modifier.width(SLIDE_OVER_WIDTH)) {
                            ResourceRail(uiState = sixFigureResourceRailUiState)
                        }
                    }
                }
            }

            RAIL_CELLS.forEach { name ->
                val rates = onAllNodesWithTag(ShellTestTags.resourceRate(name), useUnmergedTree = true)
                val roomy = rates[0].getBoundsInRoot().let { it.right - it.left }
                val slideOver = rates[1].getBoundsInRoot().let { it.right - it.left }
                assertTrue(
                    abs((slideOver - roomy).value) <= TOLERANCE.value,
                    "the $name rate is $slideOver wide in a $SLIDE_OVER_WIDTH pane but $roomy wide " +
                        "with room to spare, so it was squeezed onto the stock's line instead of " +
                        "wrapping under it",
                )
            }
        }
    }

    private fun assertRailColumn(windowWidth: Int, windowHeight: Int) {
        runDesktopComposeUiTest(width = windowWidth, height = windowHeight) {
            setContent {
                OltreTheme {
                    MainScaffold(
                        resources = testResourceRailUiState,
                        colony = { Text("colony-under-test") },
                        research = { Text("research-under-test") },
                        galaxy = { _, _ -> Text("galaxy-under-test") },
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

        // The captions as the rail states them, which is also how the cells are tagged.
        val RAIL_CELLS = listOf("METAL", "CRYSTAL", "DEUTERIUM")

        // The narrowest window the app has to survive.
        val SLIDE_OVER_WIDTH = 320.dp
    }
}
