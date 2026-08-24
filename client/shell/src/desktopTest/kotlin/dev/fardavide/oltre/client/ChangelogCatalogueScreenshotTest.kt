package dev.fardavide.oltre.client

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import dev.fardavide.oltre.client.changelog.presentation.changelogFor
import dev.fardavide.oltre.client.changelog.presentation.toChangelogUiState
import dev.fardavide.oltre.client.changelog.ui.ChangelogPage
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Italian
import dev.fardavide.oltre.client.design.text.Translations
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// **The only frames that render the real changelog** — the actual document, through the actual
// mapper, in both languages. `:client:changelog:ui`'s own frames use a fixture, deliberately, so
// that a drawing does not move because somebody rewrote a headline; these are the other half, and
// they are in the shell because it is the one module that can see a `presentation` and a `ui`
// together.
//
// **The oldest page, and that is what makes them stable.** `0.0.1` shipped on the first day and its
// page will not be rewritten, so these baselines are not a per-release chore — unlike a frame of the
// newest release, which would have to be re-recorded every time anything ships.
//
// What they are actually for is the pair of things a fixture cannot check: that the date formatter
// produces a caption that fits beside a version at both widths, and that **Italian**, which runs
// 10–15% longer than English, does not push a real page's copy anywhere it should not go. Design
// raised the second by name.
@OptIn(ExperimentalTestApi::class)
class ChangelogCatalogueScreenshotTest {

    @Test
    fun `the first release Oltre ever shipped`() {
        capture(name = "changelog_catalogue_en", translations = English, languageTag = "en")
    }

    @Test
    fun `the same page in Italian`() {
        capture(name = "changelog_catalogue_it", translations = Italian, languageTag = "it")
    }

    private fun capture(name: String, translations: Translations, languageTag: String) {
        val page = changelogFor(languageTag).toChangelogUiState().pages.last()
        runDesktopComposeUiTest(width = PHONE_WIDTH, height = PAGE_HEIGHT) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme(translations = translations) {
                    Surface {
                        Box(modifier = Modifier.padding(horizontal = INSET)) {
                            ChangelogPage(uiState = page, column = COLUMN)
                        }
                    }
                }
            }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/$name.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    private companion object {

        const val PHONE_WIDTH = 393
        const val PAGE_HEIGHT = 560
        val INSET = 26.dp
        val COLUMN = 319.dp
    }
}
