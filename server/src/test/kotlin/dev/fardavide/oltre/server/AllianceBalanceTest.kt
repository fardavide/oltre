package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.protocol.AllianceLevel
import dev.fardavide.oltre.protocol.AllianceProject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// **Every number under test here is invented rather than measured**, and the tests say so by
// asserting *shapes* rather than values wherever they can: `:sim` cannot model a roster, so a test
// that pinned a threshold would be pinning a guess and making the balance round's job harder. What
// is pinned is what must stay true whatever the balance round does to the constants.
class AllianceBalanceTest {

    // **Geometric, not linear**, which is the one thing about this ladder that is a measurement.
    // A player's experience accrues linearly in time; an alliance's income is contributions and
    // contributions come out of member income, which compounds — `experience-sheet.md` §4's other
    // case. A ladder that flattened would be the wrong shape however the constants moved.
    @Test
    fun `each level costs more to leave than the one below it`() {
        for (level in 0..20) {
            assertTrue(
                AllianceBalance.spanOf(AllianceLevel(level + 1)) > AllianceBalance.spanOf(AllianceLevel(level)),
                "level ${level + 1} is not dearer than $level",
            )
        }
    }

    @Test
    fun `a span is never zero so the gauge always has somewhere to go`() {
        for (level in 0..40) {
            assertTrue(AllianceBalance.spanOf(AllianceLevel(level)) > 0, "level $level costs nothing to leave")
        }
    }

    // The walk against its own definition: the first point of a level and the last point before the
    // next one both read as that level.
    @Test
    fun `the level a total buys is the number of spans it covers`() {
        var floor = 0L
        for (level in 0..20) {
            val span = AllianceBalance.spanOf(AllianceLevel(level))
            assertEquals(AllianceLevel(level), AllianceBalance.progressOf(floor).level, "at $floor")
            assertEquals(AllianceLevel(level), AllianceBalance.progressOf(floor + span - 1).level, "at $floor + span")
            floor += span
        }
    }

    @Test
    fun `progress is a share of this level rather than of the whole climb`() {
        val span = AllianceBalance.spanOf(AllianceLevel(0))

        val progress = AllianceBalance.progressOf(span / 2)

        assertEquals(AllianceLevel(0), progress.level)
        assertEquals(span / 2, progress.intoLevel)
        assertEquals(span, progress.span)
    }

    @Test
    fun `an alliance that has done nothing opens on an empty gauge`() {
        val progress = AllianceBalance.progressOf(0)

        assertEquals(AllianceLevel(0), progress.level)
        assertEquals(0, progress.intoLevel)
    }

    // **The game's own 1 : 2 : 3, through `core`'s single statement of it.** Two copies of the ratio
    // with no test comparing them is the drift `LevelPurpose.kt` already complains about.
    @Test
    fun `a contribution is priced on the games own ratio`() {
        assertEquals(1L, AllianceBalance.award(Resources.of(metal = 1)))
        assertEquals(2L, AllianceBalance.award(Resources.of(crystal = 1)))
        assertEquals(3L, AllianceBalance.award(Resources.of(deuterium = 1)))
    }

    // **A lump larger than what it cost**, which is `alliance-sheet.md` §3.1's whole reason for
    // having two sources rather than one: on contributions alone the level tracks hoarding.
    @Test
    fun `a finished project pays more than the pool spent on it`() {
        val cost = Resources.of(metal = 20_000, crystal = 10_000, deuterium = 5_000)

        assertTrue(AllianceBalance.projectAward(cost) > AllianceBalance.award(cost))
    }

    // **This is what makes the first contribution do something on the day it lands** — `#144` §8's
    // first condition, before any project exists.
    @Test
    fun `a level grants a seat`() {
        assertEquals(
            AllianceBalance.seatCap(AllianceLevel(0), seatsBought = 0) + AllianceBalance.SEATS_PER_LEVEL,
            AllianceBalance.seatCap(AllianceLevel(1), seatsBought = 0),
        )
    }

    @Test
    fun `a bought charter widens the roster by two`() {
        assertEquals(
            AllianceBalance.seatCap(AllianceLevel(0), seatsBought = 0) + AllianceBalance.SEATS_PER_CHARTER,
            AllianceBalance.seatCap(AllianceLevel(0), seatsBought = 1),
        )
    }

    // The roof over the sum, so no ladder and no purchase grows a roster past what the screen was
    // drawn for.
    @Test
    fun `nothing grows the roster past the cap the screen was drawn for`() {
        assertEquals(AllianceBalance.SEAT_CAP, AllianceBalance.seatCap(AllianceLevel(99), seatsBought = 99))
    }

    // **A project that cannot move its own number is absent rather than offered and refused** — an
    // entry that takes the pool and does nothing is the dead control wearing a price tag.
    @Test
    fun `the charter stops being on sale once the roster is at its ceiling`() {
        assertTrue(
            AllianceBalance.isExhausted(AllianceProject.CHARTER_EXPANSION, AllianceLevel(99), seatsBought = 99),
        )
        assertTrue(
            !AllianceBalance.isExhausted(AllianceProject.CHARTER_EXPANSION, AllianceLevel(0), seatsBought = 0),
        )
    }

    // **The roof over the walk.** `progressOf` loops over an alliance-supplied number, so the bound
    // is what makes it a total function rather than one that is merely unlikely to run long — and the
    // gauge reads full-but-not-past rather than overflowing its own span.
    @Test
    fun `a treasury nothing could fill still reads as a level and a share of one`() {
        val progress = AllianceBalance.progressOf(Long.MAX_VALUE / 2)

        assertTrue(progress.intoLevel < progress.span, "the gauge ran past its own end")
        assertTrue(progress.level.value > 0)
    }

    @Test
    fun `a negative total is refused rather than read as a level`() {
        assertFailsWith<IllegalArgumentException> { AllianceBalance.progressOf(-1) }
    }

    // The same roof on the price curve, which is the other loop over a stored number. Unreachable by
    // play — the seat cap stops the charter long before this — and a bound all the same.
    @Test
    fun `a charter bought absurdly often still has a price`() {
        val cost = AllianceBalance.costOf(AllianceProject.CHARTER_EXPANSION, timesBought = 4_000)

        assertTrue(AllianceBalance.award(cost) > 0)
    }

    @Test
    fun `each charter costs more than the last`() {
        var last = 0L
        for (bought in 0..8) {
            val cost = AllianceBalance.costOf(AllianceProject.CHARTER_EXPANSION, bought)
            val priced = AllianceBalance.award(cost)
            assertTrue(priced > last, "buying $bought did not cost more than $last")
            last = priced
        }
    }
}
