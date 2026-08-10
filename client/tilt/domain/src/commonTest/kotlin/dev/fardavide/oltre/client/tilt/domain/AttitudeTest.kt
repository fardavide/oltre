package dev.fardavide.oltre.client.tilt.domain

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// No comma appears in a test name in this file or in `TiltMonitorTest`, and that is not a style
// choice. Kotlin/Native rejects one outright — *"Name contains illegal characters"* — while the JVM
// compiles it happily, so a `commonTest` in a module with an iOS target passes locally and takes
// out four CI jobs. It cost 0.2.7 a repair commit; see `.claude/rules/session-roles.md`.
class AttitudeTest {

    @Test
    fun `a phone flat on a table is level on both axes`() {
        val flat = Attitude.fromGravity(x = 0.0, y = 0.0, z = 9.81)

        assertEquals(0.0, flat.pitch, TOLERANCE)
        assertEquals(0.0, flat.roll, TOLERANCE)
    }

    @Test
    fun `a phone held upright in portrait reads minus ninety degrees of pitch and no roll`() {
        val upright = Attitude.fromGravity(x = 0.0, y = -9.81, z = 0.0)

        assertEquals(-90.0, upright.pitch, TOLERANCE)
        assertEquals(0.0, upright.roll, TOLERANCE)
    }

    @Test
    fun `dropping the right edge is positive roll`() {
        // Rolled a quarter turn from upright so the device x axis points at the ground.
        val onItsSide = Attitude.fromGravity(x = 9.81, y = 0.0, z = 0.0)

        assertEquals(90.0, onItsSide.roll, TOLERANCE)
    }

    @Test
    fun `the two platforms read the same pose the same way`() {
        // **The one real cross-platform trap in this module.** The two devices report opposite
        // vectors for the same physical pose — Android the reaction to gravity, pointing at the
        // sky, iOS gravity itself, pointing at the ground — and on top of that Android is in metres
        // per second squared while iOS is in multiples of g. Every row below is one phone held one
        // way, written down twice as the two platforms would actually report it, and the two
        // readings have to agree or the sky leans one way on an iPhone and the other way on a
        // Pixel. That is the class of bug nobody finds without owning both.
        val poses = listOf(
            // Flat on a table, screen up.
            Triple(0.0, 0.0, 9.81) to Triple(0.0, 0.0, -1.0),
            // Upright in portrait, facing the player.
            Triple(0.0, 9.81, 0.0) to Triple(0.0, -1.0, 0.0),
            // Right edge dropped.
            Triple(-9.81, 0.0, 0.0) to Triple(1.0, 0.0, 0.0),
            // Somewhere ordinary — held at an angle, leaning slightly.
            Triple(-3.2, 8.1, 4.6) to Triple(3.2 / 9.81, -8.1 / 9.81, -4.6 / 9.81),
        )

        poses.forEach { (android, ios) ->
            val fromAndroid = Attitude.fromReactionToGravity(android.first, android.second, android.third)
            val fromIos = Attitude.fromGravity(ios.first, ios.second, ios.third)

            assertEquals(fromIos.pitch, fromAndroid.pitch, TOLERANCE, "pitch at $android / $ios")
            assertEquals(fromIos.roll, fromAndroid.roll, TOLERANCE, "roll at $android / $ios")
        }
    }

    @Test
    fun `reading the sky vector instead of the ground vector flips both angles`() {
        // What the named pair above is protecting, stated on its own. If the Android edge ever
        // reaches for `fromGravity` — the shorter name — this is what it gets.
        val ground = Attitude.fromGravity(x = 2.0, y = -7.0, z = 3.0)
        val sky = Attitude.fromGravity(x = -2.0, y = 7.0, z = -3.0)

        assertEquals(-ground.pitch, sky.pitch, TOLERANCE)
        assertEquals(-ground.roll, sky.roll, TOLERANCE)
    }

    @Test
    fun `scale does not matter so neither platform divides by anything`() {
        val strong = Attitude.fromGravity(x = 1.0, y = -2.0, z = 3.0)
        val weak = Attitude.fromGravity(x = 0.001, y = -0.002, z = 0.003)

        assertEquals(strong.pitch, weak.pitch, TOLERANCE)
        assertEquals(strong.roll, weak.roll, TOLERANCE)
    }

    @Test
    fun `both angles stay inside plus or minus ninety degrees whatever the pose`() {
        // An elevation above the horizon cannot exceed a right angle, which is the property that
        // makes these two safe to subtract. An Euler pair wraps at 180 and a difference taken
        // across the wrap reads as a full-deflection lurch.
        val poses = listOf(
            Triple(0.0, 0.0, -1.0),
            Triple(-1.0, 0.0, 0.0),
            Triple(0.0, 1.0, 0.0),
            Triple(0.6, -0.6, 0.53),
            Triple(-0.7, -0.7, -0.14),
        )

        poses.forEach { (x, y, z) ->
            val attitude = Attitude.fromGravity(x, y, z)
            assertTrue(attitude.pitch in -90.0..90.0, "pitch was ${attitude.pitch} at ($x $y $z)")
            assertTrue(attitude.roll in -90.0..90.0, "roll was ${attitude.roll} at ($x $y $z)")
        }
    }

    @Test
    fun `a phone held flat still has a defined roll rather than noise`() {
        // The reason these are elevations rather than Euler angles. A flat phone has no meaningful
        // rotation about the vertical and a formula that insists on naming one returns whatever the
        // last digit of the sensor happened to say. Two nearly identical flat poses have to give
        // two nearly identical answers.
        val flat = Attitude.fromGravity(x = 0.0005, y = -0.0005, z = 1.0)
        val alsoFlat = Attitude.fromGravity(x = -0.0005, y = 0.0005, z = 1.0)

        assertTrue(abs(flat.roll - alsoFlat.roll) < 0.2, "roll ${flat.roll} vs ${alsoFlat.roll}")
        assertTrue(abs(flat.pitch - alsoFlat.pitch) < 0.2, "pitch ${flat.pitch} vs ${alsoFlat.pitch}")
    }
}

private const val TOLERANCE = 1e-9
