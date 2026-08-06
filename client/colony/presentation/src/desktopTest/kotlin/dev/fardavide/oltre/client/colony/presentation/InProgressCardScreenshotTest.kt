package dev.fardavide.oltre.client.colony.presentation

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.ThresholdValidator
import dev.fardavide.oltre.client.design.OltreTheme
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class InProgressCardScreenshotTest {

    @Test
    fun `in-progress card with countdown and progress`() {
        runDesktopComposeUiTest(width = 393, height = 140) {
            setContent {
                OltreTheme {
                    Surface {
                        InProgressCard(
                            uiState = InProgressUiState(
                                title = "Metal Mine → 24",
                                countdown = "01:42:19",
                                progressPercent = 68,
                                doneAt = "done 11:23",
                            ),
                        )
                    }
                }
            }
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/in_progress_card.png",
                roborazziOptions = RoborazziOptions(
                    compareOptions = RoborazziOptions.CompareOptions(
                        resultValidator = ThresholdValidator(0.01f),
                    ),
                ),
            )
        }
    }
}
