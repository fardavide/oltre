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

    // **Accepted by the phone and not yet by the server.** Amber 6% inside amber 22% — the fleet
    // strip's own pair, at the fleet strip's scale — because the fleet strip is where this app
    // already says *a thing of yours is out there and has not landed*. Held is that sentence about
    // an intent instead of a hull.
    //
    // **Deliberately not `RUNNING`'s accent border.** A running card earned accent by getting an
    // answer: it has a countdown and a bar because the clock is real. A held card has neither,
    // because there is no instant to count to — and that absence, next to a card that has one, is
    // most of what tells the two apart at a glance.
    HELD,
}

// **This does not settle its colours, and 0.13.2 is where that was tried and taken back out.** The
// reasoning that put `settlingColor` here was that tapping Upgrade turns a row from ACTIONABLE to
// RUNNING between one frame and the next, which is the one moment where the player has just acted and
// the screen answers by simply being different. That is true, and it is not the only way this state
// moves.
//
// `OltreCardState` is derived from the game rather than from the tap: `App.kt` re-derives the whole
// screen every second, and `FacilityActionUiState.cardState()` reads *stocks* to choose between
// ACTIONABLE and WAITING and reads *elapsed time* to choose RUNNING. So a colony left open on the
// tab crosses a mine's price with nobody touching the phone, and a build finishes on its own — and
// an animated card would fade on both. A modifier cannot tell which of the two moved it.
//
// That is precisely the use `settlingColor`'s own KDoc forbids: a fact that fades in is a fact the
// player is invited to watch happen, and this app may not draw that. What kept the settle is the set
// of colours that *only* a tap can move — the selection pills, the discs, the window rungs, the watch
// square. A card's state is not one of them.
fun Modifier.oltreCard(state: OltreCardState): Modifier = this
    .border(1.dp, state.bevel(), oltreCardShape)
    .background(state.fill(), oltreCardShape)

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
    // **`FleetStrip`'s own fill, by its own arithmetic and to the same hex** — warn at 6% over the
    // background — because a held card and a fleet in transit are the same claim about two different
    // kinds of thing, and the design asked for one surface rather than two that nearly match.
    OltreCardState.HELD -> Color(0xFF141111)
}

// The stop is at 0.12 rather than at 0.5 for the same reason the alphas are one step apart: this has
// to read as an edge, not as a fade down the card.
private fun OltreCardState.bevel(): Brush = Brush.verticalGradient(
    0f to bevelTop(),
    0.12f to bevelFoot(),
)

// The two stops named apart rather than inlined into the brush above. That split arrived with the
// settle this file no longer does, and it is kept because it is what `OltreCardTest` can execute: a
// `Brush` is opaque to an assertion and two colours are not.
internal fun OltreCardState.bevelTop(): Color = when (this) {
    OltreCardState.RUNNING -> OltreColors.accent.copy(alpha = 0.62f)
    // **Flat rather than bevelled, and both stops are the fleet strip's single 22%.** The bevel says
    // *this card is catching the light from the rail*; the fleet strip has never had one, and a held
    // card is the fleet strip's surface. A held card with a lit top edge would be borrowing depth
    // from the family it is deliberately not in.
    OltreCardState.HELD -> OltreColors.warn.copy(alpha = 0.22f)
    OltreCardState.ACTIONABLE,
    OltreCardState.WAITING,
    -> Color.White.copy(alpha = 0.17f)
}

internal fun OltreCardState.bevelFoot(): Color = when (this) {
    OltreCardState.RUNNING -> OltreColors.accent.copy(alpha = 0.45f)
    OltreCardState.HELD -> OltreColors.warn.copy(alpha = 0.22f)
    OltreCardState.ACTIONABLE,
    OltreCardState.WAITING,
    -> Color.White.copy(alpha = 0.09f)
}
