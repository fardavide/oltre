package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.AllianceMemberId
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceSeats
import dev.fardavide.oltre.protocol.AllianceTag
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.JoinDecision
import dev.fardavide.oltre.protocol.JoinRequestId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.days

class InMemoryAllianceRepositoryTest {

    private val colonies = InMemoryColonyRepository()
    private val repository = InMemoryAllianceRepository(colonies)
    private val founder = PlayerId("founder")

    @Test
    fun `a player without a seat reads as unaffiliated`() = runTest {
        assertEquals(Affiliation.Unaffiliated, repository.allianceOf(founder, TEST_NOW))
    }

    @Test
    fun `founding stores an alliance and its founder seat together`() = runTest {
        val made = assertIs<Founded.Made>(repository.found(founder, NAME, TAG, TEST_NOW))

        val enlisted = assertIs<Affiliation.Enlisted>(repository.allianceOf(founder, TEST_NOW))
        assertEquals(made.alliance, enlisted.alliance)
        assertEquals(AllianceRole.FOUNDER, enlisted.seat.role)
        assertEquals(AllianceSeats(1, 20), enlisted.alliance.alliance.seats)
        assertEquals(NAME, enlisted.alliance.alliance.name)
        assertEquals(TAG, enlisted.alliance.alliance.tag)
    }

    @Test
    fun `retrying founding returns the alliance already owned by the caller`() = runTest {
        val first = assertIs<Founded.Made>(repository.found(founder, NAME, TAG, TEST_NOW))

        val again = assertIs<Founded.AlreadyFounded>(repository.found(founder, NAME, TAG, TEST_NOW))

        assertEquals(first.alliance, again.alliance)
        assertEquals(first.alliance, assertIs<Affiliation.Enlisted>(repository.allianceOf(founder, TEST_NOW)).alliance)
    }

    @Test
    fun `founding refuses a name differing only by Unicode case and whitespace`() = runTest {
        repository.found(founder, AllianceName("Straße  Fleet"), TAG, TEST_NOW)

        val refused = assertIs<Founded.Refused>(
            repository.found(PlayerId("rival"), AllianceName("STRASSE\tFLEET"), AllianceTag("RIV"), TEST_NOW),
        )

        assertEquals(ApiError.AllianceNameTaken, refused.error)
        assertEquals(Affiliation.Unaffiliated, repository.allianceOf(PlayerId("rival"), TEST_NOW))
    }

    @Test
    fun `founding refuses a tag held by another alliance`() = runTest {
        repository.found(founder, NAME, TAG, TEST_NOW)

        val refused = assertIs<Founded.Refused>(repository.found(PlayerId("rival"), AllianceName("Rival"), TAG, TEST_NOW))

        assertEquals(ApiError.AllianceTagTaken, refused.error)
    }

    @Test
    fun `an existing founder cannot found a different alliance`() = runTest {
        repository.found(founder, NAME, TAG, TEST_NOW)

        val refused = assertIs<Founded.Refused>(repository.found(founder, AllianceName("Other"), AllianceTag("OTH"), TEST_NOW))

        assertEquals(ApiError.AlreadyInAnAlliance, refused.error)
    }

    @Test
    fun `founding reclaims the name and tag of an alliance with no seats`() = runTest {
        val old = assertIs<Founded.Made>(repository.found(founder, NAME, TAG, TEST_NOW))
        repository.forget(founder)

        val replacement = assertIs<Founded.Made>(repository.found(PlayerId("rival"), NAME, TAG, TEST_NOW))

        kotlin.test.assertNotEquals(old.alliance.alliance.id, replacement.alliance.alliance.id)
        assertEquals(AllianceSeats(1, 20), replacement.alliance.alliance.seats)
    }

