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

// **Which answer the control is on.** A row points at a single job, so there is one question to ask
// about it; a hull card stands over an order, so there are two — tell me when the order is done, or
// tell me about every hull in it. See `HullAlert`.
//
// Three constants rather than a boolean and a glyph, because "unlit and showing two bells" is not a
// state anything can be in and a pair of parameters would let it compile.
enum class WatchAsk {

    // There is an instant to book here and the player has not booked it.
    NONE,

    // Booked, and one bell says so. Every row outside the Shipyard ends here.
    ONE,

    // Booked the second way, and only a queue has one.
    SEVERAL,
}

// **What the square itself is showing**, which is not the same question as what the row is in — and
// since 0.20 it is two facts rather than one: which answer the control is on, and whether the server
// has agreed to it yet.
//
// **The second fact could not be a fourth constant**, which is why this stopped being an enum. Held
// is orthogonal to the ask — the Shipyard's stack glyph is as reachable held as it is confirmed —
// so a flat list would have needed six members and would have let five of them be written by hand
// wrongly. The three names the app used before this are companion constants, so every call site that
// meant *not held* still says exactly what it said.
data class WatchSquareUiState(val asked: WatchAsk, val held: Boolean) {

    companion object {

        val UNASKED = WatchSquareUiState(asked = WatchAsk.NONE, held = false)

        val ASKED = WatchSquareUiState(asked = WatchAsk.ONE, held = false)

        val ASKED_SEVERAL = WatchSquareUiState(asked = WatchAsk.SEVERAL, held = false)

        // Every face there is, for the bench and for the screenshot plate. A list rather than the
        // `entries` this replaced, and it is the one thing that stops being free when a state
        // becomes a product of two: nothing derives it, so a seventh face has to be added here by
        // hand or it is drawn by no test at all.
        val FACES: List<WatchSquareUiState> = listOf(
            UNASKED,
            ASKED,
            ASKED_SEVERAL,
            UNASKED.copy(held = true),
            ASKED.copy(held = true),
            ASKED_SEVERAL.copy(held = true),
        )
    }
}

// The square's state read off the row's. Two of the row's three members mean booked and one does
// not, and the square is the only part of a row that cares which — the line, which is the reason
// `Booked` and `Subscribed` are separate at all, is drawn elsewhere.
//
// `held` is the caller's to know: a `WatchUiState` is derived from the colony, and whether a tap on
// it has reached the server is derived from the outbox. Required rather than defaulted, because the
// obvious default is also the value that means *the feature is switched off*.
//
// **A held square draws the request — and the request is already what the row says**, which is why
// nothing here inverts.
//
// The design's sentence is *"a held row asks for the opposite of what the server is on, so the
// request is what the square draws"*, and the first implementation of it read the second clause off
// the first: invert the row, get the request. That is true of the **server's** colony and false of
// the one on screen. Every one of these taps applies its own transition locally before anything is
// mapped — `alerting`, `alertingHull`, `preferring` — so by the time a row is drawn it *already*
// says what the player asked for. Inverting produced the state they were leaving.
//
// It shipped nowhere: caught at #113 by an end-to-end test that tapped a dark bell and read the
// sentence back. The lesson is the shape rather than the sign — **a mapper cannot tell an optimistic
// state from a confirmed one by looking at it**, so a rule phrased as *the opposite of what the
// server says* has no way to be true here. Phrase it as *what the row says*, which is a fact the
// mapper actually has.
//
// `held` is the caller's to know and still required: it is what draws the amber face. It no longer
// decides which face.
fun WatchUiState.asSquare(held: Boolean): WatchSquareUiState = WatchSquareUiState(
    asked = if (this != WatchUiState.Offered) WatchAsk.ONE else WatchAsk.NONE,
    held = held,
)

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
// **The third and fourth faces are the lit pair one hue over**, which is the whole of why they need
// no legend: amber already means *a thing of yours is out there and has not landed*, said about an
// intent instead of a hull, and the relationship between the two amber faces is the relationship the
// player can already read between lit and unlit. A held ON keeps the fill; a held OFF drops it and
// takes the glyph to the locked opacity.
//
// **The square says "not confirmed" and never which way**, because 29dp cannot carry both facts. The
// row that owns it says the direction in words — see `Strings.heldTurning`. That asymmetry is the
// cost of the treatment and it is written down rather than discovered.
@Composable
fun WatchSquare(state: WatchSquareUiState, onClick: () -> Unit, stacked: Boolean, modifier: Modifier = Modifier) {
    val asked = state.asked != WatchAsk.NONE
    val hue = if (state.held) OltreColors.warn else OltreColors.accent
    PressableFace(
        onClick = onClick,
        shape = RoundedCornerShape(RADIUS),
        modifier = modifier.size(width = SQUARE, height = if (stacked) SQUARE else HIT_TARGET),
        faceModifier = Modifier
            .size(SQUARE)
            .background(
                // The same 12% accent fill an actionable card carries, and nothing at all when
                // the square is merely offered: an unwatched row has no state to announce.
                // A held OFF is the only face with no fill *and* a coloured border, which is exactly
                // the picture it should be: the request is drawn, the state it asks for is not.
                color = settlingColor(if (asked) hue.copy(alpha = 0.12f) else Color.Transparent),
                shape = RoundedCornerShape(RADIUS),
            )
            .border(
                width = 1.dp,
                // 45% accent watched — the same border an active card wears — against the 16%
                // white the ghost button beside it already uses. A held square always has a border
                // at that alpha, whichever way the request went: the request is the thing being
                // drawn, and it is present either way.
                color = settlingColor(
                    if (asked || state.held) hue.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.16f),
                ),
                shape = RoundedCornerShape(RADIUS),
            ),
    ) {
        // The bell lights with the square rather than after it. Booking an alert is the one action
        // in the app whose whole result is that a control changed colour — there is no row to move
        // and no number to update — so it is the one that most wants to be seen happening.
        //
        // **42% is the locked opacity and this is the one place it means something else**, which the
        // design took deliberately: on a held OFF the ink is dimmed because the state being asked
        // for is *off*, not because the control is unavailable. Nothing here is ever unavailable —
        // the square answers taps in every face it has.
        val ink = when {
            state.held && state.asked == WatchAsk.NONE -> hue.copy(alpha = 0.42f)
            asked || state.held -> hue
            else -> OltreColors.textTertiary
        }
        val color = settlingColor(ink)
        when (state.asked) {
            WatchAsk.NONE, WatchAsk.ONE -> WatchBell(color = color)
            WatchAsk.SEVERAL -> WatchBellStack(color = color)
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
