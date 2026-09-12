package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.AllianceMemberId
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceTag
import dev.fardavide.oltre.protocol.JoinRequestId
import dev.fardavide.oltre.protocol.JoinDecision
import dev.fardavide.oltre.protocol.ApiError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.nanoseconds

class AllianceRulesTest {

    @Test
    fun `answering a missing petition refuses without changing pending requests`() {
        val fixture = AllianceFixture()
        val before = fixture.requests.toList()

        val verdict = AllianceRules.approval(fixture.caller, fixture.alliance, fixture.requests, JoinRequestId("missing"), JoinDecision.ADMITTED, emptySet())

        assertEquals(ApprovalVerdict.Refused(ApiError.NoSuchAlliance), verdict)
        assertEquals(before, fixture.requests)
    }

    @Test
    fun `answering another alliances petition refuses despite a matching request id`() {
        val fixture = AllianceFixture()
        val foreign = fixture.petition.copy(alliance = AllianceId("other"))
        val requests = mutableListOf(foreign)

        val verdict = AllianceRules.approval(fixture.caller, fixture.alliance, requests, foreign.id, JoinDecision.ADMITTED, emptySet())

        assertEquals(ApprovalVerdict.Refused(ApiError.NoSuchAlliance), verdict)
        assertEquals(listOf(foreign), requests)
    }

    @Test
    fun `admission refuses an applicant who already holds a seat and preserves their petition`() {
        val fixture = AllianceFixture()
        val before = fixture.requests.toList()
        val seated = setOf(fixture.petition.player)

        val verdict = AllianceRules.approval(fixture.caller, fixture.alliance, fixture.requests, fixture.petition.id, JoinDecision.ADMITTED, seated)

        assertEquals(ApprovalVerdict.Refused(ApiError.AlreadyInAnAlliance), verdict)
        assertEquals(before, fixture.requests)
        assertEquals(setOf(fixture.petition.player), seated)
    }

    @Test
    fun `setting the role of a missing member refuses and preserves the existing seats`() {
        val fixture = AllianceFixture()
        val before = fixture.seats.toList()

        val verdict = AllianceRules.settingRole(fixture.caller, fixture.alliance, fixture.seats, AllianceMemberId("missing"), AllianceRole.ADMIN)

        assertEquals(RoleVerdict.Refused(ApiError.NoSuchAlliance), verdict)
        assertEquals(before, fixture.seats)
    }

    @Test
    fun `setting a foreign members role refuses despite a matching member id`() {
        val fixture = AllianceFixture()
        val foreign = fixture.member.copy(alliance = AllianceId("other"))
        val seats = mutableListOf(fixture.founder, foreign)

        val verdict = AllianceRules.settingRole(fixture.caller, fixture.alliance, seats, foreign.id, AllianceRole.ADMIN)

        assertEquals(RoleVerdict.Refused(ApiError.NoSuchAlliance), verdict)
        assertEquals(listOf(fixture.founder, foreign), seats)
    }

    @Test
    fun `removing a missing member refuses and preserves the existing seats`() {
        val fixture = AllianceFixture()
        val before = fixture.seats.toList()

        val verdict = AllianceRules.removing(fixture.caller, fixture.alliance, fixture.seats, AllianceMemberId("missing"))

        assertEquals(RemovalVerdict.Refused(ApiError.NoSuchAlliance), verdict)
        assertEquals(before, fixture.seats)
    }

    @Test
    fun `removing a foreign member refuses despite a matching member id`() {
        val fixture = AllianceFixture()
        val foreign = fixture.member.copy(alliance = AllianceId("other"))
        val seats = mutableListOf(fixture.founder, foreign)

        val verdict = AllianceRules.removing(fixture.caller, fixture.alliance, seats, foreign.id)

        assertEquals(RemovalVerdict.Refused(ApiError.NoSuchAlliance), verdict)
        assertEquals(listOf(fixture.founder, foreign), seats)
    }

    @Test
    fun `a member cannot remove another member`() {
        val fixture = AllianceFixture()
        val caller = Affiliation.Enlisted(fixture.alliance, fixture.member)
        val target = fixture.member.copy(id = AllianceMemberId("target-seat"), player = PlayerId("target"))
        val seats = mutableListOf(fixture.founder, fixture.member, target)
        val before = seats.toList()

        val verdict = AllianceRules.removing(caller, fixture.alliance, seats, target.id)

        assertEquals(RemovalVerdict.Refused(ApiError.AllianceRoleTooLow), verdict)
        assertEquals(before, seats)
    }

