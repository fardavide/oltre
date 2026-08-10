package dev.fardavide.oltre.client.tilt.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// No comma appears in a test name in this module and that is not a style choice. Kotlin/Native
// rejects one outright — *"Name contains illegal characters"* — while the JVM compiles it happily,
// so a `commonTest` in a module with an iOS target passes locally and takes out four CI jobs. It
// cost 0.2.7 a repair commit; see `.claude/rules/session-roles.md`.
class GravityTest {

    @Test
    fun `a turn reads the same at every pose a hand can hold`() {
        // **The regression test for the defect this formulation replaced.** Reading a pose as the
        // elevation of an axis above the horizon folds at exactly upright-in-portrait: leaning
        // either way from there moved the sky the same way, and past it — reading lying down — the
        // whole effect ran backwards. A rotation between two real directions cannot fold, and this
        // walks the full half-turn to say so.
        val expected = sin(6.0 * PI / 180.0)

        EVERY_POSE.forEach { elevation ->
            val from = gravityAt(Pose(elevation))
            // Six degrees further from flat, whatever "flat" meant in this pose.
            val to = gravityAt(Pose(elevation + 6.0))

            assertEquals(-expected, from.rotationTo(to).aboutX, 1e-9, "at $elevation degrees")
        }
    }

    @Test
    fun `a sideways lean barely touches the other axis`() {
        // The second half of the same defect. The two elevations were not independent — because
        // `sin²(pitch) + sin²(roll) <= 1` they were pinned to a disc — so a pure sideways lean of an
        // upright phone produced **six degrees of spurious tip for six degrees of lean**, and the
        // sky went diagonally.
        //
        // The leak here is not zero, and pretending it were would be the same kind of overclaim.
        // The shortest rotation carrying one gravity direction to another is not exactly about the
        // device's own axis when the phone is tipped, so a little lands on the tip — but it is
        // *second order* in the lean angle, where the old one was first order. Measured across every
        // pose it peaks at 1.3% of full travel against the old 100%, and it is largest at 45° where
        // it is a fortieth of the signal beside it.
        val bound = 0.03 * FULL_DEFLECTION_SINE

        EVERY_POSE.forEach { elevation ->
            val turn = gravityAt(Pose(elevation)).rotationTo(gravityAt(Pose(elevation, lean = 6.0)))

            assertTrue(abs(turn.aboutX) < bound, "a lean at $elevation leaked ${turn.aboutX} into the tip")
        }
    }

    @Test
    fun `the sideways axis fades out as the phone goes flat`() {
        // **Physics rather than a shortfall, and worth pinning so nobody 'fixes' it.** An in-plane
        // lean of a phone lying flat is a spin about the vertical, which does not move `down` at
        // all; upright, the same lean moves it the full amount. In between the gain is exactly
        // `sin²(elevation)`, which this checks against the closed form.
        //
        // The tip axis has no such term — it is full gain in every pose, which the first test in
        // this file holds — so the effect never disappears altogether, it just stops having a
        // sideways component as the phone comes to rest face up.
        val unit = sin(6.0 * PI / 180.0)

        listOf(0.0, 20.0, 30.0, 45.0, 60.0, 90.0).forEach { elevation ->
            val turn = gravityAt(Pose(elevation)).rotationTo(gravityAt(Pose(elevation, lean = 6.0)))
            val expected = sin(elevation * PI / 180.0).let { it * it }

            assertEquals(expected, turn.aboutZ / unit, 1e-9, "at $elevation degrees")
        }
    }

    @Test
    fun `dropping the right edge turns the same way whatever the pose`() {
        // Sign, pinned once. Positive `aboutZ` is the right edge going down, and `TiltMonitor` is
        // the one place that decides which way the sky answers it.
        val expected = sin(6.0 * PI / 180.0)

        // Flat on a table is excluded on purpose rather than by accident: an in-plane lean of a
        // phone lying flat is a spin about the vertical, which is the one turn gravity physically
        // cannot see. That it reads zero there is `a phone spun flat on a table` below.
        EVERY_POSE.filter { it != 0.0 && it != 180.0 }.forEach { elevation ->
            val from = gravityAt(Pose(elevation))
            val to = gravityAt(Pose(elevation, lean = 6.0))

            assertTrue(from.rotationTo(to).aboutZ > 0.0, "at $elevation degrees")
            assertTrue(from.rotationTo(to).aboutZ <= expected + 1e-9, "at $elevation degrees")
        }
    }

