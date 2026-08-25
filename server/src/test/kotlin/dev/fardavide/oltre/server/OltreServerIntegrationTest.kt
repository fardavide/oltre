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
import dev.fardavide.oltre.protocol.SyncRequest
import dev.fardavide.oltre.protocol.SyncResponse
import dev.fardavide.oltre.protocol.VerbEnvelope
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

    // ── The harness ───────────────────────────────────────────────────────────────────────────

    private fun ApplicationTestBuilder.server(): MovableClock {
        val clock = MovableClock(TEST_NOW)
        application { oltre(InMemoryColonyRepository(), clock) }
        return clock
    }

    private fun sync(vararg envelopes: VerbEnvelope): SyncRequest =
        SyncRequest(ApiVersion.CURRENT, envelopes.toList())

    private suspend fun ApplicationTestBuilder.post(
        path: String,
        request: SyncRequest,
        player: String? = "davide",
    ): HttpResponse = postRaw(path, Protocol.json.encodeToString(request), player)

    private suspend fun ApplicationTestBuilder.postRaw(
        path: String,
        body: String,
        player: String? = "davide",
    ): HttpResponse = client.post(path) {
        player?.let { header(PLAYER_HEADER, it) }
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun HttpResponse.syncResponse(): SyncResponse =
        Protocol.json.decodeFromString(bodyAsText())

    private suspend fun HttpResponse.apiError(): ApiError =
        Protocol.json.decodeFromString(bodyAsText())
}
