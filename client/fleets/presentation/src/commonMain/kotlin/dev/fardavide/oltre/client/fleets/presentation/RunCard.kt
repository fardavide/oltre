package dev.fardavide.oltre.client.fleets.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.component.OltreCardState
import dev.fardavide.oltre.client.design.component.oltreCard
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.design.core.rememberOneShotFill
import kotlin.math.roundToInt

// One card per run in flight. The card is the app's `RUNNING` card, which is the whole point: a run
// **is** a job, and from three rows away it has to read as the same kind of thing a building upgrade
// and a research project already read as.
@Composable
internal fun RunCard(uiState: RunCardUiState, index: Int, compact: Boolean) {
    val mono = oltreMono()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .oltreCard(OltreCardState.RUNNING)
            .testTag(FleetsTestTags.card(index))
            .padding(11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = uiState.coordinate,
                    color = OltreColors.text,
                    fontFamily = mono,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = uiState.manifest,
                    color = OltreColors.textSecondary,
                    fontFamily = mono,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 11.dp)) {
                Text(
                    text = uiState.countdown,
                    // The countdown takes the phase's colour, which is what makes the card readable
                    // at a glance without a badge: the number you are already looking at is the one
                    // carrying the state.
                    color = uiState.phase.tint(),
                    fontFamily = mono,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = uiState.lands,
                    color = OltreColors.textTertiary,
                    fontFamily = mono,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        RunBar(bar = uiState.bar, phase = uiState.phase)
        Text(
            // The three legs lose their nouns at 320dp and keep every figure — a run card is three
            // columns of durations, and their order is what says which leg is which.
            text = if (compact) uiState.compactLegs else uiState.legs,
            color = OltreColors.textTertiary,
            fontFamily = mono,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp),
        )
    }
}

// **Three phases on one bar** — Design's seventh call. Two hairline ticks mark where the flight ends
// and begins again, and the fill takes the phase colour: accent outbound, green on station, amber
// inbound, which is the three-hue vocabulary the app already spends.
//
// It borrows `ProgressBar`'s measurements exactly — 3dp tall, white 9% track, 2dp radius — rather
// than calling it, because the fill is not always accent and the ticks are not always there. The 3dp
// bar's committed meaning is "a job is running" and a run is a job, so this is the same instrument
// reading a different quantity rather than a second one.
@Composable
private fun RunBar(bar: RunBarUiState, phase: RunPhase) {
    // Arrives once on the way in and then holds, in step with every dial and meter on the other
    // screens — see `rememberOneShotFill`.
    val fill = rememberOneShotFill()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .height(3.dp)
            .background(Color.White.copy(alpha = 0.09f), RoundedCornerShape(2.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(bar.progress * fill)
                .fillMaxHeight()
                .background(phase.tint(), RoundedCornerShape(2.dp)),
        )
        // Over the fill, so a tick the run has already passed still marks where the leg ended. Drawn
        // as two 1dp rules in the background colour rather than as a lighter tint: the bar is 3dp
        // and a tick that tried to be a colour at that size would read as a smudge.
        Ticks(fractions = listOf(bar.outboundEndsAt, bar.inboundBeginsAt))
    }
}

// Placed by fraction of the measured width, which needs a layout rather than a padding: the bar's
// width is not known until it is measured, and a `fillMaxWidth(fraction)` spacer would place the
// tick's *leading* edge at the fraction and its 1dp body past it.
@Composable
private fun Ticks(fractions: List<Float>) {
    Layout(
        content = {
            for (fraction in fractions) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(OltreColors.background),
                )
            }
        },
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable ->
                val x = (fractions[index] * constraints.maxWidth).roundToInt()
                    .coerceIn(0, constraints.maxWidth - placeable.width)
                placeable.place(x = x, y = 0)
            }
        }
    }
}

// The three-hue vocabulary, spent on the one thing in the app that has three states. Accent is what
// every other running job in the game is drawn in, so outbound is the familiar reading; green is the
// app's "this is working" and is what a hull on the surface is doing; amber is what the Colony strip
// has always used for in transit, and a run turning for home is what that strip is about.
internal fun RunPhase.tint(): Color = when (this) {
    RunPhase.OUTBOUND -> OltreColors.accent
    RunPhase.ON_STATION -> OltreColors.ok
    RunPhase.INBOUND -> OltreColors.warn
}
