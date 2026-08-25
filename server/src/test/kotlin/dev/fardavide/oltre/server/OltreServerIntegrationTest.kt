package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.Event
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.ClientVerb
import dev.fardavide.oltre.protocol.IdempotencyKey
import dev.fardavide.oltre.protocol.Protocol
import dev.fardavide.oltre.protocol.RefreshRequest
import dev.fardavide.oltre.protocol.SessionResponse
import dev.fardavide.oltre.protocol.SessionToken
import dev.fardavide.oltre.protocol.SignInRequest
import dev.fardavide.oltre.protocol.SyncRequest
import dev.fardavide.oltre.protocol.SyncResponse
import dev.fardavide.oltre.protocol.VerbEnvelope
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

// The routes over Ktor's test host, which is the one boundary this suite crosses: real routing, a
// real body and the real codec both ends share.
//
// **What every rule is doing here is `EndpointsTest`'s**, deliberately — the clamp, the refusals and
// the idempotency are decided by code that knows nothing about HTTP, and a test that has to stand up
// a server to judge them is a slow test of the wrong thing. What is left is exactly what only a
// request can prove: that the header is spelled the way the client will spell it, that a status line
// carries what the body says, that the sealed hierarchies reach the wire with their discriminators,
// and that a colony can be founded, played over a week and read back by something that only knows
// the contract.
class OltreServerIntegrationTest {

    @Test
    fun `founding a colony answers 201 with a galaxy the server minted`() = testApplication {
        val clock = server()

        val response = post("/v1/colony", sync())

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.syncResponse()
        assertEquals(ApiVersion.CURRENT, body.apiVersion)
        assertEquals(clock.now(), body.snapshot.lastUpdatedAt)
        // A colony that came back with an unsurveyed home would be a colony with no map at all.
        assertTrue(body.snapshot.state.galaxy.home in body.snapshot.state.galaxy.surveyed)
    }

    @Test
    fun `founding a second time answers 200 rather than minting another galaxy`() = testApplication {
        server()
        val first = post("/v1/colony", sync()).syncResponse()

        val again = post("/v1/colony", sync())

        assertEquals(HttpStatusCode.OK, again.status)
        assertEquals(first.snapshot.state.galaxy.seed, again.syncResponse().snapshot.state.galaxy.seed)
    }

    @Test
    fun `two players founding in the same millisecond do not open in the same galaxy`() = testApplication {
        server()

        val mine = post("/v1/colony", sync(), player = "davide").syncResponse()
        val theirs = post("/v1/colony", sync(), player = "someone-else").syncResponse()

        assertNotEquals(mine.snapshot.state.galaxy.seed, theirs.snapshot.state.galaxy.seed)
    }

    @Test
    fun `a colony nobody founded is a 404 carrying the reason`() = testApplication {
        server()

        val response = post("/v1/sync", sync())

        assertEquals(HttpStatusCode.NotFound, response.status)
        // Decoded as `ApiError` and not as the member, which is what proves the discriminator was
        // written: a response encoded from the static member type would not carry one, and the
        // client would fail to parse a message the server thought it had sent.
        assertEquals(ApiError.NoColony, response.apiError())
    }

    @Test
    fun `a request with no player header is a 401`() = testApplication {
        // The header's spelling is only a fact on the wire, so this is the one place it can be
        // pinned. `#110` replaces what the value means, not where it is read from.
        server()

        val response = post("/v1/sync", sync(), player = null)

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(ApiError.Unauthenticated, response.apiError())
    }

    @Test
    fun `a version this build does not serve is a 426 carrying the window`() = testApplication {
        server()

        val response = post("/v1/sync", SyncRequest(ApiVersion(99), emptyList()))

        assertEquals(HttpStatusCode.UpgradeRequired, response.status)
        assertEquals(
            ApiError.UnsupportedApiVersion(ApiVersion.OLDEST_SERVED, ApiVersion.CURRENT),
            response.apiError(),
        )
    }

