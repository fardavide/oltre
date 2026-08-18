package dev.fardavide.oltre.client.design.core

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

// The Sky pass's four transitions, as tokens rather than as four numbers written down five times.
//
// **This object is the one place the "effectively no animation" rule is spent by a transition**, so
// it is worth saying exactly what survives of that rule and what does not. What is gone: the claim that nothing
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
// **There is a second place the rule is spent, and since 0.4.2 it is not this one.** The starfield
// used to be the clean case — a pure function of the scroll offset with no duration in it — and the
// tilt parallax took that away: `TiltMonitor.SMOOTHING` is a time constant, so a lean arrives over
// about a tenth of a second rather than on one frame. It is spent differently enough to be worth the
// distinction this file draws — a time constant filtering an input is not a duration something plays
// *for* — but "the one place" would be false without the qualifier above.
//
// **0.4.2 owed a larger admission here and 0.4.3 paid it off.** There was a second constant,
// `RECENTRING`, a four-second average that chased the pose — and it meant a lean that had already
// finished went on settling back to level for about ten seconds afterwards, which is movement with
// the device lying still. It existed only to stop a held pose pinning a *clamped* travel against its
// stop; with the clamp gone it had nothing to do and was deleted. Put the phone down and the sky now
// stops.
//
// The surviving constant is deliberately not a token here. `:client:tilt:domain` is pure Kotlin with
// no Compose in it, and reaching this object would mean giving a domain module a Compose dependency
// to fetch one number it is the only reader of. The dependency direction decides it, not the
// argument. See `Starfield.kt`, which carries the honest accounting, and `decisions.md` at 0.4.3.
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

    // **How long the app takes to answer a tap** — one number for the two things a tap can change.
    // A destination arriving and the one it replaced leaving is one; a control turning over to say
    // it is now the selected one is the other. They are the same duration because they are the same
    // event from the player's side, and giving them two numbers would only be a way for them to
    // drift apart.
    //
    // Shorter than any of the fills above, and deliberately: a fill is a value settling into place
    // and wants to be watched, where this is the app getting out of the way of something the player
    // has already decided. Past about 250ms an answer stops reading as responsive and starts reading
    // as a wait.
    //
    // **It spends the animation rule the same way the Sky pass did and no further.** It runs once,
    // on a tap, and then holds — there is no loop in it and nothing about it says the simulation is
    // running. What it replaces is a hard cut, which was not stillness so much as the absence of a
    // transition nobody had written yet.
    const val SWITCH_MILLIS: Int = 210

    // How far a destination travels on its way in or out, as a fraction of its own width. A whole
    // screen-width slide is a carousel and claims the five tabs are laid out in a line the player
    // can move along; an eighth is a nudge in the direction of travel, which is all the direction
    // has to say.
    const val SWITCH_TRAVEL: Float = 0.125f
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

// A colour that turns rather than snaps: the fill under a selected pill, the border on a card that
// has started building, the accent a watched square takes. It lives here beside the fill above for
// the same reason that one does — what every layer of the app shares is this module's — and it is
// the token applied rather than a new token.
//
// **It is safe for a screenshot baseline, and that is worth stating rather than discovering.**
// `animateColorAsState` begins *at* its target on first composition and only animates a subsequent
// change, so a frame captured on arrival is the frame that was captured before this existed. What
// moves is only what a player's own tap moved.
//
// **Use it for a colour a tap changes, never for one the simulation changes.** A stock crossing a
// threshold and reddening a chip is the game reporting a fact, and a fact that fades in is a fact
// the player is invited to watch happen — which is the thing this app may not draw. The rule is the
// same one `rememberOneShotFill` states from the other direction.
@Composable
fun settlingColor(target: Color): Color {
    val color by animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = OltreMotion.SWITCH_MILLIS, easing = OltreMotion.Settle),
    )
    return color
}
