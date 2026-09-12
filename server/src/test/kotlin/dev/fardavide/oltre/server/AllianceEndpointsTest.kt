package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.AllianceMemberId
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceStanding
import dev.fardavide.oltre.protocol.AllianceTag
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.CreateAllianceRequest
import dev.fardavide.oltre.protocol.JoinAllianceRequest
import dev.fardavide.oltre.protocol.AnswerJoinRequest
import dev.fardavide.oltre.protocol.JoinDecision
import dev.fardavide.oltre.protocol.JoinRequestId
import dev.fardavide.oltre.protocol.RenameAllianceRequest
import dev.fardavide.oltre.protocol.SetMemberRoleRequest
import dev.fardavide.oltre.protocol.KickMemberRequest
import kotlin.time.Instant
import dev.fardavide.oltre.protocol.Protocol
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AllianceEndpointsTest {

    private val colonies = InMemoryColonyRepository()
    private val alliances = InMemoryAllianceRepository(colonies)
    private val players = InMemoryPlayerRepository(colonies, alliances, ids = sequentialPlayerIds())
    private val authenticator = HeaderAuthenticator(players)
    private val clock = MovableClock(TEST_NOW)

    @Test
    fun `every alliance handler refuses missing credentials before accessing a store or reading its body`() = runTest {
        val unreachable = UnreachableAllianceRepository()
        val authentication = HeaderAuthenticator(UnreachablePlayerRepository())
        val missing = Credentials(null, null)
        val handlers: List<suspend () -> Answer> = listOf(
            { readAlliance(unreachable, authentication, clock, missing) },
            { foundAlliance(unreachable, authentication, clock, missing, "not a request") },
            { petitionAlliance(unreachable, authentication, clock, missing, "not a request") },
            { detachAlliance(unreachable, authentication, clock, missing) },
            { answerAlliancePetition(unreachable, authentication, clock, missing, "not a request") },
            { renameAlliance(unreachable, authentication, clock, missing, "not a request") },
            { setAllianceMemberRole(unreachable, authentication, clock, missing, "not a request") },
            { removeAllianceMember(unreachable, authentication, clock, missing, "not a request") },
            { disbandAlliance(unreachable, authentication, clock, missing) },
        )

        for (handle in handlers) {
            val refused = assertIs<Answer.Failed>(handle())
            assertEquals(HttpStatusCode.Unauthorized, refused.status)
            assertEquals(ApiError.Unauthenticated, refused.error)
        }
    }

    @Test
    fun `alliance mutation handlers reject malformed bodies and negotiate unsupported versions before store access`() = runTest {
        val unreachable = UnreachableAllianceRepository()
        val credentials = Credentials(null, "founder")
        val handlers = listOf(
            AllianceBodyHandler(
                body = { version -> Protocol.json.encodeToString(CreateAllianceRequest.serializer(), CreateAllianceRequest(version, AllianceName("Vanguard"), AllianceTag("VNG"))) },
                answer = { body -> foundAlliance(unreachable, authenticator, clock, credentials, body) },
            ),
            AllianceBodyHandler(
                body = { version -> Protocol.json.encodeToString(JoinAllianceRequest.serializer(), JoinAllianceRequest(version, AllianceId("alliance"))) },
                answer = { body -> petitionAlliance(unreachable, authenticator, clock, credentials, body) },
            ),
            AllianceBodyHandler(
                body = { version -> Protocol.json.encodeToString(AnswerJoinRequest.serializer(), AnswerJoinRequest(version, JoinRequestId("request"), JoinDecision.ADMITTED)) },
                answer = { body -> answerAlliancePetition(unreachable, authenticator, clock, credentials, body) },
            ),
            AllianceBodyHandler(
                body = { version -> Protocol.json.encodeToString(RenameAllianceRequest.serializer(), RenameAllianceRequest(version, AllianceName("Horizon"), AllianceTag("HRZ"))) },
                answer = { body -> renameAlliance(unreachable, authenticator, clock, credentials, body) },
            ),
            AllianceBodyHandler(
                body = { version -> Protocol.json.encodeToString(SetMemberRoleRequest.serializer(), SetMemberRoleRequest(version, AllianceMemberId("member"), AllianceRole.ADMIN)) },
                answer = { body -> setAllianceMemberRole(unreachable, authenticator, clock, credentials, body) },
            ),
            AllianceBodyHandler(
                body = { version -> Protocol.json.encodeToString(KickMemberRequest.serializer(), KickMemberRequest(version, AllianceMemberId("member"))) },
                answer = { body -> removeAllianceMember(unreachable, authenticator, clock, credentials, body) },
            ),
        )

        for (handler in handlers) {
            val malformed = assertIs<Answer.Failed>(handler.answer("not a request"))
            assertEquals(HttpStatusCode.BadRequest, malformed.status)
            assertIs<ApiError.Malformed>(malformed.error)
            for (version in listOf(ApiVersion(0), ApiVersion(99))) {
                val unsupported = assertIs<Answer.Failed>(handler.answer(handler.body(version)))
                assertEquals(HttpStatusCode.UpgradeRequired, unsupported.status)
                assertEquals(ApiError.UnsupportedApiVersion(ApiVersion.OLDEST_SERVED, ApiVersion.CURRENT), unsupported.error)
            }
        }
    }

    @Test
    fun `reading an alliance answers with the callers standing`() = runTest {
        val answer = assertIs<Answer.Alliance>(readAlliance(alliances, authenticator, clock, Credentials(null, "visitor")))

        assertEquals(HttpStatusCode.OK, answer.status)
        assertEquals(AllianceStanding.Unaffiliated, answer.response.standing)
    }

    @Test
    fun `founding answers with the authoritative alliance and founder role`() = runTest {
        val request = CreateAllianceRequest(ApiVersion.CURRENT, AllianceName("Vanguard"), AllianceTag("VNG"))

        val answer = foundAlliance(
            alliances, authenticator, clock, Credentials(null, "founder"), Protocol.json.encodeToString(request),
        )

        assertEquals(HttpStatusCode.Created, answer.status)
        val response = assertIs<Answer.Alliance>(answer).response
        val standing = assertIs<AllianceStanding.Enlisted>(response.standing)
        assertEquals(ApiVersion.CURRENT, response.apiVersion)
        assertEquals(AllianceRole.FOUNDER, standing.role)
        assertEquals(request.name, standing.alliance.name)
        assertEquals(request.tag, standing.alliance.tag)
    }

    @Test
    fun `retrying a founding request answers 200 with the same alliance`() = runTest {
        val body = Protocol.json.encodeToString(
            CreateAllianceRequest(ApiVersion.CURRENT, AllianceName("Vanguard"), AllianceTag("VNG")),
        )
        val first = assertIs<Answer.Alliance>(foundAlliance(alliances, authenticator, clock, Credentials(null, "founder"), body))

        val again = assertIs<Answer.Alliance>(foundAlliance(alliances, authenticator, clock, Credentials(null, "founder"), body))

        assertEquals(HttpStatusCode.OK, again.status)
        assertEquals(first.response, again.response)
    }

    @Test
    fun `petitioning answers with the durable pending alliance`() = runTest {
        val created = assertIs<Answer.Alliance>(foundAlliance(alliances, authenticator, clock, Credentials(null, "founder"), Protocol.json.encodeToString(
            CreateAllianceRequest(ApiVersion.CURRENT, AllianceName("Vanguard"), AllianceTag("VNG")),
        )))
        val alliance = assertIs<AllianceStanding.Enlisted>(created.response.standing).alliance

        val answer = assertIs<Answer.Alliance>(petitionAlliance(alliances, authenticator, clock, Credentials(null, "petitioner"), Protocol.json.encodeToString(
            JoinAllianceRequest(ApiVersion.CURRENT, alliance.id),
        )))

        assertEquals(HttpStatusCode.OK, answer.status)
        assertEquals(AllianceStanding.Petitioning(alliance), answer.response.standing)
        assertEquals(answer.response, assertIs<Answer.Alliance>(readAlliance(alliances, authenticator, clock, Credentials(null, "petitioner"))).response)
    }

    @Test
    fun `withdrawing a petition allows the player to found an alliance`() = runTest {
        val founder = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "founder"))).player
        val made = assertIs<Founded.Made>(alliances.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))
        val player = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "petitioner"))).player
        alliances.petition(player, made.alliance.alliance.id, TEST_NOW, made.alliance.version)

        val withdrawn = assertIs<Answer.Alliance>(detachAlliance(alliances, authenticator, clock, Credentials(null, "petitioner")))

        assertEquals(AllianceStanding.Unaffiliated, withdrawn.response.standing)
        assertEquals(made.alliance.version.next().next(), assertIs<Affiliation.Enlisted>(alliances.allianceOf(founder, TEST_NOW)).alliance.version)
        assertIs<Founded.Made>(alliances.found(player, AllianceName("Other"), AllianceTag("OTH"), TEST_NOW))
    }

    @Test
    fun `the founder must hand over leadership or disband before leaving`() = runTest {
        val player = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "founder"))).player
        val made = assertIs<Founded.Made>(alliances.found(player, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))

        val refused = assertIs<Answer.Failed>(detachAlliance(alliances, authenticator, clock, Credentials(null, "founder")))

        assertEquals(ApiError.AllianceRoleTooLow, refused.error)
        assertEquals(made.alliance, assertIs<Affiliation.Enlisted>(alliances.allianceOf(player, TEST_NOW)).alliance)
    }

    @Test
    fun `answering a petition returns the callers updated alliance`() = runTest {
        val founder = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "founder"))).player
        val player = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "applicant"))).player
        val made = assertIs<Founded.Made>(alliances.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))
        val pending = assertIs<Affiliation.Petitioning>(assertIs<AllianceChange.Applied>(alliances.petition(player, made.alliance.alliance.id, TEST_NOW, made.alliance.version)).affiliation)

        val answer = assertIs<Answer.Alliance>(answerAlliancePetition(alliances, authenticator, clock, Credentials(null, "founder"), Protocol.json.encodeToString(AnswerJoinRequest(ApiVersion.CURRENT, pending.petition.id, JoinDecision.ADMITTED))))

        assertEquals(HttpStatusCode.OK, answer.status)
        val caller = assertIs<AllianceStanding.Enlisted>(answer.response.standing)
        assertEquals(AllianceRole.FOUNDER, caller.role)
        assertEquals(2, caller.alliance.seats.taken)
        assertEquals(AllianceRole.MEMBER, assertIs<Affiliation.Enlisted>(alliances.allianceOf(player, TEST_NOW)).seat.role)
    }

    @Test
    fun `a lost alliance compare and set reads the winner and retries`() = runTest {
        val founder = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "founder"))).player
        val made = assertIs<Founded.Made>(alliances.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))
        val contended = ContendedAllianceRepository(alliances, 1)

        val answer = assertIs<Answer.Alliance>(petitionAlliance(contended, authenticator, clock, Credentials(null, "applicant"), Protocol.json.encodeToString(JoinAllianceRequest(ApiVersion.CURRENT, made.alliance.alliance.id))))

        assertIs<AllianceStanding.Petitioning>(answer.response.standing)
        assertEquals(2, contended.attempts)
        assertEquals(made.alliance.version.next().next(), assertIs<Affiliation.Enlisted>(alliances.allianceOf(founder, TEST_NOW)).alliance.version)
    }

    @Test
    fun `three lost petition writes return stale alliance and leave the caller unaffiliated`() = runTest {
        val founder = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "founder"))).player
        val applicant = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "applicant"))).player
        val made = assertIs<Founded.Made>(alliances.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))
        val contended = ContendedAllianceRepository(alliances, 3)

        val answer = assertIs<Answer.Failed>(petitionAlliance(contended, authenticator, clock, Credentials(null, "applicant"), Protocol.json.encodeToString(JoinAllianceRequest(ApiVersion.CURRENT, made.alliance.alliance.id))))

        assertEquals(ApiError.StaleAlliance, answer.error)
        assertEquals(3, contended.attempts)
        assertEquals(Affiliation.Unaffiliated, alliances.allianceOf(applicant, TEST_NOW))
        assertEquals(made.alliance.version.next().next().next(), assertIs<Affiliation.Enlisted>(alliances.allianceOf(founder, TEST_NOW)).alliance.version)
    }

    @Test
    fun `answering a petition retries after another request advances the parent version`() = runTest {
        val founder = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "founder"))).player
        val player = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "applicant"))).player
        val made = assertIs<Founded.Made>(alliances.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))
        val pending = assertIs<Affiliation.Petitioning>(assertIs<AllianceChange.Applied>(alliances.petition(player, made.alliance.alliance.id, TEST_NOW, made.alliance.version)).affiliation)
        val contended = ContendedAllianceRepository(alliances, 1)

        val answer = assertIs<Answer.Alliance>(answerAlliancePetition(contended, authenticator, clock, Credentials(null, "founder"), Protocol.json.encodeToString(AnswerJoinRequest(ApiVersion.CURRENT, pending.petition.id, JoinDecision.ADMITTED))))

        assertEquals(HttpStatusCode.OK, answer.status)
        val caller = assertIs<AllianceStanding.Enlisted>(answer.response.standing)
        assertEquals(AllianceRole.FOUNDER, caller.role)
        assertEquals(2, caller.alliance.seats.taken)
        assertEquals(2, contended.attempts)
        assertEquals(pending.alliance.version.next().next(), assertIs<Affiliation.Enlisted>(alliances.allianceOf(founder, TEST_NOW)).alliance.version)
        assertEquals(AllianceRole.MEMBER, assertIs<Affiliation.Enlisted>(alliances.allianceOf(player, TEST_NOW)).seat.role)
    }

    @Test
    fun `renaming answers with the newly stored name and tag`() = runTest {
        val founder = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "founder"))).player
        alliances.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW)

        val answer = assertIs<Answer.Alliance>(renameAlliance(alliances, authenticator, clock, Credentials(null, "founder"), Protocol.json.encodeToString(RenameAllianceRequest(ApiVersion.CURRENT, AllianceName("New Vanguard"), AllianceTag("NEW")))))

        val standing = assertIs<AllianceStanding.Enlisted>(answer.response.standing)
        assertEquals(AllianceName("New Vanguard"), standing.alliance.name)
        assertEquals(AllianceTag("NEW"), standing.alliance.tag)
        assertEquals(answer.response, assertIs<Answer.Alliance>(readAlliance(alliances, authenticator, clock, Credentials(null, "founder"))).response)
    }

    @Test
    fun `handing over leadership answers with the callers new admin role`() = runTest {
        val founder = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "founder"))).player
        val made = assertIs<Founded.Made>(alliances.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))
        val pending = assertIs<Affiliation.Petitioning>(assertIs<AllianceChange.Applied>(alliances.petition(PlayerId("member"), made.alliance.alliance.id, TEST_NOW, made.alliance.version)).affiliation)
        alliances.approve(founder, made.alliance.alliance.id, pending.petition.id, JoinDecision.ADMITTED, TEST_NOW, pending.alliance.version)
        val member = assertIs<Affiliation.Enlisted>(alliances.allianceOf(PlayerId("member"), TEST_NOW))

        val answer = assertIs<Answer.Alliance>(setAllianceMemberRole(alliances, authenticator, clock, Credentials(null, "founder"), Protocol.json.encodeToString(SetMemberRoleRequest(ApiVersion.CURRENT, member.seat.id, AllianceRole.FOUNDER))))

        assertEquals(AllianceRole.ADMIN, assertIs<AllianceStanding.Enlisted>(answer.response.standing).role)
        assertEquals(AllianceRole.FOUNDER, assertIs<Affiliation.Enlisted>(alliances.allianceOf(PlayerId("member"), TEST_NOW)).seat.role)
    }

    @Test
    fun `role change retry refuses a caller who lost founder permission during contention`() = runTest {
        val founder = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "founder"))).player
        val made = assertIs<Founded.Made>(alliances.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))
        val successor = PlayerId("successor")
        val target = PlayerId("target")
        for (player in listOf(successor, target)) {
            val current = assertIs<Affiliation.Enlisted>(alliances.allianceOf(founder, TEST_NOW))
            val pending = assertIs<Affiliation.Petitioning>(assertIs<AllianceChange.Applied>(alliances.petition(player, made.alliance.alliance.id, TEST_NOW, current.alliance.version)).affiliation)
            assertIs<AllianceChange.Applied>(alliances.approve(founder, made.alliance.alliance.id, pending.petition.id, JoinDecision.ADMITTED, TEST_NOW, pending.alliance.version))
        }
        val successorSeat = assertIs<Affiliation.Enlisted>(alliances.allianceOf(successor, TEST_NOW))
        val targetSeat = assertIs<Affiliation.Enlisted>(alliances.allianceOf(target, TEST_NOW))
        val contended = HandoverAllianceRepository(alliances, successorSeat.seat.id)

        val answer = assertIs<Answer.Failed>(setAllianceMemberRole(contended, authenticator, clock, Credentials(null, "founder"), Protocol.json.encodeToString(SetMemberRoleRequest(ApiVersion.CURRENT, targetSeat.seat.id, AllianceRole.ADMIN))))

        assertEquals(ApiError.AllianceRoleTooLow, answer.error)
        assertEquals(listOf(targetSeat.alliance.version, targetSeat.alliance.version.next()), contended.expectedVersions)
        assertEquals(AllianceRole.ADMIN, assertIs<Affiliation.Enlisted>(alliances.allianceOf(founder, TEST_NOW)).seat.role)
        assertEquals(AllianceRole.FOUNDER, assertIs<Affiliation.Enlisted>(alliances.allianceOf(successor, TEST_NOW)).seat.role)
        assertEquals(targetSeat.seat, assertIs<Affiliation.Enlisted>(alliances.allianceOf(target, TEST_NOW)).seat)
        assertEquals(targetSeat.alliance.version.next(), assertIs<Affiliation.Enlisted>(alliances.allianceOf(founder, TEST_NOW)).alliance.version)
    }

    @Test
    fun `removing a member answers the callers reduced roster count`() = runTest {
        val founder = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "founder"))).player
        val made = assertIs<Founded.Made>(alliances.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))
        val pending = assertIs<Affiliation.Petitioning>(assertIs<AllianceChange.Applied>(alliances.petition(PlayerId("member"), made.alliance.alliance.id, TEST_NOW, made.alliance.version)).affiliation)
        alliances.approve(founder, made.alliance.alliance.id, pending.petition.id, JoinDecision.ADMITTED, TEST_NOW, pending.alliance.version)
        val member = assertIs<Affiliation.Enlisted>(alliances.allianceOf(PlayerId("member"), TEST_NOW))

        val answer = assertIs<Answer.Alliance>(removeAllianceMember(alliances, authenticator, clock, Credentials(null, "founder"), Protocol.json.encodeToString(KickMemberRequest(ApiVersion.CURRENT, member.seat.id))))

        assertEquals(1, assertIs<AllianceStanding.Enlisted>(answer.response.standing).alliance.seats.taken)
        assertEquals(Affiliation.Unaffiliated, alliances.allianceOf(PlayerId("member"), TEST_NOW))
    }

    @Test
    fun `disbanding answers an unaffiliated standing`() = runTest {
        val founder = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "founder"))).player
        alliances.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW)

        val answer = assertIs<Answer.Alliance>(disbandAlliance(alliances, authenticator, clock, Credentials(null, "founder")))

        assertEquals(HttpStatusCode.OK, answer.status)
        assertEquals(AllianceStanding.Unaffiliated, answer.response.standing)
        assertEquals(Affiliation.Unaffiliated, alliances.allianceOf(founder, TEST_NOW))
    }

    @Test
    fun `retrying founding after handover answers with the callers actual admin role`() = runTest {
        val founder = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "founder"))).player
        val made = assertIs<Founded.Made>(alliances.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))
        val pending = assertIs<Affiliation.Petitioning>(assertIs<AllianceChange.Applied>(alliances.petition(PlayerId("member"), made.alliance.alliance.id, TEST_NOW, made.alliance.version)).affiliation)
        alliances.approve(founder, made.alliance.alliance.id, pending.petition.id, JoinDecision.ADMITTED, TEST_NOW, pending.alliance.version)
        val member = assertIs<Affiliation.Enlisted>(alliances.allianceOf(PlayerId("member"), TEST_NOW))
        alliances.setRole(founder, member.alliance.alliance.id, member.seat.id, AllianceRole.FOUNDER, member.alliance.version)

        val answer = assertIs<Answer.Alliance>(foundAlliance(alliances, authenticator, clock, Credentials(null, "founder"), Protocol.json.encodeToString(CreateAllianceRequest(ApiVersion.CURRENT, AllianceName("Vanguard"), AllianceTag("VNG")))))

        assertEquals(HttpStatusCode.OK, answer.status)
        assertEquals(AllianceRole.ADMIN, assertIs<AllianceStanding.Enlisted>(answer.response.standing).role)
    }

    @Test
    fun `withdrawal retries after another request advances the parent version`() = runTest {
        val founder = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "founder"))).player
        val player = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "applicant"))).player
        val made = assertIs<Founded.Made>(alliances.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))
        alliances.petition(player, made.alliance.alliance.id, TEST_NOW, made.alliance.version)
        val contended = ContendedAllianceRepository(alliances, 1)

        val answer = assertIs<Answer.Alliance>(detachAlliance(contended, authenticator, clock, Credentials(null, "applicant")))

        assertEquals(AllianceStanding.Unaffiliated, answer.response.standing)
        assertEquals(2, contended.attempts)
    }

    @Test
    fun `three lost withdrawal writes keep the original petition and return stale alliance`() = runTest {
        val founder = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "founder"))).player
        val player = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "applicant"))).player
        val made = assertIs<Founded.Made>(alliances.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))
        val pending = assertIs<Affiliation.Petitioning>(assertIs<AllianceChange.Applied>(alliances.petition(player, made.alliance.alliance.id, TEST_NOW, made.alliance.version)).affiliation)
        val contended = ContendedAllianceRepository(alliances, 3)

        val answer = assertIs<Answer.Failed>(detachAlliance(contended, authenticator, clock, Credentials(null, "applicant")))

        assertEquals(HttpStatusCode.Conflict, answer.status)
        assertEquals(ApiError.StaleAlliance, answer.error)
        assertEquals(3, contended.attempts)
        assertEquals(pending.petition, assertIs<Affiliation.Petitioning>(alliances.allianceOf(player, TEST_NOW)).petition)
    }

    @Test
    fun `a taken founding name answers a conflict and preserves the existing alliance`() = runTest {
        val founder = assertIs<Caller.Known>(authenticator.identify(Credentials(null, "founder"))).player
        val made = assertIs<Founded.Made>(alliances.found(founder, AllianceName("Vanguard"), AllianceTag("VNG"), TEST_NOW))

        val answer = assertIs<Answer.Failed>(foundAlliance(alliances, authenticator, clock, Credentials(null, "rival"), Protocol.json.encodeToString(CreateAllianceRequest(ApiVersion.CURRENT, AllianceName("VANGUARD"), AllianceTag("RIV")))))

        assertEquals(HttpStatusCode.Conflict, answer.status)
        assertEquals(ApiError.AllianceNameTaken, answer.error)
        assertEquals(made.alliance, assertIs<Affiliation.Enlisted>(alliances.allianceOf(founder, TEST_NOW)).alliance)
    }

    @Test
    fun `petitioning an unknown alliance answers the named not found error`() = runTest {
        val credentials = Credentials(null, "applicant")
        val player = assertIs<Caller.Known>(authenticator.identify(credentials)).player

        val answer = assertIs<Answer.Failed>(petitionAlliance(alliances, authenticator, clock, credentials, Protocol.json.encodeToString(JoinAllianceRequest(ApiVersion.CURRENT, AllianceId("missing")))))

        assertEquals(HttpStatusCode.NotFound, answer.status)
        assertEquals(ApiError.NoSuchAlliance, answer.error)
        assertEquals(Affiliation.Unaffiliated, alliances.allianceOf(player, TEST_NOW))
    }

    private data class AllianceBodyHandler(
        val body: (ApiVersion) -> String,
        val answer: suspend (String) -> Answer,
    )

    private class HandoverAllianceRepository(
        private val store: AllianceRepository,
        private val successor: AllianceMemberId,
    ) : AllianceRepository by store {

        val expectedVersions = mutableListOf<AllianceVersion>()

        override suspend fun setRole(caller: PlayerId, alliance: AllianceId, member: AllianceMemberId, role: AllianceRole, expected: AllianceVersion): AllianceChange {
            expectedVersions += expected
            if (expectedVersions.size == 1) {
                assertIs<AllianceChange.Applied>(store.setRole(caller, alliance, successor, AllianceRole.FOUNDER, expected))
            }
            return store.setRole(caller, alliance, member, role, expected)
        }
    }

    private class ContendedAllianceRepository(
        private val store: AllianceRepository,
        private var contentions: Int,
    ) : AllianceRepository by store {

        var attempts = 0
            private set

        override suspend fun petition(player: PlayerId, alliance: AllianceId, now: Instant, expected: AllianceVersion): AllianceChange {
            interfere(alliance, now)
            return store.petition(player, alliance, now, expected)
        }

        override suspend fun detach(player: PlayerId, alliance: AllianceId, expected: AllianceVersion): AllianceChange {
            interfere(alliance, TEST_NOW)
            return store.detach(player, alliance, expected)
        }

        override suspend fun approve(caller: PlayerId, alliance: AllianceId, request: JoinRequestId, decision: JoinDecision, now: Instant, expected: AllianceVersion): AllianceChange {
            interfere(alliance, now)
            return store.approve(caller, alliance, request, decision, now, expected)
        }

        private suspend fun interfere(alliance: AllianceId, now: Instant) {
            attempts++
            if (contentions > 0) {
                contentions--
                val current = assertIs<AllianceLookup.Present>(store.alliance(alliance, now)).alliance
                store.petition(PlayerId("winner-$attempts"), alliance, now, current.version)
            }
        }
    }
}