    @Test
    fun `an unaffiliated caller cannot remove a member`() {
        val fixture = AllianceFixture()
        val before = fixture.seats.toList()

        val verdict = AllianceRules.removing(Affiliation.Unaffiliated, fixture.alliance, fixture.seats, fixture.member.id)

        assertEquals(RemovalVerdict.Refused(ApiError.NotInAnAlliance), verdict)
        assertEquals(before, fixture.seats)
    }

    @Test
    fun `a pending applicant cannot remove a member`() {
        val fixture = AllianceFixture()
        val caller = Affiliation.Petitioning(fixture.alliance, fixture.petition)
        val before = fixture.seats.toList()

        val verdict = AllianceRules.removing(caller, fixture.alliance, fixture.seats, fixture.member.id)

        assertEquals(RemovalVerdict.Refused(ApiError.NotInAnAlliance), verdict)
        assertEquals(before, fixture.seats)
        assertEquals(fixture.petition, caller.petition)
    }

    @Test
    fun `an unaffiliated caller cannot detach from an alliance`() {
        val fixture = AllianceFixture()

        assertEquals(DetachVerdict.Refused(ApiError.NotInAnAlliance), AllianceRules.detaching(Affiliation.Unaffiliated, fixture.alliance.alliance.id))
    }

    @Test
    fun `a pending applicant cannot withdraw from another alliance`() {
        val fixture = AllianceFixture()
        val caller = Affiliation.Petitioning(fixture.alliance, fixture.petition)

        val verdict = AllianceRules.detaching(caller, AllianceId("other"))

        assertEquals(DetachVerdict.Refused(ApiError.AllianceRoleTooLow), verdict)
        assertEquals(fixture.petition, caller.petition)
    }

    @Test
    fun `an admin cannot rename the alliance`() {
        val fixture = AllianceFixture()
        val caller = Affiliation.Enlisted(fixture.alliance, fixture.member.copy(role = AllianceRole.ADMIN))
        val occupied = mutableListOf(fixture.alliance)

        val verdict = AllianceRules.renaming(caller, fixture.alliance, AllianceName("Horizon"), AllianceTag("HRZ"), occupied)

        assertEquals(AllianceAuthority.Refused(ApiError.AllianceRoleTooLow), verdict)
        assertEquals(listOf(fixture.alliance), occupied)
        assertEquals(fixture.alliance, caller.alliance)
    }

    @Test
    fun `an admin lacks founder authority to disband the alliance`() {
        val fixture = AllianceFixture()
        val caller = Affiliation.Enlisted(fixture.alliance, fixture.member.copy(role = AllianceRole.ADMIN))

        assertEquals(AllianceAuthority.Refused(ApiError.AllianceRoleTooLow), AllianceRules.founderAuthority(caller, fixture.alliance.alliance.id))
        assertEquals(fixture.alliance, caller.alliance)
    }

    @Test
    fun `an enlisted founder cannot retry founding with the same folded name and a different tag`() {
        val fixture = AllianceFixture()

        val verdict = AllianceRules.founding(fixture.caller, AllianceName("VANGUARD"), AllianceTag("NEW"), listOf(fixture.alliance))

        assertEquals(FoundingVerdict.Refused(ApiError.AlreadyInAnAlliance), verdict)
        assertEquals(fixture.alliance, fixture.caller.alliance)
        assertEquals(fixture.founder, fixture.caller.seat)
    }

    @Test
    fun `rename refuses another alliances folded name or tag while allowing its own spelling`() {
        val target = allianceFrom(AllianceId("vanguard"), AllianceName("Vanguard"), AllianceTag("VNG"), AllianceVersion.FIRST, TEST_NOW, 0, 1)
        val rival = target.copy(alliance = target.alliance.copy(id = AllianceId("rival"), name = AllianceName("Straße  Fleet"), tag = AllianceTag("RIV")))
        val founder = Seat(AllianceMemberId("founder-seat"), PlayerId("founder"), target.alliance.id, AllianceRole.FOUNDER, TEST_NOW, 0)
        val caller = Affiliation.Enlisted(target, founder)
        val occupied = listOf(target, rival)

        assertEquals(ApiError.AllianceNameTaken, assertIs<AllianceAuthority.Refused>(AllianceRules.renaming(caller, target, AllianceName("STRASSE\tFLEET"), target.alliance.tag, occupied)).error)
        assertEquals(ApiError.AllianceTagTaken, assertIs<AllianceAuthority.Refused>(AllianceRules.renaming(caller, target, target.alliance.name, rival.alliance.tag, occupied)).error)
        assertEquals(AllianceAuthority.Granted, AllianceRules.renaming(caller, target, AllianceName("VANGUARD"), target.alliance.tag, occupied))
    }