    @Test
    fun `petitioning creates a durable request and advances the parent version`() = runTest {
        val made = assertIs<Founded.Made>(repository.found(founder, NAME, TAG, TEST_NOW))
        val player = PlayerId("petitioner")

        val changed = assertIs<AllianceChange.Applied>(repository.petition(player, made.alliance.alliance.id, TEST_NOW, made.alliance.version))

        val pending = assertIs<Affiliation.Petitioning>(changed.affiliation)
        assertEquals(player, pending.petition.player)
        assertEquals(TEST_NOW, pending.petition.requestedAt)
        assertEquals(made.alliance.version.next(), pending.alliance.version)
        assertEquals(pending, repository.allianceOf(player, TEST_NOW))
        assertEquals(made.alliance.version.next(), assertIs<Affiliation.Enlisted>(repository.allianceOf(founder, TEST_NOW)).alliance.version)
    }

    @Test
    fun `founding while petitioning is refused and preserves the pending request`() = runTest {
        val made = assertIs<Founded.Made>(repository.found(founder, NAME, TAG, TEST_NOW))
        val player = PlayerId("petitioner")
        val pending = assertIs<AllianceChange.Applied>(repository.petition(player, made.alliance.alliance.id, TEST_NOW, made.alliance.version)).affiliation

        val refused = assertIs<Founded.Refused>(repository.found(player, AllianceName("Other"), AllianceTag("OTH"), TEST_NOW))

        assertEquals(ApiError.AlreadyInAnAlliance, refused.error)
        assertEquals(pending, repository.allianceOf(player, TEST_NOW))
    }

    @Test
    fun `petitioning a different alliance requires withdrawal and keeps the original request`() = runTest {
        val first = assertIs<Founded.Made>(repository.found(founder, NAME, TAG, TEST_NOW))
        val other = assertIs<Founded.Made>(repository.found(PlayerId("other-founder"), AllianceName("Other"), AllianceTag("OTH"), TEST_NOW))
        val player = PlayerId("petitioner")
        val pending = assertIs<AllianceChange.Applied>(repository.petition(player, first.alliance.alliance.id, TEST_NOW, first.alliance.version)).affiliation

        val refused = assertIs<AllianceChange.Refused>(repository.petition(player, other.alliance.alliance.id, TEST_NOW, other.alliance.version))

        assertEquals(ApiError.AlreadyInAnAlliance, refused.error)
        assertEquals(pending, repository.allianceOf(player, TEST_NOW))
        assertEquals(other.alliance.version, assertIs<Affiliation.Enlisted>(repository.allianceOf(PlayerId("other-founder"), TEST_NOW)).alliance.version)
    }

    @Test
    fun `retrying a petition preserves its identity time and alliance version`() = runTest {
        val made = assertIs<Founded.Made>(repository.found(founder, NAME, TAG, TEST_NOW))
        val player = PlayerId("petitioner")
        val pending = assertIs<AllianceChange.Applied>(repository.petition(player, made.alliance.alliance.id, TEST_NOW, made.alliance.version))
        val version = assertIs<Affiliation.Petitioning>(pending.affiliation).alliance.version

        val retry = assertIs<AllianceChange.Applied>(repository.petition(player, made.alliance.alliance.id, TEST_NOW + kotlin.time.Duration.parse("1h"), version))

        assertEquals(pending, retry)
        assertEquals(pending.affiliation, repository.allianceOf(player, TEST_NOW))
    }

    @Test
    fun `a stale petition decision cannot overwrite a concurrent alliance write`() = runTest {
        val made = assertIs<Founded.Made>(repository.found(founder, NAME, TAG, TEST_NOW))
        repository.petition(PlayerId("first"), made.alliance.alliance.id, TEST_NOW, made.alliance.version)

        val stale = repository.petition(PlayerId("second"), made.alliance.alliance.id, TEST_NOW, made.alliance.version)

        assertEquals(AllianceChange.Stale, stale)
        assertEquals(Affiliation.Unaffiliated, repository.allianceOf(PlayerId("second"), TEST_NOW))
        assertEquals(made.alliance.version.next(), assertIs<Affiliation.Enlisted>(repository.allianceOf(founder, TEST_NOW)).alliance.version)
    }

