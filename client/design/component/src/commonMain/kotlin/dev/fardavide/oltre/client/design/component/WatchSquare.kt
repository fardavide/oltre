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
import androidx.compose.foundation.layout.Box
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
import dev.fardavide.oltre.client.design.icon.WatchBeacon

// Whether a row offers a watch, and whether it is the one holding it. Null on the row means no
// square at all — an affordable row has nothing to wait for, a running one is already the thing
// happening, a locked one has no price yet, and a row whose binding resource has no net income has
// no instant to name. Every one of those is *the absence of a control*, not a disabled one: nothing
// in this app is greyed out.
sealed interface WatchUiState {

    // A square, unlit. There is an instant to book here and the player has not booked it.
    data object Offered : WatchUiState

    // The one row in the whole game holding the watch, and the instant it named. The line is
    // carried here rather than on the row because the two are one fact — a lit square with no
    // instant beside it would be a state the row could get into and could not explain.
    data class Booked(val affordableAt: String) : WatchUiState
}

// The one new affordance the watch slice adds, and deliberately the only one: a 29dp square beside
// the ghost time that books an alert for the instant the row already prints.
//
// **The card's border is not touched, in either state.** An accent border means something is in
// flight and nothing else; a watched row is not doing anything, it is booked. Accent border is
// something happening, accent text is something booked — which is why the lit square and the
// `→ affordable` line are the whole of what changes.
//
// **44dp of touch vertically and 29dp horizontally, and the asymmetry is measured rather than
// lazy.** The design budgets 29dp off the name column and no more; a 44dp-wide target spends 15dp
// the text does not have, and at 393dp it is enough to ellipsise "metal · crystal output" on the
// Research row next door. Height is free — the card is taller than 44dp whatever this does — so the
// finger gets the platform's minimum in the axis that costs nothing. Compose cannot buy the other
// axis for free either: a child placed outside its parent's bounds does not reliably receive touch,
// which is why Material's own `minimumInteractiveComponentSize` expands the layout rather than
// overflowing it. Worth revisiting if the row ever has width to spare.
@Composable
fun WatchSquare(watched: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
            .size(width = SQUARE, height = HIT_TARGET)
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
            WatchBeacon(
                color = if (watched) OltreColors.accent else OltreColors.textTertiary,
                lit = watched,
            )
        }
    }
}

private val HIT_TARGET = 44.dp
private val SQUARE = 29.dp
private val RADIUS = 9.dp
