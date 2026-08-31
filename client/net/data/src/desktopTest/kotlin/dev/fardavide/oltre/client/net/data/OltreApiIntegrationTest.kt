package dev.fardavide.oltre.client.net.data

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.ClientVerb
import dev.fardavide.oltre.protocol.CommanderName
import dev.fardavide.oltre.protocol.IdToken
import dev.fardavide.oltre.protocol.IdempotencyKey
import dev.fardavide.oltre.protocol.MarkPreset
import dev.fardavide.oltre.protocol.PlayerMark
import dev.fardavide.oltre.protocol.PlayerProfile
import dev.fardavide.oltre.protocol.ProfileResponse
import dev.fardavide.oltre.protocol.Protocol
import dev.fardavide.oltre.protocol.RefreshRequest
import dev.fardavide.oltre.protocol.SessionResponse
import dev.fardavide.oltre.protocol.SessionToken
import dev.fardavide.oltre.protocol.SetProfileRequest
import dev.fardavide.oltre.protocol.SignInNonce
import dev.fardavide.oltre.protocol.SignInRequest
import dev.fardavide.oltre.protocol.SyncRequest
import dev.fardavide.oltre.protocol.SyncResponse
import dev.fardavide.oltre.protocol.VerbEnvelope
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// **The one boundary this module has, crossed for real.** `KtorOltreApiTest` drives the same class
// through a `MockEngine`, which is a fake and therefore a unit test by this repository's taxonomy —
// *"a fake is not a boundary"*. What it cannot prove is the thing that would be most expensive to
// have wrong: that a **real** engine over a **real** socket puts the header where the server reads
// it, and that a connection nobody answers arrives as an `IOException` rather than as something
// this module does not catch.
//
// That second one is worth the file on its own. If the engine threw anything else, every tap made
// with no signal would propagate out of `act` instead of reaching the outbox — the queue would
// never fill, and the failure would only ever show up on a train.
//
// **`com.sun.net.httpserver` and not a dependency**, because it is in the JDK. This is a desktop
// test for the reason `FileSaveFileIntegrationTest` is one: the real thing under it exists only on
// a platform.
class OltreApiIntegrationTest {

    private lateinit var server: HttpServer
    private var lastMethod: String = ""
    private var lastPath: String = ""
    private var lastAuthorization: String? = null
    private var lastBody: String = ""
    private var status: Int = 200
    private var answer: String = ""

