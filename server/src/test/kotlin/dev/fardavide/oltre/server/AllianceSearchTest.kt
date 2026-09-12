package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceTag
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AllianceSearchTest {

    @Test
    fun `an oversized structurally valid cursor is malformed before accessing a store`() = runTest {
        val colonies = InMemoryColonyRepository()
        val players = InMemoryPlayerRepository(colonies, InMemoryAllianceRepository(colonies))
        val bytes = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(bytes).use { output ->
            output.writeInt(1)
            output.writeUTF("a")
            output.writeLong(0)
            output.writeUTF("a".repeat(7_000))
            output.writeUTF("id")
        }
        val cursor = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray())

        val answer = assertIs<Answer.Failed>(searchAlliances(
            UnreachableAllianceRepository(), HeaderAuthenticator(players), Credentials(null, "visitor"), "a", cursor,
        ))

        assertEquals(HttpStatusCode.BadRequest, answer.status)
        assertIs<ApiError.Malformed>(answer.error)
    }

    @Test
    fun `cursor positions must be canonical prefixes without NUL before accessing a store`() = runTest {
        val colonies = InMemoryColonyRepository()
        val players = InMemoryPlayerRepository(colonies, InMemoryAllianceRepository(colonies))
        for ((name, id) in listOf("a\u0000" to "id", "a" to "id\u0000", "b" to "id", "aA" to "id")) {
            val bytes = java.io.ByteArrayOutputStream()
            java.io.DataOutputStream(bytes).use { output ->
                output.writeInt(1)
                output.writeUTF("a")
                output.writeLong(0)
                output.writeUTF(name)
                output.writeUTF(id)
            }
            val cursor = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray())

            val answer = assertIs<Answer.Failed>(searchAlliances(
                UnreachableAllianceRepository(), HeaderAuthenticator(players), Credentials(null, "visitor"), "a", cursor,
            ))

            assertEquals(HttpStatusCode.BadRequest, answer.status)
            assertIs<ApiError.Malformed>(answer.error)
        }
    }

    @Test
    fun `a NUL query is malformed before accessing a store`() = runTest {
        val colonies = InMemoryColonyRepository()
        val players = InMemoryPlayerRepository(colonies, InMemoryAllianceRepository(colonies))

        val answer = assertIs<Answer.Failed>(searchAlliances(
            UnreachableAllianceRepository(), HeaderAuthenticator(players), Credentials(null, "visitor"), "fleet\u0000", null,
        ))

        assertEquals(HttpStatusCode.BadRequest, answer.status)
        assertIs<ApiError.Malformed>(answer.error)
    }

    @Test
    fun `a failed search names the store failure rather than answering an empty page`() = runTest {
        val colonies = InMemoryColonyRepository()
        val players = InMemoryPlayerRepository(colonies, InMemoryAllianceRepository(colonies))

        val answer = assertIs<Answer.Failed>(searchAlliances(
            UnreachableAllianceRepository(), HeaderAuthenticator(players), Credentials(null, "visitor"), "fleet", null,
        ))

        assertEquals(HttpStatusCode.InternalServerError, answer.status)
        assertEquals(ApiError.Internal("no route to host"), answer.error)
    }

    @Test
    fun `a minted cursor accepts a display name expanded past its original length`() = runTest {
        val colonies = InMemoryColonyRepository()
        val alliances = InMemoryAllianceRepository(colonies)
        val players = InMemoryPlayerRepository(colonies, alliances)
        val authentication = HeaderAuthenticator(players)
        repeat(21) { number ->
            val player = assertIs<Caller.Known>(authentication.identify(Credentials(null, "founder-$number"))).player
            alliances.found(player, AllianceName("\uFDFA".repeat(30) + number.toString().padStart(2, '0')), AllianceTag("F$number"), TEST_NOW)
        }

        val first = assertIs<Answer.Alliances>(searchAlliances(alliances, authentication, Credentials(null, "visitor"), "\uFDFA".repeat(30), null)).response
        val next = kotlin.test.assertNotNull(first.nextCursor)
        val second = assertIs<Answer.Alliances>(searchAlliances(alliances, authentication, Credentials(null, "visitor"), "\uFDFA".repeat(30), next.value)).response

        assertEquals(1, second.results.size)
        assertEquals(null, second.nextCursor)
    }

    @Test
    fun `search accepts prefixes outside the display name length and rejects Unicode whitespace alone`() = runTest {
        val colonies = InMemoryColonyRepository()
        val alliances = InMemoryAllianceRepository(colonies)
        val players = InMemoryPlayerRepository(colonies, alliances)
        val authentication = HeaderAuthenticator(players)

        val answer = assertIs<Answer.Alliances>(searchAlliances(alliances, authentication, Credentials(null, "visitor"), "a".repeat(100), null))
        assertEquals(emptyList(), answer.response.results)
        for (query in listOf("", "\t \n", "\u0085", "\u00a0\u3000")) {
            val failure = assertIs<Answer.Failed>(searchAlliances(alliances, authentication, Credentials(null, "visitor"), query, null))
            assertEquals(HttpStatusCode.BadRequest, failure.status)
        }
    }

    @Test
    fun `invalid cursors are malformed requests rather than failures or page one`() = runTest {
        val colonies = InMemoryColonyRepository()
        val alliances = InMemoryAllianceRepository(colonies)
        val players = InMemoryPlayerRepository(colonies, alliances)

        for (cursor in listOf("", "not a cursor", "AA", "AAAAAgABYQAAAAAAAAAAAAFhAAFh", "AAAAAQABYgAAAAAAAAAAAAFhAAFh", "AAAAAQABYf__________AAFhAAFh", "AAAAAQABYQAAAAAAAAAAAAFhAAFhAA", "AAAAAQABYQAAAAAAAAAAAAFhAAJpZA==")) {
            val failure = assertIs<Answer.Failed>(searchAlliances(alliances, HeaderAuthenticator(players), Credentials(null, "visitor"), "a", cursor))
            assertEquals(HttpStatusCode.BadRequest, failure.status, cursor)
            assertIs<ApiError.Malformed>(failure.error)
        }
    }

    @Test
    fun `a capped keyset walk returns names in order exactly once`() = runTest {
        val colonies = InMemoryColonyRepository()
        val alliances = InMemoryAllianceRepository(colonies)
        val players = InMemoryPlayerRepository(colonies, alliances)
        val authentication = HeaderAuthenticator(players)
        val made = (25 downTo 1).map { number ->
            val player = assertIs<Caller.Known>(authentication.identify(Credentials(null, "founder-$number"))).player
            assertIs<Founded.Made>(alliances.found(player, AllianceName("Fleet ${number.toString().padStart(2, '0')}"), AllianceTag("F$number"), TEST_NOW)).alliance.alliance
        }.sortedBy { it.name.value }

        val first = assertIs<Answer.Alliances>(searchAlliances(alliances, authentication, Credentials(null, "visitor"), "fleet", null)).response
        val next = kotlin.test.assertNotNull(first.nextCursor)
        val second = assertIs<Answer.Alliances>(searchAlliances(alliances, authentication, Credentials(null, "visitor"), "fleet", next.value)).response

        assertEquals(20, first.results.size)
        assertEquals(made, first.results + second.results)
        assertEquals(null, second.nextCursor)
    }

    @Test
    fun `a prefix finds the same spelling that creation refuses as a duplicate`() = runTest {
        val colonies = InMemoryColonyRepository()
        val alliances = InMemoryAllianceRepository(colonies)
        val players = InMemoryPlayerRepository(colonies, alliances)
        val authentication = HeaderAuthenticator(players)
        val founder = assertIs<Caller.Known>(authentication.identify(Credentials(null, "founder"))).player
        val rival = assertIs<Caller.Known>(authentication.identify(Credentials(null, "rival"))).player
        val made = assertIs<Founded.Made>(alliances.found(founder, AllianceName("Ｓｔｒａße  Fleet"), AllianceTag("VNG"), TEST_NOW))
        assertEquals(Founded.Refused(ApiError.AllianceNameTaken), alliances.found(rival, AllianceName("STRASSE\tFLEET"), AllianceTag("RIV"), TEST_NOW))

        val answer = assertIs<Answer.Alliances>(searchAlliances(alliances, authentication, Credentials(null, "visitor"), "  STRASSE   f", null))

        assertEquals(listOf(made.alliance.alliance), answer.response.results)
    }

    @Test
    fun `nothing matched is an empty successful page echoing the normalised query`() = runTest {
        val colonies = InMemoryColonyRepository()
        val alliances = InMemoryAllianceRepository(colonies)
        val players = InMemoryPlayerRepository(colonies, alliances)

        val answer = assertIs<Answer.Alliances>(searchAlliances(
            alliances, HeaderAuthenticator(players), Credentials(null, "visitor"), "  VANGUARD   FLEET  ", null,
        ))

        assertEquals(HttpStatusCode.OK, answer.status)
        assertEquals("vanguard fleet", answer.response.query)
        assertEquals(emptyList(), answer.response.results)
        assertEquals(null, answer.response.nextCursor)
    }

    @Test
    fun `search authenticates before validating a query`() = runTest {
        val colonies = InMemoryColonyRepository()
        val alliances = InMemoryAllianceRepository(colonies)
        val players = InMemoryPlayerRepository(colonies, alliances)

        val answer = searchAlliances(alliances, HeaderAuthenticator(players), Credentials(null, null), null, null)

        val failure = assertIs<Answer.Failed>(answer)
        assertEquals(HttpStatusCode.Unauthorized, failure.status)
        assertEquals(ApiError.Unauthenticated, failure.error)
    }

    @Test
    fun `search requires a query before accessing the alliance store`() = runTest {
        val colonies = InMemoryColonyRepository()
        val alliances = InMemoryAllianceRepository(colonies)
        val players = InMemoryPlayerRepository(colonies, alliances)

        val answer = searchAlliances(alliances, HeaderAuthenticator(players), Credentials(null, "visitor"), null, null)

        val failure = assertIs<Answer.Failed>(answer)
        assertEquals(HttpStatusCode.BadRequest, failure.status)
        assertIs<ApiError.Malformed>(failure.error)
    }
}
