package dev.fardavide.oltre.client.galaxy.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono

// The system's fifteen orbits, drawn once, left to right, hot to cold.
//
// This is the half of the screen the list cannot be: it shows the *empty* slots, and therefore the
// shape of a system — that four of fifteen are occupied and where the gaps fall. The list can only
// show what is there. It is also the only place a player can learn that slot 13 means cold without
// being told so in a sentence, which is the whole reason the coordinate is worth having.
//
// Drawn as a Canvas rather than assembled from Boxes for the reason `PowerMark` is: a circle from a
// shaped Box resolves through the platform's shape renderer, and the screenshot baselines are
// recorded on macOS and verified on Linux. A Canvas is the same pixels everywhere.
@Composable
internal fun SystemMap(map: SystemMapUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Star()
            Box(modifier = Modifier.padding(start = 10.dp).fillMaxWidth().height(SLOT_ROW_HEIGHT)) {
                Orbits(map = map)
            }
        }
        SlotNumbers(map = map)
        BandStrip()
    }
}

// The one legitimate gradient in the app: a star is a lit sphere, and everything else on both maps
// is a flat circle or a hairline.
@Composable
private fun Star() {
    Canvas(modifier = Modifier.size(STAR_DIAMETER)) {
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(0f to STAR_CORE, 0.42f to STAR_MID, 1f to STAR_EDGE),
                center = Offset(x = size.width * 0.36f, y = size.height * 0.32f),
                radius = size.minDimension,
            ),
            radius = size.minDimension / 2f,
        )
    }
}

@Composable
private fun Orbits(map: SystemMapUiState) {
    Canvas(modifier = Modifier.fillMaxWidth().height(SLOT_ROW_HEIGHT)) {
        val step = size.width / map.slots.size
        val centreY = size.height / 2f
        drawLine(
            color = Color.White.copy(alpha = 0.09f),
            start = Offset(x = 0f, y = centreY),
            end = Offset(x = size.width, y = centreY),
            strokeWidth = 1f,
        )
        map.slots.forEachIndexed { index, slot ->
            val centreX = step * index + step / 2f
            val centre = Offset(x = centreX, y = centreY)
            when (slot.mark) {
                // A tick rather than a dot, and it is most of them. An empty slot is a fact about
                // the system, so it is drawn — just not as something you could go to.
                MapMark.EMPTY -> drawCircle(color = Color.White.copy(alpha = 0.16f), radius = 1.5.dp.toPx(), center = centre)
                MapMark.RELAY -> drawCircle(
                    color = OltreColors.accent.copy(alpha = 0.55f),
                    radius = 2.5.dp.toPx(),
                    center = centre,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                )
                MapMark.HOME -> {
                    // Punched out of the background and ringed, the one mark that has to be found
                    // at a glance on a strip of fifteen.
                    drawCircle(color = OltreColors.background, radius = 7.dp.toPx(), center = centre)
                    drawCircle(color = OltreColors.accent, radius = 5.5.dp.toPx(), center = centre)
                    drawCircle(
                        color = OltreColors.accent.copy(alpha = 0.45f),
                        radius = 8.dp.toPx(),
                        center = centre,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                    )
                }
                else -> drawCircle(color = slot.mark.hue(), radius = 4.5.dp.toPx(), center = centre)
            }
        }
    }
}

@Composable
private fun SlotNumbers(map: SystemMapUiState) {
    val mono = oltreMono()
    Row(modifier = Modifier.padding(start = STAR_DIAMETER + 10.dp).fillMaxWidth()) {
        map.slots.forEach { slot ->
            Text(
                text = "${slot.slot}",
                color = if (slot.mark == MapMark.EMPTY) OltreColors.textTertiary else OltreColors.textSecondary,
                fontFamily = mono,
                fontSize = 9.5.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// The only place on the screen where a colour means temperature. Each band is weighted by how many
// slots it holds, so the strip lines up with the dots above it.
@Composable
private fun BandStrip() {
    val mono = oltreMono()
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(start = STAR_DIAMETER + 10.dp).fillMaxWidth(),
    ) {
        OrbitBand.entries.forEach { band ->
            Column(modifier = Modifier.weight(band.slots.count().toFloat())) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(band.hue().copy(alpha = 0.28f), RoundedCornerShape(1.dp)),
                )
                Text(
                    text = band.label,
                    color = band.hue(),
                    fontFamily = mono,
                    fontSize = 9.5.sp,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
    }
}

// Deuterium for cold and metal for temperate is not decoration: those are the resources those
// orbits are rich in, so the strip is already teaching the axis-to-resource table §1 sets out.
internal fun OrbitBand.hue(): Color = when (this) {
    OrbitBand.HOT -> OltreColors.warn
    OrbitBand.TEMPERATE -> OltreColors.metal
    OrbitBand.COLD -> OltreColors.deuterium
}

// Red for Blocked because it is the same "you are short of something" that reddens a cost chip,
// green for Settleable because it is production, and grey for the two that are facts rather than
// states. Unsurveyed is bright rather than dim: dimming is the *locked* treatment, and calling
// 4,746 surveyable worlds locked would be a lie.
private fun MapMark.hue(): Color = when (this) {
    MapMark.HOME, MapMark.RELAY -> OltreColors.accent
    MapMark.BLOCKED -> OltreColors.danger
    MapMark.SETTLEABLE -> OltreColors.ok
    MapMark.OCCUPIED, MapMark.BARREN -> OltreColors.textSecondary
    MapMark.UNSURVEYED -> OltreColors.text.copy(alpha = 0.62f)
    MapMark.EMPTY -> Color.White.copy(alpha = 0.16f)
}

private val STAR_DIAMETER = 26.dp
private val SLOT_ROW_HEIGHT = 26.dp

private val STAR_CORE = Color(0xFFFFE6C2)
private val STAR_MID = Color(0xFFFFB454)
private val STAR_EDGE = Color(0xFF6A4A22)