    @BeforeTest
    fun start() {
        // Port zero is the operating system picking a free one, so two of these can run at once and
        // nothing has to be reserved.
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange -> answer(exchange) }
        server.start()
    }

    @AfterTest
    fun stop() {
        server.stop(0)
    }

    @Test
    fun `a sync over a real socket carries the session and comes back with the colony`() = runTest {
        // given
        answer = Protocol.json.encodeToString(colony())

        // when
        val result = api().sync(PLAYER, listOf(UPGRADE))

        // then — what went out
        assertEquals("/v1/sync", lastPath)
        assertEquals(Protocol.BEARER_PREFIX + "davide", lastAuthorization)
        assertEquals(
            SyncRequest(apiVersion = ApiVersion.CURRENT, envelopes = listOf(UPGRADE)),
            Protocol.json.decodeFromString<SyncRequest>(lastBody),
        )

        // and — what came back
        assertEquals(ApiResult.Answered(colony()), result)
    }

    @Test
    fun `founding over a real socket reaches the colony route`() = runTest {
        // given
        status = 201
        answer = Protocol.json.encodeToString(colony())

        // when
        val result = api().foundColony(PLAYER)

        // then — `201 Created` is a success and is read as one
        assertEquals("/v1/colony", lastPath)
        assertIs<ApiResult.Answered<SyncResponse>>(result)
    }

    @Test
    fun `an error status over a real socket comes back as the error the server named`() = runTest {
        // given
        status = 401
        answer = Protocol.json.encodeToString<ApiError>(ApiError.Unauthenticated)

        // when / then
        assertEquals(ApiResult.Refused(ApiError.Unauthenticated), api().sync(PLAYER, emptyList()))
    }

    @Test
    fun `a sign-in over a real socket reaches the provider's route and comes back with a session`() = runTest {
        // given
        answer = Protocol.json.encodeToString(session())

        // when
        val result = api().signInWithApple(IdToken("apple.id.token"), SignInNonce("a-nonce"))

        // then — what went out, including the absence that matters: a sign-in carries no session,
        // because it is what produces one, and `/v1/auth/*` is the unauthenticated surface
        assertEquals("/v1/auth/apple", lastPath)
        assertNull(lastAuthorization)
        assertEquals(
            SignInRequest(ApiVersion.CURRENT, IdToken("apple.id.token"), SignInNonce("a-nonce")),
            Protocol.json.decodeFromString<SignInRequest>(lastBody),
        )

        // and — what came back
        assertEquals(ApiResult.Answered(session()), result)
    }

    @Test
    fun `a refresh over a real socket trades the refresh token for a fresh pair`() = runTest {
        // given
        answer = Protocol.json.encodeToString(session())

        // when
        val result = api().refresh(SessionToken("the.refresh.token"))

        // then
        assertEquals("/v1/auth/refresh", lastPath)
        assertEquals(
            RefreshRequest(ApiVersion.CURRENT, SessionToken("the.refresh.token")),
            Protocol.json.decodeFromString<RefreshRequest>(lastBody),
        )
        assertIs<ApiResult.Answered<SessionResponse>>(result)
    }

    // **A success with no body at all, over a real engine.** This is the arm a `MockEngine` is least
    // able to speak for: a `204` legitimately carries no content and no `Content-Length`, and a
    // transport that reached for the body anyway would turn the one irreversible call in the API
    // into an error the player would be shown after their account had already gone.
    @Test
    fun `deleting an account over a real socket succeeds on a bodiless response`() = runTest {
        // given
        status = 204
        answer = ""

        // when
        val result = api().deleteAccount(PLAYER)

        // then
        assertEquals(HttpMethod.Delete.value, lastMethod)
        assertEquals("/v1/account", lastPath)
        assertEquals(Protocol.BEARER_PREFIX + "davide", lastAuthorization)
        assertEquals(ApiResult.Answered(Unit), result)
    }

    // The other side of it: deletion cannot be held, so a refusal has to arrive as one rather than
    // as an optimistic success. An account that is still there after the app said it was gone is the
    // worst outcome this call has.
    @Test
    fun `a deletion the server refuses over a real socket is not mistaken for success`() = runTest {
        // given
        status = 401
        answer = Protocol.json.encodeToString<ApiError>(ApiError.Unauthenticated)

        // when / then
        assertEquals(ApiResult.Refused(ApiError.Unauthenticated), api().deleteAccount(PLAYER))
    }

    // **The only `GET` in the API, over a real engine**, and the empty body is the half worth the
    // socket: every other call shares a helper that sets a content type and a body, and a read that
    // went through it would put a JSON document on a method that has no room for one. A `MockEngine`
    // records whatever the builder produced; this asserts what a server actually received.
    @Test
    fun `a profile read over a real socket carries the session and no body`() = runTest {
        // given
        answer = Protocol.json.encodeToString(profile())

        // when
        val result = api().profile(PLAYER)

        // then — what went out
        assertEquals(HttpMethod.Get.value, lastMethod)
        assertEquals("/v1/profile", lastPath)
        assertEquals(Protocol.BEARER_PREFIX + "davide", lastAuthorization)
        assertEquals("", lastBody)

        // and — what came back is the profile rather than the envelope that carried it
        assertEquals(ApiResult.Answered(profile().profile), result)
    }

    @Test
    fun `a profile write over a real socket posts the whole profile and comes back with it`() = runTest {
        // given
        answer = Protocol.json.encodeToString(profile())

        // when
        val result = api().setProfile(PLAYER, profile().profile)

        // then — what went out
        assertEquals(HttpMethod.Post.value, lastMethod)
        assertEquals("/v1/profile", lastPath)
        assertEquals(
            SetProfileRequest(ApiVersion.CURRENT, profile().profile),
            Protocol.json.decodeFromString<SetProfileRequest>(lastBody),
        )

        // and — what came back
        assertEquals(ApiResult.Answered(profile().profile), result)
    }

    // **The one that matters most, and the one a `MockEngine` cannot answer.** A refused connection
    // has to arrive as `Unreachable` — anything else and a tap made with no signal never reaches the
    // outbox at all.
    @Test
    fun `a connection nobody answers reads as unreachable`() = runTest {
        // given — the address is real and there is nothing behind it any more
        val address = "http://${server.address.hostString}:${server.address.port}"
        server.stop(0)

        // when / then
        assertEquals(
            ApiResult.Unreachable,
            KtorOltreApi(oltreHttpClient(), address).sync(PLAYER, listOf(UPGRADE)),
        )
    }

    // `oltreHttpClient()` and not a client built here: the timeouts and `expectSuccess` are what
    // every shipping client is configured with, and a test that quietly used different ones would be
    // testing a transport this app never sends.
    private fun api(): KtorOltreApi = KtorOltreApi(
        client = oltreHttpClient(),
        baseUrl = "http://${server.address.hostString}:${server.address.port}",
    )

    private fun answer(exchange: HttpExchange) {
        lastMethod = exchange.requestMethod
        lastPath = exchange.requestURI.path
        lastAuthorization = exchange.requestHeaders.getFirst(Protocol.AUTHORIZATION_HEADER)
        lastBody = exchange.requestBody.readBytes().decodeToString()

        // **`-1` and not `0`, which is what makes this a real `204`.** `com.sun.net.httpserver`
        // reads the second argument as "there is a body of this length"; zero means chunked and
        // still writes the framing. Passing `-1` is the only way to get the bodiless response the
        // server actually sends, which is the whole point of testing this one over a socket.
        val bytes = answer.encodeToByteArray()
        if (bytes.isEmpty()) {
            exchange.sendResponseHeaders(status, -1)
            exchange.close()
            return
        }

        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun session(): SessionResponse = SessionResponse(
        apiVersion = ApiVersion.CURRENT,
        accessToken = SessionToken("an.access.token"),
        accessExpiresAt = NOW + 1.hours,
        refreshToken = SessionToken("a.refresh.token"),
        refreshExpiresAt = NOW + 90.days,
    )

    // Both halves chosen, because a profile with two nulls would parse whatever the guards did.
    private fun profile(): ProfileResponse = ProfileResponse(
        apiVersion = ApiVersion.CURRENT,
        profile = PlayerProfile(
            name = CommanderName("Ada"),
            mark = PlayerMark.Preset(MarkPreset.THRESHOLD),
        ),
    )

    private fun colony(): SyncResponse = SyncResponse(
        apiVersion = ApiVersion.CURRENT,
        snapshot = GameSnapshot(lastUpdatedAt = NOW, state = GameState.initial(GalaxySeed(20_260_825))),
        applied = setOf(IdempotencyKey("key")),
        rejected = emptyList(),
    )

    private companion object {

        val NOW: Instant = Instant.parse("2026-08-25T09:00:00Z")

        val PLAYER = SessionToken("davide")

        val UPGRADE = VerbEnvelope(
            verb = ClientVerb.StartUpgrade(BuildingType.METAL_MINE),
            clientInstant = NOW,
            idempotencyKey = IdempotencyKey("key"),
        )
    }
}
