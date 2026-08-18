package dev.fardavide.oltre.client.colony.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.component.oltreCardSurface
import dev.fardavide.oltre.client.design.core.rememberOneShotFill
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.design.core.resolve
import dev.fardavide.oltre.client.design.text.Strings

// Energy is a ratio between two numbers with a consequence attached, so it gets a length rather
// than a sentence: a ratio is the one kind of quantity a bar is genuinely better at than a
// number. The track spans the larger of the two terms and the green fill is what the plant
// actually delivers, so healthy the empty tail is the headroom, and in deficit the boundary is
// the plant's ceiling and the amber tail is how far past it the colony has been built.
//
// It lives inside the colony's own column rather than in the resource rail: energy is computed
// entirely from one colony's buildings, and the rail spans every screen and will eventually span
// every colony, so an empire-wide energy figure would be a number with no referent.
@Composable
fun PowerIndicator(uiState: EnergyUiState, modifier: Modifier = Modifier) {
    val mono = oltreMono()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(14.dp))
            .background(oltreCardSurface, RoundedCornerShape(14.dp))
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = Strings.powerHeading().resolve(),
                color = OltreColors.textTertiary,
                fontFamily = mono,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
            // Amber, never red: red is scoped to the one resource a cost is short of, and a
            // deficit is a state the player occupies most weeks with nothing broken in it.
            Text(
                text = uiState.verdict.resolve(),
                color = if (uiState.deficit) OltreColors.warn else OltreColors.ok,
                fontFamily = mono,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
        }
        PowerTrack(coveredFraction = uiState.coveredFraction, deficit = uiState.deficit)
        // Wraps rather than clipping, and deliberately carries no maxLines. At 320dp the card's
        // interior is 266dp and the line advances 6.83dp per character — 6.3 for JetBrains
        // Mono's 0.6em plus the 0.5sp letterSpacing inherited from Material3's bodyLarge — so it
        // holds 38 characters, and four-digit figures on both terms reach 40. All three numbers
        // are load-bearing; the card growing a line in a deep colony is the cheap side of that
        // trade, and losing the last term silently is not.
        Text(
            text = uiState.terms.resolve(),
            color = OltreColors.textSecondary,
            fontFamily = mono,
            fontSize = 10.5.sp,
        )
    }
}

// The uncovered length carries the state: neutral while it is headroom you have not spent, amber
// once it is draw the plant cannot cover. At zero production the whole track is amber, which is
// the one total case and needs no new colour — a track with no green in it already says so.
//
// One Canvas rather than a Box inside a Box since the Sky pass, for two reasons. The head is a
// radial gradient centred on the fill's leading edge, and that edge is a fraction of a width the
// composable only learns at draw time — a nested Box knows its own size and not its parent's. And a
// rounded rectangle resolved through the platform's shape renderer is not the same pixels on macOS
// and on Linux, which is the whole argument the system map already makes.
@Composable
private fun PowerTrack(coveredFraction: Float, deficit: Boolean) {
    // The fill arrives once and then holds, exactly as the dial's arc does — see `rememberOneShotFill`.
    val fill = rememberOneShotFill()
    Canvas(modifier = Modifier.fillMaxWidth().height(TRACK_HEIGHT)) {
        val radius = CornerRadius(TRACK_RADIUS.toPx())
        drawRoundRect(
            color = if (deficit) OltreColors.warn else Color.White.copy(alpha = 0.10f),
            cornerRadius = radius,
        )
        val covered = size.width * coveredFraction.coerceIn(0f, 1f) * fill
        drawRoundRect(
            color = OltreColors.ok,
            size = Size(width = covered, height = size.height),
            cornerRadius = radius,
        )
        // Centred on the leading edge in both axes, so what the eye reads is the boundary between
        // what the plant makes and what the colony has been built to draw — which is the one place
        // on this card where a decision is waiting.
        val head = HEAD_SIZE.toPx() / 2f
        val centre = Offset(x = covered, y = size.height / 2f)
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to OltreColors.ok.copy(alpha = 0.30f),
                    0.68f to Color.Transparent,
                ),
                center = centre,
                radius = head,
            ),
            radius = head,
            center = centre,
        )
    }
}

// A dp taller than the bar it replaces, which is what gives the head something to sit on: a 26dp
// glow centred on a 3dp line reads as a smudge beside it rather than as light coming off it.
private val TRACK_HEIGHT = 4.dp
private val TRACK_RADIUS = 2.dp
private val HEAD_SIZE = 26.dp
