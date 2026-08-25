package dev.fardavide.oltre.client.net.data

import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.ClientVerb
import dev.fardavide.oltre.protocol.IdempotencyKey
import dev.fardavide.oltre.protocol.Protocol
import dev.fardavide.oltre.protocol.SyncRequest
import dev.fardavide.oltre.protocol.SyncResponse
import dev.fardavide.oltre.protocol.VerbEnvelope
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.io.readString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

private val NOW: Instant = Instant.parse("2026-08-25T09:00:00Z")

private val PLAYER = PlayerHandle("davide")

private const val BASE_URL = "https://oltre.example"

private val UPGRADE = VerbEnvelope(
    verb = ClientVerb.StartUpgrade(BuildingType.METAL_MINE),
    clientInstant = NOW,
    idempotencyKey = IdempotencyKey("key"),
)

private fun colonyResponse(): SyncResponse = SyncResponse(
    apiVersion = ApiVersion.CURRENT,
    snapshot = fakeColony(NOW),
    applied = setOf(IdempotencyKey("key")),
    rejected = emptyList(),
)

// **`oltreDefaults()` and not a bare client**, deliberately: `expectSuccess` and the timeouts are
// the configuration every real client is built with, and a test that quietly used different ones
// would be testing a transport this app never ships.
private fun api(records: MutableList<HttpRequestData> = mutableListOf(), handler: MockHandler): KtorOltreApi =
    KtorOltreApi(
        client = HttpClient(
            MockEngine { request ->
                records += request
                handler(request)
            },
        ) { oltreDefaults() },
        baseUrl = BASE_URL,
    )

private typealias MockHandler = suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData

private val JSON = headersOf(HttpHeaders.ContentType, "application/json")

private suspend fun HttpRequestData.bodyText(): String = body.toByteArray().decodeToString()

class KtorOltreApiTest {

    @Test
    fun `a sync posts what is queued to the sync route`() = runTest {
        // given
        val records = mutableListOf<HttpRequestData>()

        // when
        api(records) { respond(Protocol.json.encodeToString(colonyResponse()), HttpStatusCode.OK, JSON) }
            .sync(PLAYER, listOf(UPGRADE))

        // then
        val request = records.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/v1/sync", request.url.encodedPath)
        assertEquals(
            SyncRequest(apiVersion = ApiVersion.CURRENT, envelopes = listOf(UPGRADE)),
            Protocol.json.decodeFromString<SyncRequest>(request.bodyText()),
        )
    }

    // The header both ends read, and it is `Protocol.PLAYER_HEADER` at both ends rather than a
    // string spelled out twice — a client that disagreed by one character would read as a player
    // who never signed in, and nothing would say so.
    @Test
    fun `a request says who is asking`() = runTest {
        // given
        val records = mutableListOf<HttpRequestData>()

        // when
        api(records) { respond(Protocol.json.encodeToString(colonyResponse()), HttpStatusCode.OK, JSON) }
            .sync(PLAYER, emptyList())

        // then
        assertEquals("davide", records.single().headers[Protocol.PLAYER_HEADER])
    }

    @Test
    fun `founding posts to the colony route with nothing queued`() = runTest {
        // given
        val records = mutableListOf<HttpRequestData>()

        // when
        api(records) { respond(Protocol.json.encodeToString(colonyResponse()), HttpStatusCode.Created, JSON) }
            .foundColony(PLAYER)

        // then
        val request = records.single()
        assertEquals("/v1/colony", request.url.encodedPath)
        assertEquals(
            emptyList(),
            Protocol.json.decodeFromString<SyncRequest>(request.bodyText()).envelopes,
        )
    }

    @Test
    fun `a colony that comes back is handed up as data`() = runTest {
        // given / when
        val result = api { respond(Protocol.json.encodeToString(colonyResponse()), HttpStatusCode.OK, JSON) }
            .sync(PLAYER, listOf(UPGRADE))

        // then
        assertEquals(ApiResult.Answered(colonyResponse()), result)
    }

    // The three sentences `ApiError` exists to keep apart have to survive the wire, or the client
    // has to say the vaguest of them to everybody.
    @Test
    fun `an error the server named comes back whole`() = runTest {
        // given / when
        val result = api {
            respond(
                Protocol.json.encodeToString<ApiError>(ApiError.UnsupportedApiVersion(ApiVersion(2), ApiVersion(3))),
                HttpStatusCode.UpgradeRequired,
                JSON,
            )
        }.sync(PLAYER, emptyList())

        // then
        assertEquals(
            ApiResult.Refused(ApiError.UnsupportedApiVersion(ApiVersion(2), ApiVersion(3))),
            result,
        )
    }