    @Test
    fun `a body this build cannot read is a 400`() = testApplication {
        server()

        val response = postRaw("/v1/sync", """{"apiVersion":1,"envelopes":[],"somethingNew":true}""")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.apiError() is ApiError.Malformed)
    }

    @Test
    fun `a colony is founded, played across a week and read back over HTTP`() = testApplication {
        val clock = server()
        post("/v1/colony", sync())

        val started = post(
            "/v1/sync",
            sync(envelope(ClientVerb.StartUpgrade(BuildingType.METAL_MINE), at = clock.now(), key = "mine")),
        ).syncResponse()
        assertEquals(setOf(IdempotencyKey("mine")), started.applied)
        assertTrue(started.snapshot.state.builds.containsKey(BuildingType.METAL_MINE))

        // A week away, and the app opens with an empty outbox — which is the normal request rather
        // than a degenerate one, and the whole of what this epic moves: the colony grew on the
        // server while nothing was running on the phone.
        clock.advanceBy(7.days)
        val week = post("/v1/sync", sync()).syncResponse()

        assertEquals(clock.now(), week.snapshot.lastUpdatedAt)
        assertTrue(week.snapshot.state.eventLog.any { it is Event.BuildCompleted })
        assertEquals(emptyList(), week.snapshot.state.builds.values.toList())
    }

    @Test
    fun `a fleet bought over HTTP flies from the colony the server holds and comes home`() = testApplication {
        val clock = server()
        val colony = post("/v1/colony", sync()).syncResponse().snapshot
        // A day's mining first — the opening stock is 500 metal and a skiff is 800, so the first
        // hull is a purchase the colony has to grow into.
        clock.advanceBy(1.days)
        post(
            "/v1/sync",
            sync(envelope(ClientVerb.BuildShips(Ships.of(ShipType.SKIFF, 1)), at = clock.now(), key = "skiff")),
        )

        clock.advanceBy(1.days)
        val target = colony.state.galaxy.surveyed.first { it != colony.state.galaxy.home }
        val dispatched = post(
            "/v1/sync",
            sync(
                envelope(
                    ClientVerb.StartRun(
                        target = target,
                        gathering = ResourceKind.METAL,
                        ships = Ships.of(ShipType.SKIFF, 1),
                        window = 12.hours,
                    ),
                    at = clock.now(),
                    key = "run",
                ),
            ),
        ).syncResponse()

        assertEquals(setOf(IdempotencyKey("run")), dispatched.applied)
        assertEquals(emptyList(), dispatched.rejected)
        assertEquals(1, dispatched.snapshot.state.runs.size)

        clock.advanceBy(3.days)
        val home = post("/v1/sync", sync()).syncResponse()

        assertTrue(home.snapshot.state.eventLog.any { it is Event.FleetReturned })
        assertEquals(emptyList(), home.snapshot.state.runs)
    }

    // ── Signed in ─────────────────────────────────────────────────────────────────────────────
    //
    // The four routes `#110` added, over the same test host. **What is under test is the wiring** —
    // the paths, the status lines, the `Authorization` header's spelling and the 204 with no body —
    // exactly as above: every rule about what makes a token good is `IdTokenVerifierTest`'s and
    // `AuthEndpointsTest`'s, and standing up a server to judge one would be a slow test of the wrong
    // thing.

    @Test
    fun `a sign-in over HTTP answers a session that founds a colony`() = testApplication {
        signedInServer()

        val session = post("/v1/auth/google", signIn()).session()

        assertEquals(ApiVersion.CURRENT, session.apiVersion)
        val founded = post("/v1/colony", sync(), player = null, bearer = session.accessToken)
        assertEquals(HttpStatusCode.Created, founded.status)
    }

    @Test
    fun `a colony endpoint reached with no bearer token is a 401 however the player header is spelled`() =
        testApplication {
            // **The half of trap 1 that is worth an integration test.** `Protocol.PLAYER_HEADER` is
            // still read — `#112`'s client sends it on every request and deleting it would stop that
            // client compiling — and a server holding a session key ignores it completely. This is
            // the line that says the placeholder stopped being *believed* at `#110` even though it
            // stops being *sent* at `#113`.
            signedInServer()

            val response = post("/v1/sync", sync(), player = "davide")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals(ApiError.Unauthenticated, response.apiError())
        }

    @Test
    fun `a token this server will not verify is a 401 rather than a 400`() = testApplication {
        signedInServer()
        val body = Protocol.json.encodeToString(
            SignInRequest(ApiVersion.CURRENT, providerKey.sign(idTokenClaims(audience = "somebody.else")), TEST_NONCE),
        )

        val response = postRaw("/v1/auth/google", body, player = null)

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(ApiError.Unauthenticated, response.apiError())
    }

    @Test
    fun `a refresh over HTTP trades one session for another`() = testApplication {
        val clock = signedInServer()
        val session = post("/v1/auth/google", signIn()).session()

        clock.advanceBy(2.hours)
        val body = Protocol.json.encodeToString(RefreshRequest(ApiVersion.CURRENT, session.refreshToken))
        val refreshed = postRaw("/v1/auth/refresh", body, player = null).session()

        assertEquals(clock.now() + 1.hours, refreshed.accessExpiresAt)
        assertEquals(
            HttpStatusCode.Created,
            post("/v1/colony", sync(), player = null, bearer = refreshed.accessToken).status,
        )
    }

    @Test
    fun `deleting an account answers 204 with no body at all and the session stops working`() = testApplication {
        // App Review 5.1.1(v). The empty body is the assertion that matters here: a `204` carrying
        // JSON is a `204` some HTTP stacks will refuse to read.
        signedInServer()
        val session = post("/v1/auth/google", signIn()).session()
        post("/v1/colony", sync(), player = null, bearer = session.accessToken)

        val deleted = client.delete("/v1/account") { bearer(session.accessToken) }

        assertEquals(HttpStatusCode.NoContent, deleted.status)
        assertEquals("", deleted.bodyAsText())
        assertEquals(HttpStatusCode.Unauthorized, post("/v1/sync", sync(), bearer = session.accessToken).status)
    }

    @Test
    fun `deleting with no credential at all is a 401`() = testApplication {
        signedInServer()

        assertEquals(HttpStatusCode.Unauthorized, client.delete("/v1/account").status)
    }

    // ── The harness ───────────────────────────────────────────────────────────────────────────

    // No identity, which is the shape `./gradlew :server:run` has and the one that keeps every test
    // above this line reading as it did at `#108`: a request names its player in a header. The
    // bearer half is `signed in` below, where a server *with* a session key is stood up.
    private fun ApplicationTestBuilder.server(): MovableClock {
        val clock = MovableClock(TEST_NOW)
        val colonies = InMemoryColonyRepository()
        application {
            oltre(colonies, InMemoryPlayerRepository(colonies, ids = sequentialPlayerIds()), clock, identity = null)
        }
        return clock
    }

    // A server with a session key, so the bearer half of `Authenticator.kt` is the one in play. The
    // provider is a keypair generated in this process and served by a handwritten fake — nothing in
    // this suite reaches Apple or Google.
    private fun ApplicationTestBuilder.signedInServer(): MovableClock {
        val clock = MovableClock(TEST_NOW)
        val colonies = InMemoryColonyRepository()
        val players = InMemoryPlayerRepository(colonies, ids = sequentialPlayerIds())
        application {
            oltre(
                colonies = colonies,
                players = players,
                clock = clock,
                identity = Identity(
                    verifier = IdTokenVerifier(
                        specs = mapOf(IdentityProvider.GOOGLE to testSpec()),
                        keys = JwksKeys(FakeJwksSource(jwksOf(providerKey)), clock),
                        clock = clock,
                    ),
                    sessions = Sessions(TEST_SIGNING_KEY, clock),
                ),
            )
        }
        return clock
    }

    private fun signIn(): String = Protocol.json.encodeToString(
        SignInRequest(ApiVersion.CURRENT, providerKey.sign(idTokenClaims()), TEST_NONCE),
    )

    private fun sync(vararg envelopes: VerbEnvelope): SyncRequest =
        SyncRequest(ApiVersion.CURRENT, envelopes.toList())

    private suspend fun ApplicationTestBuilder.post(
        path: String,
        request: SyncRequest,
        player: String? = "davide",
        bearer: SessionToken? = null,
    ): HttpResponse = postRaw(path, Protocol.json.encodeToString(request), player, bearer)

    private suspend fun ApplicationTestBuilder.post(path: String, body: String): HttpResponse =
        postRaw(path, body, player = null)

    private suspend fun ApplicationTestBuilder.postRaw(
        path: String,
        body: String,
        player: String? = "davide",
        bearer: SessionToken? = null,
    ): HttpResponse = client.post(path) {
        player?.let { header(Protocol.PLAYER_HEADER, it) }
        bearer?.let { bearer(it) }
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    // The scheme is built from `Protocol.BEARER_PREFIX` rather than spelled here, which is the same
    // rule the player header follows and for the same reason: a wire string written out at both ends
    // is one that can differ at both ends.
    private fun HttpRequestBuilder.bearer(token: SessionToken) {
        header(Protocol.AUTHORIZATION_HEADER, Protocol.BEARER_PREFIX + token.value)
    }

    private suspend fun HttpResponse.syncResponse(): SyncResponse =
        Protocol.json.decodeFromString(bodyAsText())

    private suspend fun HttpResponse.session(): SessionResponse {
        assertEquals(HttpStatusCode.OK, status, bodyAsText())
        return Protocol.json.decodeFromString(bodyAsText())
    }

    private suspend fun HttpResponse.apiError(): ApiError =
        Protocol.json.decodeFromString(bodyAsText())

    private companion object {

        val providerKey = ProviderKey("the-published-key")
    }
}
