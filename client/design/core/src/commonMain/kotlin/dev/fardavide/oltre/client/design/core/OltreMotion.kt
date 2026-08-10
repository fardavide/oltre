package dev.fardavide.oltre.client.design.core

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

// The Sky pass's four transitions, as tokens rather than as four numbers written down five times.
//
// **This object is the one place the "effectively no animation" rule is spent**, so it is worth
// saying exactly what survives of that rule and what does not. What is gone: the claim that nothing
// moves. What is intact, and was always the reason: nothing here loops, nothing repeats, and nothing
// implies a live clock. Every duration below belongs to a transition that runs **once** when the
// thing it describes enters composition and then holds its value forever — a colony closed for two
// days plays exactly one of each on the way in and is then as still as it was before.
//
// That distinction is load-bearing rather than pedantic. A game whose whole premise is that it
// progresses while closed must never draw anything that a player could read as "it is happening
// now"; a spinner or a pulse would be a lie about the simulation. A value settling into place on
// arrival is the opposite — it is the app showing what changed while you were gone.
//
// The starfield's parallax is deliberately *not* here: it has no duration because it is not a
// transition. It is a function of the scroll offset, exactly as the list's own position is.
object OltreMotion {

    // The stock roll on the rail, the dial and bar fills, and the energy meter's fill. One number
    // for all four, so nothing arrives out of step with anything else on the way in.
    const val FILL_MILLIS: Int = 900

    // The band that crosses a row which finished while the app was closed, and how long it waits
    // before it starts. The delay is what keeps it from competing with the fill above: the numbers
    // settle first, and only then does the row that changed say so.
    const val SWEEP_MILLIS: Int = 750
    const val SWEEP_DELAY_MILLIS: Int = 420

    // 0.62 of the prototype's 1,500ms master clock, which is 68% of the way through the sweep rather
    // than the "midpoint" the handoff prose calls it. The prototype's number wins because it is the
    // one that was reviewed: the band has passed the badge by then, so the level changes behind it
    // rather than in front of it.
    const val SWEEP_LEVEL_SWAP_MILLIS: Int = SWEEP_DELAY_MILLIS + 510

    // Material's standard "enters decisively, settles gently", which is what the handoff names for
    // every one of the fills. Written out rather than taken from `FastOutSlowInEasing` so that a
    // Compose animation-API change cannot silently redraw the pass.
    val Settle: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    // How far a card shrinks under a finger. Small enough that it reads as the card taking the
    // press rather than as the card moving.
    const val PRESS_SCALE: Float = 0.985f
}

// A value arriving: 0 to 1 over 900ms when the caller enters composition, and 1 forever after.
//
// It lives beside the tokens rather than with the components because it is the token applied — the
// rail, the dial, the energy meter and the probe's bar all fill on this one curve, and three of
// those are in three different modules. `OltreTheme` and `oltreMono` are already here on the same
// argument: what every layer of the app shares is this module's, whether it is a colour or a curve.
//
// **Multiply a target by it rather than animating the target itself.** That distinction is the
// reason this returns a fraction instead of taking a value: the numbers behind these instruments
// tick every second, and an `animateFloatAsState` pointed at a ticking target is an animation that
// never stops — exactly the thing the app may not draw. Multiplied, the fill runs once and the
// instrument then tracks its number instantly, which is what "then hold" means.
@Composable
fun rememberOneShotFill(): Float {
    val fill = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        fill.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = OltreMotion.FILL_MILLIS, easing = OltreMotion.Settle),
        )
    }
    return fill.value
}
