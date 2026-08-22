package dev.fardavide.oltre.client.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.settlingColor
import dev.fardavide.oltre.client.design.icon.WatchBell
import dev.fardavide.oltre.client.design.icon.WatchBellStack

// What the square on a row says, and whether the row has one at all. Null means no square: an
// affordable row has nothing to wait for, a locked one has no price yet, and a row whose binding
// resource has no net income has no instant to name. Every one of those is *the absence of a
// control*, not a disabled one — nothing in this app is greyed out.
//
// Three members for two kinds of alert, and the asymmetry is the design's. A row that is waiting on
// its price gains a line when it is booked, because the instant it named is not otherwise anywhere
// on the card. A row that is building already prints `→ LV 13 · done 11:23`, so subscribing to it
// adds nothing: the lit square is the whole state.
sealed interface WatchUiState {

    // A square, unlit. There is an instant to book here and the player has not booked it — either
    // a price they are waiting for or a completion they have not asked about.
    data object Offered : WatchUiState

    // The one row in the whole game holding the affordability watch, and the instant it named. The
    // line is carried here rather than on the row because the two are one fact — a lit square with
    // no instant beside it would be a state the row could get into and could not explain.
    data class Booked(val affordableAt: TextRes) : WatchUiState

    // A running job whose completion the player asked about. No line, deliberately: the row's own
    // accent line already says when it lands, and repeating it under the price would be the second
    // time one card said the same thing.
    data object Subscribed : WatchUiState
}

// **What the square itself is showing**, which is not the same question as what the row is in. A row
// has three states because two of them carry different *lines*; the square has three because the
// Shipyard's control has a second way of being on.
//
// Three constants rather than a boolean and a glyph, because "unlit and showing two bells" is not a
// state anything can be in and a pair of parameters would let it compile.
enum class WatchSquareUiState {

    // There is an instant to book here and the player has not booked it.
    UNASKED,

    // Booked, and one bell says so. Every row outside the Shipyard ends here.
    ASKED,

    // **Booked the second way, and only a queue has one.** A facility row points at a single job, so
    // there is one question to ask about it; a hull card stands over an order, so there are two —
    // tell me when the order is done, or tell me about every hull in it. See `HullAlert`.
    ASKED_SEVERAL,
}

// The square's state read off the row's. Two of the row's three members mean booked and one does
// not, and the square is the only part of a row that cares which — the line, which is the reason
// `Booked` and `Subscribed` are separate at all, is drawn elsewhere.
fun WatchUiState.asSquare(): WatchSquareUiState =
    if (this == WatchUiState.Offered) WatchSquareUiState.UNASKED else WatchSquareUiState.ASKED

// The one new affordance the watch slice adds, and deliberately the only one: a 29dp square beside
// the ghost time that books an alert for the instant the row already prints.
//
// **The card's border is not touched, in either state.** An accent border means something is in
// flight and nothing else; a watched row is not doing anything, it is booked. Accent border is
// something happening, accent text is something booked — which is why the lit square and the
// `→ affordable` line are the whole of what changes.
//
// **The touch target is 29dp wide and as tall as the row can spare, and both halves of that are
// measured rather than lazy.**
//
// *Width* is 29dp because the design budgets 29dp off the name column and no more; a 44dp-wide
// target spends 15dp the text does not have, and at 393dp that is enough to ellipsise
// "metal · crystal output" on the Research row next door. Compose cannot buy the axis for free
// either — a child placed outside its parent's bounds does not reliably receive touch, which is why
// Material's own `minimumInteractiveComponentSize` expands the layout rather than overflowing it.
//
// *Height* is 44dp beside the ghost time, where the card is taller than that whatever this does, and
// 29dp when the row has **stacked** the square under the ghost — because there it is the tallest
// thing in the action column and every pixel of it is a pixel of row. Stacked at 44dp the column is
// 28 + 7 + 44 = 79dp against a 56dp content column, which grows the row to 101dp where the design
// drew 88. At 29dp it is 64dp and the row lands where it was drawn.
// `PressableFace` rather than `pressable`, because the tap area is taller than the thing it presses:
// a ripple filling 29x44 beside a 29dp square reads as a smear. This file argued that first and
// wired it by hand; since 0.13.1 the wiring is the design system's, and two more call sites that had
// copied it — the probe's Dispatch and the caption's — call the same component.
//
// **The chrome has two states and the glyph has three**, which is deliberate rather than an
// oversight: what the fill and the border say is *booked*, and both ways of being booked are booked.
// A third fill would be a colour this system does not spend, and it would make the second tap look
// like a different kind of thing rather than the same question answered differently. What changes is
// the mark — one bell or two — which is the only part of the control that is about *how many alerts*.
@Composable
fun WatchSquare(state: WatchSquareUiState, onClick: () -> Unit, stacked: Boolean, modifier: Modifier = Modifier) {
    val asked = state != WatchSquareUiState.UNASKED
    PressableFace(
        onClick = onClick,
        shape = RoundedCornerShape(RADIUS),
        modifier = modifier.size(width = SQUARE, height = if (stacked) SQUARE else HIT_TARGET),
        faceModifier = Modifier
            .size(SQUARE)
            .background(
                // The same 12% accent fill an actionable card carries, and nothing at all when
                // the square is merely offered: an unwatched row has no state to announce.
                color = settlingColor(
                    if (asked) OltreColors.accent.copy(alpha = 0.12f) else Color.Transparent,
                ),
                shape = RoundedCornerShape(RADIUS),
            )
            .border(
                width = 1.dp,
                // 45% accent watched — the same border an active card wears — against the 16%
                // white the ghost button beside it already uses.
                color = settlingColor(
                    if (asked) OltreColors.accent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.16f),
                ),
                shape = RoundedCornerShape(RADIUS),
            ),
    ) {
        // The bell lights with the square rather than after it. Booking an alert is the one action
        // in the app whose whole result is that a control changed colour — there is no row to move
        // and no number to update — so it is the one that most wants to be seen happening.
        val color = settlingColor(if (asked) OltreColors.accent else OltreColors.textTertiary)
        when (state) {
            WatchSquareUiState.UNASKED, WatchSquareUiState.ASKED -> WatchBell(color = color)
            WatchSquareUiState.ASKED_SEVERAL -> WatchBellStack(color = color)
        }
    }
}

// The ghost time and the square that books an alert for it — **side by side above the compact
// width, stacked and right-aligned below it.** Shared by the colony's facility rows and Research's
// project rows, because the two screens must not be able to disagree about where the control sits.
//
// Stacking is what pays for the square at 320dp. Beside the ghost its 29dp comes out of the name
// column, and there it clips "Robotics Factory" mid-word — which this app never does; it authors a
// short name instead. Under the ghost it costs the row about eight pixels of height and no words at
// all, and the name column goes back to the width it had before the square existed.
@Composable
fun WatchableAction(
    watch: WatchSquareUiState?,
    stacked: Boolean,
    onToggleWatch: () -> Unit,
    watchModifier: Modifier = Modifier,
    ghost: @Composable () -> Unit,
) {
    val square: @Composable () -> Unit = {
        watch?.let {
            WatchSquare(
                state = it,
                onClick = onToggleWatch,
                stacked = stacked,
                modifier = watchModifier,
            )
        }
    }
    if (stacked) {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(GAP)) {
            ghost()
            square()
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GAP)) {
            ghost()
            square()
        }
    }
}

private val HIT_TARGET = 44.dp
private val SQUARE = 29.dp
private val RADIUS = 9.dp

// The same gap either way, so the pair reads as one control at both widths.
private val GAP = 7.dp
