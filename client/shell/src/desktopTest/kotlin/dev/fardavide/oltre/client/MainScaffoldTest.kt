package dev.fardavide.oltre.client

import androidx.compose.material3.Text
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.OltreTheme
import org.junit.Test

// The bar is the one piece of navigation every later screen hangs off, so what it has to get
// right is small and worth pinning: the game opens on the colony, all five destinations are
// reachable, and a tab with no screen behind it says so rather than showing the colony again.
@OptIn(ExperimentalTestApi::class)
class MainScaffoldTest {

    @Test
    fun `the game opens on the colony`() {
        scaffold {
            onNodeWithText(COLONY_MARKER).assertIsDisplayed()
            onNodeWithTag(ShellTestTags.tab(OltreTab.COLONY)).assertIsSelected()
        }
    }

    @Test
    fun `every tab in the bar is reachable`() {
        OltreTab.entries.forEach { tab ->
            scaffold {
                onNodeWithTag(ShellTestTags.tab(tab)).performClick()
                onNodeWithTag(ShellTestTags.tab(tab)).assertIsSelected()
                OltreTab.entries.filter { it != tab }.forEach { other ->
                    onNodeWithTag(ShellTestTags.tab(other)).assertIsNotSelected()
                }
            }
        }
    }

    // The honest empty state is the whole point of shipping the bar before the screens: a tab
    // that quietly re-showed the colony would read as a bug in the colony.
    @Test
    fun `an unbuilt tab says what will be there rather than showing the colony`() {
        OltreTab.entries.filter { it.pendingWork != null }.forEach { tab ->
            scaffold {
                onNodeWithTag(ShellTestTags.tab(tab)).performClick()
                onNodeWithText(COLONY_MARKER).assertDoesNotExist()
                onNodeWithText(checkNotNull(tab.pendingWork)).assertIsDisplayed()
            }
        }
    }

    @Test
    fun `the colony comes back when its tab does`() {
        scaffold {
            onNodeWithTag(ShellTestTags.tab(OltreTab.FLEETS)).performClick()
            onNodeWithTag(ShellTestTags.tab(OltreTab.COLONY)).performClick()
            onNodeWithText(COLONY_MARKER).assertIsDisplayed()
        }
    }

    // A phone-sized window: the bar has to fit five destinations at the narrowest width the game
    // actually ships at.
    private fun scaffold(assertions: ComposeUiTest.() -> Unit) {
        runDesktopComposeUiTest(width = 393, height = 852) {
            setContent {
                OltreTheme {
                    MainScaffold { Text(COLONY_MARKER) }
                }
            }
            assertions()
        }
    }

    private companion object {

        // Stands in for the real Colony screen: the scaffold's job is to show it, not to know
        // what is in it, and the shell's tests should not need a colony to assert navigation.
        const val COLONY_MARKER = "colony-under-test"
    }
}
