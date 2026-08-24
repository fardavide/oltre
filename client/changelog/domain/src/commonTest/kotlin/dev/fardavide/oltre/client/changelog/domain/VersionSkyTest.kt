package dev.fardavide.oltre.client.changelog.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// **The mark, as arithmetic.** *A Sky Per Build* §2 is the rule and this is it in numbers — the
// drawing itself is four primitives in a `DrawScope` and has nothing left to get wrong once these
// hold.
//
// The design asked for exactly this rather than for a baseline, in as many words: *"a 20dp
// screenshot diff cannot state where the ink is — the suite already learned that with the bell and
// the player mark."* A baseline says the picture changed; these say the sky is a sky.
class VersionSkyTest {

    @Test
    fun `the bodies are the minor lines and the patches on top of them`() {
        val sky = ReleaseVersion(0, 17, 1).skyAt(PAGE)

        assertEquals(18, sky.bodies.size)
        assertEquals(17, sky.bodies.count { it.filled })
        assertEquals(1, sky.bodies.count { !it.filled })
    }

    @Test
    fun `a minor line with no patches is all filled`() {
        val sky = ReleaseVersion(0, 18, 0).skyAt(PAGE)

        assertEquals(18, sky.bodies.size)
        assertTrue(sky.bodies.all { it.filled })
    }

    @Test
    fun `prehistory is all hollow`() {
        // Minor 0 means every body is a patch riding on a line that was never settled. Twelve pages
        // of empty rings is what the first week has to say and the page does not pretend otherwise.
        val sky = ReleaseVersion(0, 0, 3).skyAt(PAGE)

        assertEquals(3, sky.bodies.size)
        assertTrue(sky.bodies.none { it.filled })
    }

    @Test
    fun `a major empties the sky and puts a world on the limb`() {
        // Falls out of the rule rather than being chosen — see the sheet's own note about 1.0.0
        // being the single most visible frame the mark will ever draw.
        val sky = ReleaseVersion(1, 0, 0).skyAt(PAGE)

        assertTrue(sky.bodies.isEmpty())
        assertEquals(1, sky.worlds.size)
    }

    @Test
    fun `every major is a world of its own`() {
        assertEquals(3, ReleaseVersion(3, 0, 0).skyAt(PAGE).worlds.size)
    }

    @Test
    fun `the worlds are centred on the mark`() {
        // **The half of this drawing nothing else measures.** Every property below walks `bodies`,
        // and until 0.19 the only assertion about a world was how many there were — so `(major - 1)
        // / 2f` becoming an integer division would have hung a pair of worlds off to the right of
        // the mark with the whole suite still green, on the one frame the design calls the most
        // visible the mark will ever draw.
        for (major in 1..4) {
            val worlds = ReleaseVersion(major, 0, 0).skyAt(PAGE).worlds
            val centre = worlds.sumOf { it.x.toDouble() } / worlds.size

            assertEquals(PAGE / 2, centre.toFloat(), absoluteTolerance = 0.01f, "major $major is off centre")
        }
    }

    @Test
    fun `a world rests on the limb rather than through it`() {
        for (major in 1..4) {
            val sky = ReleaseVersion(major, 0, 0).skyAt(PAGE)

            for (world in sky.worlds) {
                assertTrue(
                    world.y + world.radius <= sky.limb.crestY,
                    "major $major put a world through the horizon",
                )
            }
        }
    }

    @Test
    fun `the worlds stay inside the box for every major this project could reach`() {
        // **A bound rather than a guarantee, and it is the design's own formula that sets it.**
        // Worlds are spaced 2.7 radii apart and centred, so their half-width grows by 0.0675 of the
        // side per major while the box only ever has 0.5 — which runs out at major 8. Seven majors
        // is more of this project than anybody has planned, and the day an eighth is real the
        // spacing has to give rather than this test.
        for (major in 1..7) {
            for (world in ReleaseVersion(major, 0, 0).skyAt(PAGE).worlds) {
                assertTrue(
                    world.x - world.radius >= 0f && world.x + world.radius <= PAGE,
                    "major $major put a world outside the box",
                )
            }
        }
    }

    @Test
    fun `the outermost body sits on the edge of the disc`() {
        // rho = R x sqrt(i / N) puts the last body at exactly R whatever N is — which is what keeps
        // a three-body sky and a nineteen-body sky the same size.
        val sky = ReleaseVersion(0, 12, 0).skyAt(PAGE)
        val outermost = sky.bodies.last()
        val reach = hypot(outermost.x - PAGE / 2, outermost.y - CENTRE_Y * PAGE)

        assertEquals(RADIUS * PAGE, reach, absoluteTolerance = 0.01f)
    }

    @Test
    fun `a patch re-lays the whole sky rather than adding a dot to it`() {
        // The distinctness proof from §2c: the bearing carries i + patch, so 0.17.1 and 0.18.0 have
        // eighteen bodies each and share not one position. Two versions can draw the same sky only
        // if they are the same release.
        val patched = ReleaseVersion(0, 17, 1).skyAt(PAGE)
        val settled = ReleaseVersion(0, 18, 0).skyAt(PAGE)

        val shared = patched.bodies.count { one ->
            settled.bodies.any { other ->
                hypot(one.x - other.x, one.y - other.y) < 0.5f
            }
        }
        assertEquals(0, shared)
    }

