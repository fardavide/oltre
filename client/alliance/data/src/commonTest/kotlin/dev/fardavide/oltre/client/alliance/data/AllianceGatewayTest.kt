package dev.fardavide.oltre.client.alliance.data

import dev.fardavide.oltre.client.alliance.domain.AllianceRosterReading
import dev.fardavide.oltre.client.alliance.domain.AllianceState
import dev.fardavide.oltre.client.net.data.AllianceRequest
import dev.fardavide.oltre.client.net.data.AllianceRoute
import dev.fardavide.oltre.client.net.data.ApiResult
import dev.fardavide.oltre.client.net.data.FakeOltreApi
import dev.fardavide.oltre.client.net.data.FakeSessionStore
import dev.fardavide.oltre.client.net.data.OltreApi
import dev.fardavide.oltre.client.net.data.SessionKeeper
import dev.fardavide.oltre.core.Experience
import dev.fardavide.oltre.protocol.Alliance
import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.AllianceLevel
import dev.fardavide.oltre.protocol.AllianceMember
import dev.fardavide.oltre.protocol.AllianceMemberId
import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceRosterResponse
import dev.fardavide.oltre.protocol.AllianceSearchCursor
import dev.fardavide.oltre.protocol.AllianceSearchResponse
import dev.fardavide.oltre.protocol.AllianceSeats
import dev.fardavide.oltre.protocol.AllianceStanding
import dev.fardavide.oltre.protocol.AllianceTag
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.ExperienceReading
import dev.fardavide.oltre.protocol.JoinDecision
import dev.fardavide.oltre.protocol.JoinRequestId
import dev.fardavide.oltre.protocol.PlayerProfile
import dev.fardavide.oltre.protocol.SessionResponse
import dev.fardavide.oltre.protocol.SessionToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class AllianceGatewayTest {

    @Test
    fun `a gateway starts without claiming an alliance was read`() {
        assertEquals(AllianceState.Unread, Scenario().gateway.standing)
    }

    @Test
    fun `an unaffiliated answer replaces the unread standing`() = runTest {
        val scenario = Scenario()

        assertEquals(
            ApiResult.Answered(AllianceState.Unaffiliated),
            scenario.gateway.alliance(SessionToken("access")),
        )
        assertEquals(AllianceState.Unaffiliated, scenario.gateway.standing)
    }

    @Test
    fun `a pending petition keeps the alliance it is waiting on`() = runTest {
        val alliance = fakeAlliance()
        val scenario = Scenario(FakeOltreApi(allianceStanding = AllianceStanding.Petitioning(alliance)))

        assertEquals(
            ApiResult.Answered(AllianceState.Petitioning(alliance)),
            scenario.gateway.alliance(SessionToken("access")),
        )
    }

    @Test
    fun `an enlisted read combines standing and roster`() = runTest {
        val alliance = fakeAlliance()
        val roster = AllianceRosterResponse(ApiVersion.CURRENT, emptyList(), emptyList())
        val scenario = Scenario(
            FakeOltreApi(
                allianceStanding = AllianceStanding.Enlisted(alliance, AllianceRole.FOUNDER),
                allianceRoster = roster,
            ),
        )

        assertEquals(
            ApiResult.Answered(AllianceState.Enlisted(alliance, AllianceRole.FOUNDER, AllianceRosterReading.Read(roster))),
            scenario.gateway.alliance(SessionToken("access")),
        )
    }

    @Test
    fun `transferring leadership caches the returned admin role and invalidates the roster`() = runTest {
        val alliance = fakeAlliance()
        val scenario = Scenario(
            FakeOltreApi(
                allianceStanding = AllianceStanding.Enlisted(alliance, AllianceRole.FOUNDER),
            ),
        )
        scenario.gateway.alliance(SessionToken("access"))
        scenario.api.allianceStanding = AllianceStanding.Enlisted(alliance, AllianceRole.ADMIN)

        assertEquals(
            ApiResult.Answered(AllianceState.Enlisted(alliance, AllianceRole.ADMIN, AllianceRosterReading.Unread)),
            scenario.gateway.setMemberRole(SessionToken("access"), AllianceMemberId("successor"), AllianceRole.FOUNDER),
        )
        assertEquals(
            AllianceState.Enlisted(alliance, AllianceRole.ADMIN, AllianceRosterReading.Unread),
            scenario.gateway.standing,
        )
        assertEquals(
            AllianceRequest.SetMemberRole(
                SessionToken("access"), AllianceMemberId("successor"), AllianceRole.FOUNDER,
            ),
            scenario.api.allianceRequests().last(),
        )
    }

    @Test
    fun `leaving clears the cached alliance and roster`() = runTest {
        val scenario = Scenario(
            FakeOltreApi(
                allianceStanding = AllianceStanding.Enlisted(fakeAlliance(), AllianceRole.MEMBER),
            ),
        )
        scenario.gateway.alliance(SessionToken("access"))
        scenario.api.allianceStanding = AllianceStanding.Unaffiliated

        assertEquals(
            ApiResult.Answered(AllianceState.Unaffiliated),
            scenario.gateway.leaveAlliance(SessionToken("access")),
        )
        assertEquals(AllianceState.Unaffiliated, scenario.gateway.standing)
        assertEquals(AllianceRequest.Leave(SessionToken("access")), scenario.api.allianceRequests().last())
    }

    @Test
    fun `disbanding clears the founder standing and roster`() = runTest {
        val scenario = Scenario(
            FakeOltreApi(
                allianceStanding = AllianceStanding.Enlisted(fakeAlliance(), AllianceRole.FOUNDER),
            ),
        )
        scenario.gateway.alliance(SessionToken("access"))
        scenario.api.allianceStanding = AllianceStanding.Unaffiliated

        assertEquals(
            ApiResult.Answered(AllianceState.Unaffiliated),
            scenario.gateway.disbandAlliance(SessionToken("access")),
        )
        assertEquals(AllianceState.Unaffiliated, scenario.gateway.standing)
        assertEquals(AllianceRequest.Disband(SessionToken("access")), scenario.api.allianceWrites().last())
    }

    @Test
    fun `founding caches the alliance returned by the server`() = runTest {
        val alliance = fakeAlliance()
        val scenario = Scenario(
            FakeOltreApi(
                allianceStanding = AllianceStanding.Enlisted(alliance, AllianceRole.FOUNDER),
            ),
        )

        assertEquals(
            ApiResult.Answered(AllianceState.Enlisted(alliance, AllianceRole.FOUNDER, AllianceRosterReading.Unread)),
            scenario.gateway.createAlliance(SessionToken("access"), AllianceName("Ferro Alto"), AllianceTag("FERRO")),
        )
        assertEquals(
            AllianceRequest.Create(SessionToken("access"), AllianceName("Ferro Alto"), AllianceTag("FERRO")),
            scenario.api.allianceWrites().single(),
        )
    }

    @Test
    fun `renaming uses the server answer rather than the submitted name`() = runTest {
        val alliance = fakeAlliance()
        val scenario = Scenario(
            FakeOltreApi(
                allianceStanding = AllianceStanding.Enlisted(alliance, AllianceRole.FOUNDER),
            ),
        )

        assertEquals(
            ApiResult.Answered(AllianceState.Enlisted(alliance, AllianceRole.FOUNDER, AllianceRosterReading.Unread)),
            scenario.gateway.renameAlliance(SessionToken("access"), AllianceName("Different"), AllianceTag("ALT")),
        )
        assertEquals(
            AllianceRequest.Rename(SessionToken("access"), AllianceName("Different"), AllianceTag("ALT")),
            scenario.api.allianceWrites().single(),
        )
    }

    @Test
    fun `requesting entry caches the pending petition`() = runTest {
        val alliance = fakeAlliance()
        val scenario = Scenario(FakeOltreApi(allianceStanding = AllianceStanding.Petitioning(alliance)))

        assertEquals(
            ApiResult.Answered(AllianceState.Petitioning(alliance)),
            scenario.gateway.requestToJoin(SessionToken("access"), alliance.id),
        )
        assertEquals(AllianceState.Petitioning(alliance), scenario.gateway.standing)
        assertEquals(
            AllianceRequest.RequestToJoin(SessionToken("access"), alliance.id),
            scenario.api.allianceWrites().single(),
        )
    }

    @Test
    fun `answering a petition forwards the typed decision and invalidates the old roster`() = runTest {
        val alliance = fakeAlliance()
        val scenario = Scenario(
            FakeOltreApi(
                allianceStanding = AllianceStanding.Enlisted(alliance, AllianceRole.ADMIN),
            ),
        )
        scenario.gateway.alliance(SessionToken("access"))

        assertEquals(
            ApiResult.Answered(AllianceState.Enlisted(alliance, AllianceRole.ADMIN, AllianceRosterReading.Unread)),
            scenario.gateway.answerRequest(SessionToken("access"), JoinRequestId("petition"), JoinDecision.ADMITTED),
        )
        assertEquals(
            AllianceRequest.AnswerRequest(SessionToken("access"), JoinRequestId("petition"), JoinDecision.ADMITTED),
            scenario.api.allianceWrites().single(),
        )
    }

    @Test
    fun `removing a member invalidates the cached roster`() = runTest {
        val alliance = fakeAlliance()
        val scenario = Scenario(
            FakeOltreApi(
                allianceStanding = AllianceStanding.Enlisted(alliance, AllianceRole.ADMIN),
            ),
        )
        scenario.gateway.alliance(SessionToken("access"))

        assertEquals(
            ApiResult.Answered(AllianceState.Enlisted(alliance, AllianceRole.ADMIN, AllianceRosterReading.Unread)),
            scenario.gateway.removeMember(SessionToken("access"), AllianceMemberId("member")),
        )
        assertEquals(
            AllianceRequest.RemoveMember(SessionToken("access"), AllianceMemberId("member")),
            scenario.api.allianceWrites().single(),
        )
    }

    @Test
    fun `searching forwards the query and cursor without changing standing`() = runTest {
        val cursor = AllianceSearchCursor("opaque-cursor")
        val results = AllianceSearchResponse(ApiVersion.CURRENT, "ferro", emptyList(), cursor)
        val scenario = Scenario(FakeOltreApi(allianceSearch = results))

        assertEquals(
            ApiResult.Answered(results),
            scenario.gateway.searchAlliances(SessionToken("access"), "ferro", cursor),
        )
        assertEquals(AllianceState.Unread, scenario.gateway.standing)
        assertEquals(
            AllianceRequest.Search(SessionToken("access"), "ferro", cursor),
            scenario.api.allianceRequests().single(),
        )
    }

    @Test
    fun `a read still fetching its roster cannot restore membership after leaving`() = runTest {
        val scenario = Scenario(
            FakeOltreApi(
                allianceStanding = AllianceStanding.Enlisted(fakeAlliance(), AllianceRole.MEMBER),
            ),
        )
        scenario.api.holdAlliance(AllianceRoute.ROSTER)
        val reading = launch { scenario.gateway.alliance(SessionToken("access")) }
        runCurrent()
        scenario.api.allianceStanding = AllianceStanding.Unaffiliated
        val leaving = launch { scenario.gateway.leaveAlliance(SessionToken("access")) }
        runCurrent()

        scenario.api.answerAlliance(AllianceRoute.ROSTER)
        advanceUntilIdle()
        reading.join()
        leaving.join()

        assertEquals(AllianceState.Unaffiliated, scenario.gateway.standing)
    }

    @Test
    fun `an unreachable first read remains unread`() = runTest {
        val scenario = Scenario(FakeOltreApi(offline = true))

        assertEquals(ApiResult.Unreachable, scenario.gateway.alliance(SessionToken("access")))
        assertEquals(AllianceState.Unread, scenario.gateway.standing)
        assertEquals(1, scenario.api.allianceRequests().size)
    }

    @Test
    fun `a refused first read preserves the wire error and unread standing`() = runTest {
        val scenario = Scenario(FakeOltreApi(allianceError = ApiError.AllianceRoleTooLow))

        assertEquals(
            ApiResult.Refused(ApiError.AllianceRoleTooLow),
            scenario.gateway.alliance(SessionToken("access")),
        )
        assertEquals(AllianceState.Unread, scenario.gateway.standing)
        assertEquals(1, scenario.api.allianceRequests().size)
    }

    @Test
    fun `a failed reread retains the last complete answer`() = runTest {
        for (failure in listOf(ApiResult.Unreachable, ApiResult.Refused(ApiError.AllianceRoleTooLow))) {
            val scenario = Scenario(FakeOltreApi(allianceStanding = AllianceStanding.Petitioning(fakeAlliance())))
            scenario.gateway.alliance(SessionToken("access"))
            val held = scenario.gateway.standing
            scenario.api.offline = failure == ApiResult.Unreachable
            scenario.api.allianceError = ApiError.AllianceRoleTooLow

            assertEquals(failure, scenario.gateway.alliance(SessionToken("access")))
            assertEquals(held, scenario.gateway.standing)
        }
    }

    @Test
    fun `a roster refusal leaves a successful mutation standing explicitly unread`() = runTest {
        val scenario = Scenario(
            FakeOltreApi(
                allianceStanding = AllianceStanding.Enlisted(fakeAlliance(), AllianceRole.FOUNDER),
            ),
        )
        scenario.gateway.createAlliance(SessionToken("access"), AllianceName("Ferro Alto"), AllianceTag("FERRO"))
        val held = scenario.gateway.standing
        scenario.api.allianceRosterError = ApiError.AllianceRoleTooLow

        assertEquals(
            ApiResult.Refused(ApiError.AllianceRoleTooLow),
            scenario.gateway.alliance(SessionToken("access")),
        )
        assertEquals(held, scenario.gateway.standing)
    }

    @Test
    fun `losing the connection during a roster read preserves the complete cached roster`() = runTest {
        val scenario = Scenario(
            FakeOltreApi(
                allianceStanding = AllianceStanding.Enlisted(fakeAlliance(), AllianceRole.ADMIN),
            ),
        )
        scenario.gateway.alliance(SessionToken("access"))
        val held = scenario.gateway.standing
        scenario.api.holdAlliance(AllianceRoute.ROSTER)
        val reading = async { scenario.gateway.alliance(SessionToken("access")) }
        runCurrent()
        scenario.api.offline = true
        scenario.api.answerAlliance(AllianceRoute.ROSTER)

        assertEquals(ApiResult.Unreachable, reading.await())
        assertEquals(held, scenario.gateway.standing)
    }

    @Test
    fun `every refused mutation preserves the last authoritative cache`() = runTest {
        for (mutation in Mutation.entries) {
            val scenario = Scenario(
                FakeOltreApi(
                    allianceStanding = AllianceStanding.Enlisted(fakeAlliance(), AllianceRole.FOUNDER),
                ),
            )
            scenario.gateway.alliance(SessionToken("access"))
            val held = scenario.gateway.standing
            scenario.api.error = ApiError.AllianceRoleTooLow

            assertEquals(ApiResult.Refused(ApiError.AllianceRoleTooLow), scenario.mutate(mutation), mutation.name)
            assertEquals(held, scenario.gateway.standing, mutation.name)
        }
    }

    @Test
    fun `every unreachable mutation preserves the last authoritative cache`() = runTest {
        for (mutation in Mutation.entries) {
            val scenario = Scenario(FakeOltreApi(allianceStanding = AllianceStanding.Petitioning(fakeAlliance())))
            scenario.gateway.alliance(SessionToken("access"))
            val held = scenario.gateway.standing
            scenario.api.offline = true

            assertEquals(ApiResult.Unreachable, scenario.mutate(mutation), mutation.name)
            assertEquals(held, scenario.gateway.standing, mutation.name)
        }
    }

    @Test
    fun `an expired standing read renews once and supplies the new credential to the roster`() = runTest {
        val scenario = Scenario(
            FakeOltreApi(
                allianceStanding = AllianceStanding.Enlisted(fakeAlliance(), AllianceRole.MEMBER),
            ),
        )
        scenario.api.transientErrors += ApiError.SessionExpired

        scenario.gateway.alliance(SessionToken("access"))

        assertEquals(
            listOf(
                AllianceRequest.Standing(SessionToken("access")),
                AllianceRequest.Standing(scenario.api.session.accessToken),
                AllianceRequest.Roster(scenario.api.session.accessToken),
            ),
            scenario.api.allianceRequests(),
        )
        assertEquals(scenario.api.session, scenario.store.read())
    }

    @Test
    fun `an expired roster renews the whole read once on the new credential`() = runTest {
        val scenario = Scenario(
            FakeOltreApi(
                allianceStanding = AllianceStanding.Enlisted(fakeAlliance(), AllianceRole.ADMIN),
            ),
        )
        scenario.api.holdAlliance(AllianceRoute.ROSTER)
        val reading = async { scenario.gateway.alliance(SessionToken("access")) }
        runCurrent()
        scenario.api.transientErrors += ApiError.SessionExpired
        scenario.api.answerAlliance(AllianceRoute.ROSTER)
        reading.await()

        assertEquals(
            listOf(
                AllianceRequest.Standing(SessionToken("access")),
                AllianceRequest.Roster(SessionToken("access")),
                AllianceRequest.Standing(scenario.api.session.accessToken),
                AllianceRequest.Roster(scenario.api.session.accessToken),
            ),
            scenario.api.allianceRequests(),
        )
    }

    @Test
    fun `every expired mutation is retried once on the renewed credential`() = runTest {
        for (mutation in Mutation.entries) {
            val scenario = Scenario()
            scenario.api.transientErrors += ApiError.SessionExpired

            assertEquals(ApiResult.Answered(AllianceState.Unaffiliated), scenario.mutate(mutation), mutation.name)
            assertEquals(
                listOf(SessionToken("access"), scenario.api.session.accessToken),
                scenario.api.allianceWrites().map { it.access },
                mutation.name,
            )
        }
    }

    @Test
    fun `a second expiry becomes unauthenticated without another retry`() = runTest {
        val scenario = Scenario(FakeOltreApi(allianceError = ApiError.SessionExpired))

        assertEquals(
            ApiResult.Refused(ApiError.Unauthenticated),
            scenario.gateway.alliance(SessionToken("access")),
        )
        assertEquals(2, scenario.api.allianceRequests().size)
        assertEquals(AllianceState.Unread, scenario.gateway.standing)
    }

    @Test
    fun `a renewal nobody answers remains unreachable and keeps the session`() = runTest {
        val api = FakeOltreApi(allianceError = ApiError.SessionExpired)
        val scenario = Scenario(api, SilentRenewal(api))
        val held = scenario.store.read()

        assertEquals(ApiResult.Unreachable, scenario.gateway.alliance(SessionToken("access")))
        assertEquals(held, scenario.store.read())
        assertEquals(AllianceState.Unread, scenario.gateway.standing)
        assertEquals(1, api.allianceRequests().size)
    }

    @Test
    fun `an expired search retains its query and cursor on the renewed credential`() = runTest {
        val scenario = Scenario()
        val cursor = AllianceSearchCursor("opaque-cursor")
        scenario.api.transientErrors += ApiError.SessionExpired

        assertEquals(
            ApiResult.Answered(scenario.api.allianceSearch),
            scenario.gateway.searchAlliances(SessionToken("access"), "ferro", cursor),
        )
        assertEquals(
            listOf(
                AllianceRequest.Search(SessionToken("access"), "ferro", cursor),
                AllianceRequest.Search(scenario.api.session.accessToken, "ferro", cursor),
            ),
            scenario.api.allianceRequests(),
        )
        assertEquals(AllianceState.Unread, scenario.gateway.standing)
    }

    @Test
    fun `a roster preserves unknown experience and known zero without exposing hidden petitions`() = runTest {
        val alliance = fakeAlliance()
        val roster = AllianceRosterResponse(
            ApiVersion.CURRENT,
            listOf(
                AllianceMember(
                    AllianceMemberId("unknown"),
                    PlayerProfile(null, null),
                    AllianceRole.FOUNDER,
                    ExperienceReading.Unknown,
                    Instant.parse("2026-08-26T09:00:00Z"),
                ),
                AllianceMember(
                    AllianceMemberId("zero"),
                    PlayerProfile(null, null),
                    AllianceRole.MEMBER,
                    ExperienceReading.Known(Experience(0)),
                    Instant.parse("2026-08-26T09:00:00Z"),
                ),
            ),
            null,
        )
        val scenario = Scenario(
            FakeOltreApi(
                allianceStanding = AllianceStanding.Enlisted(alliance, AllianceRole.MEMBER),
                allianceRoster = roster,
            ),
        )

        assertEquals(
            ApiResult.Answered(AllianceState.Enlisted(alliance, AllianceRole.MEMBER, AllianceRosterReading.Read(roster))),
            scenario.gateway.alliance(SessionToken("access")),
        )
    }
}

