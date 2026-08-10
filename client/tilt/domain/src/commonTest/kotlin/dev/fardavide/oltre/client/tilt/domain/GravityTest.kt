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
    fun `the tip is the elevation the phone is actually held at`() {
        EVERY_POSE.forEach { elevation ->
            assertEquals(elevation.radians(), gravityAt(Pose(elevation)).tip, 1e-9, "at $elevation degrees")
        }
    }

    @Test
    fun `the tip climbs without folding across every pose the screen can be read from`() {
        // **The regression test for the defect the very first version of this module shipped**, kept
        // through two later formulations because it is the thing most easily broken again. Reading a
        // pose as `asin` of a single component folds at exactly upright-in-portrait: leaning either
        // way from there moved the sky the same way, and past it — reading lying down — the whole
        // effect ran backwards. The crease sat on the most common pose there is.
        //
        // `atan2` of a *pair* of components knows which quadrant it is in, so it walks from face up
        // through upright to face down without a crease anywhere in between. That it turns back at
        // the far end rather than carrying on round is the trade named on `Gravity` and pinned by
        // the test below — a different thing from a fold, and at a pose nobody is looking at.
        val walk = (0..180 step 10).map { gravityAt(Pose(it.toDouble())).tip }

        walk.zipWithNext().forEach { (before, after) ->
            assertTrue(after > before, "the tip went backwards between $before and $after")
        }
    }

    @Test
    fun `the tip is untouched by any amount of roll`() {
        // **The property 0.4.3's first draft did not have, and the defect a device found.** That
        // draft read the tip as the elevation of the phone's long *edge*, which a roll sweeps round
        // a cone — so a purely sideways gesture dragged the sky vertically by up to twice the
        // elevation: 4.17 units of unwanted vertical on a quarter turn at a fifty-degree hold, 8.33
        // on a half turn. Its own guard test only ever rolled six degrees, where the leak is second
        // order and invisible.
        //
        // The screen normal is the axis a roll turns *about*, so rolling does not move it at all.
        // This asserts equality rather than a small bound, at the angles a full turn goes through.
        EVERY_POSE.forEach { elevation ->
            val upright = gravityAt(Pose(elevation)).tip

            listOf(6.0, 45.0, 90.0, 180.0, 270.0).forEach { roll ->
                assertEquals(upright, gravityAt(Pose(elevation, lean = roll)).tip, 1e-9, "$roll roll at $elevation")
            }
        }
    }

    @Test
    fun `the tip turns back past face down which is the trade this reading makes`() {
        // Stated as a property rather than left to be found. No reading of the tip can be blind to
        // the roll, monotonic through a full end-over-end turn, and a function of the current pose
        // all at once — `Gravity`'s note carries the one-line proof. This is the corner chosen, and
        // the range it gives up is the half turn where the screen faces away from the player.
        assertTrue(gravityAt(Pose(190.0)).tip < gravityAt(Pose(180.0)).tip)
        assertEquals(gravityAt(Pose(170.0)).tip, gravityAt(Pose(190.0)).tip, 1e-9)
    }

    @Test
    fun `the tip can be read from every pose there is`() {
        // The other half of what the screen normal buys, and a pose the previous draft lost
        // outright: held in landscape with the long edge horizontal, its reading had no plane left
        // to be read in and the vertical axis died — measured on a device as no response at all.
        // `x² + y²` and `z` cannot both vanish on a unit vector, so this one has no such pose and
        // needs no companion measure of whether to trust it.
        EVERY_POSE.forEach { elevation ->
            listOf(0.0, 90.0, 180.0).forEach { roll ->
                val tip = gravityAt(Pose(elevation, lean = roll)).tip

                assertTrue(tip.isFinite(), "no reading at $elevation with $roll roll")
            }
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
    fun `the two platforms disagree about the tip so the convention is stated rather than assumed`() {
        // **The cross-platform trap this module has now had three chances to fall into.** Android
        // reports the *reaction* to gravity — pointing at the sky, in metres per second squared —
        // and iOS reports gravity itself. Every component is negated and the scale differs by ten.
        //
        // Normalising discards the scale, and until 0.4.3 nothing discarded the sign because nothing
        // needed to: the cross product was blind to it, and so was the pair of `atan2`s that replaced
        // the cross product, since negating all three components turned both bearings by exactly half
        // a circle and every *difference* cancelled it. The lean still works that way — the test
        // below walks it.
        //
        // The tip does not, and this is what says so out loud. `√(x² + y²)` does not care about the
        // sign and `z` does, so the two platforms come out **reflected** rather than offset:
        // `tip(-g) = π - tip(g)`. Differences negate instead of cancelling, and a phone would have
        // leaned the right way on an iPhone and upside down on a Pixel. That is why
        // `TiltMonitor.sampleReactionToGravity` exists rather than a minus sign in a sensor callback.
        EVERY_POSE.forEach { elevation ->
            val pose = Pose(elevation, lean = 20.0)
            val ios = gravityAt(pose).tip
            val android = gravityFrom(pose.androidReading).tip

            assertEquals(PI - ios, android, 1e-9, "at $elevation degrees")
        }
    }

    @Test
    fun `the stated convention makes the two platforms agree again`() {
        EVERY_POSE.forEach { elevation ->
            val pose = Pose(elevation, lean = 20.0)
            val (x, y, z) = pose.androidReading
            val converted = gravityFrom(Triple(-x, -y, -z))

            assertEquals(gravityAt(pose).tip, converted.tip, 1e-9, "tip at $elevation")
            assertEquals(gravityAt(pose).lean.radians, converted.lean.radians, 1e-9, "lean at $elevation")
        }
    }

    @Test
    fun `the two platforms read the same sideways movement the same way`() {
        // **The cross-platform trap that shipped in the first draft of this module and was caught
        // before merge.** Android reports the reaction to gravity — pointing at the sky, in metres
        // per second squared — and iOS reports gravity itself, pointing at the ground, in multiples
        // of g. Every component is negated and the scale differs by ten.
        //
        // Normalising discards the scale, and negating every component turns the *lean* bearing by
        // exactly half a circle — so the two platforms disagree about the angle and agree about
        // every difference between two of them, which is all anything downstream reads. This axis
        // therefore needs no correction at either edge, and never did.
        //
        // **The tip is the axis where that stopped being true at 0.4.3**, which is why the two tests
        // above exist and why `TiltMonitor.sampleReactionToGravity` does. Do not read this test as
        // saying the module is sign-blind; it says one of its two axes is.
        EVERY_POSE.forEach { elevation ->
            val fromPose = Pose(elevation)
            val toPose = Pose(elevation + 6.0, lean = 4.0)

            val ios = gravityAt(fromPose).leanTurnedTo(gravityAt(toPose))
            val android = gravityFrom(fromPose.androidReading).leanTurnedTo(gravityFrom(toPose.androidReading))

            assertEquals(ios, android, 1e-9, "lean at $elevation")
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

private fun Gravity.leanTurnedTo(other: Gravity): Double = turnedFrom(lean.radians, to = other.lean.radians)

private fun magnitudeOf(gravity: Gravity?): Double {
    val g = checkNotNull(gravity)
    return kotlin.math.sqrt(g.x * g.x + g.y * g.y + g.z * g.z)
}