    @Test
    fun `petitioning a missing alliance returns the named refusal`() = runTest {
        val refused = assertIs<AllianceChange.Refused>(repository.petition(founder, dev.fardavide.oltre.protocol.AllianceId("missing"), TEST_NOW, AllianceVersion.FIRST))

        assertEquals(ApiError.NoSuchAlliance, refused.error)
        assertEquals(Affiliation.Unaffiliated, repository.allianceOf(founder, TEST_NOW))
    }

    @Test
    fun `every mutation refuses an absent parent and preserves existing affiliations and petitions`() = runTest {
        val made = assertIs<Founded.Made>(repository.found(founder, NAME, TAG, TEST_NOW))
        val petitioner = PlayerId("petitioner")
        val pending = assertIs<Affiliation.Petitioning>(assertIs<AllianceChange.Applied>(repository.petition(petitioner, made.alliance.alliance.id, TEST_NOW, made.alliance.version)).affiliation)
        val enlisted = assertIs<Affiliation.Enlisted>(repository.allianceOf(founder, TEST_NOW))
        val missing = AllianceId("missing")
        val request = JoinRequestId("missing-request")
        val member = AllianceMemberId("missing-member")
        val mutations: List<suspend () -> AllianceChange> = listOf(
            { repository.petition(founder, missing, TEST_NOW, AllianceVersion.FIRST) },
            { repository.detach(founder, missing, AllianceVersion.FIRST) },
            { repository.approve(founder, missing, request, JoinDecision.ADMITTED, TEST_NOW, AllianceVersion.FIRST) },
            { repository.setRole(founder, missing, member, AllianceRole.ADMIN, AllianceVersion.FIRST) },
            { repository.kick(founder, missing, member, AllianceVersion.FIRST) },
            { repository.rename(founder, missing, AllianceName("Other"), AllianceTag("OTH"), AllianceVersion.FIRST) },
            { repository.disband(founder, missing, AllianceVersion.FIRST) },
        )

        for (mutate in mutations) {
            assertEquals(AllianceChange.Refused(ApiError.NoSuchAlliance), mutate())
            assertEquals(AllianceLookup.Absent, repository.alliance(missing, TEST_NOW))
            assertEquals(AllianceLookup.Present(enlisted.alliance), repository.alliance(made.alliance.alliance.id, TEST_NOW))
            assertEquals(enlisted, repository.allianceOf(founder, TEST_NOW))
            assertEquals(pending, repository.allianceOf(petitioner, TEST_NOW))
        }
    }

    @Test
    fun `every stale mutation preserves the winner alliance its seats and its pending request`() = runTest {
        val made = assertIs<Founded.Made>(repository.found(founder, NAME, TAG, TEST_NOW))
        val member = PlayerId("member")
        val membership = assertIs<Affiliation.Petitioning>(assertIs<AllianceChange.Applied>(repository.petition(member, made.alliance.alliance.id, TEST_NOW, made.alliance.version)).affiliation)
        assertIs<AllianceChange.Applied>(repository.approve(founder, made.alliance.alliance.id, membership.petition.id, JoinDecision.ADMITTED, TEST_NOW, membership.alliance.version))
        val before = assertIs<Affiliation.Enlisted>(repository.allianceOf(member, TEST_NOW))
        val petitioner = PlayerId("petitioner")
        val pending = assertIs<Affiliation.Petitioning>(assertIs<AllianceChange.Applied>(repository.petition(petitioner, made.alliance.alliance.id, TEST_NOW, before.alliance.version)).affiliation)
        val enlisted = assertIs<Affiliation.Enlisted>(repository.allianceOf(founder, TEST_NOW))
        val seated = assertIs<Affiliation.Enlisted>(repository.allianceOf(member, TEST_NOW))
        val newcomer = PlayerId("newcomer")
        val id = made.alliance.alliance.id
        val expected = before.alliance.version
        val mutations: List<suspend () -> AllianceChange> = listOf(
            { repository.petition(newcomer, id, TEST_NOW, expected) },
            { repository.detach(member, id, expected) },
            { repository.approve(founder, id, pending.petition.id, JoinDecision.ADMITTED, TEST_NOW, expected) },
            { repository.setRole(founder, id, seated.seat.id, AllianceRole.ADMIN, expected) },
            { repository.kick(founder, id, seated.seat.id, expected) },
            { repository.rename(founder, id, AllianceName("Other"), AllianceTag("OTH"), expected) },
            { repository.disband(founder, id, expected) },
        )

        for (mutate in mutations) {
            assertEquals(AllianceChange.Stale, mutate())
            assertEquals(AllianceLookup.Present(enlisted.alliance), repository.alliance(id, TEST_NOW))
            assertEquals(enlisted, repository.allianceOf(founder, TEST_NOW))
            assertEquals(seated, repository.allianceOf(member, TEST_NOW))
            assertEquals(pending, repository.allianceOf(petitioner, TEST_NOW))
            assertEquals(Affiliation.Unaffiliated, repository.allianceOf(newcomer, TEST_NOW))
        }
    }

