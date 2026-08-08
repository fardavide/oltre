package dev.fardavide.oltre.client.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.fardavide.oltre.client.design.core.OltreColors

// What a row on a list is, told to the row rather than repeated by it. A technology and a facility
// draw the same card and always did — the same radius, the same hairline, the same fill — as two
// copies of one chain that had to be edited twice and stayed identical by luck. The depth pass
// would have made that three values each; it is one call instead.
//
// The card is lit from above, because that is where the rail is: the top edge takes one step more
// white than the other three sides, which is enough to read as an edge catching a light and not
// enough to read as a gradient running down the card.
enum class OltreCardState {

    // Everything this row needs is in hand, so the button on it is live. The brightest of the
    // three fills, and deliberately the only one that reads as foreground.
    ACTIONABLE,

    // Waiting on something — a stock still filling, a prerequisite not met. Dimmer than the
    // background it sits on is not an option, so it is dimmer than ACTIONABLE instead: six
    // identical cards become a foreground and a background at no cost in ink.
    WAITING,

    // In flight. The only lit thing on the screen from four rows away, which is the whole answer
    // to "why can nothing else start" on a screen with one slot.
    RUNNING,
}

fun Modifier.oltreCard(state: OltreCardState): Modifier = this
    .border(1.dp, state.bevel(), SHAPE)
    .background(state.fill(), SHAPE)

private val SHAPE = RoundedCornerShape(14.dp)

// Opaque, and that is a knowing exception to "everything above the background is an alpha
// overlay". The starfield behind the content column is the reason: an alpha fill lets the stars
// through the card, where they read as dust on its surface and fight the lit edge above. Each of
// these is the alpha fill it replaces composited over OltreColors.background, so over the flat
// window it is the same colour it always was.
private fun OltreCardState.fill(): Color = when (this) {
    // white 6% over background
    OltreCardState.ACTIONABLE -> Color(0xFF14161C)
    // white 3% over background
    OltreCardState.WAITING -> Color(0xFF0C0E14)
    // accent 6% over background, pairing with the accent border it already had
    OltreCardState.RUNNING -> Color(0xFF090F1C)
}

// The stop is at 0.12 rather than at 0.5 for the same reason the alphas are one step apart: this
// has to read as an edge, not as a fade down the card.
private fun OltreCardState.bevel(): Brush = when (this) {
    OltreCardState.RUNNING -> Brush.verticalGradient(
        0f to OltreColors.accent.copy(alpha = 0.62f),
        0.12f to OltreColors.accent.copy(alpha = 0.45f),
    )
    OltreCardState.ACTIONABLE,
    OltreCardState.WAITING,
    -> Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.17f),
        0.12f to Color.White.copy(alpha = 0.09f),
    )
}
