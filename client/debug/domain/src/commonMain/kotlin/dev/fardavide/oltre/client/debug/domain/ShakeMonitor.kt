package dev.fardavide.oltre.client.debug.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

// Deciding whether a stream of accelerometer samples is a shake. It lives here, in a module with no
// platform in it, because otherwise the same judgement gets written twice — once against Android's
// `SensorManager` and once against iOS's `CMMotionManager` — and the two drift until the gesture
// means something different on each phone. The platforms supply samples; this says what they add up
// to, and it is testable without a device precisely because it is only arithmetic.
//
// A shake is JOLTS_REQUIRED separate jolts inside WINDOW. "Separate" is doing real work: an
// accelerometer sampling at 50 Hz sees a single sharp movement as several consecutive samples over
// the threshold, so counting samples rather than jolts would fire on one flick of the wrist. JOLT_GAP
// is what makes the gesture mean *back and forth* rather than *hard*.
data class ShakeMonitor(val jolts: List<Instant> = emptyList()) {

    // True the moment the gesture completes. The caller acts on it and calls `reset()`; leaving that
    // to the caller rather than self-clearing is what keeps this a value — the same monitor sampled
    // with the same arguments always gives the same answer.
    val shaken: Boolean get() = jolts.size >= JOLTS_REQUIRED

    // `gForce` is total acceleration in multiples of standard gravity, gravity included — which is
    // what both platforms hand out, and which means a phone lying on a table reads ~1.0 rather than
    // ~0.0. Taking it pre-divided rather than in m/s² keeps the threshold a number a human can
    // reason about and keeps this file free of either platform's units.
    fun sample(gForce: Double, at: Instant): ShakeMonitor {
        // Expired jolts go first, and on every sample rather than only on the ones above the
        // threshold: a phone that is jolted twice, put down for a minute and then jolted once must
        // not read as a shake, and it would if the window were only pruned while shaking.
        val recent = jolts.filter { at - it <= WINDOW }
        if (gForce < THRESHOLD_G) return ShakeMonitor(recent)
        val last = recent.lastOrNull()
        if (last != null && at - last < JOLT_GAP) return ShakeMonitor(recent)
        return ShakeMonitor(recent + at)
    }

    fun reset(): ShakeMonitor = ShakeMonitor()

    companion object {

        // Deliberately well above the ~1.4 g of picking a phone up briskly and below the ~3 g of a
        // deliberate shake, so the menu does not appear in a pocket. Tuned by arithmetic rather than
        // by a device — nobody has shaken a phone at this yet, so treat all three as starting points
        // and expect the first real session to move them.
        const val THRESHOLD_G: Double = 2.2
        const val JOLTS_REQUIRED: Int = 3
        val WINDOW: Duration = 900.milliseconds
        val JOLT_GAP: Duration = 120.milliseconds
    }
}
