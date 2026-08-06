package dev.fardavide.oltre.client

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The mockup's tab glyphs, drawn rather than imported. They are bespoke — a ringed world, a lab
// ring, a rocket, a galaxy, a fleet wedge — so no icon pack carries them, and a Canvas keeps them
// exact while adding no dependency. Every path below is written in the SVG's own 24-unit space
// and scaled to the requested size, so the numbers here read straight against the `<svg>` blocks
// in docs/ui-mockup.html.
@Composable
internal fun TabIcon(
    tab: OltreTab,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 21.dp,
) {
    Canvas(modifier.size(size)) {
        val factor = this.size.width / VIEWPORT
        withTransform({ scale(factor, factor, pivot = Offset.Zero) }) {
            when (tab) {
                OltreTab.COLONY -> drawColony(tint)
                OltreTab.RESEARCH -> drawResearch(tint)
                OltreTab.SHIPYARD -> drawShipyard(tint)
                OltreTab.GALAXY -> drawGalaxy(tint)
                OltreTab.FLEETS -> drawFleets(tint)
            }
        }
    }
}

// A world with a ring tilted off its axis.
private fun DrawScope.drawColony(tint: Color) {
    drawCircle(tint, radius = 7.5f, center = CENTRE, style = Stroke(width = 1.7f))
    withTransform({ rotate(degrees = -24f, pivot = CENTRE) }) {
        drawOval(
            color = tint,
            topLeft = Offset(1f, 8f),
            size = Size(22f, 8f),
            alpha = 0.55f,
            style = Stroke(width = 1.4f),
        )
    }
}

// A nucleus with four spokes: the lab.
private fun DrawScope.drawResearch(tint: Color) {
    listOf(
        Offset(12f, 3f) to Offset(12f, 7f),
        Offset(12f, 17f) to Offset(12f, 21f),
        Offset(4.5f, 12f) to Offset(8.5f, 12f),
        Offset(15.5f, 12f) to Offset(19.5f, 12f),
    ).forEach { (start, end) ->
        drawLine(tint, start, end, strokeWidth = 1.7f, cap = StrokeCap.Round)
    }
    drawCircle(tint, radius = 3.6f, center = CENTRE, style = Stroke(width = 1.7f))
}

// A hull on its pad, with two legs.
private fun DrawScope.drawShipyard(tint: Color) {
    val hull = Path().apply {
        moveTo(12f, 2.5f)
        cubicTo(15.2f, 5.9f, 16.8f, 9.3f, 16.8f, 13.7f)
        lineTo(12f, 18f)
        lineTo(7.2f, 13.7f)
        cubicTo(7.2f, 9.3f, 8.8f, 5.9f, 12f, 2.5f)
        close()
    }
    drawPath(hull, tint, style = Stroke(width = 1.6f, join = StrokeJoin.Round))
    drawLine(tint, Offset(9f, 18.5f), Offset(7f, 21.5f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawLine(tint, Offset(15f, 18.5f), Offset(17f, 21.5f), strokeWidth = 1.6f, cap = StrokeCap.Round)
}

// A core inside two crossed orbital discs.
private fun DrawScope.drawGalaxy(tint: Color) {
    drawCircle(tint, radius = 2.2f, center = CENTRE)
    drawDisc(tint)
    withTransform({ rotate(degrees = 60f, pivot = CENTRE) }) {
        drawDisc(tint)
    }
}

private fun DrawScope.drawDisc(tint: Color) {
    drawOval(
        color = tint,
        topLeft = Offset(2.5f, 8f),
        size = Size(19f, 8f),
        style = Stroke(width = 1.5f),
    )
}

// A wedge of ships in formation.
private fun DrawScope.drawFleets(tint: Color) {
    val wedge = Path().apply {
        moveTo(3f, 17.5f)
        lineTo(12f, 4f)
        lineTo(21f, 17.5f)
        lineTo(12f, 14f)
        close()
    }
    drawPath(wedge, tint, style = Stroke(width = 1.6f, join = StrokeJoin.Round))
}

// The SVG viewBox every path above is written in.
private const val VIEWPORT = 24f
private val CENTRE = Offset(12f, 12f)
