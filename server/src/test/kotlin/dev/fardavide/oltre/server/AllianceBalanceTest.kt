package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.AllianceSpeedup
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

    // ── What the level takes off a wait ──────────────────────────────────────────────────────
    //
    // **The one place the alliance's level becomes a number `core` understands.** `core` never
    // learns an alliance exists — it is handed a percentage — so this is the whole of the
    // translation, and the ceiling on `AllianceSpeedup` is what stops it ever mattering how far
    // the ladder is walked.

    @Test
    fun `an alliance at level zero takes nothing off`() {
        assertEquals(AllianceSpeedup.NONE, AllianceBalance.speedupOf(earned = 0, logisticsBought = 0))
    }

    @Test
    fun `two percent a level, up to the floor at fifteen`() {
        assertEquals(2, speedupAtLevel(1).percent)
        assertEquals(14, speedupAtLevel(7).percent)
        assertEquals(30, speedupAtLevel(15).percent)
    }

    // **The ladder runs to 200 and the boon stops at 15**, so every level past it buys seats and
    // standing and no more speed. Asserted rather than assumed, because the clamp is the only thing
    // between a very old alliance and a build that serves instantly.
    @Test
    fun `no alliance can take off more than the floor, however far the ladder is walked`() {
        assertEquals(30, speedupAtLevel(40).percent)
        assertEquals(30, AllianceBalance.speedupOf(Long.MAX_VALUE / 2, logisticsBought = 0).percent)
    }

    // The walk in `progressOf` is what decides the level, so the boon has to move on exactly the
    // same boundary the gauge does — an alliance one point short of a level takes off what the level
    // below it does.
    @Test
    fun `the boon moves on the same boundary the gauge does`() {
        val atSeven = experienceForLevel(7)

        assertEquals(14, AllianceBalance.speedupOf(atSeven, logisticsBought = 0).percent)
        assertEquals(12, AllianceBalance.speedupOf(atSeven - 1, logisticsBought = 0).percent)
    }

    // ── What the catalogue takes off on top of it ────────────────────────────────────────────
    //
    // **One number, two sources.** A colony is told what comes off its next build and never by what
    // it was earned, which is `#167`'s call re-applied: a second field would be a second schema hop
    // carrying a distinction nothing renders.

    @Test
    fun `a bought logistics is a point on top of the level`() {
        assertEquals(14, speedupAtLevel(7, logistics = 0).percent)
        assertEquals(15, speedupAtLevel(7, logistics = 1).percent)
        assertEquals(18, speedupAtLevel(7, logistics = 4).percent)
    }

    // **The floor is one floor over both sources and not one each.** Membership becoming mandatory
    // rather than attractive is `alliance-sheet.md` §4.3's stated risk, and a catalogue that could be
    // bought past the ceiling the level respects is exactly how the ceiling stops meaning anything.
    @Test
    fun `buying cannot take an alliance past the floor the level stops at`() {
        assertEquals(30, speedupAtLevel(15, logistics = 20).percent)
        assertEquals(30, speedupAtLevel(0, logistics = 99).percent)
        assertEquals(30, speedupAtLevel(40, logistics = 40).percent)
    }

    // A column can only be incremented by a purchase, so this is unreachable by play — and it is on
    // the hot path of every sync, where an `IllegalArgumentException` inside `AllianceSpeedup` is a
    // 500 on a colony read rather than an odd number on a screen.
    @Test
    fun `a count that could not have happened still reads as a boon`() {
        assertEquals(0, speedupAtLevel(0, logistics = -5).percent)
    }

    private fun speedupAtLevel(level: Int, logistics: Int = 0): AllianceSpeedup =
        AllianceBalance.speedupAt(AllianceLevel(level), logistics)

    private fun experienceForLevel(level: Int): Long {
        var earned = 0L
        repeat(level) { earned += AllianceBalance.spanOf(AllianceLevel(it)) }
        return earned
    }

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
        assertTrue(exhausted(AllianceProject.CHARTER_EXPANSION, level = 99, ProjectsBought(seats = 99)))
        assertTrue(!exhausted(AllianceProject.CHARTER_EXPANSION, level = 0, ProjectsBought.NONE))
    }

    // The same rule from the other entry's side: a pool that cannot make the number move is offered
    // no row rather than a row it would be refused at.
    @Test
    fun `the logistics stops being on sale once the boon is at the floor`() {
        assertTrue(!exhausted(AllianceProject.SHARED_LOGISTICS, level = 0, ProjectsBought.NONE))
        assertTrue(exhausted(AllianceProject.SHARED_LOGISTICS, level = 15, ProjectsBought.NONE))
        assertTrue(exhausted(AllianceProject.SHARED_LOGISTICS, level = 10, ProjectsBought(logistics = 10)))
    }

    // **The two entries run out independently**, which is what having one tally per project is for: a
    // roster at its ceiling is still allowed to buy speed, and an alliance at the speed floor is still
    // allowed to buy seats.
    @Test
    fun `one exhausted entry does not take the other off the shelf`() {
        val atTheSeatCap = ProjectsBought(seats = 99)
        assertTrue(exhausted(AllianceProject.CHARTER_EXPANSION, level = 0, atTheSeatCap))
        assertTrue(!exhausted(AllianceProject.SHARED_LOGISTICS, level = 0, atTheSeatCap))

        val atTheFloor = ProjectsBought(logistics = 30)
        assertTrue(exhausted(AllianceProject.SHARED_LOGISTICS, level = 0, atTheFloor))
        assertTrue(!exhausted(AllianceProject.CHARTER_EXPANSION, level = 0, atTheFloor))
    }

    private fun exhausted(project: AllianceProject, level: Int, bought: ProjectsBought): Boolean =
        AllianceBalance.isExhausted(project, AllianceLevel(level), bought)

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

    // Every entry rides the same ×1.5, so this is asserted over the catalogue rather than over the
    // one row that happened to be in it — a third entry gets the property for free and cannot ship a
    // flat curve by omission.
    @Test
    fun `each purchase costs more than the last, whichever entry it is`() {
        for (project in AllianceProject.entries) {
            var last = 0L
            for (bought in 0..8) {
                val priced = AllianceBalance.award(AllianceBalance.costOf(project, bought))
                assertTrue(priced > last, "$project bought $bought did not cost more than $last")
                last = priced
            }
        }
    }

    // **The premium is the only thing about `LOGISTICS_BASE` worth pinning**, and it is a shape
    // rather than a figure: contributions climb the ladder on their own and the ladder already hands
    // out speed, so an entry cheaper per point than levelling would make levelling the slow way to do
    // what the level is for. The balance round may move both numbers; it may not invert them.
    @Test
    fun `buying a point of speed costs more than levelling into one`() {
        val bought = AllianceBalance.award(AllianceBalance.costOf(AllianceProject.SHARED_LOGISTICS, timesBought = 0))
        val levelled = AllianceBalance.spanOf(AllianceLevel(0)) / AllianceBalance.SPEEDUP_PER_LEVEL

        assertTrue(bought > levelled, "a bought point at $bought undercuts a levelled one at $levelled")
    }

    // Each entry prices off its own count, which is the whole reason `ProjectsBought` is a type: a
    // charter bought four times must not make the first logistics dearer.
    @Test
    fun `an entry is priced by its own purchases and not by the catalogues`() {
        val bought = ProjectsBought(seats = 4, logistics = 0)

        assertEquals(
            AllianceBalance.costOf(AllianceProject.SHARED_LOGISTICS, timesBought = 0),
            AllianceBalance.costOf(AllianceProject.SHARED_LOGISTICS, bought.timesBought(AllianceProject.SHARED_LOGISTICS)),
        )
    }

    // A count that could not have happened prices as the first one rather than raising inside
    // `Resources.of`, for `speedupAt`'s reason: this runs on a route.
    @Test
    fun `a negative count prices as the first purchase`() {
        assertEquals(
            AllianceBalance.CHARTER_BASE,
            AllianceBalance.costOf(AllianceProject.CHARTER_EXPANSION, timesBought = -3),
        )
    }
}
