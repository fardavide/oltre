package dev.fardavide.oltre.client.changelog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.fardavide.oltre.client.changelog.domain.ReleaseVersion
import dev.fardavide.oltre.client.changelog.domain.skyAt
import dev.fardavide.oltre.client.design.component.oltreCardSurface

// **A sky per build.** Claude Design's mark, accepted 2026-08-23: `minor + patch` bodies on a
// golden-angle spiral over a world's limb, filled for the minor lines reached and hollow for the
// patches riding on the current one. `skyAt` in `:client:changelog:domain` is the whole rule; this
// is four primitives walking what it returns.
//
// **The drawing is deliberately the thin half.** Everything a test could catch — a body outside the
// box, two bodies on top of each other, a limb through the sky — is a property of the arithmetic and
// is asserted there across every version the project could reach, which is what the design asked for
// in place of a 29dp baseline it could not read.
//
// **Colour: none.** White at four alphas and nothing else. Amber is in transit, red is short, blue is
// in flight and green is production — every hue in this system is spoken for, and a mark that
// borrowed one would be making a claim about a release. That is the world portrait's argument from
// 0.10, applied to a picture with even less to say.
//
// Nothing here ever animates. A constellation is the first thing anybody would want to twinkle, and
// the rule against loops is older than the mark.
@Composable
fun VersionMark(version: ReleaseVersion, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        drawVersionMark(version = version, side = size)
    }
}

// Split from the composable above so a caller already inside a `DrawScope` — a page drawing its own
// background, a future row drawing a mark behind text — does not have to nest a `Canvas` to get one.
// `PowerMark` is the precedent for the shape, and the reason it is a `Path` rather than a glyph: a
// stroked path is the same pixels on macOS and on Linux CI, and an icon font is not.
fun DrawScope.drawVersionMark(version: ReleaseVersion, side: Dp) {
    // The rule is stated in dp — its floors are dp, so that the settings row's 29dp mark keeps its
    // ink — and one multiplication is the whole of the conversion.
    val density = 1.dp.toPx()
    val sky = version.skyAt(side.value)

    // The limb first, so a world drawn on it can occlude it rather than being crossed by it.
    val limb = sky.limb
    val limbRadius = limb.radius * density
    drawArc(
        color = Color.White.copy(alpha = LIMB_ALPHA),
        // Both angles come from the sky rather than from here: the arc belongs to a circle half
        // again as wide as the mark, and everything but the sliver crossing the card is off it.
        startAngle = limb.startAngleDegrees,
        sweepAngle = limb.sweepAngleDegrees,
        useCenter = false,
        // The circle it is cut from, stated as the box it is inscribed in — centred under the mark
        // with its top at the crest, so it hangs a full radius below the card.
        topLeft = Offset(
            x = sky.side * density / 2f - limbRadius,
            y = limb.crestY * density,
        ),
        size = Size(width = 2f * limbRadius, height = 2f * limbRadius),
        style = Stroke(width = limb.stroke * density),
    )

    for (world in sky.worlds) {
        // Filled with the sheet's own surface rather than with nothing, so the limb stops at the
        // world's edge instead of running through it. A world is a body that finished.
        drawCircle(
            color = oltreCardSurface,
            radius = world.radius * density,
            center = Offset(world.x * density, world.y * density),
        )
        drawCircle(
            color = Color.White.copy(alpha = WORLD_ALPHA),
            radius = world.radius * density,
            center = Offset(world.x * density, world.y * density),
            style = Stroke(width = limb.stroke * density),
        )
    }

    for (body in sky.bodies) {
        val centre = Offset(body.x * density, body.y * density)
        if (body.filled) {
            drawCircle(
                color = Color.White.copy(alpha = FILLED_ALPHA),
                radius = body.radius * density,
                center = centre,
            )
        } else {
            drawCircle(
                color = Color.White.copy(alpha = HOLLOW_ALPHA),
                radius = body.radius * density,
                center = centre,
                style = Stroke(width = sky.ringStroke * density),
            )
        }
    }
}

private const val FILLED_ALPHA = 0.72f
private const val HOLLOW_ALPHA = 0.40f
private const val LIMB_ALPHA = 0.22f
private const val WORLD_ALPHA = 0.34f