    @Test
    fun `a founder cannot relinquish their role without selecting a successor`() {
        val alliance = allianceFrom(AllianceId("vanguard"), AllianceName("Vanguard"), AllianceTag("VNG"), AllianceVersion.FIRST, TEST_NOW, 0, 1)
        val founder = Seat(AllianceMemberId("founder-seat"), PlayerId("founder"), alliance.alliance.id, AllianceRole.FOUNDER, TEST_NOW, 0)
        for (role in listOf(AllianceRole.ADMIN, AllianceRole.MEMBER)) {
            assertEquals(ApiError.AllianceRoleTooLow, assertIs<RoleVerdict.Refused>(AllianceRules.settingRole(Affiliation.Enlisted(alliance, founder), alliance, listOf(founder), founder.id, role)).error)
        }
    }

    @Test
    fun `handing leadership over promotes the target and makes the old founder an admin`() {
        val alliance = allianceFrom(AllianceId("vanguard"), AllianceName("Vanguard"), AllianceTag("VNG"), AllianceVersion.FIRST, TEST_NOW, 0, 2)
        val founder = Seat(AllianceMemberId("founder-seat"), PlayerId("founder"), alliance.alliance.id, AllianceRole.FOUNDER, TEST_NOW, 0)
        val member = founder.copy(id = AllianceMemberId("member-seat"), player = PlayerId("member"), role = AllianceRole.MEMBER)

        val verdict = assertIs<RoleVerdict.Change>(AllianceRules.settingRole(Affiliation.Enlisted(alliance, founder), alliance, listOf(founder, member), member.id, AllianceRole.FOUNDER))

        assertEquals(listOf(founder.copy(role = AllianceRole.ADMIN), member.copy(role = AllianceRole.FOUNDER)), verdict.seats)
    }

    @Test
    fun `only the target alliances founder may set member roles`() {
        val alliance = allianceFrom(AllianceId("vanguard"), AllianceName("Vanguard"), AllianceTag("VNG"), AllianceVersion.FIRST, TEST_NOW, 0, 2)
        val seat = Seat(AllianceMemberId("caller-seat"), PlayerId("caller"), alliance.alliance.id, AllianceRole.FOUNDER, TEST_NOW, 0)
        val member = seat.copy(id = AllianceMemberId("member-seat"), player = PlayerId("member"), role = AllianceRole.MEMBER)
        for (role in listOf(AllianceRole.ADMIN, AllianceRole.MEMBER)) {
            assertEquals(ApiError.AllianceRoleTooLow, assertIs<RoleVerdict.Refused>(AllianceRules.settingRole(Affiliation.Enlisted(alliance, seat.copy(role = role)), alliance, listOf(seat, member), member.id, AllianceRole.ADMIN)).error)
        }
        assertEquals(ApiError.NotInAnAlliance, assertIs<RoleVerdict.Refused>(AllianceRules.settingRole(Affiliation.Unaffiliated, alliance, listOf(seat, member), member.id, AllianceRole.ADMIN)).error)
        val other = alliance.copy(alliance = alliance.alliance.copy(id = AllianceId("other")))
        assertEquals(ApiError.AllianceRoleTooLow, assertIs<RoleVerdict.Refused>(AllianceRules.settingRole(Affiliation.Enlisted(other, seat.copy(alliance = other.alliance.id)), alliance, listOf(seat, member), member.id, AllianceRole.ADMIN)).error)
    }

    @Test
    fun `a full alliance refuses admission but still allows declining a request`() {
        val alliance = allianceFrom(AllianceId("vanguard"), AllianceName("Vanguard"), AllianceTag("VNG"), AllianceVersion.FIRST, TEST_NOW, 0, 20)
        val seat = Seat(AllianceMemberId("seat"), PlayerId("caller"), alliance.alliance.id, AllianceRole.FOUNDER, TEST_NOW, 0)
        val petition = Petition(JoinRequestId("petition"), PlayerId("applicant"), alliance.alliance.id, TEST_NOW)
        val caller = Affiliation.Enlisted(alliance, seat)

        assertEquals(ApiError.AllianceFull, assertIs<ApprovalVerdict.Refused>(AllianceRules.approval(caller, alliance, listOf(petition), petition.id, JoinDecision.ADMITTED, emptySet())).error)
        assertIs<ApprovalVerdict.Decline>(AllianceRules.approval(caller, alliance, listOf(petition), petition.id, JoinDecision.DECLINED, emptySet()))
    }

