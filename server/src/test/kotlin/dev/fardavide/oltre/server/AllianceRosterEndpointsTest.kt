package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.Experience
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.AllianceMember
import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceTag
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.ExperienceReading
import dev.fardavide.oltre.protocol.CommanderName
import dev.fardavide.oltre.protocol.JoinRequest
import dev.fardavide.oltre.protocol.MarkPreset
import dev.fardavide.oltre.protocol.PlayerMark
import dev.fardavide.oltre.protocol.PlayerProfile
import dev.fardavide.oltre.protocol.JoinDecision
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class AllianceRosterEndpointsTest {

    @Test
    fun `an admitted admin can read a pending petition with its actual profile and earned points`() = runTest {
        val colonies = InMemoryColonyRepository()
        val alliances = InMemoryAllianceRepository(colonies)
        val players = InMemoryPlayerRepository(colonies, alliances, ids = sequentialPlayerIds())
        val authenticator = HeaderAuthenticator(players)
        val founder = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "founder"))).player
        val admin = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "admin"))).player
        val applicant = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "applicant"))).player
        val profile = PlayerProfile(CommanderName("Ada di Notte"), PlayerMark.Preset(MarkPreset.SEXTANT))
        assertTrue(players.setProfile(applicant, profile))
        val fresh = freshColony()
        val colony = fresh.copy(state = fresh.state.copy(experience = Experience(340)))
        colonies.found(founder, fresh)
        colonies.found(admin, fresh)
        colonies.found(applicant, colony)
        val made = assertIs<Founded.Made>(alliances.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))
        val pending = assertIs<Affiliation.Petitioning>(assertIs<AllianceChange.Applied>(alliances.petition(
            admin, made.alliance.alliance.id, TEST_NOW, made.alliance.version,
        )).affiliation)
        assertIs<AllianceChange.Applied>(alliances.approve(
            founder, made.alliance.alliance.id, pending.petition.id, JoinDecision.ADMITTED, TEST_NOW, pending.alliance.version,
        ))
        val member = assertIs<Affiliation.Enlisted>(alliances.allianceOf(admin, TEST_NOW))
        assertIs<AllianceChange.Applied>(alliances.setRole(
            founder, made.alliance.alliance.id, member.seat.id, AllianceRole.ADMIN, member.alliance.version,
        ))
        val current = assertIs<Affiliation.Enlisted>(alliances.allianceOf(founder, TEST_NOW))
        val askedAt = TEST_NOW + 1.hours
        val petition = assertIs<Affiliation.Petitioning>(assertIs<AllianceChange.Applied>(alliances.petition(
            applicant, made.alliance.alliance.id, askedAt, current.alliance.version,
        )).affiliation).petition

        val answer = assertIs<Answer.Roster>(readAllianceRoster(alliances, authenticator, MovableClock(askedAt), Credentials(null, "admin")))

        assertEquals(listOf(JoinRequest(
            petition.id, profile, ExperienceReading.Known(colony.state.experience), askedAt,
        )), answer.response.pending)
    }

    @Test
    fun `an out of order roster read before founding a colony receives the no colony error`() = runTest {
        val colonies = InMemoryColonyRepository()
        val alliances = InMemoryAllianceRepository(colonies)
        val authenticator = HeaderAuthenticator(InMemoryPlayerRepository(colonies, alliances))
        val credentials = Credentials(null, "founder")
        val founder = assertIs<Caller.Known>(authenticator.identify(credentials)).player
        assertIs<Founded.Made>(alliances.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))

        val answer = readAllianceRoster(alliances, authenticator, MovableClock(TEST_NOW), credentials)

        val refused = assertIs<Answer.Failed>(answer)
        assertEquals(HttpStatusCode.NotFound, refused.status)
        assertEquals(ApiError.NoColony, refused.error)
    }

    @Test
    fun `a roster repository failure becomes an internal error instead of escaping the handler`() = runTest {
        val colonies = InMemoryColonyRepository()
        val authenticator = HeaderAuthenticator(InMemoryPlayerRepository(colonies, InMemoryAllianceRepository(colonies)))

        val answer = readAllianceRoster(UnreachableAllianceRepository(), authenticator, MovableClock(TEST_NOW), Credentials(null, "founder"))

        val refused = assertIs<Answer.Failed>(answer)
        assertEquals(HttpStatusCode.InternalServerError, refused.status)
        assertIs<ApiError.Internal>(refused.error)
    }

    @Test
    fun `a petitioner cannot read the roster before admission`() = runTest {
        val colonies = InMemoryColonyRepository()
        val alliances = InMemoryAllianceRepository(colonies)
        val authenticator = HeaderAuthenticator(InMemoryPlayerRepository(colonies, alliances))
        val founder = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "founder"))).player
        val applicant = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "applicant"))).player
        val made = assertIs<Founded.Made>(alliances.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))
        assertIs<AllianceChange.Applied>(alliances.petition(applicant, made.alliance.alliance.id, TEST_NOW, made.alliance.version))

        val answer = readAllianceRoster(alliances, authenticator, MovableClock(TEST_NOW), Credentials(null, "applicant"))

        val refused = assertIs<Answer.Failed>(answer)
        assertEquals(HttpStatusCode.Conflict, refused.status)
        assertEquals(ApiError.NotInAnAlliance, refused.error)
    }

    @Test
    fun `an unaffiliated caller receives a conflict instead of an empty roster`() = runTest {
        val colonies = InMemoryColonyRepository()
        val alliances = InMemoryAllianceRepository(colonies)
        val authenticator = HeaderAuthenticator(InMemoryPlayerRepository(colonies, alliances))

        val answer = readAllianceRoster(alliances, authenticator, MovableClock(TEST_NOW), Credentials(null, "visitor"))

        val refused = assertIs<Answer.Failed>(answer)
        assertEquals(HttpStatusCode.Conflict, refused.status)
        assertEquals(ApiError.NotInAnAlliance, refused.error)
    }

    @Test
    fun `a founder sees a pending petition with its chosen profile earned points and request instant`() = runTest {
        val colonies = InMemoryColonyRepository()
        val alliances = InMemoryAllianceRepository(colonies)
        val players = InMemoryPlayerRepository(colonies, alliances, ids = sequentialPlayerIds())
        val authenticator = HeaderAuthenticator(players)
        val founder = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "founder"))).player
        val applicant = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "applicant"))).player
        val profile = PlayerProfile(CommanderName("Ada di Notte"), PlayerMark.Preset(MarkPreset.SEXTANT))
        assertTrue(players.setProfile(applicant, profile))
        val fresh = freshColony()
        val colony = fresh.copy(state = fresh.state.copy(experience = Experience(340)))
        colonies.found(founder, fresh)
        colonies.found(applicant, colony)
        val made = assertIs<Founded.Made>(alliances.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))
        val askedAt = TEST_NOW + 1.hours
        val petition = assertIs<Affiliation.Petitioning>(assertIs<AllianceChange.Applied>(alliances.petition(
            applicant, made.alliance.alliance.id, askedAt, made.alliance.version,
        )).affiliation).petition

        val answer = assertIs<Answer.Roster>(readAllianceRoster(alliances, authenticator, MovableClock(askedAt), Credentials(null, "founder")))

        assertEquals(listOf(JoinRequest(
            petition.id, profile, ExperienceReading.Known(colony.state.experience), askedAt,
        )), answer.response.pending)
    }

    @Test
    fun `a plain member cannot see pending join requests`() = runTest {
        val colonies = InMemoryColonyRepository()
        val alliances = InMemoryAllianceRepository(colonies)
        val players = InMemoryPlayerRepository(colonies, alliances)
        val authenticator = HeaderAuthenticator(players)
        val founder = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "founder"))).player
        val member = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "member"))).player
        val applicant = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "applicant"))).player
        for (player in listOf(founder, member, applicant)) colonies.found(player, freshColony())
        val made = assertIs<Founded.Made>(alliances.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))
        val pending = assertIs<Affiliation.Petitioning>(assertIs<AllianceChange.Applied>(alliances.petition(
            member, made.alliance.alliance.id, TEST_NOW, made.alliance.version,
        )).affiliation)
        alliances.approve(founder, made.alliance.alliance.id, pending.petition.id, JoinDecision.ADMITTED, TEST_NOW, pending.alliance.version)
        val enlisted = assertIs<Affiliation.Enlisted>(alliances.allianceOf(founder, TEST_NOW))
        alliances.petition(applicant, made.alliance.alliance.id, TEST_NOW, enlisted.alliance.version)

        val answer = assertIs<Answer.Roster>(readAllianceRoster(alliances, authenticator, MovableClock(TEST_NOW), Credentials(null, "member")))

        assertEquals(null, answer.response.pending)
    }

    @Test
    fun `a founder reads their own roster with the last earned points`() = runTest {
        val colonies = InMemoryColonyRepository()
        val alliances = InMemoryAllianceRepository(colonies)
        val players = InMemoryPlayerRepository(colonies, alliances)
        val authenticator = HeaderAuthenticator(players)
        val credentials = Credentials(null, "founder")
        val founder = assertIs<Caller.Known>(authenticator.identify(credentials)).player
        val colony = freshColony()
        colonies.found(founder, colony)
        alliances.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW)
        val seat = assertIs<Affiliation.Enlisted>(alliances.allianceOf(founder, TEST_NOW)).seat

        val answer = assertIs<Answer.Roster>(readAllianceRoster(alliances, authenticator, MovableClock(TEST_NOW), credentials))

        assertEquals(HttpStatusCode.OK, answer.status)
        assertEquals(ApiVersion.CURRENT, answer.response.apiVersion)
        assertEquals(listOf(AllianceMember(
            seat.id, PlayerProfile(null, null), AllianceRole.FOUNDER,
            ExperienceReading.Known(colony.state.experience), TEST_NOW,
        )), answer.response.members)
        assertEquals(emptyList(), answer.response.pending)
    }

    @Test
    fun `the roster handler refuses absent credentials before accessing any store`() = runTest {
        val alliances = UnreachableAllianceRepository()
        val authenticator = HeaderAuthenticator(UnreachablePlayerRepository())
        val clock = MovableClock(TEST_NOW)

        val answer = readAllianceRoster(alliances, authenticator, clock, Credentials(null, null))

        val refused = assertIs<Answer.Failed>(answer)
        assertEquals(HttpStatusCode.Unauthorized, refused.status)
        assertEquals(ApiError.Unauthenticated, refused.error)
    }
}
