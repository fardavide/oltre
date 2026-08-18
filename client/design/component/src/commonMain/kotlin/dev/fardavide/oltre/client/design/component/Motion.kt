package dev.fardavide.oltre.client.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.OltreMotion

// The two transitions that belong to a row rather than to one screen, plus the press every tappable
// card takes. Each is one-shot by construction — see `OltreMotion` for why that word is doing the
// whole of the work that the old "nothing animates" rule used to do, and `rememberOneShotFill`
// there for the third transition, which every layer of the app shares.

// Where the band on a row that finished while the app was closed has got to, and whether the row
// may yet show the level it arrived at.
@Immutable
data class CompletionSweep(
    // 0 to 1 as the band crosses the card; null when there is no band to draw — which is every row
    // on every screen except the one that finished, and that one too once the band has left.
    val band: Float?,
    // False only while the band is still short of the swap point. It is what makes a level change
    // *behind* the band rather than in front of it, so the eye is drawn by the light and finds the
    // new number already there.
    val settled: Boolean,
)

private val SETTLED = CompletionSweep(band = null, settled = true)

// The band's own width, and the reason the travel below is `width + BAND` rather than `width`: the
// band starts wholly off the leading edge and ends wholly off the trailing one, so neither end of
// the crossing shows a half-drawn gradient parked against the card's border.
private val BAND_WIDTH = 150.dp

// **Latched at the row's first composition, and that is the whole of what makes this safe.** The
// announcement it draws is withdrawn by the shell as soon as the screen showing it has been
// composed — otherwise every return to the tab would replay a sweep about a launch that already
// happened. Read live, that withdrawal would land mid-crossing: the band would vanish at whatever
// position it had reached and the level badge would snap in the same frame, which is the exact
// inverse of the intent. Latched, the row plays the whole 1,170ms it was promised and the shell is
// free to forget about it immediately.
@Composable
fun rememberCompletionSweep(play: Boolean): CompletionSweep {
    val playing = remember { play }
    val total = (OltreMotion.SWEEP_DELAY_MILLIS + OltreMotion.SWEEP_MILLIS).toFloat()
    val elapsed = remember { Animatable(0f) }
    // Keyed on nothing, so nothing can cancel it. Linear, and the one easing in the pass that is:
    // the fills are values settling into place and want a curve, where this is a light crossing a
    // surface at a constant speed. Eased, it would read as a swipe rather than as a sweep.
    LaunchedEffect(Unit) {
        if (playing) {
            elapsed.animateTo(total, tween(durationMillis = total.toInt(), easing = LinearEasing))
        }
    }
    if (!playing) return SETTLED
    val millis = elapsed.value
    return CompletionSweep(
        band = ((millis - OltreMotion.SWEEP_DELAY_MILLIS) / OltreMotion.SWEEP_MILLIS)
            .takeIf { it > 0f && it < 1f },
        settled = millis >= OltreMotion.SWEEP_LEVEL_SWAP_MILLIS,
    )
}

// Clipped to the card's own shape, so the band's leading edge is cut by the rounded corner exactly
// as the card's fill is. The clip is conditional because it costs a graphics layer, and outside the
// 750ms this is drawing nothing at all.
fun Modifier.completionSweep(sweep: CompletionSweep): Modifier {
    val fraction = sweep.band ?: return this
    return clip(oltreCardShape).drawWithContent {
        drawContent()
        val band = BAND_WIDTH.toPx()
        val x = -band + fraction * (size.width + band)
        drawRect(
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.5f to OltreColors.accent.copy(alpha = 0.16f),
                    1f to Color.Transparent,
                ),
                startX = x,
                endX = x + band,
            ),
            topLeft = Offset(x = x, y = 0f),
            size = Size(width = band, height = size.height),
        )
    }
}

// A card or a button taking a press, under whatever indication the theme already supplies — the
// Material3 ripple here, unchanged and un-replaced.
//
// **Place this ahead of the fill and the border in the chain**: a `graphicsLayer` transforms what is
// drawn inside it, and a `background` declared before it is drawn outside, which would scale the text
// on a card whose fill stayed put.
//
// **`shape` is required, and that is the fix rather than a convenience.** Until 0.13.1 this modifier
// took a click and nothing else, so the ripple was a circle clipped to the node's *rectangle* — which
// is to say clipped to nothing a player can see, because every tappable surface in this app is
// rounded. It spilled square out of all of them. Compose has one answer and it is an ordering: an
// indication is clipped by whatever layer is declared *before* it, so the clip has to sit between the
// scale and the click. Making the shape a parameter with no default is what stops the next caller
// from being the fifteenth to forget — there is no shape this could sensibly assume, and a
// `RectangleShape` default would reintroduce exactly the bug under a name that sounds deliberate.
//
// One `graphicsLayer` carries both the scale and the clip rather than a `clip()` after a
// `graphicsLayer`: they would otherwise be two layers doing one job, and in this order the clip is
// applied in the layer's own coordinates, so the corner shrinks with the card instead of staying put
// while the card moves away from it.
@Composable
fun Modifier.pressable(shape: Shape, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction)
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.shape = shape
        clip = true
    }.clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
}

// The press, when the tap area is deliberately bigger than the thing it presses — a 30dp button that
// claims the 44dp iOS minimum, or a 29dp square that claims the height of the row it sits on.
//
// **The click and the indication are on two different nodes, and that is the whole of what this
// buys.** Put them on one and the ripple fills the claimed area: a smear beside the button rather
// than the button taking the press, which is what `WatchSquare` has said about its own 29x44 since
// the watch slice. What it said, three call sites then reproduced by hand — the probe's Dispatch, the
// caption's Dispatch and the square itself — with the spring copied out each time. This is that
// chain, written once.
//
// The face is measured by its content and clipped to `shape`, so `faceModifier` carries the fill and
// the border and nothing has to agree with anything twice.
@Composable
fun PressableFace(
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    faceModifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction)
    Box(
        contentAlignment = Alignment.Center,
        // `indication = null`: this node is the target, not the drawing. It is also where the
        // testTag belongs, so a robot presses what a finger presses.
        modifier = modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.shape = shape
                    clip = true
                }
                // Ahead of `faceModifier`, which is where the fill is: an indication draws its
                // content first and its ripple over the top, so declared here the ripple lands on
                // the fill rather than under it. Behind the clip above, which is what keeps it
                // inside the face's own corners — `PressableBehaviourTest` reads the pixel that
                // proves it, and read the comment there before trusting this one.
                .indication(interaction, LocalIndication.current)
                .then(faceModifier),
            content = { content() },
        )
    }
}

// A spring rather than a duration, and the only one in the pass. A press has no length — it lasts as
// long as the finger does — so the release has to be able to interrupt the press mid-travel and hand
// back a continuous motion, which is the one thing a tween cannot do.
@Composable
private fun pressScale(interaction: MutableInteractionSource): Float {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) OltreMotion.PRESS_SCALE else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
    )
    return scale
}
