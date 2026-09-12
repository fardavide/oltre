package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceStanding
import dev.fardavide.oltre.protocol.AllianceTag
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.CreateAllianceRequest
import dev.fardavide.oltre.protocol.Protocol
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AllianceEndpointsTest {

    private val colonies = InMemoryColonyRepository()
    private val alliances = InMemoryAllianceRepository(colonies)
    private val players = InMemoryPlayerRepository(colonies, ids = sequentialPlayerIds())
    private val authenticator = HeaderAuthenticator(players)
    private val clock = MovableClock(TEST_NOW)

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
}