    @Test
    fun `an admin cannot rename or disband and preserves the alliance every seat and pending petition`() = runTest {
        val made = assertIs<Founded.Made>(repository.found(founder, NAME, TAG, TEST_NOW))
        val admin = PlayerId("admin")
        val membership = assertIs<Affiliation.Petitioning>(assertIs<AllianceChange.Applied>(repository.petition(admin, made.alliance.alliance.id, TEST_NOW, made.alliance.version)).affiliation)
        assertIs<AllianceChange.Applied>(repository.approve(founder, made.alliance.alliance.id, membership.petition.id, JoinDecision.ADMITTED, TEST_NOW, membership.alliance.version))
        val member = assertIs<Affiliation.Enlisted>(repository.allianceOf(admin, TEST_NOW))
        assertIs<AllianceChange.Applied>(repository.setRole(founder, made.alliance.alliance.id, member.seat.id, AllianceRole.ADMIN, member.alliance.version))
        val beforePetition = assertIs<Affiliation.Enlisted>(repository.allianceOf(founder, TEST_NOW))
        val petitioner = PlayerId("petitioner")
        val pending = assertIs<Affiliation.Petitioning>(assertIs<AllianceChange.Applied>(repository.petition(petitioner, made.alliance.alliance.id, TEST_NOW, beforePetition.alliance.version)).affiliation)
        val enlisted = assertIs<Affiliation.Enlisted>(repository.allianceOf(founder, TEST_NOW))
        val administrator = assertIs<Affiliation.Enlisted>(repository.allianceOf(admin, TEST_NOW))
        val mutations: List<suspend () -> AllianceChange> = listOf(
            { repository.rename(admin, made.alliance.alliance.id, AllianceName("Other"), AllianceTag("OTH"), enlisted.alliance.version) },
            { repository.disband(admin, made.alliance.alliance.id, enlisted.alliance.version) },
        )

        for (mutate in mutations) {
            assertEquals(AllianceChange.Refused(ApiError.AllianceRoleTooLow), mutate())
            assertEquals(AllianceLookup.Present(enlisted.alliance), repository.alliance(made.alliance.alliance.id, TEST_NOW))
            assertEquals(enlisted, repository.allianceOf(founder, TEST_NOW))
            assertEquals(administrator, repository.allianceOf(admin, TEST_NOW))
            assertEquals(pending, repository.allianceOf(petitioner, TEST_NOW))
        }
    }

    @Test
    fun `account deletion removes its pending petition`() = runTest {
        val made = assertIs<Founded.Made>(repository.found(founder, NAME, TAG, TEST_NOW))
        val player = PlayerId("petitioner")
        repository.petition(player, made.alliance.alliance.id, TEST_NOW, made.alliance.version)

        repository.forget(player)

        assertEquals(Affiliation.Unaffiliated, repository.allianceOf(player, TEST_NOW))
    }

