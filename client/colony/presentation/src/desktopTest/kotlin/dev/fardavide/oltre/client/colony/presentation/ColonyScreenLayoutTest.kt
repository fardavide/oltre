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
// arbitrary width. Past OltreLayout.maxContentWidth the colony stops stretching and centres,
// because a 1000dp-wide facility row of 13.5sp text is unreadable.
@OptIn(ExperimentalTestApi::class)
class ColonyScreenLayoutTest {

    @Test
    fun `content is capped and centred in a window wider than the max content width`() {
        runDesktopComposeUiTest(width = 1400, height = 900) {
            setContent {
                OltreTheme {
                    ColonyScreen(uiState = testColonyUiState, onUpgrade = {})
                }
            }

            val root = onRoot().getBoundsInRoot()
            CONSTRAINED_TAGS.forEach { tag ->
                val bounds = onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()
                val width = bounds.right - bounds.left
                assertTrue(
                    width <= OltreLayout.maxContentWidth + TOLERANCE,
                    "$tag is $width wide, expected at most ${OltreLayout.maxContentWidth}",
                )
                val leftGap = bounds.left - root.left
                val rightGap = root.right - bounds.right
                assertTrue(
                    abs((leftGap - rightGap).value) <= TOLERANCE.value,
                    "$tag is off-centre: $leftGap on the left, $rightGap on the right",
                )
            }
        }
    }

    @Test
    fun `content fills a window narrower than the max content width`() {
        runDesktopComposeUiTest(width = 393, height = 852) {
            setContent {
                OltreTheme {
                    ColonyScreen(uiState = testColonyUiState, onUpgrade = {})
                }
            }

            val rootBounds = onRoot().getBoundsInRoot()
            val rootWidth = rootBounds.right - rootBounds.left
            CONSTRAINED_TAGS.forEach { tag ->
                val bounds = onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()
                val width = bounds.right - bounds.left
                assertTrue(
                    abs((width - rootWidth).value) <= TOLERANCE.value,
                    "$tag is $width wide, expected to fill the $rootWidth window",
                )
            }
        }
    }

    private companion object {

        val CONSTRAINED_TAGS = listOf(ColonyTestTags.RESOURCE_RAIL_CONTENT, ColonyTestTags.CONTENT)

        // Layout rounds to whole pixels; a Dp of slack keeps the assertions about the layout
        // rule rather than about rounding.
        val TOLERANCE = 1.dp
    }
}
