package dev.fardavide.oltre.client.galaxy.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatorMutex
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlin.math.pow

// **Where the eye is, held where a finger and a flight can both move it.** The view is one value
// that a pinch writes on every pointer event and a flight writes on every frame, and it is read
// back on the very next one — so it lives in a holder the gestures reach directly, the way a
// `ScrollState` does, rather than being hoisted through a callback that only lands a frame later.
@Stable
class SkyViewState(initial: SkyView) {

    var view: SkyView by mutableStateOf(initial)

    // Where a flight in progress is going, and the view itself when none is: what a notch of the
    // wheel zooms from, so a burst of notches compounds towards one target rather than each one
    // restarting from wherever the last one's easing had got to.
    var heading: SkyView by mutableStateOf(initial)
        private set

    private val flights = MutatorMutex()

    // A flight: 420ms from here to there, easing out, the zoom in log space and the centre in a
    // straight line. A new flight replaces one in progress; a gesture interrupts one — the finger
    // moved the view, so the flight would be flying from somewhere it no longer is. A wheel's notch
    // is the same flight over 120ms: a step of zoom with no in-between read as a jolt.
    suspend fun fly(target: SkyView, millis: Int = FLIGHT_MILLIS) = flights.mutate {
        val from = view
        var expected = from
        heading = target
        try {
            Animatable(0f).animateTo(1f, tween(millis, easing = EaseOutCubic)) {
                if (view != expected) throw CancellationException("a gesture moved the view mid-flight")
                expected = from.towards(target, value)
                view = expected
            }
            view = target
        } finally {
            heading = view
        }
    }

    companion object {
        const val FLIGHT_MILLIS: Int = 420

        // The wheel: how long a notch takes to land, and what a pixel of it is worth in zoom — a
        // notch of 120 on the desktop is a fifth more.
        const val WHEEL_MILLIS: Int = 120
        const val WHEEL_STEP: Float = 0.0015f

        // The design's one-shot: `1 − (1 − t)³`.
        val EaseOutCubic: Easing = Easing { t -> 1f - (1f - t).pow(3) }
    }
}