    @Test
    fun `approval exchanges the pending request for a member seat atomically`() = runTest {
        val made = assertIs<Founded.Made>(repository.found(founder, NAME, TAG, TEST_NOW))
        val player = PlayerId("petitioner")
        val pending = assertIs<Affiliation.Petitioning>(assertIs<AllianceChange.Applied>(repository.petition(player, made.alliance.alliance.id, TEST_NOW, made.alliance.version)).affiliation)

        val approved = assertIs<AllianceChange.Applied>(repository.approve(founder, made.alliance.alliance.id, pending.petition.id, JoinDecision.ADMITTED, TEST_NOW, pending.alliance.version))

        val member = assertIs<Affiliation.Enlisted>(repository.allianceOf(player, TEST_NOW))
        assertEquals(AllianceRole.MEMBER, member.seat.role)
        assertEquals(TEST_NOW, member.seat.joinedAt)
        assertEquals(0L, member.seat.contributed)
        assertEquals(AllianceSeats(2, 20), member.alliance.alliance.seats)
        assertEquals(pending.alliance.version.next(), member.alliance.version)
        assertEquals(member.alliance, assertIs<Affiliation.Enlisted>(approved.affiliation).alliance)
    }

    @Test
    fun `declining removes the petition without creating a seat`() = runTest {
        val made = assertIs<Founded.Made>(repository.found(founder, NAME, TAG, TEST_NOW))
        val player = PlayerId("petitioner")
        val pending = assertIs<Affiliation.Petitioning>(assertIs<AllianceChange.Applied>(repository.petition(player, made.alliance.alliance.id, TEST_NOW, made.alliance.version)).affiliation)

        val declined = assertIs<AllianceChange.Applied>(repository.approve(founder, made.alliance.alliance.id, pending.petition.id, JoinDecision.DECLINED, TEST_NOW, pending.alliance.version))

        assertEquals(Affiliation.Unaffiliated, repository.allianceOf(player, TEST_NOW))
        val caller = assertIs<Affiliation.Enlisted>(declined.affiliation)
        assertEquals(AllianceSeats(1, 20), caller.alliance.alliance.seats)
        assertEquals(pending.alliance.version.next(), caller.alliance.version)
    }

    @Test
    fun `a member leaves without taking the alliance or its founder with them`() = runTest {
        val made = assertIs<Founded.Made>(repository.found(founder, NAME, TAG, TEST_NOW))
        val player = PlayerId("member")
        val pending = assertIs<Affiliation.Petitioning>(assertIs<AllianceChange.Applied>(repository.petition(player, made.alliance.alliance.id, TEST_NOW, made.alliance.version)).affiliation)
        repository.approve(founder, made.alliance.alliance.id, pending.petition.id, JoinDecision.ADMITTED, TEST_NOW, pending.alliance.version)
        val member = assertIs<Affiliation.Enlisted>(repository.allianceOf(player, TEST_NOW))

        val left = assertIs<AllianceChange.Applied>(repository.detach(player, member.alliance.alliance.id, member.alliance.version))

        assertEquals(Affiliation.Unaffiliated, left.affiliation)
        assertEquals(Affiliation.Unaffiliated, repository.allianceOf(player, TEST_NOW))
        val retained = assertIs<Affiliation.Enlisted>(repository.allianceOf(founder, TEST_NOW))
        assertEquals(AllianceSeats(1, 20), retained.alliance.alliance.seats)
        assertEquals(AllianceRole.FOUNDER, retained.seat.role)
        assertEquals(member.alliance.version.next(), retained.alliance.version)
    }

