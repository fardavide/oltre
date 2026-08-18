package dev.fardavide.oltre.client.dispatch.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import dev.fardavide.oltre.client.dispatch.domain.StepperHold
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// **A tap that steps once, and a finger left there that keeps stepping.** Davide, 2026-08-17, on a
// dispatch sheet that opened at 55 hulls above a note saying three would do: *"Going from 55 to 3 is
// a lot of taps 😅"*. The suggested manifest makes that walk short in the common case; this is what
// stops the uncommon one being fifty taps.
//
// **A file of its own, and that is the coverage gate's doing rather than tidiness** (Davide's call,
// 2026-08-17). A screenshot test renders a frame; it cannot press one. So every line below is
// reachable by a behaviour test and by no screenshot, forever — which is the mirror of the reason
// the screenshot pass already drops mappers, and the root build script names this file for that
// filter. Keeping it in one file is what makes the exclusion narrow enough to be honest: **nothing
// here draws**, so nothing a baseline could catch can hide in it.
//
// **The pace is `StepperHold`'s, not this file's.** How long a thumb rests before the control starts
// running, and how the ramp gets there, is arithmetic — and arithmetic in a draw scope is a claim no
// test can check. It lives in `:client:dispatch:domain`, where the first test written against it
// found the ramp a fifth short of the trip it was chosen for.
//
// Three things this has to get right, each of which is a defect if it does not, and each of which
// `DispatchSheetBehaviourTest` asserts:
//
// - **The step reads the count that is on screen now**, not the one that was there when the finger
//   landed. `rememberUpdatedState` is what hands the loop the freshest lambda; without it a hold
//   asks for the same number fifty times. If no frame has landed between two ticks the loop asks for
//   a number it already asked for, which loses a step and can never overshoot — the right way round
//   for a control with a bound.
// - **A hold does not add a step when the finger comes off.** `clickable` fires on the up whatever
//   the press was, so the repeat says out loud that it already stepped and the tap stands down.
// - **A dead control stays dead.** `enabled` is read inside the loop rather than captured, so the
//   repeat stops itself at the bound rather than leaning on the mapper's clamp to hide it.
@Composable
internal fun Modifier.steppingWhileHeld(enabled: Boolean, onStep: () -> Unit): Modifier {
    val step by rememberUpdatedState(onStep)
    val live by rememberUpdatedState(enabled)
    // Written by the repeat and read by the tap, which is the whole of the off-by-one guard. Plain
    // Compose state costs nothing here despite being written every tick: a snapshot write of a value
    // that is already there is not a change, so this recomposes at most twice in a gesture.
    var repeated by remember { mutableStateOf(false) }
    return this
        .pointerInput(Unit) {
            coroutineScope {
                awaitEachGesture {
                    // Unconsumed is not required: `clickable` sits inside this in the chain and sees
                    // the down first, and a press it has taken an interest in is exactly the press
                    // this needs to hear about.
                    awaitFirstDown(requireUnconsumed = false)
                    repeated = false
                    // **Wait, step, wait, step — and no arithmetic of its own.** The first wait is
                    // the rest that keeps a tap a tap, which is why the flag is only raised once a
                    // step has actually fired.
                    val repeat = launch {
                        for (wait in StepperHold.waits()) {
                            delay(wait)
                            if (!live) break
                            repeated = true
                            step()
                        }
                    }
                    waitForUpOrCancellation()
                    repeat.cancel()
                }
            }
        }
        .clickable(enabled = enabled) { if (!repeated) step() }
}
