package dev.fardavide.oltre.client.settings.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.core.AlertCategory
import dev.fardavide.oltre.core.AlertDelivery
import dev.fardavide.oltre.core.AlertMode
import kotlin.test.assertEquals

// The sheet's contents, driven through a robot rather than through raw node queries in a test body —
// the taxonomy's rule, and here it earns its keep twice over: what a chip *says* is a `TextRes` that
// only a `Translations` can turn into a string, and the seven rows are found by category rather than
// by label for exactly that reason.
@OptIn(ExperimentalTestApi::class)
internal fun alertSheet(
    mode: AlertMode = AlertMode.BY_CATEGORY,
    delivery: AlertDelivery = AlertDelivery.TOTAL,
    off: Set<AlertCategory> = emptySet(),
    compact: Boolean = false,
    onSelectMode: (AlertMode) -> Unit = {},
    onToggleCategory: (AlertCategory) -> Unit = {},
    onSelectDelivery: (AlertDelivery) -> Unit = {},
    block: AlertSheetRobot.() -> Unit,
) {
    runDesktopComposeUiTest(width = if (compact) 320 else 393, height = 900) {
        setContent {
            OltreTheme {
                Surface {
                    AlertSheetContent(
                        uiState = alertSheetUiState(mode = mode, delivery = delivery, off = off),
                        compact = compact,
                        onSelectMode = onSelectMode,
                        onToggleCategory = onToggleCategory,
                        onSelectDelivery = onSelectDelivery,
                    )
                }
            }
        }
        AlertSheetRobot(this).block()
    }
}

@OptIn(ExperimentalTestApi::class)
internal class AlertSheetRobot(private val test: ComposeUiTest) {

    fun tapMode(mode: AlertMode): AlertSheetRobot = apply {
        test.onNodeWithTag(SettingsTestTags.mode(mode)).performClick()
    }

    fun tapDelivery(delivery: AlertDelivery): AlertSheetRobot = apply {
        test.onNodeWithTag(SettingsTestTags.delivery(delivery)).performClick()
    }

    // **The row, not the square.** The whole 38dp width answers, which is what lets the square stay
    // at the colony's own 29dp — so a robot that pressed the square would be exercising a target the
    // design deliberately did not build.
    fun tapCategory(category: AlertCategory): AlertSheetRobot = apply {
        test.onNodeWithTag(SettingsTestTags.category(category)).performClick()
    }

    fun assertPanelShowing(): AlertSheetRobot = apply {
        test.onNodeWithTag(SettingsTestTags.PANEL).assertIsDisplayed()
    }

    // **Absent rather than collapsed**, which is the assertion the design asks for by name: under
    // `Per item` the panel does not exist, so the ladder above it has not moved.
    fun assertNoPanel(): AlertSheetRobot = apply {
        test.onNodeWithTag(SettingsTestTags.PANEL).assertDoesNotExist()
    }

    fun assertSays(text: TextRes): AlertSheetRobot = apply {
        test.onNodeWithText(English.resolve(text)).assertIsDisplayed()
    }

    fun assertDoesNotSay(text: TextRes): AlertSheetRobot = apply {
        test.onNodeWithText(English.resolve(text)).assertDoesNotExist()
    }

    fun assertExample(text: TextRes): AlertSheetRobot = apply {
        test.onNodeWithTag(SettingsTestTags.EXAMPLE).assertTextEquals(English.resolve(text))
    }

    fun assertNoTiming(): AlertSheetRobot = apply {
        test.onNodeWithTag(SettingsTestTags.TIMING).assertDoesNotExist()
    }

    // What a bell says when it is read aloud. Asserted on the row rather than on the square, because
    // the row is the target and the state belongs to whatever a finger can press.
    fun assertSpoken(category: AlertCategory, on: Boolean): AlertSheetRobot = apply {
        val expected = English.resolve(
            Strings.clauses(listOf(Strings.alertCategoryName(category), Strings.alertBellState(on))),
        )
        test.onNodeWithTag(SettingsTestTags.category(category)).assertContentDescriptionEquals(expected)
    }

    fun assertCategoryCount(expected: Int): AlertSheetRobot = apply {
        val actual = AlertCategory.entries.count { category ->
            test.onAllNodesWithTag(SettingsTestTags.category(category)).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(expected, actual, "the panel holds $actual rows")
    }
}