    @Test
    fun `leaving another alliance is refused and preserves both alliances and the caller membership`() = runTest {
        val first = assertIs<Founded.Made>(repository.found(founder, NAME, TAG, TEST_NOW))
        val otherFounder = PlayerId("other-founder")
        val other = assertIs<Founded.Made>(repository.found(otherFounder, AllianceName("Other"), AllianceTag("OTH"), TEST_NOW))
        val player = PlayerId("member")
        val pending = assertIs<Affiliation.Petitioning>(assertIs<AllianceChange.Applied>(repository.petition(player, other.alliance.alliance.id, TEST_NOW, other.alliance.version)).affiliation)
        assertIs<AllianceChange.Applied>(repository.approve(otherFounder, other.alliance.alliance.id, pending.petition.id, JoinDecision.ADMITTED, TEST_NOW, pending.alliance.version))
        val firstFounderBefore = assertIs<Affiliation.Enlisted>(repository.allianceOf(founder, TEST_NOW))
        val otherFounderBefore = assertIs<Affiliation.Enlisted>(repository.allianceOf(otherFounder, TEST_NOW))
        val memberBefore = assertIs<Affiliation.Enlisted>(repository.allianceOf(player, TEST_NOW))

        val refused = assertIs<AllianceChange.Refused>(repository.detach(player, first.alliance.alliance.id, first.alliance.version))

        assertEquals(ApiError.AllianceRoleTooLow, refused.error)
        assertEquals(firstFounderBefore, repository.allianceOf(founder, TEST_NOW))
        assertEquals(otherFounderBefore, repository.allianceOf(otherFounder, TEST_NOW))
        assertEquals(memberBefore, repository.allianceOf(player, TEST_NOW))
        assertEquals(AllianceSeats(1, 20), firstFounderBefore.alliance.alliance.seats)
        assertEquals(AllianceSeats(2, 20), otherFounderBefore.alliance.alliance.seats)
        assertEquals(other.alliance.alliance.id, memberBefore.alliance.alliance.id)
    }

    @Test
    fun `setting a member role changes only the role and parent version`() = runTest {
        val made = assertIs<Founded.Made>(repository.found(founder, NAME, TAG, TEST_NOW))
        val player = PlayerId("member")
        val pending = assertIs<Affiliation.Petitioning>(assertIs<AllianceChange.Applied>(repository.petition(player, made.alliance.alliance.id, TEST_NOW, made.alliance.version)).affiliation)
        repository.approve(founder, made.alliance.alliance.id, pending.petition.id, JoinDecision.ADMITTED, TEST_NOW, pending.alliance.version)
        val member = assertIs<Affiliation.Enlisted>(repository.allianceOf(player, TEST_NOW))

        assertIs<AllianceChange.Applied>(repository.setRole(founder, member.alliance.alliance.id, member.seat.id, AllianceRole.ADMIN, member.alliance.version))

        val promoted = assertIs<Affiliation.Enlisted>(repository.allianceOf(player, TEST_NOW))
        assertEquals(member.seat.copy(role = AllianceRole.ADMIN), promoted.seat)
        assertEquals(member.alliance.copy(version = member.alliance.version.next()), promoted.alliance)
    }

    @Test
    fun `reading after an inactive founder promotes the active admin once`() = runTest {
        val made = assertIs<Founded.Made>(repository.found(founder, NAME, TAG, TEST_NOW))
        val admin = PlayerId("admin")
        val pending = assertIs<Affiliation.Petitioning>(assertIs<AllianceChange.Applied>(repository.petition(admin, made.alliance.alliance.id, TEST_NOW, made.alliance.version)).affiliation)
        repository.approve(founder, made.alliance.alliance.id, pending.petition.id, JoinDecision.ADMITTED, TEST_NOW, pending.alliance.version)
        val member = assertIs<Affiliation.Enlisted>(repository.allianceOf(admin, TEST_NOW))
        repository.setRole(founder, member.alliance.alliance.id, member.seat.id, AllianceRole.ADMIN, member.alliance.version)
        val before = assertIs<Affiliation.Enlisted>(repository.allianceOf(founder, TEST_NOW))
        colonies.found(founder, freshColony())
        colonies.found(admin, freshColony().copy(lastUpdatedAt = TEST_NOW + 31.days))

        val succeeded = assertIs<Affiliation.Enlisted>(repository.allianceOf(admin, TEST_NOW + 31.days))

        assertEquals(AllianceRole.FOUNDER, succeeded.seat.role)
        assertEquals(before.alliance.version.next(), succeeded.alliance.version)
        assertEquals(AllianceRole.ADMIN, assertIs<Affiliation.Enlisted>(repository.allianceOf(founder, TEST_NOW + 31.days)).seat.role)
        assertEquals(succeeded, repository.allianceOf(admin, TEST_NOW + 31.days))
    }