private class SilentRenewal(api: OltreApi) : OltreApi by api {
    override suspend fun refresh(refreshToken: SessionToken): ApiResult<SessionResponse> = ApiResult.Unreachable
}

private enum class Mutation {
    CREATE,
    RENAME,
    REQUEST_TO_JOIN,
    ANSWER_REQUEST,
    SET_MEMBER_ROLE,
    REMOVE_MEMBER,
    LEAVE,
    DISBAND,
}

private suspend fun Scenario.mutate(mutation: Mutation): ApiResult<AllianceState> = when (mutation) {
    Mutation.CREATE -> gateway.createAlliance(SessionToken("access"), AllianceName("Ferro Alto"), AllianceTag("FERRO"))
    Mutation.RENAME -> gateway.renameAlliance(SessionToken("access"), AllianceName("Ferro Alto"), AllianceTag("FERRO"))
    Mutation.REQUEST_TO_JOIN -> gateway.requestToJoin(SessionToken("access"), AllianceId("ferro-alto"))
    Mutation.ANSWER_REQUEST -> gateway.answerRequest(SessionToken("access"), JoinRequestId("petition"), JoinDecision.ADMITTED)
    Mutation.SET_MEMBER_ROLE -> gateway.setMemberRole(SessionToken("access"), AllianceMemberId("member"), AllianceRole.ADMIN)
    Mutation.REMOVE_MEMBER -> gateway.removeMember(SessionToken("access"), AllianceMemberId("member"))
    Mutation.LEAVE -> gateway.leaveAlliance(SessionToken("access"))
    Mutation.DISBAND -> gateway.disbandAlliance(SessionToken("access"))
}

private fun fakeAlliance(): Alliance = Alliance(
    id = AllianceId("ferro-alto"),
    name = AllianceName("Ferro Alto"),
    tag = AllianceTag("FERRO"),
    level = AllianceLevel(0),
    seats = AllianceSeats(taken = 1, cap = 20),
)

private class Scenario(val api: FakeOltreApi = FakeOltreApi(), transport: OltreApi = api) {
    val store = FakeSessionStore(api.session)
    val sessions = SessionKeeper(transport, store, FakeClock())
    val gateway = AllianceGateway(transport, sessions)
}
