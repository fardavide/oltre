package dev.fardavide.oltre.client.net.data

import dev.fardavide.oltre.protocol.Alliance
import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.AllianceLevel
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
import dev.fardavide.oltre.protocol.JoinDecision
import dev.fardavide.oltre.protocol.JoinRequestId
import dev.fardavide.oltre.protocol.SessionToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class FakeOltreApiAllianceTest {

    @Test
    fun `alliance routes apply offline then transient then global then route refusals`() = runTest {
        for (request in fakeRequests()) {
            val api = FakeOltreApi(offline = true, error = ApiError.Unauthenticated)
            api.transientErrors += ApiError.TooManyRequests(41)
            refuseAlliance(api, request.route, ApiError.AllianceRoleTooLow)
            assertEquals(ApiResult.Unreachable, invokeAlliance(api, request), request.route.name)
            api.offline = false
            assertEquals(ApiResult.Refused(ApiError.TooManyRequests(41)), invokeAlliance(api, request), request.route.name)
            assertEquals(ApiResult.Refused(ApiError.Unauthenticated), invokeAlliance(api, request), request.route.name)
            api.error = null
            assertEquals(ApiResult.Refused(ApiError.AllianceRoleTooLow), invokeAlliance(api, request), request.route.name)
            assertEquals(List(4) { request }, api.allianceRequests())
        }
    }

    @Test
    fun `a route refusal does not prevent other alliance routes from answering`() = runTest {
        for (route in AllianceRoute.entries) {
            val api = FakeOltreApi()
            refuseAlliance(api, route, ApiError.AllianceRoleTooLow)
            for (request in fakeRequests()) {
                assertEquals(
                    if (request.route == route) ApiResult.Refused(ApiError.AllianceRoleTooLow) else ApiResult.Answered(Unit),
                    invokeAlliance(api, request),
                    "${route.name} refusal while asking ${request.route.name}",
                )
            }
        }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `each alliance request records its token and typed subjects before the route is answered`() = runTest {
        for (request in fakeRequests()) {
            val api = FakeOltreApi()
            api.holdAlliance(request.route)
            val call = launch { invokeAlliance(api, request) }
            runCurrent()
            assertFalse(call.isCompleted, request.route.name)
            assertEquals(listOf(request), api.allianceRequests())
            api.answerAlliance(request.route)
            runCurrent()
            assertEquals(true, call.isCompleted, request.route.name)
        }
    }

    @Test
    fun `mutations answer authoritative scripted standing instead of echoing the request`() = runTest {
        val standing = AllianceStanding.Enlisted(fakeAlliance(), AllianceRole.FOUNDER)
        for (mutation in fakeMutations()) {
            assertEquals(ApiResult.Answered(standing), mutation(FakeOltreApi(allianceStanding = standing)))
        }
    }

    @Test
    fun `search returns the scripted normalized query and page marker`() = runTest {
        val response = AllianceSearchResponse(
            ApiVersion.CURRENT, "ferro", listOf(fakeAlliance()), AllianceSearchCursor("next-page"),
        )
        val api = FakeOltreApi(allianceSearch = response)
        assertEquals(ApiResult.Answered(response), api.searchAlliances(SessionToken("adas.access"), "FERRO", null))
    }

    @Test
    fun `roster read preserves the scripted pending visibility`() = runTest {
        val roster = AllianceRosterResponse(ApiVersion.CURRENT, emptyList(), emptyList())
        val api = FakeOltreApi(allianceRoster = roster)
        assertEquals(ApiResult.Answered(roster), api.allianceRoster(SessionToken("adas.access")))
    }

    @Test
    fun `alliance read answers the standing this server holds`() = runTest {
        val standing = AllianceStanding.Petitioning(fakeAlliance())
        val api = FakeOltreApi(allianceStanding = standing)
        assertEquals(ApiResult.Answered(standing), api.alliance(SessionToken("adas.access")))
    }
}

internal fun fakeAlliance(): Alliance = Alliance(
    AllianceId("ferro-alto"),
    AllianceName("Ferro Alto"),
    AllianceTag("FERRO"),
    AllianceLevel(0),
    AllianceSeats(1, 12),
)

internal fun fakeMutations(): List<suspend (OltreApi) -> ApiResult<AllianceStanding>> = listOf(
    { it.createAlliance(SessionToken("adas.access"), AllianceName("Vanguard"), AllianceTag("VAN")) },
    { it.renameAlliance(SessionToken("adas.access"), AllianceName("Vanguard"), AllianceTag("VAN")) },
    { it.requestToJoin(SessionToken("adas.access"), AllianceId("vanguard")) },
    { it.answerRequest(SessionToken("adas.access"), JoinRequestId("adas-petition"), JoinDecision.ADMITTED) },
    { it.setMemberRole(SessionToken("adas.access"), AllianceMemberId("adas-seat"), AllianceRole.ADMIN) },
    { it.removeMember(SessionToken("adas.access"), AllianceMemberId("adas-seat")) },
    { it.leaveAlliance(SessionToken("adas.access")) },
    { it.disbandAlliance(SessionToken("adas.access")) },
)

internal fun fakeRequests(): List<AllianceRequest> = listOf(
    AllianceRequest.Standing(SessionToken("adas.access")),
    AllianceRequest.Roster(SessionToken("adas.access")),
    AllianceRequest.Search(SessionToken("adas.access"), "FERRO", AllianceSearchCursor("next-page")),
    AllianceRequest.Create(SessionToken("adas.access"), AllianceName("Vanguard"), AllianceTag("VAN")),
    AllianceRequest.Rename(SessionToken("adas.access"), AllianceName("Vanguard"), AllianceTag("VAN")),
    AllianceRequest.RequestToJoin(SessionToken("adas.access"), AllianceId("vanguard")),
    AllianceRequest.AnswerRequest(SessionToken("adas.access"), JoinRequestId("adas-petition"), JoinDecision.ADMITTED),
    AllianceRequest.SetMemberRole(SessionToken("adas.access"), AllianceMemberId("adas-seat"), AllianceRole.ADMIN),
    AllianceRequest.RemoveMember(SessionToken("adas.access"), AllianceMemberId("adas-seat")),
    AllianceRequest.Leave(SessionToken("adas.access")),
    AllianceRequest.Disband(SessionToken("adas.access")),
)

internal suspend fun invokeAlliance(api: OltreApi, request: AllianceRequest): ApiResult<Unit> =
    when (request) {
        is AllianceRequest.Standing -> api.alliance(request.access).withoutValue()
        is AllianceRequest.Roster -> api.allianceRoster(request.access).withoutValue()
        is AllianceRequest.Search -> api.searchAlliances(request.access, request.query, request.cursor).withoutValue()
        is AllianceRequest.Create -> api.createAlliance(request.access, request.name, request.tag).withoutValue()
        is AllianceRequest.Rename -> api.renameAlliance(request.access, request.name, request.tag).withoutValue()
        is AllianceRequest.RequestToJoin -> api.requestToJoin(request.access, request.alliance).withoutValue()
        is AllianceRequest.AnswerRequest -> api.answerRequest(request.access, request.request, request.decision).withoutValue()
        is AllianceRequest.SetMemberRole -> api.setMemberRole(request.access, request.member, request.role).withoutValue()
        is AllianceRequest.RemoveMember -> api.removeMember(request.access, request.member).withoutValue()
        is AllianceRequest.Leave -> api.leaveAlliance(request.access).withoutValue()
        is AllianceRequest.Disband -> api.disbandAlliance(request.access).withoutValue()
    }

private fun <T> ApiResult<T>.withoutValue(): ApiResult<Unit> = when (this) {
    is ApiResult.Answered -> ApiResult.Answered(Unit)
    is ApiResult.Refused -> ApiResult.Refused(error)
    ApiResult.Unreachable -> ApiResult.Unreachable
}

private fun refuseAlliance(api: FakeOltreApi, route: AllianceRoute, error: ApiError) {
    when (route) {
        AllianceRoute.STANDING -> api.allianceError = error
        AllianceRoute.ROSTER -> api.allianceRosterError = error
        AllianceRoute.SEARCH -> api.searchAlliancesError = error
        AllianceRoute.CREATE -> api.createAllianceError = error
        AllianceRoute.RENAME -> api.renameAllianceError = error
        AllianceRoute.REQUEST_TO_JOIN -> api.requestToJoinError = error
        AllianceRoute.ANSWER_REQUEST -> api.answerRequestError = error
        AllianceRoute.SET_MEMBER_ROLE -> api.setMemberRoleError = error
        AllianceRoute.REMOVE_MEMBER -> api.removeMemberError = error
        AllianceRoute.LEAVE -> api.leaveAllianceError = error
        AllianceRoute.DISBAND -> api.disbandAllianceError = error
    }
}
