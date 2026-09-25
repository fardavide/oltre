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

    private val flights = MutatorMutex()

    // A flight: 420ms from here to there, easing out, the zoom in log space and the centre in a
    // straight line. A new flight replaces one in progress; a gesture interrupts one — the finger
    // moved the view, so the flight would be flying from somewhere it no longer is.
    suspend fun fly(target: SkyView) = flights.mutate {
        val from = view
        var expected = from
        Animatable(0f).animateTo(1f, tween(FLIGHT_MILLIS, easing = EaseOutCubic)) {
            if (view != expected) throw CancellationException("a gesture moved the view mid-flight")
            expected = from.towards(target, value)
            view = expected
        }
        view = target
    }

    companion object {
        const val FLIGHT_MILLIS: Int = 420

        // The design's one-shot: `1 − (1 − t)³`.
        val EaseOutCubic: Easing = Easing { t -> 1f - (1f - t).pow(3) }
    }
}
