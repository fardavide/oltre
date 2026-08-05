package dev.fardavide.oltre.client.colony.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.OltreTheme
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ResourceRailScreenshotTest {

    @Test
    fun `resource rail with metal stock and rate`() {
        runDesktopComposeUiTest {
            setContent {
                OltreTheme {
                    ResourceRail(
                        uiState = ColonyUiState(
                            metal = "482,910",
                            metalRatePerHour = "+3,600/h",
                        ),
                    )
                }
            }
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/resource_rail.png",
            )
        }
    }
}