    @Test
    fun `only a founder or admin in the target alliance may answer petitions`() {
        val alliance = allianceFrom(AllianceId("vanguard"), AllianceName("Vanguard"), AllianceTag("VNG"), AllianceVersion.FIRST, TEST_NOW, 0, 1)
        val petition = Petition(JoinRequestId("petition"), PlayerId("applicant"), alliance.alliance.id, TEST_NOW)
        val seat = Seat(AllianceMemberId("seat"), PlayerId("caller"), alliance.alliance.id, AllianceRole.FOUNDER, TEST_NOW, 0)
        for (role in listOf(AllianceRole.FOUNDER, AllianceRole.ADMIN)) {
            assertIs<ApprovalVerdict.Admit>(AllianceRules.approval(Affiliation.Enlisted(alliance, seat.copy(role = role)), alliance, listOf(petition), petition.id, JoinDecision.ADMITTED, emptySet()))
        }
        assertEquals(ApiError.AllianceRoleTooLow, assertIs<ApprovalVerdict.Refused>(AllianceRules.approval(Affiliation.Enlisted(alliance, seat.copy(role = AllianceRole.MEMBER)), alliance, listOf(petition), petition.id, JoinDecision.ADMITTED, emptySet())).error)
        assertEquals(ApiError.NotInAnAlliance, assertIs<ApprovalVerdict.Refused>(AllianceRules.approval(Affiliation.Unaffiliated, alliance, listOf(petition), petition.id, JoinDecision.ADMITTED, emptySet())).error)
        val other = alliance.copy(alliance = alliance.alliance.copy(id = AllianceId("other")))
        assertEquals(ApiError.AllianceRoleTooLow, assertIs<ApprovalVerdict.Refused>(AllianceRules.approval(Affiliation.Enlisted(other, seat.copy(alliance = other.alliance.id)), alliance, listOf(petition), petition.id, JoinDecision.ADMITTED, emptySet())).error)
    }

    @Test
    fun `a pending petitioner must withdraw before founding an alliance`() {
        val player = PlayerId("petitioner")
        val alliance = allianceFrom(AllianceId("vanguard"), AllianceName("Vanguard"), AllianceTag("VNG"), AllianceVersion.FIRST, TEST_NOW, 0, 1)
        val pending = Petition(JoinRequestId("petition"), player, alliance.alliance.id, TEST_NOW)

        val verdict = AllianceRules.founding(Affiliation.Petitioning(alliance, pending), AllianceName("Other"), AllianceTag("OTH"), emptyList())

        assertEquals(ApiError.AlreadyInAnAlliance, assertIs<FoundingVerdict.Refused>(verdict).error)
    }

    @Test
    fun `succession chooses the longest serving active admin with player id breaking ties`() {
        val alliance = AllianceId("vanguard")
        val inactiveFounder = Seat(
            AllianceMemberId("founder-seat"), PlayerId("founder"), alliance,
            AllianceRole.FOUNDER, TEST_NOW - 100.days, 0,
        )
        val inactiveOlderAdmin = Seat(
            AllianceMemberId("inactive-seat"), PlayerId("inactive"), alliance,
            AllianceRole.ADMIN, TEST_NOW - 90.days, 0,
        )
        val tiedLaterId = Seat(
            AllianceMemberId("zeta-seat"), PlayerId("zeta"), alliance,
            AllianceRole.ADMIN, TEST_NOW - 60.days, 0,
        )
        val expected = Seat(
            AllianceMemberId("alpha-seat"), PlayerId("alpha"), alliance,
            AllianceRole.ADMIN, TEST_NOW - 60.days, 0,
        )
        val newerAdmin = Seat(
            AllianceMemberId("aardvark-seat"), PlayerId("aardvark"), alliance,
            AllianceRole.ADMIN, TEST_NOW - 10.days, 0,
        )

        val choice = AllianceRules.successor(
            seats = listOf(inactiveFounder, inactiveOlderAdmin, tiedLaterId, newerAdmin, expected),
            active = setOf(tiedLaterId.player, expected.player, newerAdmin.player),
        )

        assertEquals(expected, assertIs<SuccessionChoice.Promote>(choice).seat)
    }

    @Test
    fun `a sync exactly thirty days ago is active and an older sync is inactive`() {
        assertTrue(AllianceRules.isActive(TEST_NOW - 30.days, TEST_NOW))
        assertFalse(AllianceRules.isActive(TEST_NOW - 30.days - 1.nanoseconds, TEST_NOW))
    }