    @Test
    fun `looking up the parent alliance promotes the active admin after its founder becomes inactive once`() = runTest {
        val made = assertIs<Founded.Made>(repository.found(founder, NAME, TAG, TEST_NOW))
        val admin = PlayerId("admin")
        val pending = assertIs<Affiliation.Petitioning>(assertIs<AllianceChange.Applied>(repository.petition(admin, made.alliance.alliance.id, TEST_NOW, made.alliance.version)).affiliation)
        assertIs<AllianceChange.Applied>(repository.approve(founder, made.alliance.alliance.id, pending.petition.id, JoinDecision.ADMITTED, TEST_NOW, pending.alliance.version))
        val member = assertIs<Affiliation.Enlisted>(repository.allianceOf(admin, TEST_NOW))
        assertIs<AllianceChange.Applied>(repository.setRole(founder, member.alliance.alliance.id, member.seat.id, AllianceRole.ADMIN, member.alliance.version))
        val before = assertIs<Affiliation.Enlisted>(repository.allianceOf(founder, TEST_NOW))
        colonies.found(founder, freshColony())
        colonies.found(admin, freshColony().copy(lastUpdatedAt = TEST_NOW + 31.days))

        val lookedUp = assertIs<AllianceLookup.Present>(repository.alliance(made.alliance.alliance.id, TEST_NOW + 31.days))

        assertEquals(before.alliance.version.next(), lookedUp.alliance.version)
        assertEquals(before.alliance.alliance, lookedUp.alliance.alliance)
        assertEquals(lookedUp, repository.alliance(made.alliance.alliance.id, TEST_NOW + 31.days))
        val succeeded = assertIs<Affiliation.Enlisted>(repository.allianceOf(admin, TEST_NOW + 31.days))
        assertEquals(AllianceRole.FOUNDER, succeeded.seat.role)
        assertEquals(lookedUp.alliance, succeeded.alliance)
        assertEquals(AllianceRole.ADMIN, assertIs<Affiliation.Enlisted>(repository.allianceOf(founder, TEST_NOW + 31.days)).seat.role)
        assertEquals(succeeded, repository.allianceOf(admin, TEST_NOW + 31.days))
    }

    @Test
    fun `removing a member frees their seat and leaves the founder enlisted`() = runTest {
        val made = assertIs<Founded.Made>(repository.found(founder, NAME, TAG, TEST_NOW))
        val player = PlayerId("member")
        val pending = assertIs<Affiliation.Petitioning>(assertIs<AllianceChange.Applied>(repository.petition(player, made.alliance.alliance.id, TEST_NOW, made.alliance.version)).affiliation)
        repository.approve(founder, made.alliance.alliance.id, pending.petition.id, JoinDecision.ADMITTED, TEST_NOW, pending.alliance.version)
        val member = assertIs<Affiliation.Enlisted>(repository.allianceOf(player, TEST_NOW))

        val removed = assertIs<AllianceChange.Applied>(repository.kick(founder, member.alliance.alliance.id, member.seat.id, member.alliance.version))

        assertEquals(Affiliation.Unaffiliated, repository.allianceOf(player, TEST_NOW))
        val caller = assertIs<Affiliation.Enlisted>(removed.affiliation)
        assertEquals(AllianceRole.FOUNDER, caller.seat.role)
        assertEquals(AllianceSeats(1, 20), caller.alliance.alliance.seats)
        assertEquals(member.alliance.version.next(), caller.alliance.version)
    }

