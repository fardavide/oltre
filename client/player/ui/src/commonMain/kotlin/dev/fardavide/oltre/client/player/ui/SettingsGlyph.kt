package dev.fardavide.oltre.client.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// A cog, in the icon set's box and at its weights. **Eight primitives where the set's marks carry
// three or four**, which is over its own budget and is the price of the one shape a player reads
// without being taught it: this control has no label and no text beside it, so it has to be legible
// on its own or it is nothing.
//
// **The rim is what makes it a cog.** Without it — a hub and six strokes standing off in space — it
// is a sun, which is what the first cut drew and what `SettingsGlyphTest` now measures away from.
// The teeth carry a heavier stroke than the two circles for the same reason: at 18dp, teeth at the
// rim's own weight read as rays.
@Composable
internal fun SettingsGlyph(color: Color, modifier: Modifier = Modifier) {
    // One caller, one size — see `PlayerMark` for why there is no `size` parameter.
    Canvas(modifier = modifier.size(GEAR_SIZE)) {
        drawSettingsGlyph(unit = size.width / MARK_VIEWBOX, dx = 0f, dy = 0f, color = color)
    }
}

// A plain `DrawScope` function, for `drawPlayerMark`'s reason — a `Canvas { }` lambda is not
// `@Composable`, so the unit pass counts it and cannot reach it.
internal fun DrawScope.drawSettingsGlyph(unit: Float, dx: Float, dy: Float, color: Color) {
    fun at(x: Float, y: Float) = Offset(x = (x + dx) * unit, y = (y + dy) * unit)

    val ring = Stroke(width = GEAR_RING_STROKE * unit)
    val centre = at(GEAR_CENTRE, GEAR_CENTRE)
    drawCircle(color = color, radius = RIM_R * unit, center = centre, style = ring)
    // The bore. A filled centre would read as a pressed button rather than as a fastener.
    drawCircle(color = color, radius = HUB_R * unit, center = centre, style = ring)
    repeat(TEETH) { tooth ->
        val angle = tooth * (FULL_TURN / TEETH) * PI_OVER_180
        drawLine(
            color = color,
            start = at(
                x = GEAR_CENTRE + RIM_R * cos(angle),
                y = GEAR_CENTRE + RIM_R * sin(angle),
            ),
            end = at(
                x = GEAR_CENTRE + TOOTH_R * cos(angle),
                y = GEAR_CENTRE + TOOTH_R * sin(angle),
            ),
            strokeWidth = GEAR_TOOTH_STROKE * unit,
            cap = StrokeCap.Round,
        )
    }
}

// 18dp rather than the mark's 20: the gear is a fastener beside a face, and drawing the two at one
// size would make the control the loudest thing in the strip.
internal val GEAR_SIZE = 18.dp

internal const val GEAR_CENTRE = 12f

private const val GEAR_RING_STROKE = 1.6f

// Heavier than the circles it hangs on — see the note above.
private const val GEAR_TOOTH_STROKE = 2f

private const val RIM_R = 5.6f
private const val HUB_R = 2.3f
private const val TOOTH_R = 7.9f

private const val TEETH = 6
private const val FULL_TURN = 360f
private const val PI_OVER_180 = 0.017453292f
