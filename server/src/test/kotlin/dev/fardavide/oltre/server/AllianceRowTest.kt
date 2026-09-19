package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.AllianceLevel
import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceSeats
import dev.fardavide.oltre.protocol.AllianceTag
import kotlin.test.Test
import kotlin.test.assertEquals

class AllianceRowTest {

    @Test
    fun `a stored alliance row carries its version and authoritative seat count`() {
        val stored = allianceFrom(ID, NAME, TAG, AllianceVersion(3), TEST_NOW, experience = 0, taken = 3)

        assertEquals(AllianceVersion(3), stored.version)
        assertEquals(TEST_NOW, stored.createdAt)
        assertEquals(AllianceSeats(3, OPENING_SEATS), stored.alliance.seats)
    }

    // **The level is derived from the stored total rather than written as zero**, which is the
    // treasury arriving in this function: it wrote `AllianceLevel(0)` unconditionally until now, with
    // a comment saying the balance slice would replace it.
    @Test
    fun `the level is the one the stored experience buys`() {
        val opening = allianceFrom(ID, NAME, TAG, AllianceVersion.FIRST, TEST_NOW, experience = 0, taken = 1)
        val climbed = allianceFrom(
            ID,
            NAME,
            TAG,
            AllianceVersion.FIRST,
            TEST_NOW,
            experience = AllianceBalance.spanOf(AllianceLevel(0)),
            taken = 1,
        )

        assertEquals(AllianceLevel(0), opening.alliance.level)
        assertEquals(AllianceLevel(1), climbed.alliance.level)
    }

    @Test
    fun `a level and a bought charter each widen the roster`() {
        val earned = allianceFrom(
            ID,
            NAME,
            TAG,
            AllianceVersion.FIRST,
            TEST_NOW,
            experience = AllianceBalance.spanOf(AllianceLevel(0)),
            taken = 1,
        )
        val bought = allianceFrom(
            ID,
            NAME,
            TAG,
            AllianceVersion.FIRST,
            TEST_NOW,
            experience = 0,
            taken = 1,
            projects = ProjectsBought(seats = 1),
        )

        assertEquals(OPENING_SEATS + AllianceBalance.SEATS_PER_LEVEL, earned.alliance.seats.cap)
        assertEquals(OPENING_SEATS + AllianceBalance.SEATS_PER_CHARTER, bought.alliance.seats.cap)
    }

    // **The residual `Alliance.kt` names beside `AllianceSeats`' own guard, paid here.** That type
    // refuses to be built with more seats taken than its cap, so a roster that grew under a wider cap
    // — or one a balance round narrows — would make the whole response *undecodable* rather than
    // merely odd. The clamp is what turns "this alliance is over its cap" into a readable fact.
    @Test
    fun `a roster wider than its own cap reads as full rather than refusing to decode`() {
        val crowded = allianceFrom(ID, NAME, TAG, AllianceVersion.FIRST, TEST_NOW, experience = 0, taken = 12)

        assertEquals(AllianceSeats(12, 12), crowded.alliance.seats)
    }

    @Test
    fun `the pool and what it has bought ride on the row`() {
        val pool = Resources.of(metal = 486_300, crystal = 232_900, deuterium = 71_400)

        val stored = allianceFrom(
            ID,
            NAME,
            TAG,
            AllianceVersion.FIRST,
            TEST_NOW,
            experience = 0,
            taken = 1,
            pool = pool,
            projects = ProjectsBought(seats = 2, logistics = 3),
        )

        assertEquals(pool, stored.pool)
        // Both counters, because a row that carried the seats and dropped the speed would leave a
        // paid-for boon on the floor with nothing to say so.
        assertEquals(ProjectsBought(seats = 2, logistics = 3), stored.projects)
    }

    private companion object {
        val ID = AllianceId("vanguard")
        val NAME = AllianceName("Vanguard")
        val TAG = AllianceTag("VNG")
    }
}
