package dev.fardavide.oltre.client.net.data

import dev.fardavide.oltre.core.BuildingType
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private val NOW: Instant = Instant.parse("2026-08-25T09:00:00Z")

private val PLAYER = SessionToken("an.access.token")

private const val BASE_URL = "https://oltre.example"

private val UPGRADE = VerbEnvelope(
    verb = ClientVerb.StartUpgrade(BuildingType.METAL_MINE),
    clientInstant = NOW,
    idempotencyKey = IdempotencyKey("key"),
)

private fun session(): SessionResponse = SessionResponse(
    apiVersion = ApiVersion.CURRENT,
    accessToken = SessionToken("an.access.token"),
    accessExpiresAt = NOW + 1.hours,
    refreshToken = SessionToken("a.refresh.token"),
    refreshExpiresAt = NOW + 90.days,
)

// A name and a mark that were both chosen, because the interesting decoding is on the halves that
// are *not* null — a `PlayerProfile(null, null)` would parse whatever the guards did.
private fun chosen(): PlayerProfile = PlayerProfile(
    name = CommanderName("Ada"),
    mark = PlayerMark.Preset(MarkPreset.THRESHOLD),
)

private fun profileResponse(): ProfileResponse =
    ProfileResponse(apiVersion = ApiVersion.CURRENT, profile = chosen())

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

    // **The cutover, in one assertion.** `#112` sent `X-Oltre-Player` and a deployed server has
    // ignored it outright since `#110`; what a request carries now is a session this server signed
    // itself. Both strings are `Protocol`'s rather than spelled out here, for the reason the header
    // moved into that module in the first place — a client that disagreed by one character would
    // read exactly like a player who never signed in, and nothing would say so.
    @Test
    fun `a request says who is asking with a session the server signed`() = runTest {
        // given
        val records = mutableListOf<HttpRequestData>()

        // when
        api(records) { respond(Protocol.json.encodeToString(colonyResponse()), HttpStatusCode.OK, JSON) }
            .sync(PLAYER, emptyList())

        // then
        assertEquals(
            Protocol.BEARER_PREFIX + "an.access.token",
            records.single().headers[Protocol.AUTHORIZATION_HEADER],
        )
    }

    // The other half of the cutover, and it is worth its own assertion rather than being implied by
    // the one above: the placeholder is *gone*, not merely unread. A client still sending it would
    // work against today's server and would be claiming an identity it has not proved the day
    // anything reads the header again.
    @Test
    fun `a request no longer claims a player by name`() = runTest {
        // given
        val records = mutableListOf<HttpRequestData>()

        // when
        api(records) { respond(Protocol.json.encodeToString(colonyResponse()), HttpStatusCode.OK, JSON) }
            .sync(PLAYER, emptyList())

        // then
        assertNull(records.single().headers[Protocol.PLAYER_HEADER])
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

    // **The provider is the path and not a field**, which is `Auth.kt`'s call and the reason there
    // are two methods here rather than one taking an enum. The two tokens are verified against
    // different issuers, different audiences and different key sets, so a single route would have to
    // branch on its own body to decide who to believe.
    @Test
    fun `signing in with apple posts the token and the nonce to apple's route`() = runTest {
        // given
        val records = mutableListOf<HttpRequestData>()

        // when
        api(records) { respond(Protocol.json.encodeToString(session()), HttpStatusCode.OK, JSON) }
            .signInWithApple(IdToken("apple.id.token"), SignInNonce("a-nonce"))

        // then
        val request = records.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/v1/auth/apple", request.url.encodedPath)
        assertEquals(
            SignInRequest(ApiVersion.CURRENT, IdToken("apple.id.token"), SignInNonce("a-nonce")),
            Protocol.json.decodeFromString<SignInRequest>(request.bodyText()),
        )
    }

    @Test
    fun `signing in with google posts to google's route`() = runTest {
        // given
        val records = mutableListOf<HttpRequestData>()

        // when
        api(records) { respond(Protocol.json.encodeToString(session()), HttpStatusCode.OK, JSON) }
            .signInWithGoogle(IdToken("google.id.token"), SignInNonce("a-nonce"))

        // then
        assertEquals("/v1/auth/google", records.single().url.encodedPath)
    }

    // **A sign-in is the one call that carries no session**, because it is what produces one. Sending
    // an expired token here would be harmless today and is exactly the sort of thing that stops being
    // harmless: `/v1/auth/*` is the unauthenticated surface, and a credential on it is a credential
    // in a log that nobody is treating as one.
    @Test
    fun `a sign-in carries no session of its own`() = runTest {
        // given
        val records = mutableListOf<HttpRequestData>()

        // when
        api(records) { respond(Protocol.json.encodeToString(session()), HttpStatusCode.OK, JSON) }
            .signInWithApple(IdToken("apple.id.token"), SignInNonce("a-nonce"))

        // then
        assertNull(records.single().headers[Protocol.AUTHORIZATION_HEADER])
    }

    @Test
    fun `a session that comes back is handed up whole`() = runTest {
        // given / when
        val result = api { respond(Protocol.json.encodeToString(session()), HttpStatusCode.OK, JSON) }
            .signInWithApple(IdToken("apple.id.token"), SignInNonce("a-nonce"))

        // then — both tokens and both expiries, because the client refreshes on the stated instant
        // rather than by decoding a credential it only ever carries
        assertEquals(ApiResult.Answered(session()), result)
    }

    // A provider token this server will not accept. One sentence for every refusal is the server's
    // call — telling a client *which* check failed tells anybody holding a stolen token which check
    // to work on — and what matters here is only that the sentence survives the wire.
    @Test
    fun `a sign-in the server refused comes back as unauthenticated`() = runTest {
        // given / when
        val result = api {
            respond(
                Protocol.json.encodeToString<ApiError>(ApiError.Unauthenticated),
                HttpStatusCode.Unauthorized,
                JSON,
            )
        }.signInWithApple(IdToken("stolen.id.token"), SignInNonce("a-nonce"))

        // then
        assertEquals(ApiResult.Refused(ApiError.Unauthenticated), result)
    }

    // **The one error that says *when* rather than *what*.** `/v1/auth/*` is publicly reachable and
    // does a signature check per request, so it is the one surface where a loop costs real money —
    // and the gate needs the number, because "ask again in 41s" and "try again later" are different
    // sentences.
    @Test
    fun `a rate limit comes back carrying the wait the gate has to print`() = runTest {
        // given / when
        val result = api {
            respond(
                Protocol.json.encodeToString<ApiError>(ApiError.TooManyRequests(41)),
                HttpStatusCode.TooManyRequests,
                JSON,
            )
        }.signInWithApple(IdToken("apple.id.token"), SignInNonce("a-nonce"))

        // then
        assertEquals(ApiResult.Refused(ApiError.TooManyRequests(41)), result)
    }

    @Test
    fun `a refresh trades the refresh token for a fresh pair`() = runTest {
        // given
        val records = mutableListOf<HttpRequestData>()

        // when
        api(records) { respond(Protocol.json.encodeToString(session()), HttpStatusCode.OK, JSON) }
            .refresh(SessionToken("the.refresh.token"))

        // then
        val request = records.single()
        assertEquals("/v1/auth/refresh", request.url.encodedPath)
        assertEquals(
            RefreshRequest(ApiVersion.CURRENT, SessionToken("the.refresh.token")),
            Protocol.json.decodeFromString<RefreshRequest>(request.bodyText()),
        )
    }

    // **A refresh token that has itself run out is `Unauthenticated` and not `SessionExpired`.** That
    // reads as inconsistent and is the point: `SessionExpired` means *"ask again in a moment"*, and a
    // client told that about the credential it asks *with* would loop forever.
    @Test
    fun `a refresh token ninety days old ends at the gate rather than in a loop`() = runTest {
        // given / when
        val result = api {
            respond(
                Protocol.json.encodeToString<ApiError>(ApiError.Unauthenticated),
                HttpStatusCode.Unauthorized,
                JSON,
            )
        }.refresh(SessionToken("the.stale.refresh.token"))

        // then
        assertEquals(ApiResult.Refused(ApiError.Unauthenticated), result)
    }

    // App Review guideline 5.1.1(v). `DELETE` with the session and no body at all — there is nothing
    // in either direction to disagree about, which is why this route alone does no version
    // negotiation.
    @Test
    fun `deleting an account sends the session and nothing else`() = runTest {
        // given
        val records = mutableListOf<HttpRequestData>()

        // when
        api(records) { respond("", HttpStatusCode.NoContent) }.deleteAccount(PLAYER)

        // then
        val request = records.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("/v1/account", request.url.encodedPath)
        assertEquals(
            Protocol.BEARER_PREFIX + "an.access.token",
            request.headers[Protocol.AUTHORIZATION_HEADER],
        )
    }

    // **`204` and an empty body is the success**, and it has to be read as one rather than as a body
    // that failed to parse. Deleting twice answers the same way — a client that lost the response to
    // the first attempt will send a second, and telling it its account does not exist is telling it
    // the thing it asked for.
    @Test
    fun `an account that went away answers with nothing and that is the success`() = runTest {
        // given / when
        val result = api { respond("", HttpStatusCode.NoContent) }.deleteAccount(PLAYER)

        // then
        assertEquals(ApiResult.Answered(Unit), result)
    }

    @Test
    fun `deleting with a session the server will not accept is refused rather than assumed done`() = runTest {
        // given / when
        val result = api {
            respond(
                Protocol.json.encodeToString<ApiError>(ApiError.Unauthenticated),
                HttpStatusCode.Unauthorized,
                JSON,
            )
        }.deleteAccount(PLAYER)

        // then
        assertEquals(ApiResult.Refused(ApiError.Unauthenticated), result)
    }

    // **Deletion cannot be held**, which is the design's own call and the reason this arm matters:
    // the account is removed on the server and the server has to answer, so a request that never
    // arrived must read as nothing having happened rather than as a deletion in flight.
    @Test
    fun `a deletion that never reached anybody reads as unreachable`() = runTest {
        // given / when
        val result = api { throw IOException("no route to host") }.deleteAccount(PLAYER)

        // then
        assertEquals(ApiResult.Unreachable, result)
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

    // **The one `GET` in the API**, and the assertion about the body is the load-bearing half. Every
    // other call has something to say, so they all share a helper that sets a content type and a
    // body unconditionally; a read that went through it would send a JSON document nobody wrote on a
    // method that has no room for one.
    @Test
    fun `reading a profile gets the profile route with nothing to say`() = runTest {
        // given
        val records = mutableListOf<HttpRequestData>()

        // when
        api(records) { respond(Protocol.json.encodeToString(profileResponse()), HttpStatusCode.OK, JSON) }
            .profile(PLAYER)

        // then
        val request = records.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/v1/profile", request.url.encodedPath)
        assertEquals(0L, request.body.contentLength)
    }

    // **The whole profile goes up and not the half that moved**, which is `SetProfileRequest`'s call
    // rather than this transport's: with a merge, `null` would have to mean *leave it alone* and
    // *clear it* at once, and clearing is the only way out of a name a player regrets.
    @Test
    fun `writing a profile posts the whole of it to the profile route`() = runTest {
        // given
        val records = mutableListOf<HttpRequestData>()

        // when
        api(records) { respond(Protocol.json.encodeToString(profileResponse()), HttpStatusCode.OK, JSON) }
            .setProfile(PLAYER, chosen())

        // then — the version is this build's and not the caller's, exactly as every other route
        // states it
        val request = records.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/v1/profile", request.url.encodedPath)
        assertEquals(
            SetProfileRequest(ApiVersion.CURRENT, chosen()),
            Protocol.json.decodeFromString<SetProfileRequest>(request.bodyText()),
        )
    }

    // A profile belongs to an account rather than to a colony, so the only thing that says whose it
    // is is the session — and the read is the route most likely to get this wrong, because it is the
    // one that does not go through the shared body helper.
    @Test
    fun `reading a profile says who is asking`() = runTest {
        // given
        val records = mutableListOf<HttpRequestData>()

        // when
        api(records) { respond(Protocol.json.encodeToString(profileResponse()), HttpStatusCode.OK, JSON) }
            .profile(PLAYER)

        // then
        assertEquals(
            Protocol.BEARER_PREFIX + "an.access.token",
            records.single().headers[Protocol.AUTHORIZATION_HEADER],
        )
    }

    @Test
    fun `writing a profile says who is asking`() = runTest {
        // given
        val records = mutableListOf<HttpRequestData>()

        // when
        api(records) { respond(Protocol.json.encodeToString(profileResponse()), HttpStatusCode.OK, JSON) }
            .setProfile(PLAYER, chosen())

        // then
        assertEquals(
            Protocol.BEARER_PREFIX + "an.access.token",
            records.single().headers[Protocol.AUTHORIZATION_HEADER],
        )
    }

    // **The envelope is dropped and the profile is handed up**, which is where this pair differs
    // from `sync`: a caller reads `applied` and `rejected` off a `SyncResponse` and acts on them,
    // while a `ProfileResponse` carries the version and nothing else a client can do anything with.
    @Test
    fun `a profile that comes back is handed up as what the player chose`() = runTest {
        // given / when
        val result = api { respond(Protocol.json.encodeToString(profileResponse()), HttpStatusCode.OK, JSON) }
            .profile(PLAYER)

        // then
        assertEquals(ApiResult.Answered(chosen()), result)
    }

    // **What a write answers is a read and not an echo** — the server writes the row and then reads
    // it back, so the answer is what it now holds rather than the client's own claim handed to it.
    // A transport that dropped it would leave the strip drawing the draft.
    @Test
    fun `a profile the server stored comes back as what it now holds`() = runTest {
        // given — the server answers with a name that is not the one sent
        val held = PlayerProfile(name = CommanderName("Ada Lovelace"), mark = PlayerMark.Preset(MarkPreset.WAKE))

        // when
        val result = api {
            respond(
                Protocol.json.encodeToString(ProfileResponse(ApiVersion.CURRENT, held)),
                HttpStatusCode.OK,
                JSON,
            )
        }.setProfile(PLAYER, chosen())

        // then
        assertEquals(ApiResult.Answered(held), result)
    }

    @Test
    fun `a profile write the server will not accept is refused rather than assumed saved`() = runTest {
        // given / when
        val result = api {
            respond(
                Protocol.json.encodeToString<ApiError>(ApiError.Unauthenticated),
                HttpStatusCode.Unauthorized,
                JSON,
            )
        }.setProfile(PLAYER, chosen())

        // then
        assertEquals(ApiResult.Refused(ApiError.Unauthenticated), result)
    }

    @Test
    fun `a profile refusal this build cannot parse is malformed`() = runTest {
        // given / when
        val result = api { respond("not an error either", HttpStatusCode.BadRequest, JSON) }
            .setProfile(PLAYER, chosen())

        // then
        assertIs<ApiResult.Refused>(result)
        assertIs<ApiError.Malformed>(result.error)
    }

    // Cloud Run scales to zero here too: the first read after an idle spell can be answered by the
    // load balancer rather than by the app. A strip that drew `Dead Reckoning` because a gateway
    // spoke would be showing a player somebody else's name.
    @Test
    fun `a gateway answering for a profile route that is not up yet reads as unreachable`() = runTest {
        // given / when
        val result = api {
            respond(
                "<html><head><title>502 Bad Gateway</title></head></html>",
                HttpStatusCode.BadGateway,
                headersOf(HttpHeaders.ContentType, "text/html"),
            )
        }.profile(PLAYER)

        // then
        assertEquals(ApiResult.Unreachable, result)
    }

    // **A rename made with no signal is not queued and does not pretend**, which is the design's own
    // call: the outbox stores verbs the server can validate by replaying them, and a profile write
    // has nothing to replay against. So this arm is the whole of what offline means here.
    @Test
    fun `a profile write that never reached anybody reads as unreachable`() = runTest {
        // given / when
        val result = api { throw IOException("no route to host") }.setProfile(PLAYER, chosen())

        // then
        assertEquals(ApiResult.Unreachable, result)
    }

    // **`CommanderName`'s own guard runs on decode**, and this is the arm `decode`'s second catch
    // was written for: a modified client can write a name no field could produce, and a strip has
    // nowhere to put twenty-five characters. Malformed rather than a crash in a coroutine nobody is
    // watching.
    @Test
    fun `a name longer than the field can produce is malformed rather than drawn`() = runTest {
        // given — a well-formed body carrying a name that cannot exist
        val overlong = Protocol.json.encodeToString(profileResponse())
            .replace("\"Ada\"", "\"" + "a".repeat(CommanderName.MAX_LENGTH + 1) + "\"")

        // when
        val result = api { respond(overlong, HttpStatusCode.OK, JSON) }.profile(PLAYER)

        // then
        assertIs<ApiResult.Refused>(result)
        assertIs<ApiError.Malformed>(result.error)
    }
}

private suspend fun io.ktor.http.content.OutgoingContent.toByteArray(): ByteArray = when (this) {
    is io.ktor.http.content.OutgoingContent.ByteArrayContent -> bytes()
    is io.ktor.http.content.OutgoingContent.ReadChannelContent ->
        readFrom().readRemaining().readString().encodeToByteArray()

    else -> error("a request body this test cannot read: ${this::class.simpleName}")
}
