package dev.fardavide.oltre.client.alliance.data

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.fardavide.oltre.client.alliance.domain.AllianceRosterReading
import dev.fardavide.oltre.client.alliance.domain.AllianceState
import dev.fardavide.oltre.client.net.data.ApiResult
import dev.fardavide.oltre.client.net.data.FakeSessionStore
import dev.fardavide.oltre.client.net.data.KtorOltreApi
import dev.fardavide.oltre.client.net.data.SessionKeeper
import dev.fardavide.oltre.client.net.data.oltreHttpClient
import dev.fardavide.oltre.core.Experience
import dev.fardavide.oltre.protocol.Alliance
import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.AllianceLevel
import dev.fardavide.oltre.protocol.AllianceMember
import dev.fardavide.oltre.protocol.AllianceMemberId
import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceResponse
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceRosterResponse
import dev.fardavide.oltre.protocol.AllianceSearchCursor
import dev.fardavide.oltre.protocol.AllianceSearchResponse
import dev.fardavide.oltre.protocol.AllianceSeats
import dev.fardavide.oltre.protocol.AllianceStanding
import dev.fardavide.oltre.protocol.AllianceTag
import dev.fardavide.oltre.protocol.AnswerJoinRequest
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.CreateAllianceRequest
import dev.fardavide.oltre.protocol.ExperienceReading
import dev.fardavide.oltre.protocol.JoinAllianceRequest
import dev.fardavide.oltre.protocol.JoinDecision
import dev.fardavide.oltre.protocol.JoinRequest
import dev.fardavide.oltre.protocol.JoinRequestId
import dev.fardavide.oltre.protocol.KickMemberRequest
import dev.fardavide.oltre.protocol.PlayerProfile
import dev.fardavide.oltre.protocol.Protocol
import dev.fardavide.oltre.protocol.RefreshRequest
import dev.fardavide.oltre.protocol.RenameAllianceRequest
import dev.fardavide.oltre.protocol.SessionResponse
import dev.fardavide.oltre.protocol.SessionToken
import dev.fardavide.oltre.protocol.SetMemberRoleRequest
import kotlinx.coroutines.test.runTest
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class AllianceGatewayIntegrationTest {

    @Test
    fun `an enlisted read authenticates both requests and caches the complete roster`() = runTest {
        val held = fakeHeldSession()
        val alliance = fakeAlliance()
        val roster = fakeRoster()
        val read = AllianceState.Enlisted(alliance, AllianceRole.FOUNDER, AllianceRosterReading.Read(roster))
        SocketScenario(held).use { scenario ->
            scenario.reply(AllianceStanding.Enlisted(alliance, AllianceRole.FOUNDER))
            scenario.replyPayload(roster)

            assertEquals(ApiResult.Answered(read), scenario.gateway.alliance(held.accessToken))
            assertEquals(read, scenario.gateway.standing)
            assertEquals(listOf("/v1/alliance", "/v1/alliance/roster"), scenario.requests.map { it.path })
            scenario.requests.forEach { request ->
                assertEquals("GET", request.method)
                assertEquals(Protocol.BEARER_PREFIX + held.accessToken.value, request.authorization)
                assertEquals("", request.body)
            }
        }
    }

    @Test
    fun `an unaffiliated read does not ask for a roster`() = runTest {
        val held = fakeHeldSession()
        SocketScenario(held).use { scenario ->
            scenario.reply(AllianceStanding.Unaffiliated)

            assertEquals(ApiResult.Answered(AllianceState.Unaffiliated), scenario.gateway.alliance(held.accessToken))
            assertEquals(AllianceState.Unaffiliated, scenario.gateway.standing)
            assertEquals("/v1/alliance", scenario.requests.single().path)
        }
    }

    @Test
    fun `a petitioning read preserves the alliance without requesting a roster`() = runTest {
        val held = fakeHeldSession()
        val alliance = fakeAlliance()
        SocketScenario(held).use { scenario ->
            scenario.reply(AllianceStanding.Petitioning(alliance))

            assertEquals(ApiResult.Answered(AllianceState.Petitioning(alliance)), scenario.gateway.alliance(held.accessToken))
            assertEquals(AllianceState.Petitioning(alliance), scenario.gateway.standing)
            assertEquals("/v1/alliance", scenario.requests.single().path)
        }
    }

    @Test
    fun `founding posts the chosen name and caches the server assigned alliance`() = runTest {
        val held = fakeHeldSession()
        val alliance = fakeAlliance()
        val name = AllianceName("Chosen Name")
        val tag = AllianceTag("CHOSN")
        val unread = AllianceState.Enlisted(alliance, AllianceRole.FOUNDER, AllianceRosterReading.Unread)
        SocketScenario(held).use { scenario ->
            scenario.reply(AllianceStanding.Enlisted(alliance, AllianceRole.FOUNDER), status = 201)

            assertEquals(ApiResult.Answered(unread), scenario.gateway.createAlliance(held.accessToken, name, tag))
            assertEquals(unread, scenario.gateway.standing)
            assertEquals(CreateAllianceRequest(ApiVersion.CURRENT, name, tag),
                scenario.posted<CreateAllianceRequest>("/v1/alliance", held.accessToken))
        }
    }

    @Test
    fun `renaming caches the authoritative name and discards the old roster`() = runTest {
        val held = fakeHeldSession()
        val alliance = fakeAlliance()
        val name = AllianceName("Chosen Name")
        val tag = AllianceTag("CHOSN")
        SocketScenario(held).use { scenario ->
            scenario.readMembership(held.accessToken, alliance, fakeRoster())
            val renamed = alliance.copy(name = AllianceName("Server Name"), tag = AllianceTag("SRVR"))
            scenario.reply(AllianceStanding.Enlisted(renamed, AllianceRole.FOUNDER))

            val result = scenario.gateway.renameAlliance(held.accessToken, name, tag)

            val expected = AllianceState.Enlisted(renamed, AllianceRole.FOUNDER, AllianceRosterReading.Unread)
            assertEquals(ApiResult.Answered(expected), result)
            assertEquals(expected, scenario.gateway.standing)
            assertEquals(RenameAllianceRequest(ApiVersion.CURRENT, name, tag),
                scenario.posted<RenameAllianceRequest>("/v1/alliance/name", held.accessToken))
        }
    }

    @Test
    fun `requesting entry posts the typed alliance id and caches the petition`() = runTest {
        val held = fakeHeldSession()
        val alliance = fakeAlliance()
        SocketScenario(held).use { scenario ->
            scenario.reply(AllianceStanding.Petitioning(alliance))

            assertEquals(ApiResult.Answered(AllianceState.Petitioning(alliance)),
                scenario.gateway.requestToJoin(held.accessToken, alliance.id))
            assertEquals(AllianceState.Petitioning(alliance), scenario.gateway.standing)
            assertEquals(JoinAllianceRequest(ApiVersion.CURRENT, alliance.id),
                scenario.posted<JoinAllianceRequest>("/v1/alliance/join", held.accessToken))
        }
    }

    @Test
    fun `answering a petition posts the decision and invalidates the cached roster`() = runTest {
        val held = fakeHeldSession()
        val alliance = fakeAlliance()
        val petition = JoinRequestId("petition")
        val unread = AllianceState.Enlisted(alliance, AllianceRole.FOUNDER, AllianceRosterReading.Unread)
        SocketScenario(held).use { scenario ->
            scenario.readMembership(held.accessToken, alliance, fakeRoster())
            scenario.reply(AllianceStanding.Enlisted(alliance, AllianceRole.FOUNDER))

            assertEquals(ApiResult.Answered(unread), scenario.gateway.answerRequest(held.accessToken, petition, JoinDecision.ADMITTED))
            assertEquals(unread, scenario.gateway.standing)
            assertEquals(AnswerJoinRequest(ApiVersion.CURRENT, petition, JoinDecision.ADMITTED),
                scenario.posted<AnswerJoinRequest>("/v1/alliance/join/answer", held.accessToken))
        }
    }

    @Test
    fun `transferring leadership caches the returned role rather than the requested role`() = runTest {
        val held = fakeHeldSession()
        val alliance = fakeAlliance()
        val member = AllianceMemberId("successor-seat")
        SocketScenario(held).use { scenario ->
            scenario.readMembership(held.accessToken, alliance, fakeRoster())
            scenario.reply(AllianceStanding.Enlisted(alliance, AllianceRole.ADMIN))

            val expected = AllianceState.Enlisted(alliance, AllianceRole.ADMIN, AllianceRosterReading.Unread)
            assertEquals(ApiResult.Answered(expected), scenario.gateway.setMemberRole(held.accessToken, member, AllianceRole.FOUNDER))
            assertEquals(expected, scenario.gateway.standing)
            assertEquals(SetMemberRoleRequest(ApiVersion.CURRENT, member, AllianceRole.FOUNDER),
                scenario.posted<SetMemberRoleRequest>("/v1/alliance/members/role", held.accessToken))
        }
    }

    @Test
    fun `removing a member posts the seat id and discards the cached roster`() = runTest {
        val held = fakeHeldSession()
        val alliance = fakeAlliance()
        val member = AllianceMemberId("successor-seat")
        val unread = AllianceState.Enlisted(alliance, AllianceRole.FOUNDER, AllianceRosterReading.Unread)
        SocketScenario(held).use { scenario ->
            scenario.readMembership(held.accessToken, alliance, fakeRoster())
            scenario.reply(AllianceStanding.Enlisted(alliance, AllianceRole.FOUNDER))

            assertEquals(ApiResult.Answered(unread), scenario.gateway.removeMember(held.accessToken, member))
            assertEquals(unread, scenario.gateway.standing)
            assertEquals(KickMemberRequest(ApiVersion.CURRENT, member),
                scenario.posted<KickMemberRequest>("/v1/alliance/members/remove", held.accessToken))
        }
    }

    @Test
    fun `leaving sends a bodiless membership delete and clears cached membership`() = runTest {
        val held = fakeHeldSession()
        SocketScenario(held).use { scenario ->
            scenario.readMembership(held.accessToken, fakeAlliance(), fakeRoster())
            scenario.reply(AllianceStanding.Unaffiliated)

            assertEquals(ApiResult.Answered(AllianceState.Unaffiliated), scenario.gateway.leaveAlliance(held.accessToken))
            assertEquals(AllianceState.Unaffiliated, scenario.gateway.standing)
            scenario.assertDelete("/v1/alliance/membership", held.accessToken)
        }
    }

    @Test
    fun `disbanding deletes the alliance and clears the founder membership`() = runTest {
        val held = fakeHeldSession()
        SocketScenario(held).use { scenario ->
            scenario.readMembership(held.accessToken, fakeAlliance(), fakeRoster())
            scenario.reply(AllianceStanding.Unaffiliated)

            assertEquals(ApiResult.Answered(AllianceState.Unaffiliated), scenario.gateway.disbandAlliance(held.accessToken))
            assertEquals(AllianceState.Unaffiliated, scenario.gateway.standing)
            scenario.assertDelete("/v1/alliance", held.accessToken)
        }
    }

    @Test
    fun `search sends an escaped query and opaque cursor without changing membership`() = runTest {
        val held = fakeHeldSession()
        val alliance = fakeAlliance()
        val roster = fakeRoster()
        val read = AllianceState.Enlisted(alliance, AllianceRole.FOUNDER, AllianceRosterReading.Read(roster))
        SocketScenario(held).use { scenario ->
            scenario.readMembership(held.accessToken, alliance, roster)
            val cursor = AllianceSearchCursor("opaque+/=&")
            val answer = AllianceSearchResponse(ApiVersion.CURRENT, "ferro & alto", listOf(alliance), null)
            scenario.replyPayload(answer)

            assertEquals(ApiResult.Answered(answer), scenario.gateway.searchAlliances(held.accessToken, "Ferro & Alto", cursor))
            assertEquals(read, scenario.gateway.standing)
            val request = scenario.requests.last()
            assertEquals("GET", request.method)
            assertEquals("/v1/alliance/search", request.path)
            assertEquals(Protocol.BEARER_PREFIX + held.accessToken.value, request.authorization)
            assertEquals("", request.body)
            assertEquals(listOf("q" to "Ferro & Alto", "cursor" to cursor.value), request.query())
        }
    }

    @Test
    fun `first page search omits the cursor and preserves the next page marker`() = runTest {
        val held = fakeHeldSession()
        SocketScenario(held).use { scenario ->
            val answer = AllianceSearchResponse(ApiVersion.CURRENT, "ferro", listOf(fakeAlliance()), AllianceSearchCursor("next"))
            scenario.replyPayload(answer)

            assertEquals(ApiResult.Answered(answer), scenario.gateway.searchAlliances(held.accessToken, "ferro", null))
            assertEquals(listOf("q" to "ferro"), scenario.requests.single().query())
            assertEquals(AllianceState.Unread, scenario.gateway.standing)
        }
    }

    @Test
    fun `expired standing renews once and authenticates the whole read with the replacement`() = runTest {
        val held = fakeHeldSession()
        val fresh = fakeFreshSession()
        val alliance = fakeAlliance()
        val roster = fakeRoster()
        SocketScenario(held).use { scenario ->
            scenario.refuse(ApiError.SessionExpired)
            scenario.replyPayload(fresh)
            scenario.reply(AllianceStanding.Enlisted(alliance, AllianceRole.FOUNDER))
            scenario.replyPayload(roster)

            assertEquals(ApiResult.Answered(AllianceState.Enlisted(alliance, AllianceRole.FOUNDER, AllianceRosterReading.Read(roster))),
                scenario.gateway.alliance(held.accessToken))
            assertEquals(listOf("/v1/alliance", "/v1/auth/refresh", "/v1/alliance", "/v1/alliance/roster"),
                scenario.requests.map { it.path })
            scenario.assertRenewed(held, fresh)
            assertEquals(listOf(fresh.accessToken, fresh.accessToken).map { Protocol.BEARER_PREFIX + it.value },
                scenario.requests.takeLast(2).map { it.authorization })
        }
    }

    @Test
    fun `expired roster renews and rereads standing before publishing a complete answer`() = runTest {
        val held = fakeHeldSession()
        val fresh = fakeFreshSession()
        val alliance = fakeAlliance()
        val roster = fakeRoster()
        val read = AllianceState.Enlisted(alliance, AllianceRole.FOUNDER, AllianceRosterReading.Read(roster))
        SocketScenario(held).use { scenario ->
            scenario.reply(AllianceStanding.Enlisted(alliance, AllianceRole.FOUNDER))
            scenario.refuse(ApiError.SessionExpired)
            scenario.replyPayload(fresh)
            scenario.reply(AllianceStanding.Enlisted(alliance, AllianceRole.FOUNDER))
            scenario.replyPayload(roster)

            assertEquals(ApiResult.Answered(read), scenario.gateway.alliance(held.accessToken))
            assertEquals(read, scenario.gateway.standing)
            assertEquals(listOf("/v1/alliance", "/v1/alliance/roster", "/v1/auth/refresh", "/v1/alliance", "/v1/alliance/roster"),
                scenario.requests.map { it.path })
            scenario.assertRenewed(held, fresh)
            scenario.requests.takeLast(2).forEach { assertEquals(Protocol.BEARER_PREFIX + fresh.accessToken.value, it.authorization) }
        }
    }

    @Test
    fun `a second expiry ends as unauthenticated without another renewal or cache replacement`() = runTest {
        val held = fakeHeldSession()
        val fresh = fakeFreshSession()
        val alliance = fakeAlliance()
        val roster = fakeRoster()
        SocketScenario(held).use { scenario ->
            scenario.readMembership(held.accessToken, alliance, roster)
            scenario.refuse(ApiError.SessionExpired)
            scenario.replyPayload(fresh)
            scenario.refuse(ApiError.SessionExpired)

            assertEquals(ApiResult.Refused(ApiError.Unauthenticated), scenario.gateway.leaveAlliance(held.accessToken))
            assertEquals(AllianceState.Enlisted(alliance, AllianceRole.FOUNDER, AllianceRosterReading.Read(roster)), scenario.gateway.standing)
            assertEquals(1, scenario.requests.count { it.path == "/v1/auth/refresh" })
            scenario.assertRenewed(held, fresh)
        }
    }

    @Test
    fun `a refused mutation keeps the complete cached membership and session`() = runTest {
        val held = fakeHeldSession()
        val alliance = fakeAlliance()
        val roster = fakeRoster()
        SocketScenario(held).use { scenario ->
            scenario.readMembership(held.accessToken, alliance, roster)
            scenario.refuse(ApiError.AllianceRoleTooLow)

            assertEquals(ApiResult.Refused(ApiError.AllianceRoleTooLow), scenario.gateway.removeMember(held.accessToken, AllianceMemberId("successor-seat")))
            assertEquals(AllianceState.Enlisted(alliance, AllianceRole.FOUNDER, AllianceRosterReading.Read(roster)), scenario.gateway.standing)
            assertEquals(held, scenario.store.read())
            assertEquals(0, scenario.store.writeCount)
            assertEquals(0, scenario.store.clearCount)
        }
    }

    @Test
    fun `a refused roster cannot replace the last complete read with partial standing`() = runTest {
        val held = fakeHeldSession()
        val alliance = fakeAlliance()
        val roster = fakeRoster()
        SocketScenario(held).use { scenario ->
            scenario.readMembership(held.accessToken, alliance, roster)
            scenario.reply(AllianceStanding.Enlisted(alliance, AllianceRole.MEMBER))
            scenario.refuse(ApiError.AllianceRoleTooLow)

            assertEquals(ApiResult.Refused(ApiError.AllianceRoleTooLow), scenario.gateway.alliance(held.accessToken))
            assertEquals(AllianceState.Enlisted(alliance, AllianceRole.FOUNDER, AllianceRosterReading.Read(roster)), scenario.gateway.standing)
        }
    }

    @Test
    fun `an unanswered socket keeps cached membership and the usable session`() = runTest {
        val held = fakeHeldSession()
        val alliance = fakeAlliance()
        val roster = fakeRoster()
        SocketScenario(held).use { scenario ->
            scenario.readMembership(held.accessToken, alliance, roster)
            scenario.server.stop(0)

            assertEquals(ApiResult.Unreachable, scenario.gateway.leaveAlliance(held.accessToken))
            assertEquals(ApiResult.Unreachable, scenario.gateway.alliance(held.accessToken))
            assertEquals(AllianceState.Enlisted(alliance, AllianceRole.FOUNDER, AllianceRosterReading.Read(roster)), scenario.gateway.standing)
            assertEquals(held, scenario.store.read())
            assertEquals(0, scenario.store.clearCount)
        }
    }

    private class SocketScenario(held: SessionResponse) : AutoCloseable {
        val server: HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        val requests = CopyOnWriteArrayList<ReceivedRequest>()
        private val responses = ConcurrentLinkedQueue<SocketResponse>()
        private val client = oltreHttpClient()
        private val api = KtorOltreApi(client, "http://${server.address.hostString}:${server.address.port}")
        val store = FakeSessionStore(held)
        val gateway = AllianceGateway(api, SessionKeeper(api, store, FakeClock()))

        init {
            server.createContext("/") { exchange -> respond(exchange) }
            server.start()
        }

        fun reply(standing: AllianceStanding, status: Int = 200) {
            responses.add(SocketResponse(status, Protocol.json.encodeToString(AllianceResponse(ApiVersion.CURRENT, standing))))
        }

        inline fun <reified T> replyPayload(value: T) {
            responses.add(SocketResponse(200, Protocol.json.encodeToString(value)))
        }

        fun refuse(error: ApiError) {
            responses.add(SocketResponse(401, Protocol.json.encodeToString<ApiError>(error)))
        }

        suspend fun readMembership(access: SessionToken, alliance: Alliance, roster: AllianceRosterResponse) {
            reply(AllianceStanding.Enlisted(alliance, AllianceRole.FOUNDER))
            replyPayload(roster)
            assertEquals(ApiResult.Answered(AllianceState.Enlisted(alliance, AllianceRole.FOUNDER, AllianceRosterReading.Read(roster))),
                gateway.alliance(access))
        }

        inline fun <reified T> posted(path: String, access: SessionToken): T {
            val request = requests.last()
            assertEquals("POST", request.method)
            assertEquals(path, request.path)
            assertEquals(Protocol.BEARER_PREFIX + access.value, request.authorization)
            return Protocol.json.decodeFromString<T>(request.body)
        }

        fun assertDelete(path: String, access: SessionToken) {
            val request = requests.last()
            assertEquals("DELETE", request.method)
            assertEquals(path, request.path)
            assertEquals(Protocol.BEARER_PREFIX + access.value, request.authorization)
            assertEquals("", request.body)
        }

        suspend fun assertRenewed(held: SessionResponse, fresh: SessionResponse) {
            val request = requests.single { it.path == "/v1/auth/refresh" }
            assertEquals("POST", request.method)
            assertNull(request.authorization)
            assertEquals(RefreshRequest(ApiVersion.CURRENT, held.refreshToken), Protocol.json.decodeFromString<RefreshRequest>(request.body))
            assertEquals(fresh, store.read())
            assertEquals(1, store.writeCount)
            assertEquals(0, store.clearCount)
        }

        private fun respond(exchange: HttpExchange) {
            requests.add(ReceivedRequest(
                method = exchange.requestMethod,
                path = exchange.requestURI.path,
                rawQuery = exchange.requestURI.rawQuery,
                authorization = exchange.requestHeaders.getFirst(Protocol.AUTHORIZATION_HEADER),
                body = exchange.requestBody.readBytes().decodeToString(),
            ))
            val response = responses.remove()
            val bytes = response.body.encodeToByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(response.status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        override fun close() {
            client.close()
            server.stop(0)
        }
    }

    private data class SocketResponse(val status: Int, val body: String)

    private data class ReceivedRequest(
        val method: String,
        val path: String,
        val rawQuery: String?,
        val authorization: String?,
        val body: String,
    ) {
        fun query(): List<Pair<String, String>> = checkNotNull(rawQuery).split('&').map { parameter ->
            val pair = parameter.split('=', limit = 2)
            URLDecoder.decode(pair[0], Charsets.UTF_8) to URLDecoder.decode(pair[1], Charsets.UTF_8)
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-26T09:00:00Z")

        fun fakeAlliance(): Alliance = Alliance(
            AllianceId("server-alliance"), AllianceName("Ferro Alto"), AllianceTag("FERRO"), AllianceLevel(2), AllianceSeats(2, 20),
        )

        fun fakeRoster(): AllianceRosterResponse = AllianceRosterResponse(
            ApiVersion.CURRENT,
            listOf(AllianceMember(
                AllianceMemberId("successor-seat"), PlayerProfile(null, null), AllianceRole.MEMBER, ExperienceReading.Known(Experience(0)), NOW,
            )),
            listOf(JoinRequest(JoinRequestId("petition"), PlayerProfile(null, null), ExperienceReading.Unknown, NOW)),
        )

        fun fakeHeldSession(): SessionResponse = SessionResponse(
            ApiVersion.CURRENT, SessionToken("held-access"), NOW + 1.hours, SessionToken("held-refresh"), NOW + 90.days,
        )

        fun fakeFreshSession(): SessionResponse = SessionResponse(
            ApiVersion.CURRENT, SessionToken("fresh-access"), NOW + 2.hours, SessionToken("fresh-refresh"), NOW + 90.days,
        )
    }
}
