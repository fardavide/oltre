package dev.fardavide.oltre.client.alliance.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Italian
import dev.fardavide.oltre.client.design.text.Translations
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// The tab, ahead of its real screen. Both languages, since the whole screen is catalogue copy and
// Italian is the one that has broken a layout before — this is the one frame where it matters most,
// because there is nothing else on the screen to absorb the extra length.
@OptIn(ExperimentalTestApi::class)
class AllianceScreenScreenshotTest {

    @Test
    fun `the placeholder in English`() {
        capture(name = "alliance_coming_soon_en", translations = English)
    }

    @Test
    fun `the placeholder in Italian`() {
        capture(name = "alliance_coming_soon_it", translations = Italian)
    }

    @Test
    fun `the placeholder in a Slide Over window`() {
        capture(name = "alliance_coming_soon_slide_over", translations = English, width = SLIDE_OVER_WIDTH)
    }

    private fun capture(name: String, translations: Translations, width: Int = PHONE_WIDTH) {
        runDesktopComposeUiTest(width = width, height = 300) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme(translations = translations) {
                    Surface {
                        AllianceScreen()
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
        const val SLIDE_OVER_WIDTH = 320
    }
}
