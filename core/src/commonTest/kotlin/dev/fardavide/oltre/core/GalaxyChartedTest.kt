package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

// The third knowledge tier, and the only one that is an *interval* rather than a set.
//
// Charted is `[lo, hi]` per galaxy, two integers, widened by where a hull came down and never by
// what it found. Everything here is about that one shape: it only grows, it clamps to the ends of
// its own galaxy, and a galaxy nothing has ever reached has no interval at all.
class GalaxyChartedTest {

    private fun home(state: GameState): SystemAddress = SystemAddress.of(state.galaxy.home)

    @Test
    fun `a new colony has charted an hour of flight either side of home`() {
        // given a colony on its first launch, which has landed nowhere
        val galaxy = GameState.initial().galaxy
        val home = SystemAddress.of(galaxy.home)

        // then the hour of grace is all it has, and it is centred on the one place it is standing
        val span = galaxy.spanIn(home.galaxy)
        assertEquals(home.system - SurveyBalance.GRACE_SYSTEMS, span?.lo)
        assertEquals(home.system + SurveyBalance.GRACE_SYSTEMS, span?.hi)
    }

    @Test
    fun `a new colony has charted nothing in the three galaxies it has never reached`() {
        val galaxy = GameState.initial().galaxy
        val homeGalaxy = galaxy.home.galaxy

        for (other in 1..GalaxyBalance.GALAXIES) {
            if (other == homeGalaxy) continue
            assertNull(galaxy.spanIn(other), "galaxy $other has never had a hull in it")
            assertEquals(0, galaxy.chartedCountIn(other))
            assertFalse(galaxy.hasCharted(SystemAddress(galaxy = other, system = 1)))
        }
    }

    @Test
    fun `a landing charts an hour of flight either side of where the hull came down`() {
        // given a colony that has charted its own doorstep and nothing else, and a target far
        // enough out to move the frontier but not so far that the galaxy's own end clamps it —
        // this test is about the arithmetic, and the clamp has a test of its own below
        val galaxy = GameState.initial().galaxy
        val home = SystemAddress.of(galaxy.home)
        val reached = SystemAddress(
            galaxy = home.galaxy,
            system = (home.system + 40)
                .coerceAtMost(GalaxyBalance.SYSTEMS_PER_GALAXY - SurveyBalance.GRACE_SYSTEMS),
        )
        assertTrue(
            reached.system > requireNotNull(galaxy.spanIn(home.galaxy)).hi,
            "the fixture needs a target past the light the colony already has",
        )

        // when a hull comes down there
        val widened = galaxy.withCharted(reached)

        // then the far end moves to an hour past it and the near end does not move at all
        assertEquals(home.system - SurveyBalance.GRACE_SYSTEMS, widened.spanIn(home.galaxy)?.lo)
        assertEquals(reached.system + SurveyBalance.GRACE_SYSTEMS, widened.spanIn(home.galaxy)?.hi)
    }

    @Test
    fun `a span clamps to the ends of its own galaxy`() {
        val galaxy = GameState.initial().galaxy
        val homeGalaxy = galaxy.home.galaxy

        val atTheStart = galaxy.withCharted(SystemAddress(galaxy = homeGalaxy, system = 1))
        val atTheEnd = galaxy.withCharted(
            SystemAddress(galaxy = homeGalaxy, system = GalaxyBalance.SYSTEMS_PER_GALAXY),
        )

        assertEquals(1, atTheStart.spanIn(homeGalaxy)?.lo)
        assertEquals(GalaxyBalance.SYSTEMS_PER_GALAXY, atTheEnd.spanIn(homeGalaxy)?.hi)
    }

    @Test
    fun `a landing inside what is already charted widens nothing`() {
        val galaxy = GameState.initial().galaxy
        val home = SystemAddress.of(galaxy.home)

        // when a hull lands on the doorstep it already had the map of
        val again = galaxy.withCharted(home)

        // then the state is not merely equal, it is the same object — the widen is a no-op rather
        // than a rewrite, which is what lets `advance` apply it at every boundary for free.
        assertEquals(galaxy, again)
        assertEquals(0, galaxy.wouldChart(home))
    }