    @Test
    fun `a server that names its own failure is believed rather than guessed at`() = runTest {
        // given / when — a real `500` from the app, carrying the taxonomy
        val result = api {
            respond(
                Protocol.json.encodeToString<ApiError>(ApiError.Internal("the store went away")),
                HttpStatusCode.InternalServerError,
                JSON,
            )
        }.sync(PLAYER, emptyList())

        // then
        assertEquals(ApiResult.Refused(ApiError.Internal("the store went away")), result)
    }

    @Test
    fun `a response that is not json at all is malformed rather than half rendered`() = runTest {
        // given / when
        val result = api { respond("{ this is not a colony", HttpStatusCode.OK, JSON) }
            .sync(PLAYER, emptyList())

        // then
        assertIs<ApiResult.Refused>(result)
        assertIs<ApiError.Malformed>(result.error)
    }

    // `Sync.kt`'s two `init` guards run on **decode**, which is the point of them: a response that
    // cannot be read coherently is one this module turns into `Malformed`, the way `GameSave.decode`
    // turns a broken model invariant into a `Failure`, rather than one the client renders half of.
    @Test
    fun `a response that breaks its own invariant is malformed`() = runTest {
        // given — a well-formed body carrying a key that cannot exist
        val blanked = Protocol.json.encodeToString(colonyResponse()).replace("\"key\"", "\"\"")

        // when
        val result = api { respond(blanked, HttpStatusCode.OK, JSON) }.sync(PLAYER, emptyList())

        // then
        assertIs<ApiResult.Refused>(result)
        assertIs<ApiError.Malformed>(result.error)
    }

    @Test
    fun `a rejection this build cannot read is malformed rather than dropped`() = runTest {
        // given — a refusal from a build that knows one this one does not
        val unknown = Protocol.json.encodeToString(colonyResponse())
            .replace("\"rejected\":[]", """"rejected":[{"envelope":null}]""")

        // when
        val result = api { respond(unknown, HttpStatusCode.OK, JSON) }.sync(PLAYER, emptyList())

        // then
        assertIs<ApiResult.Refused>(result)
    }

    // **The failure that would have been expensive on this host.** `#106` §6 puts the server on
    // Cloud Run, which scales to zero — so the first request after an idle spell can be answered by
    // the load balancer rather than by the app, with HTML. Read as an error that would surface to
    // the player *and* stop the verb being queued.
    @Test
    fun `a gateway answering for a server that is not up yet reads as unreachable`() = runTest {
        // given / when
        val result = api {
            respond(
                "<html><head><title>502 Bad Gateway</title></head></html>",
                HttpStatusCode.BadGateway,
                headersOf(HttpHeaders.ContentType, "text/html"),
            )
        }.sync(PLAYER, emptyList())

        // then
        assertEquals(ApiResult.Unreachable, result)
    }

    // The other way round: something did read the request and formed an opinion about it, so a body
    // this build cannot parse is a disagreement about the contract rather than an absent server.
    @Test
    fun `a refusal this build cannot parse is malformed and not mistaken for silence`() = runTest {
        // given / when
        val result = api { respond("not an error either", HttpStatusCode.BadRequest, JSON) }
            .sync(PLAYER, emptyList())

        // then
        assertIs<ApiResult.Refused>(result)
        assertIs<ApiError.Malformed>(result.error)
    }

    // The diagnostic carries the status and something of the body, and it is never player copy —
    // every word the game says is a `TextRes` built through `Strings`, and a transport cannot build
    // one.
    @Test
    fun `an unreadable refusal says what it was reading`() = runTest {
        // given / when
        val result = api { respond("nothing useful", HttpStatusCode.BadRequest, JSON) }
            .sync(PLAYER, emptyList())

        // then
        val error = assertIs<ApiError.Malformed>(assertIs<ApiResult.Refused>(result).error)
        assertTrue(error.detail.contains("400"), "no status in ${error.detail}")
        assertTrue(error.detail.contains("nothing useful"), "no body in ${error.detail}")
    }

    // Every way a request can fail to happen is one answer, because there is one thing to do about
    // them: the queue is intact and this is worth trying again.
    @Test
    fun `a connection that never opens reads as unreachable`() = runTest {
        // given / when
        val result = api { throw IOException("no route to host") }.sync(PLAYER, listOf(UPGRADE))

        // then
        assertEquals(ApiResult.Unreachable, result)
    }
}

private suspend fun io.ktor.http.content.OutgoingContent.toByteArray(): ByteArray = when (this) {
    is io.ktor.http.content.OutgoingContent.ByteArrayContent -> bytes()
    is io.ktor.http.content.OutgoingContent.ReadChannelContent ->
        readFrom().readRemaining().readString().encodeToByteArray()

    else -> error("a request body this test cannot read: ${this::class.simpleName}")
}
