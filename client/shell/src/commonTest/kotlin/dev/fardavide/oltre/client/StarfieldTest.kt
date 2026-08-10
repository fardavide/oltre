package dev.fardavide.oltre.client

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

// The one part of the leaning starfield a test in this repository can reach.
//
// **What this does not cover, said plainly:** there is no screenshot baseline of a leaning field,
// because recording one needs a machine that can run Roborazzi and the session that wrote this
// could not. So the branch in `Starfield` that draws four copies per star instead of two reaches
// `main` verified by compilation alone, and the arithmetic below is the only thing standing where a
// baseline should be. That is a smaller gap than it was — the 0.4.0 review found the *vertical*
// version of exactly this bug in code nothing rendered either — but it is a gap.
//
// No comma appears in a test name here: this is a `commonTest` in a module with an iOS target, and
// Kotlin/Native rejects one outright while the JVM compiles it happily. It cost 0.2.7 a repair
// commit. See `.claude/rules/session-roles.md`.
class StarfieldTest {

    @Test
    fun `a leaned star stays inside the box`() {
        // The property the fold exists for. Whatever the lean — including one larger than the box
        // and one pointing the other way — the first copy lands somewhere a viewer can see.
        STAR_FRACTIONS.forEach { fraction ->
            LEANS.forEach { lean ->
                val x = leanedAcross(fraction = fraction, lean = lean, width = WIDTH)

                assertTrue(x >= 0f && x < WIDTH, "$fraction at lean $lean landed at $x")
            }
        }
    }

    @Test
    fun `the two copies of a star tile the box with no seam`() {
        // What "wrapped rather than translated" has to mean. The pair sits exactly one width apart
        // and straddles the left edge — so whatever leaves one side comes back on the other and no
        // lean can drag in a bare strip. This is the horizontal form of the defect the 0.4.0 review
        // found on the vertical axis where a plane was translated and left the sky empty.
        STAR_FRACTIONS.forEach { fraction ->
            LEANS.forEach { lean ->
                val near = leanedAcross(fraction = fraction, lean = lean, width = WIDTH)
                val companion = near - WIDTH

                assertTrue(companion < 0f, "the second copy of $fraction at lean $lean was not off-screen")
                assertTrue(
                    abs((near - companion) - WIDTH) < 0.01f,
                    "the two copies of $fraction were ${near - companion} apart rather than $WIDTH",
                )
            }
        }
    }

    @Test
    fun `the companion copy is drawn off the left edge rather than the right`() {
        // Not a preference. At `x + width` a star near fraction zero lands just *inside* the right
        // edge instead of safely outside it, which would move `starfield_scrolled.png` and
        // `main_scaffold.png` — the two baselines this whole feature is built to leave alone.
        val leftmost = leanedAcross(fraction = 0.0004f, lean = 0f, width = WIDTH)

        assertTrue(leftmost - WIDTH < -WIDTH + 1f, "the companion copy was ${leftmost - WIDTH}")
    }

    @Test
    fun `a lean of a whole box is the same as no lean at all`() {
        // The fold is a fold rather than a clamp: a plane pushed exactly one width comes back where
        // it started, which is what lets the lean be applied without any bound on its size.
        STAR_FRACTIONS.forEach { fraction ->
            val still = leanedAcross(fraction = fraction, lean = 0f, width = WIDTH)
            val wrapped = leanedAcross(fraction = fraction, lean = WIDTH, width = WIDTH)

            assertTrue(abs(still - wrapped) < 0.01f, "$fraction: $still against $wrapped")
        }
    }
}

// A phone at 393dp, the width every baseline in this module is recorded at.
private const val WIDTH = 393f

// The real extremes of the star table in `Starfield`, plus the middle. `x` runs edge to edge with no
// margin, which is the whole reason the horizontal wrap has to exist at all.
private val STAR_FRACTIONS = listOf(0.0004f, 0.2f, 0.5f, 0.8826f, 0.9845f)

// Far past anything a lean can actually produce — the near plane reaches about 14dp — because a fold
// that only works inside its expected range is a fold with a bug waiting in it.
private val LEANS = listOf(-800f, -393f, -14f, -0.5f, 0f, 0.5f, 14f, 393f, 800f)
