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
    fun `the tip bearing is the elevation the phone is actually held at`() {
        EVERY_POSE.forEach { elevation ->
            assertEquals(elevation.radians(), gravityAt(Pose(elevation)).tip.radians, 1e-9, "at $elevation degrees")
        }
    }

    @Test
    fun `the tip bearing keeps climbing past upright rather than folding back at it`() {
        // **The regression test for the defect the first version of this module shipped with**, kept
        // word for word through a second formulation because it is the thing most easily broken
        // again. Reading a pose as `asin` of a single component folds at exactly upright-in-portrait:
        // leaning either way from there moved the sky the same way, and past it — reading lying down
        // — the whole effect ran backwards.
        //
        // `atan2` of a *pair* of components is the fix and is not the same function wearing a hat: it
        // knows which quadrant it is in, so it walks the full circle instead of turning round at the
        // top of a half one. That is also what makes 360 degrees of travel available at all — a
        // reading that folds has nowhere past the fold to report.
        val walk = (0..350 step 10).map { gravityAt(Pose(it.toDouble())).tip.radians }

        walk.zipWithNext().forEach { (before, after) ->
            assertTrue(
                turnedFrom(before, to = after) > 0.0,
                "the tip went backwards between $before and $after",
            )
        }
    }

    @Test
    fun `the lean bearing is the in-plane roll whatever the elevation`() {
        // The property the sideways axis is built on, and the one the cross product could not give:
        // the angle itself is the same number at every pose, where the *turn* the cross product
        // reports carries a `sin²(elevation)` factor. See the two tests below for what became of it.
        EVERY_POSE.filter { it != 0.0 && it != 180.0 }.forEach { elevation ->
            assertEquals(
                17.0.radians(),
                gravityAt(Pose(elevation, lean = 17.0)).lean.radians,
                1e-9,
                "at $elevation degrees",
            )
        }
    }

    @Test
    fun `the lean bearing goes all the way round`() {
        val walk = (0..350 step 10).map { gravityAt(Pose(elevation = 60.0, lean = it.toDouble())).lean.radians }

        walk.zipWithNext().forEach { (before, after) ->
            assertTrue(
                turnedFrom(before, to = after) > 0.0,
                "the lean went backwards between $before and $after",
            )
        }
    }

    @Test
    fun `how much of down lies in the sideways plane is the sine of the elevation`() {
        // **Physics rather than a shortfall, and worth pinning so nobody 'fixes' it.** An in-plane
        // lean of a phone lying flat is a spin about the vertical, which does not move `down` at all.
        //
        // What changed at 0.4.3 is where this number is allowed to act. It used to arrive *squared*,
        // as a gain on the reported turn, so the sky answered a sideways lean at a quarter strength
        // on a phone held at 30 degrees and at half on one held at 45 — which is exactly the
        // laziness the first device session reported. It is now a statement about how well the angle
        // can be *read* rather than about how far the sky should move: full strength wherever the
        // reading is trustworthy, fading out only as it stops being.
        listOf(0.0, 20.0, 30.0, 45.0, 60.0, 90.0).forEach { elevation ->
            assertEquals(
                sin(elevation.radians()),
                gravityAt(Pose(elevation, lean = 33.0)).lean.inPlane,
                1e-9,
                "at $elevation degrees",
            )
        }
    }

    @Test
    fun `how much of down lies in the tip plane empties out with the phone on its side`() {
        // The same fact about the other axis, and the pose is the one nobody thinks of: a phone held
        // in landscape with its long edge horizontal has its own `x` axis pointing at the sky, so
        // turning about that axis is a spin about the vertical — the one turn gravity cannot see.
        // Both axes have such a pose and neither has one a hand rests in.
        assertEquals(1.0, gravityAt(Pose(elevation = 90.0)).tip.inPlane, 1e-9)
        assertEquals(0.0, gravityAt(Pose(elevation = 90.0, lean = 90.0)).tip.inPlane, 1e-9)
    }

    @Test
    fun `a sideways lean barely touches the tip`() {
        // A pure in-plane lean does move the elevation of the long edge a little, because rolling
        // the phone in its own plane sweeps that edge round a cone. So the two axes are not exactly
        // independent — but the leak is *second order* in the lean angle, where the very first
        // version of this module was first order and sent the sky diagonally on every sideways
        // lean. Measured across every pose it peaks at 1.3% of full travel at 45 degrees, which is
        // the same figure the cross product gave and about a third of a pixel on the nearest plane.
        val bound = 0.03 * FULL_TRAVEL_RADIANS

        EVERY_POSE.forEach { elevation ->
            val leaked = gravityAt(Pose(elevation, lean = 6.0)).tip.radians - elevation.radians()

            assertTrue(abs(leaked) < bound, "a lean at $elevation leaked $leaked into the tip")
        }
    }

    @Test
    fun `the two platforms read the same movement the same way`() {
        // **The cross-platform trap that shipped in the first draft of this module and was caught
        // before merge.** Android reports the reaction to gravity — pointing at the sky, in metres
        // per second squared — and iOS reports gravity itself, pointing at the ground, in multiples
        // of g. Every component is negated and the scale differs by ten.
        //
        // Normalising discards the scale, and negating every component turns both bearings by
        // exactly half a circle — so the *angles* differ between the platforms and every *difference*
        // between two of them is identical. Since nothing downstream ever reads a bearing except to
        // subtract it from another one, the two phones lean the same way with no correction at
        // either edge. That is what this walks: the same two poses, twice, as each device reports
        // them.
        EVERY_POSE.forEach { elevation ->
            val from = Pose(elevation)
            val to = Pose(elevation + 6.0, lean = 4.0)

            val ios = gravityAt(from).turnedTo(gravityAt(to))
            val android = gravityFrom(from.androidReading).turnedTo(gravityFrom(to.androidReading))

            assertEquals(ios.first, android.first, 1e-9, "tip at $elevation")
            assertEquals(ios.second, android.second, 1e-9, "lean at $elevation")
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
        // The renormalisation the filter depends on: a bearing read off a vector that is not unit
        // length is still the right angle, but `inPlane` beside it would be a number about the
        // vector's size rather than about the pose.
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

private val FULL_TRAVEL_RADIANS = TiltMonitor.FULL_TRAVEL_DEGREES * PI / 180.0

private fun Double.radians(): Double = this * PI / 180.0

private fun gravityAt(pose: Pose): Gravity = gravityFrom(pose.gravity)

private fun gravityFrom(reading: Triple<Double, Double, Double>): Gravity =
    checkNotNull(Gravity.normalised(reading.first, reading.second, reading.third)) {
        "$reading is not a direction"
    }

private fun Gravity.turnedTo(other: Gravity): Pair<Double, Double> = Pair(
    turnedFrom(tip.radians, to = other.tip.radians),
    turnedFrom(lean.radians, to = other.lean.radians),
)

private fun magnitudeOf(gravity: Gravity?): Double {
    val g = checkNotNull(gravity)
    return kotlin.math.sqrt(g.x * g.x + g.y * g.y + g.z * g.z)
}
