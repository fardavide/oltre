package dev.fardavide.oltre.client.design.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.OltreMotion
import dev.fardavide.oltre.client.design.icon.WatchBell

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
    data class Booked(val affordableAt: String) : WatchUiState

    // A running job whose completion the player asked about. No line, deliberately: the row's own
    // accent line already says when it lands, and repeating it under the price would be the second
    // time one card said the same thing.
    data object Subscribed : WatchUiState
}

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
@Composable
fun WatchSquare(watched: Boolean, onClick: () -> Unit, stacked: Boolean, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // `pressable`'s spring, restated rather than called: that modifier puts the click and the
    // indication on one node, and here they are deliberately on two — the tap area is taller than
    // the thing it presses, and a ripple filling 29x44 beside a 29dp square reads as a smear.
    val scale by animateFloatAsState(
        targetValue = if (pressed) OltreMotion.PRESS_SCALE else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
    )
    Box(
        modifier = modifier
            .size(width = SQUARE, height = if (stacked) SQUARE else HIT_TARGET)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                // Ahead of the fill and the border, so the press scales what is drawn inside it —
                // the same ordering the Upgrade button's own comment insists on.
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .size(SQUARE)
                .clip(RoundedCornerShape(RADIUS))
                .indication(interaction, LocalIndication.current)
                .background(
                    // The same 12% accent fill an actionable card carries, and nothing at all when
                    // the square is merely offered: an unwatched row has no state to announce.
                    color = if (watched) OltreColors.accent.copy(alpha = 0.12f) else Color.Transparent,
                    shape = RoundedCornerShape(RADIUS),
                )
                .border(
                    width = 1.dp,
                    // 45% accent watched — the same border an active card wears — against the 16%
                    // white the ghost button beside it already uses.
                    color = if (watched) {
                        OltreColors.accent.copy(alpha = 0.45f)
                    } else {
                        Color.White.copy(alpha = 0.16f)
                    },
                    shape = RoundedCornerShape(RADIUS),
                ),
            contentAlignment = Alignment.Center,
        ) {
            WatchBell(color = if (watched) OltreColors.accent else OltreColors.textTertiary)
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
    watch: WatchUiState?,
    stacked: Boolean,
    onToggleWatch: () -> Unit,
    watchModifier: Modifier = Modifier,
    ghost: @Composable () -> Unit,
) {
    val square: @Composable () -> Unit = {
        watch?.let {
            WatchSquare(
                watched = it != WatchUiState.Offered,
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