    @Test
    fun `renaming replaces the name and tag together and advances the parent version`() = runTest {
        val made = assertIs<Founded.Made>(repository.found(founder, NAME, TAG, TEST_NOW))

        val renamed = assertIs<AllianceChange.Applied>(repository.rename(founder, made.alliance.alliance.id, AllianceName("New Vanguard"), AllianceTag("NEW"), made.alliance.version))

        val enlisted = assertIs<Affiliation.Enlisted>(renamed.affiliation)
        assertEquals(made.alliance.alliance.copy(name = AllianceName("New Vanguard"), tag = AllianceTag("NEW")), enlisted.alliance.alliance)
        assertEquals(made.alliance.version.next(), enlisted.alliance.version)
        assertEquals(enlisted, repository.allianceOf(founder, TEST_NOW))
    }

    @Test
    fun `disbanding removes the alliance its seats and its pending requests`() = runTest {
        val made = assertIs<Founded.Made>(repository.found(founder, NAME, TAG, TEST_NOW))
        val member = PlayerId("member")
        val pending = assertIs<Affiliation.Petitioning>(assertIs<AllianceChange.Applied>(repository.petition(member, made.alliance.alliance.id, TEST_NOW, made.alliance.version)).affiliation)
        repository.approve(founder, made.alliance.alliance.id, pending.petition.id, JoinDecision.ADMITTED, TEST_NOW, pending.alliance.version)
        val afterApproval = assertIs<Affiliation.Enlisted>(repository.allianceOf(founder, TEST_NOW))
        val waiting = PlayerId("waiting")
        val request = assertIs<Affiliation.Petitioning>(assertIs<AllianceChange.Applied>(repository.petition(waiting, made.alliance.alliance.id, TEST_NOW, afterApproval.alliance.version)).affiliation)

        val disbanded = assertIs<AllianceChange.Applied>(repository.disband(founder, made.alliance.alliance.id, request.alliance.version))

        assertEquals(Affiliation.Unaffiliated, disbanded.affiliation)
        for (player in listOf(founder, member, waiting)) assertEquals(Affiliation.Unaffiliated, repository.allianceOf(player, TEST_NOW))
        assertEquals(AllianceLookup.Absent, repository.alliance(made.alliance.alliance.id, TEST_NOW))
        assertIs<Founded.Made>(repository.found(member, NAME, TAG, TEST_NOW))
    }

    @Test
    fun `reaping an empty alliance also removes petitions for that alliance`() = runTest {
        val made = assertIs<Founded.Made>(repository.found(founder, NAME, TAG, TEST_NOW))
        val player = PlayerId("petitioner")
        repository.petition(player, made.alliance.alliance.id, TEST_NOW, made.alliance.version)
        repository.forget(founder)

        assertIs<Founded.Made>(repository.found(PlayerId("replacement"), NAME, TAG, TEST_NOW))

        assertEquals(Affiliation.Unaffiliated, repository.allianceOf(player, TEST_NOW))
    }

    @Test
    fun `a petitioner cannot erase their pending request by founding after the founder deletes their account`() = runTest {
        val made = assertIs<Founded.Made>(repository.found(founder, NAME, TAG, TEST_NOW))
        val player = PlayerId("petitioner")
        repository.petition(player, made.alliance.alliance.id, TEST_NOW, made.alliance.version)
        repository.forget(founder)
        val pending = repository.allianceOf(player, TEST_NOW)

        val refused = assertIs<Founded.Refused>(repository.found(player, NAME, TAG, TEST_NOW))

        assertEquals(ApiError.AlreadyInAnAlliance, refused.error)
        assertEquals(pending, repository.allianceOf(player, TEST_NOW))
    }

    private companion object {


        val NAME = AllianceName("Vanguard")
        val TAG = AllianceTag("VNG")
    }
}
