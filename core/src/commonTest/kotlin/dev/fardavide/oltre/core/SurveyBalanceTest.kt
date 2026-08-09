package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class SurveyBalanceTest {

    private fun at(galaxy: Int, system: Int) = SystemAddress(galaxy = galaxy, system = system)

    @Test
    fun `distance is symmetric and zero at home`() {
        // then — a probe cannot be cheaper one way than the other
        assertEquals(0, SurveyBalance.distanceUnits(at(1, 100), at(1, 100)))
        assertEquals(
            SurveyBalance.distanceUnits(at(1, 100), at(1, 140)),
            SurveyBalance.distanceUnits(at(1, 140), at(1, 100)),
        )
    }

    @Test
    fun `a galaxy hop costs as much as crossing a galaxy end to end`() {
        // then the four galaxies read as separate places rather than one strip of a thousand
        assertEquals(
            GalaxyBalance.SYSTEMS_PER_GALAXY,
            SurveyBalance.distanceUnits(at(1, 100), at(2, 100)),
        )
    }

    @Test
    fun `the published flight times`() {
        // The curve this verb was tuned against: round 8 measured gaps of four to nine hours
        // between check-ins, and a player who cannot reach that range has bought nothing.
        val home = at(2, 125)

        assertEquals(31.minutes, SurveyBalance.duration(home, at(2, 126)))
        assertEquals(40.minutes, SurveyBalance.duration(home, at(2, 135)))
        assertEquals(80.minutes, SurveyBalance.duration(home, at(2, 175)))
        assertEquals(154.minutes, SurveyBalance.duration(home, at(2, 249)))
        assertEquals(280.minutes, SurveyBalance.duration(home, at(3, 125)))
        assertEquals(404.minutes, SurveyBalance.duration(home, at(3, 249)))
        assertEquals(654.minutes, SurveyBalance.duration(home, at(4, 1)))
    }

    @Test
    fun `the nearest target still outlasts the session that ordered it`() {
        // The failure the base term exists to prevent: a probe that lands inside the check-in that
        // dispatched it is the 72-minute booking with a different label.
        val home = at(1, 1)
        assertTrue(
            SurveyBalance.duration(home, at(1, 2)) >= 30.minutes,
            "a dispatch must survive the player putting the phone down",
        )
    }

    @Test
    fun `the longest flight covers a night and the shortest does not overshoot a day`() {
        // Both ends matter. Too short and the verb cannot cover the overnight gap it exists for;
        // too long and a player is choosing a target they will not see the result of.
        val corner = SurveyBalance.duration(at(1, 1), at(GalaxyBalance.GALAXIES, GalaxyBalance.SYSTEMS_PER_GALAXY))
        assertTrue(corner >= 8.hours, "the far corner must be able to cover a night, was $corner")
        assertTrue(corner <= 24.hours, "no dispatch should outlast a day, was $corner")
    }

    @Test
    fun `the price is flat, and it is metal`() {
        // The one shape decision in this object: distance changes when a probe lands and never
        // what it costs, so a far probe is a longer buy rather than a worse one — and it never
        // competes with the deuterium that gates the Robotics Factory.
        val cost = SurveyBalance.cost()
        assertEquals(SurveyBalance.COST_METAL, cost.metal)
        assertEquals(0L, cost.crystal)
        assertEquals(0L, cost.deuterium)
    }

    @Test
    fun `a new colony can pay for a probe out of its starting stock`() {
        // "A new colony opens on a decision, not on a wait" — and from 0.2 that decision has two
        // kinds of thing in it rather than one.
        assertTrue(PlaceholderBalance.startingResources().covers(SurveyBalance.cost()))
    }
}