    @Test
    fun `a phone spun flat on a table has not moved as far as gravity knows`() {
        // Not a limitation to work around — a fact about the instrument, and the reason the sideways
        // axis fades out as the phone goes flat. Down is still down.
        val from = gravityAt(Pose(elevation = 0.0))
        val to = gravityAt(Pose(elevation = 0.0, lean = 40.0))
        val turn = from.rotationTo(to)

        assertEquals(0.0, turn.aboutX, 1e-12)
        assertEquals(0.0, turn.aboutY, 1e-12)
        assertEquals(0.0, turn.aboutZ, 1e-12)
    }

    @Test
    fun `yaw stays out of it because gravity cannot see yaw`() {
        // `aboutY` is returned rather than dropped so that a reading near zero documents the
        // instrument. A tip and a lean are both turns gravity can observe; neither should put
        // anything meaningful on the axis that points out of the glass.
        EVERY_POSE.forEach { elevation ->
            val from = gravityAt(Pose(elevation))
            val tipped = from.rotationTo(gravityAt(Pose(elevation + 6.0)))

            assertEquals(0.0, tipped.aboutY, 1e-9, "a tip at $elevation")
        }
    }

    @Test
    fun `the two platforms read the same pose the same way`() {
        // **The cross-platform trap that shipped in the first draft of this module and was caught
        // before merge.** Android reports the reaction to gravity — pointing at the sky, in metres
        // per second squared — and iOS reports gravity itself, pointing at the ground, in multiples
        // of g. Every component is negated and the scale differs by ten.
        //
        // The previous formulation needed a named conversion at the Android edge to survive that.
        // This one needs nothing at all: normalising discards the scale and `(-a) x (-b) = a x b`
        // discards the sign, so the same physical movement reads identically from either platform's
        // numbers. That is what this walks — the same two poses, twice, as each device reports them.
        EVERY_POSE.forEach { elevation ->
            val fromPose = Pose(elevation)
            val toPose = Pose(elevation + 6.0, lean = 4.0)

            val ios = gravityAt(fromPose).rotationTo(gravityAt(toPose))
            val android = gravityFrom(fromPose.androidReading).rotationTo(gravityFrom(toPose.androidReading))

            assertEquals(ios.aboutX, android.aboutX, 1e-9, "tip at $elevation")
            assertEquals(ios.aboutZ, android.aboutZ, 1e-9, "lean at $elevation")
        }
    }

    @Test
    fun `a reading with no direction in it is refused`() {
        // Free fall reports nothing in every axis and the accelerometer fallback can pass through
        // zero mid-shake. Unguarded this is a division by zero — and one NaN entering an exponential
        // average stays there for the life of the process.
        assertNull(Gravity.normalised(x = 0.0, y = 0.0, z = 0.0))
        assertNull(Gravity.normalised(x = Double.NaN, y = 1.0, z = 0.0))
        assertNull(Gravity.normalised(x = Double.POSITIVE_INFINITY, y = 0.0, z = 0.0))
    }

    @Test
    fun `scale is discarded so neither platform divides by anything`() {
        val weak = Gravity.normalised(x = 0.001, y = -0.002, z = 0.003)
        val strong = Gravity.normalised(x = 1.0, y = -2.0, z = 3.0)

        assertEquals(strong, weak)
    }

    @Test
    fun `an average of two directions comes back a direction`() {
        // The renormalisation the filter depends on: the cross product only reads as a sine if both
        // sides are unit length.
        val from = gravityAt(Pose(elevation = 20.0))
        val to = gravityAt(Pose(elevation = 100.0, lean = 30.0))
        val part = from.towards(to, 0.4)

        assertTrue(part != null)
        assertEquals(1.0, magnitudeOf(part), 1e-12)
    }

    @Test
    fun `an interpolation between exactly opposite directions is refused rather than returning nothing`() {
        // A phone that flipped completely between two samples forty milliseconds apart. The monitor
        // holds the previous direction rather than taking a zero-length vector.
        val up = Gravity(x = 0.0, y = 0.0, z = 1.0)
        val down = Gravity(x = 0.0, y = 0.0, z = -1.0)

        assertNull(up.towards(down, 0.5))
    }
}

private val FULL_DEFLECTION_SINE = sin(TiltMonitor.FULL_DEFLECTION_DEGREES * PI / 180.0)

private fun gravityAt(pose: Pose): Gravity = gravityFrom(pose.gravity)

private fun gravityFrom(reading: Triple<Double, Double, Double>): Gravity =
    checkNotNull(Gravity.normalised(reading.first, reading.second, reading.third)) {
        "$reading is not a direction"
    }

private fun magnitudeOf(gravity: Gravity?): Double {
    val g = checkNotNull(gravity)
    return kotlin.math.sqrt(g.x * g.x + g.y * g.y + g.z * g.z)
}