    @Test
    fun `every body stands inside the box at every size the mark is drawn`() {
        // **Measured to the ink rather than to the centreline.** A hollow body is drawn as a stroke
        // *centred* on its radius, so it reaches half a stroke further than `radius` says — and at
        // 29dp, where both the radius and the stroke are on their floors, that half is most of the
        // margin. Checking the radius alone would leave the assertion slack it did not know it had.
        for (side in DRAWN_SIZES) {
            for (version in grid()) {
                val sky = version.skyAt(side)
                for (body in sky.bodies) {
                    val ink = body.radius + if (body.filled) 0f else sky.ringStroke / 2f
                    assertTrue(
                        body.x - ink >= 0f &&
                            body.x + ink <= side &&
                            body.y - ink >= 0f &&
                            body.y + ink <= side,
                        "$version at $side put a body outside the box",
                    )
                }
            }
        }
    }

    @Test
    fun `no two bodies touch at the sizes a page draws`() {
        // Asserted at 319 and 262 and deliberately not at 29: the settings row's mark is texture
        // rather than a count — the design says so — and the floors that keep it visible at all are
        // what make bodies meet there. Measured 2026-08-23: at page size the run's tightest pair is
        // 42dp apart against a 10dp diameter.
        for (side in PAGE_SIZES) {
            for (version in grid()) {
                val bodies = version.skyAt(side).bodies
                for (i in bodies.indices) {
                    for (j in i + 1 until bodies.size) {
                        val gap = hypot(bodies[i].x - bodies[j].x, bodies[i].y - bodies[j].y)
                        assertTrue(
                            gap > bodies[i].radius + bodies[j].radius,
                            "$version at $side drew two bodies on top of each other",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `the limb never crosses the sky`() {
        // The limb is the page's only rule between the picture and the copy. A body sitting on it
        // would read as a moon on the horizon rather than as a release.
        for (side in DRAWN_SIZES) {
            for (version in grid()) {
                val sky = version.skyAt(side)
                for (body in sky.bodies) {
                    assertTrue(
                        body.y + body.radius < sky.limb.crestY,
                        "$version at $side put a body through the limb",
                    )
                }
            }
        }
    }

    @Test
    fun `the limb spans the box and no more`() {
        // The arc belongs to a circle one and a half times the width of the mark, so all but a
        // sliver of it is off the card. These are the two angles that cut the sliver out — and they
        // are asserted here rather than trusted in a `DrawScope`, because a sweep that is too wide
        // draws a line off the edge of the page and nothing but an eye would catch it.
        val sky = ReleaseVersion(0, 18, 0).skyAt(PAGE)
        val limb = sky.limb
        val centreY = limb.crestY + limb.radius

        val start = limb.startAngleDegrees * PI.toFloat() / 180f
        val end = (limb.startAngleDegrees + limb.sweepAngleDegrees) * PI.toFloat() / 180f

        assertEquals(0f, PAGE / 2 + limb.radius * cos(start), absoluteTolerance = 0.01f)
        assertEquals(PAGE, PAGE / 2 + limb.radius * cos(end), absoluteTolerance = 0.01f)
        assertEquals(limb.edgeY, centreY + limb.radius * sin(start), absoluteTolerance = 0.01f)
        assertEquals(limb.edgeY, centreY + limb.radius * sin(end), absoluteTolerance = 0.01f)
    }

    @Test
    fun `the crest of the limb is the highest the limb reaches`() {
        val limb = ReleaseVersion(0, 18, 0).skyAt(PAGE).limb

        assertTrue(limb.edgeY > limb.crestY)
    }

    @Test
    fun `the mark keeps its ink at the smallest size it is drawn`() {
        // Floors, not scaling: at 29dp the arithmetic would give a 0.36dp body, which is nothing at
        // all on a screen. A body is a fill rather than a stroke, so the glyph family's 1.4dp floor
        // does not apply to it — which is the note the design left for exactly this line.
        val sky = ReleaseVersion(0, 13, 2).skyAt(ROW)

        assertTrue(sky.bodies.filter { it.filled }.all { it.radius >= 1.3f })
        assertTrue(sky.bodies.filter { !it.filled }.all { it.radius >= 1.6f })
        assertTrue(sky.ringStroke >= 0.8f)
        assertTrue(sky.limb.stroke >= 1.1f)
    }

    private companion object {

        // The three sizes the mark is drawn at: a page at 393dp, a page in a Slide Over pane and the
        // settings sheet's build row.
        const val PAGE = 319f
        const val NARROW = 262f
        const val ROW = 29f

        val PAGE_SIZES = listOf(PAGE, NARROW)
        val DRAWN_SIZES = listOf(PAGE, NARROW, ROW)

        const val CENTRE_Y = 0.44f
        const val RADIUS = 0.36f

        // Every version the project could plausibly reach rather than the ones it has, because
        // the properties are about the rule and not about today's run. Minor to 25 is a year of this
        // cadence; patch to 12 is the longest line the project has actually had.
        fun grid(): List<ReleaseVersion> = buildList {
            for (minor in 0..25) {
                for (patch in 0..12) {
                    add(ReleaseVersion(major = 0, minor = minor, patch = patch))
                }
            }
        }
    }
}
