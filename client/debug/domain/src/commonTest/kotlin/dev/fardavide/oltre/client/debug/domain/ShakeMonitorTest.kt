package dev.fardavide.oltre.client.debug.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ShakeMonitorTest {

    @Test
    fun `a phone at rest never shakes`() {
        // Gravity alone reads about 1 g, which is why the threshold is expressed in multiples of it
        // rather than in m/s squared — a resting phone is 1 and not 0.
        var monitor = ShakeMonitor()
        repeat(100) { tick -> monitor = monitor.sample(1.0, at = EPOCH + (tick * 20).milliseconds) }

        assertFalse(monitor.shaken)
    }

    @Test
    fun `three jolts inside the window are a shake`() {
        val monitor = shakeWith(gaps = listOf(200.milliseconds, 200.milliseconds))

        assertTrue(monitor.shaken)
    }

    @Test
    fun `two jolts are not enough`() {
        var monitor = ShakeMonitor().sample(3.0, at = EPOCH)
        monitor = monitor.sample(3.0, at = EPOCH + 200.milliseconds)

        assertFalse(monitor.shaken)
    }

    @Test
    fun `one sharp movement sampled many times is not a shake`() {
        // The failure this guards against is the one that makes a shake detector unusable: an
        // accelerometer at 50 Hz sees a single flick as several consecutive samples over the
        // threshold, so counting samples rather than jolts fires on putting the phone down.
        var monitor = ShakeMonitor()
        repeat(10) { tick -> monitor = monitor.sample(4.0, at = EPOCH + (tick * 20).milliseconds) }

        assertFalse(monitor.shaken)
    }

    @Test
    fun `jolts spread beyond the window are not a shake`() {
        val monitor = shakeWith(gaps = listOf(1.seconds, 1.seconds))

        assertFalse(monitor.shaken)
    }

    @Test
    fun `an old jolt expires even while the phone is still`() {
        // The window is pruned on every sample rather than only on the ones above the threshold —
        // otherwise a phone jolted twice, pocketed for a minute and jolted once reads as a shake.
        var monitor = ShakeMonitor().sample(3.0, at = EPOCH)
        monitor = monitor.sample(3.0, at = EPOCH + 200.milliseconds)
        monitor = monitor.sample(1.0, at = EPOCH + 1.seconds)
        monitor = monitor.sample(3.0, at = EPOCH + 1.seconds + 200.milliseconds)

        assertFalse(monitor.shaken)
    }

    @Test
    fun `a shake below the threshold is ignored however long it goes on`() {
        var monitor = ShakeMonitor()
        repeat(30) { tick ->
            monitor = monitor.sample(ShakeMonitor.THRESHOLD_G - 0.1, at = EPOCH + (tick * 150).milliseconds)
        }

        assertFalse(monitor.shaken)
    }

    @Test
    fun `resetting clears the gesture so the menu does not reopen on the next jolt`() {
        val monitor = shakeWith(gaps = listOf(200.milliseconds, 200.milliseconds))

        assertTrue(monitor.shaken)
        assertFalse(monitor.reset().shaken)
        assertFalse(monitor.reset().sample(3.0, at = EPOCH + 1.seconds).shaken)
    }

    @Test
    fun `the three constants are a coherent gesture rather than three separate guesses`() {
        // Not taste, arithmetic. If the gap between jolts were as long as the window, the required
        // number of them could never fit inside it and the gesture would be unperformable at any
        // vigour — so the relationship below is what makes the other tests possible at all.
        assertTrue(
            ShakeMonitor.JOLT_GAP * (ShakeMonitor.JOLTS_REQUIRED - 1) < ShakeMonitor.WINDOW,
            "${ShakeMonitor.JOLTS_REQUIRED} jolts ${ShakeMonitor.JOLT_GAP} apart do not fit in ${ShakeMonitor.WINDOW}",
        )
        // Above a brisk pick-up (~1.4 g) so the menu does not open in a pocket, and below the ~3 g
        // of a deliberate shake so that it opens when asked.
        assertTrue(ShakeMonitor.THRESHOLD_G > 1.4, "was ${ShakeMonitor.THRESHOLD_G}")
        assertTrue(ShakeMonitor.THRESHOLD_G < 3.0, "was ${ShakeMonitor.THRESHOLD_G}")
    }

    @Test
    fun `sampling is a value so the same monitor gives the same answer twice`() {
        val monitor = ShakeMonitor().sample(3.0, at = EPOCH)

        assertTrue(monitor.sample(3.0, at = EPOCH + 200.milliseconds) == monitor.sample(3.0, at = EPOCH + 200.milliseconds))
    }
}

// Jolts hard enough to count, separated by the given gaps. The first is at EPOCH, so `gaps` is one
// shorter than the number of jolts.
private fun shakeWith(gaps: List<Duration>): ShakeMonitor {
    var at: Instant = EPOCH
    var monitor = ShakeMonitor().sample(4.0, at = at)
    gaps.forEach { gap ->
        at += gap
        monitor = monitor.sample(4.0, at = at)
    }
    return monitor
}
