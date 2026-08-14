package dev.fardavide.oltre.client.galaxy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import dev.fardavide.oltre.core.Gravity
import dev.fardavide.oltre.core.Hazard
import dev.fardavide.oltre.core.Pressure
import dev.fardavide.oltre.core.Temperature
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// **The one component in the app whose whole specification is a drawing**, so it is the one where a
// baseline is not a regression net but the primary check. Nothing else would catch the two mistakes
// `WorldPortrait`'s header warns about — a gradient normalised to the disc radius instead of the
// farthest corner over-darkens every limb by a fixed amount, and it is invisible to any assertion
// that is not a picture.
//
// Each frame is a ladder along one channel with the other two held still, because that is how the
// design specifies them and how a diff is read: if the temperature ramp moves, exactly one of these
// changes.
@OptIn(ExperimentalTestApi::class)
class WorldPortraitScreenshotTest {

    @Test
    fun `temperature is the fill`() {
        captureRow(
            name = "portrait_temperature",
            worlds = listOf(-140, -52, -6, 61, 128).map { world(celsius = it) },
        )
    }

    @Test
    fun `pressure is the banding and both its ends are epithet nouns`() {
        // 0 bands is a `waste`, seven closing into one veil is a `shroud` — the drawing and the
        // vocabulary agree, which is what lets a player tell them apart without reading either word.
        captureRow(
            name = "portrait_pressure",
            worlds = listOf(20, 400, 1_400, 3_800, 8_600).map { world(milliAtm = it) },
        )
    }

    @Test
    fun `gravity is the diameter and the box never changes`() {
        captureRow(
            name = "portrait_gravity",
            worlds = listOf(310, 1_060, 2_620).map { world(milliG = it) },
        )
    }

    @Test
    fun `four hazards own four layers so any pair composes`() {
        captureRow(
            name = "portrait_hazards",
            worlds = listOf(
                Hazard.TIDALLY_LOCKED,
                Hazard.ION_STORMS,
                Hazard.SEISMIC_INSTABILITY,
                Hazard.RADIATION_BELT,
                Hazard.THIN_CRUST,
            ).map { world(celsius = -40, milliG = 1_200, milliAtm = 1_200, hazards = setOf(it)) },
        )
    }

    @Test
    fun `two hazards at once and the ring that means nothing`() {
        captureRow(
            name = "portrait_hazard_pairs",
            worlds = listOf(
                world(-96, 1_610, 2_900, setOf(Hazard.TIDALLY_LOCKED, Hazard.RADIATION_BELT)),
                world(61, 880, 310, setOf(Hazard.ION_STORMS, Hazard.SEISMIC_INSTABILITY)),
                world(-52, 2_200, 1_100, setOf(Hazard.SEISMIC_INSTABILITY, Hazard.RADIATION_BELT), ring = true),
                // Seismic wins outright over thin crust: three deeper lines, never five.
                world(-52, 2_200, 1_100, setOf(Hazard.SEISMIC_INSTABILITY, Hazard.THIN_CRUST)),
            ),
        )
    }

    // The gate is on the BOX and not on the drawn diameter, so this frame is the one that would
    // catch it being keyed off `d`: at 26dp the swirl, the fractures, the craters and the ring are
    // all gone and the banding is capped at two, while the fill, the diameter, the terminator and
    // the halo survive.
    @Test
    fun `the small disc drops what would be noise at row scale`() {
        captureRow(
            name = "portrait_row_scale",
            box = 26.dp,
            worlds = listOf(
                world(-96, 1_610, 2_900, setOf(Hazard.RADIATION_BELT, Hazard.SEISMIC_INSTABILITY)),
                world(61, 880, 310, setOf(Hazard.ION_STORMS)),
                world(-6, 1_020, 1_100),
                world(-6, 1_020, 8_600),
            ) + listOf(WorldPortraitUiState.Unsurveyed),
        )
    }

    // 98% of every list. A hairline socket at one size, next to the filled one it is waiting to
    // become — and the reason the word `Unsurveyed` could leave the row at all.
    @Test
    fun `the unsurveyed socket is one size and leaks nothing`() {
        captureRow(
            name = "portrait_unsurveyed",
            worlds = listOf(WorldPortraitUiState.Unsurveyed, world(-6, 1_020, 1_100)),
        )
    }

    private fun captureRow(
        name: String,
        worlds: List<WorldPortraitUiState>,
        box: Dp = 96.dp,
    ) {
        val cell = if (box > 60.dp) 120 else 44
        capture(width = cell * worlds.size, height = cell, name = name) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(if (box > 60.dp) 22.dp else 8.dp),
                modifier = Modifier.padding(if (box > 60.dp) 12.dp else 9.dp),
            ) {
                worlds.forEach { WorldPortrait(uiState = it, box = box) }
            }
        }
    }

    private fun capture(width: Int, height: Int, name: String, content: @Composable () -> Unit) {
        runDesktopComposeUiTest(width = width, height = height) {
            mainClock.autoAdvance = false
            setContent { OltreTheme { Surface { Column { content() } } } }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/$name.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }
}

// Middling on every axis unless the frame is about that axis, so each ladder varies exactly one
// thing — which is what makes the diff readable when one of them moves.
private fun world(
    celsius: Int = -6,
    milliG: Int = 1_000,
    milliAtm: Int = 900,
    hazards: Set<Hazard> = emptySet(),
    ring: Boolean = false,
): WorldPortraitUiState = WorldPortraitUiState.Surveyed(
    temperature = Temperature(celsius),
    gravity = Gravity(milliG),
    pressure = Pressure(milliAtm),
    hazards = hazards,
    hasRing = ring,
)
