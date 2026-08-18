package dev.fardavide.oltre.client.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.settlingColor

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

// **Composable since 0.13.1, and only so that a card can change state without snapping.** Tapping
// Upgrade turns a row from ACTIONABLE to RUNNING — the fill lifts and the hairline goes accent — and
// until now those two happened between one frame and the next, which is the one moment on the screen
// where the player has just done something and the screen answers by simply being different.
//
// The bevel is a gradient, so it is the two stops that turn rather than the brush: a `Brush` has no
// interpolation of its own, and rebuilding it each frame from two settling colours is both the
// cheapest way and the only one that keeps the 0.12 stop where the design put it.
@Composable
fun Modifier.oltreCard(state: OltreCardState): Modifier = this
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            0f to settlingColor(state.bevelTop()),
            0.12f to settlingColor(state.bevelFoot()),
        ),
        shape = oltreCardShape,
    )
    .background(settlingColor(state.fill()), oltreCardShape)

// The white 4.5% that every card in the app carried before this pass, composited over the same
// base as the three fills above — and carrying the same caveat about which base that is.
//
// It exists for the surfaces that are cards but have no state to report: the galaxy's world rows
// and its system map, the colony's power indicator. They keep their own borders and take no bevel,
// because the depth pass was reviewed against Research and Colony rows and extending its light to
// the rest of the app is a design call rather than a bug fix. What is *not* optional is the
// opacity: the starfield sits under every destination now, and an alpha fill lets it through, so
// leaving these translucent would put stars inside a world row. Use this, or use `oltreCard` —
// never a raw alpha over the background.
val oltreCardSurface = Color(0xFF101218)

// Public since the Sky pass, and for one caller: the completion sweep has to clip its band to the
// card it crosses, and a band cut square across a rounded corner is the one way that transition can
// look like a defect. Everything else about the card stays this file's business.
//
// Since 0.13.1 it has a second caller and a second reason, which is the more important one: a press
// has to be clipped to the thing it presses, so every tappable card now states this shape at its own
// call site. See `pressable`.
val oltreCardShape = RoundedCornerShape(14.dp)

// The app's one verb — the colony's Upgrade, Research's Research, the shipyard's Build, the probe's
// Dispatch and the sheet footer's own button are a single control drawn five times, and this is its
// radius. It sits beside the card's shape rather than in five feature modules for the reason the card
// enum above already gives: the copies stayed identical by luck, and the press fix would have made it
// six places to edit rather than five, because a shape a ripple is clipped to and a shape a fill is
// drawn with must never be able to disagree.
val oltreActionShape = RoundedCornerShape(9.dp)

// Opaque, and that is a knowing exception to "everything above the background is an alpha
// overlay". The starfield behind the content column is the reason: an alpha fill lets the stars
// through the card, where they read as dust on its surface and fight the lit edge above.
//
// **These are the handoff's literal values, and its stated derivation does not hold.** Each is the
// alpha fill it replaces composited over `OltreColors.background` (#05070D), which the handoff
// says makes it "visually identical over the flat window". It is not: `App.kt` wraps the whole app
// in a Material `Surface`, which takes `OltreColors.surface` (#0A0E18) from the theme, so the
// window behind these cards is the lighter of the two. Composited over the colour actually there,
// the old white 4.5% was #151922 — five to ten units per channel brighter than what is written
// here. The values are kept as specified because the handoff calls them final and recomputing them
// would be inventing colours nobody approved; the discrepancy is Davide's to settle. If the base
// is ever corrected, every fill in this file and `oltreCardSurface` move together.
// `internal` rather than private, on `GalaxyMapDrawingTest`'s precedent: the mapping from a state to
// a colour is separable from the composable that draws it, and separating it is what makes it
// executable by a test that needs no composition. What that test pins is the paragraph above — that
// these are the handoff's literal values — so an accidental edit to one of six hex literals is caught
// by an assertion rather than by a baseline's tolerance.
internal fun OltreCardState.fill(): Color = when (this) {
    // white 6% over background
    OltreCardState.ACTIONABLE -> Color(0xFF14161C)
    // white 3% over background
    OltreCardState.WAITING -> Color(0xFF0C0E14)
    // accent 6% over background, pairing with the accent border it already had
    OltreCardState.RUNNING -> Color(0xFF090F1C)
}

// The two stops of the lit edge, apart rather than as one brush so that each can be interpolated on
// its own. The stop is at 0.12 rather than at 0.5 for the same reason the alphas are one step apart:
// this has to read as an edge, not as a fade down the card.
internal fun OltreCardState.bevelTop(): Color = when (this) {
    OltreCardState.RUNNING -> OltreColors.accent.copy(alpha = 0.62f)
    OltreCardState.ACTIONABLE,
    OltreCardState.WAITING,
    -> Color.White.copy(alpha = 0.17f)
}

internal fun OltreCardState.bevelFoot(): Color = when (this) {
    OltreCardState.RUNNING -> OltreColors.accent.copy(alpha = 0.45f)
    OltreCardState.ACTIONABLE,
    OltreCardState.WAITING,
    -> Color.White.copy(alpha = 0.09f)
}