    @Test
    fun `charting only ever widens`() {
        val galaxy = GameState.initial().galaxy
        val homeGalaxy = galaxy.home.galaxy
        val far = SystemAddress(galaxy = homeGalaxy, system = GalaxyBalance.SYSTEMS_PER_GALAXY)

        // when a flight to the far end of the galaxy is followed by one to the near end
        val wide = galaxy.withCharted(far)
        val andBack = wide.withCharted(SystemAddress(galaxy = homeGalaxy, system = 1))

        // then the second flight pushes the near end outward and does not pull the far end back —
        // the frontier is two points and each of them only ever travels one way
        assertEquals(GalaxyBalance.SYSTEMS_PER_GALAXY, andBack.spanIn(homeGalaxy)?.hi)
        assertEquals(1, andBack.spanIn(homeGalaxy)?.lo)
        assertTrue(andBack.chartedCountIn(homeGalaxy) > wide.chartedCountIn(homeGalaxy))
    }

    @Test
    fun `a galaxy is charted nowhere until a hull lands in it`() {
        val galaxy = GameState.initial().galaxy
        val elsewhere = (1..GalaxyBalance.GALAXIES).first { it != galaxy.home.galaxy }
        val arrival = SystemAddress(galaxy = elsewhere, system = 120)

        assertEquals(0, galaxy.chartedCountIn(elsewhere))

        val reached = galaxy.withCharted(arrival)

        // then one probe opens the hour either side and nothing more
        assertEquals(2 * SurveyBalance.GRACE_SYSTEMS + 1, reached.chartedCountIn(elsewhere))
        // and it has not touched the galaxy the colony lives in
        assertEquals(galaxy.chartedCountIn(galaxy.home.galaxy), reached.chartedCountIn(galaxy.home.galaxy))
    }

    @Test
    fun `what a flight would chart is exactly what charting it adds`() {
        val galaxy = GameState.initial().galaxy
        val homeGalaxy = galaxy.home.galaxy

        for (system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY step 17) {
            val target = SystemAddress(galaxy = homeGalaxy, system = system)
            val before = galaxy.chartedCountIn(homeGalaxy)
            val after = galaxy.withCharted(target).chartedCountIn(homeGalaxy)

            assertEquals(after - before, galaxy.wouldChart(target), "at system $system")
        }
    }

    @Test
    fun `the grace is one hour of the probe's own clock`() {
        // The whole justification for the constant, pinned so the two cannot drift: a probe is 30
        // minutes of base plus a minute a system, so an hour of flight reaches exactly this far. If
        // the probe's clock is ever rebalanced this test says which way the map moved with it.
        val home = SystemAddress(galaxy = 1, system = 100)
        val anHourOut = SystemAddress(galaxy = 1, system = home.system + SurveyBalance.GRACE_SYSTEMS)

        assertEquals(60.minutes, SurveyBalance.duration(from = home, to = anHourOut))
    }

    @Test
    fun `a charted star is charted and a star past the span is not`() {
        val galaxy = GameState.initial().galaxy
        val home = SystemAddress.of(galaxy.home)
        val span = requireNotNull(galaxy.spanIn(home.galaxy))

        assertTrue(galaxy.hasCharted(SystemAddress(home.galaxy, span.lo)))
        assertTrue(galaxy.hasCharted(SystemAddress(home.galaxy, span.hi)))
        if (span.lo > 1) assertFalse(galaxy.hasCharted(SystemAddress(home.galaxy, span.lo - 1)))
        if (span.hi < GalaxyBalance.SYSTEMS_PER_GALAXY) {
            assertFalse(galaxy.hasCharted(SystemAddress(home.galaxy, span.hi + 1)))
        }
    }
}
