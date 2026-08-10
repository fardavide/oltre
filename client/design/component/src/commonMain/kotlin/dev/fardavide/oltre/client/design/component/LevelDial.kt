package dev.fardavide.oltre.client.design.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.design.core.rememberOneShotFill

// What a row that is running says about itself, in place of the hairline bar it used to draw across
// the bottom of the card. Both screens that run a job draw this — a facility upgrading and a project
// being researched — and a probe in flight does not, because the probe's card is not a row and has
// no level to put in the middle of a ring.
//
// **It carries the level it is on now, not the one it is going to.** The line above it already reads
// "→ LV 14 · done 11:23", so a dial repeating 14 would spend the one legible glyph on the card
// saying a second time what the row has just said. Where it is going is a sentence; where it *is* is
// a number, and a number is what a ring can hold.
//
// Drawn as a Canvas rather than assembled from shaped Boxes for the reason `PowerMark` and the
// system map are: a stroked circle resolved through the platform's shape renderer is not the same
// pixels on macOS and on Linux, and the baselines are recorded on one and verified on the other.
@Composable
fun LevelDial(level: Int, percent: Int, modifier: Modifier = Modifier) {
    val fill = rememberOneShotFill()
    val progress = percent.coerceIn(0, 100) / 100f * fill
    Box(modifier = modifier.size(DIAL_SIZE), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(DIAL_SIZE)) {
            val centre = Offset(x = size.width / 2f, y = size.height / 2f)
            val ring = RING_RADIUS.toPx()

            // Wider than the dial and drawn under everything, so what reads as light around the ring
            // is light and not a second ring. It arrives at 35% rather than at nothing, because a
            // halo that grows from zero reads as the dial switching on — and nothing in this app
            // switches on, it resumes.
            val halo = HALO_SIZE.toPx() / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to OltreColors.accent.copy(alpha = 0.22f * (0.35f + 0.65f * fill)),
                        0.66f to Color.Transparent,
                    ),
                    center = centre,
                    radius = halo,
                ),
                radius = halo,
                center = centre,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = ring,
                center = centre,
                style = Stroke(width = TRACK_STROKE.toPx()),
            )

            // Two arcs on one circle, and the wider dimmer one underneath is the whole trick: a
            // stroke inside a stroke reads as a lit filament where a single stroke reads as a ring
            // that happens to be blue. It is the same move the fleet strip makes with amber 6%
            // inside amber 22%, and like that one it costs no blur and no shadow.
            val arc = Rect(center = centre, radius = ring)
            val sweep = progress * 360f
            drawArc(
                color = OltreColors.accent.copy(alpha = 0.35f),
                startAngle = SWEEP_ORIGIN,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = arc.topLeft,
                size = arc.size,
                style = Stroke(width = BLOOM_STROKE.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = OltreColors.accent,
                startAngle = SWEEP_ORIGIN,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = arc.topLeft,
                size = arc.size,
                style = Stroke(width = TRACK_STROKE.toPx(), cap = StrokeCap.Round),
            )
        }
        Text(
            text = "$level",
            color = OltreColors.text,
            fontFamily = oltreMono(),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

// Twelve o'clock. Compose measures 0° at three o'clock and clockwise, so the quarter turn back is
// what makes a dial that has just started read as "barely begun" rather than as "a quarter done".
private const val SWEEP_ORIGIN = -90f

private val DIAL_SIZE = 34.dp
private val RING_RADIUS = 13.dp
private val TRACK_STROKE = 2.5.dp
private val BLOOM_STROKE = 5.dp

// Larger than the dial, and deliberately drawn from inside it: 13 + 2.5 puts the ring's outer edge
// at 15.5 of the 17dp half-box, so the halo spills 9dp past the layout on every side and lands in
// the card's own 11dp padding without ever reaching its border.
private val HALO_SIZE = 52.dp