    @Test
    fun `succession leaves leadership unchanged when everybody is inactive`() {
        val seat = Seat(AllianceMemberId("admin-seat"), PlayerId("admin"), AllianceId("vanguard"), AllianceRole.ADMIN, TEST_NOW, 0)

        assertEquals(SuccessionChoice.Unchanged, AllianceRules.successor(listOf(seat), emptySet()))
    }

    @Test
    fun `an admin may remove members but not admins or the founder`() {
        assertTrue(AllianceRules.canRemove(AllianceRole.ADMIN, AllianceRole.MEMBER))
        assertFalse(AllianceRules.canRemove(AllianceRole.ADMIN, AllianceRole.ADMIN))
        assertFalse(AllianceRules.canRemove(AllianceRole.ADMIN, AllianceRole.FOUNDER))
    }

    @Test
    fun `an active founder retains leadership despite eligible admins`() {
        val founder = Seat(AllianceMemberId("founder-seat"), PlayerId("founder"), AllianceId("vanguard"), AllianceRole.FOUNDER, TEST_NOW, 0)
        val admin = founder.copy(id = AllianceMemberId("admin-seat"), player = PlayerId("admin"), role = AllianceRole.ADMIN)

        assertEquals(SuccessionChoice.Unchanged, AllianceRules.successor(listOf(founder, admin), setOf(founder.player, admin.player)))
    }

    @Test
    fun `the founder may remove admins and members but may not remove a founder`() {
        assertTrue(AllianceRules.canRemove(AllianceRole.FOUNDER, AllianceRole.ADMIN))
        assertTrue(AllianceRules.canRemove(AllianceRole.FOUNDER, AllianceRole.MEMBER))
        assertFalse(AllianceRules.canRemove(AllianceRole.FOUNDER, AllianceRole.FOUNDER))
    }

    @Test
    fun `without active admins the largest active contributor succeeds with player id breaking ties`() {
        val template = Seat(AllianceMemberId("inactive-seat"), PlayerId("inactive"), AllianceId("vanguard"), AllianceRole.MEMBER, TEST_NOW, 1_000)
        val tiedLaterId = template.copy(id = AllianceMemberId("zeta-seat"), player = PlayerId("zeta"), contributed = 100)
        val expected = template.copy(id = AllianceMemberId("alpha-seat"), player = PlayerId("alpha"), contributed = 100)
        val smaller = template.copy(id = AllianceMemberId("aardvark-seat"), player = PlayerId("aardvark"), contributed = 50)

        val choice = AllianceRules.successor(listOf(template, tiedLaterId, smaller, expected), setOf(expected.player, tiedLaterId.player, smaller.player))

        assertEquals(expected, assertIs<SuccessionChoice.Promote>(choice).seat)
    }

    @Test
    fun `transferring leadership demotes the absent founder to admin and preserves every other seat`() {
        val founder = Seat(AllianceMemberId("founder-seat"), PlayerId("founder"), AllianceId("vanguard"), AllianceRole.FOUNDER, TEST_NOW - 100.days, 250)
        val successor = founder.copy(id = AllianceMemberId("successor-seat"), player = PlayerId("successor"), role = AllianceRole.MEMBER, contributed = 100)
        val bystander = founder.copy(id = AllianceMemberId("bystander-seat"), player = PlayerId("bystander"), role = AllianceRole.MEMBER)

        val transferred = AllianceRules.transferLeadership(listOf(founder, successor, bystander), successor.id)

        assertEquals(listOf(founder.copy(role = AllianceRole.ADMIN), successor.copy(role = AllianceRole.FOUNDER), bystander), transferred)
    }
    private class AllianceFixture {

        val alliance: StoredAlliance = allianceFrom(AllianceId("vanguard"), AllianceName("Vanguard"), AllianceTag("VNG"), AllianceVersion.FIRST, TEST_NOW, 0, 2)
        val founder: Seat = Seat(AllianceMemberId("founder-seat"), PlayerId("founder"), alliance.alliance.id, AllianceRole.FOUNDER, TEST_NOW, 10)
        val member: Seat = founder.copy(id = AllianceMemberId("member-seat"), player = PlayerId("member"), role = AllianceRole.MEMBER, contributed = 5)
        val petition: Petition = Petition(JoinRequestId("petition"), PlayerId("applicant"), alliance.alliance.id, TEST_NOW)
        val caller: Affiliation.Enlisted = Affiliation.Enlisted(alliance, founder)
        val seats: MutableList<Seat> = mutableListOf(founder, member)
        val requests: MutableList<Petition> = mutableListOf(petition)
    }
}
