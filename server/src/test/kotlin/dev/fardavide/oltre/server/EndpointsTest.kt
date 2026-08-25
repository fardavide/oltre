package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.Event
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.ClientVerb
import dev.fardavide.oltre.protocol.IdempotencyKey
import dev.fardavide.oltre.protocol.Protocol
import dev.fardavide.oltre.protocol.RejectionReason
import dev.fardavide.oltre.protocol.SyncRequest
import dev.fardavide.oltre.protocol.SyncResponse
import dev.fardavide.oltre.protocol.VerbEnvelope
import dev.fardavide.oltre.protocol.VerbRefusal
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

// What the two endpoints decide, with no HTTP anywhere in it. The routes that carry these to a
// socket are `OltreServerIntegrationTest`'s, and the line between the two suites is the line
// `Endpoints.kt` draws: the rules here, the wiring there.
class EndpointsTest {

    private val repository = InMemoryColonyRepository()
    private val clock = MovableClock(TEST_NOW)

    // ── Admission ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a request that names no player is unauthenticated`() = runTest {
        val answer = syncColony(repository, clock, player = null, body = body())

        assertEquals(HttpStatusCode.Unauthorized, answer.status)
        assertEquals(ApiError.Unauthenticated, answer.error())
    }

    @Test
    fun `a player header of nothing but spaces is unauthenticated`() = runTest {
        val answer = syncColony(repository, clock, player = "   ", body = body())

        assertEquals(HttpStatusCode.Unauthorized, answer.status)
        assertEquals(ApiError.Unauthenticated, answer.error())
    }

    @Test
    fun `a body that is not JSON at all is malformed`() = runTest {
        val answer = syncColony(repository, clock, DAVIDE, body = "not a request")

        assertEquals(HttpStatusCode.BadRequest, answer.status)
        assertIs<ApiError.Malformed>(answer.error())
    }

    @Test
    fun `a body carrying a key this build does not know is malformed`() = runTest {
        // `Protocol.json` sets no `ignoreUnknownKeys`, deliberately: an unknown key is a
        // disagreement about the contract, and `apiVersion` is the field that exists to settle
        // those. Dropping it silently would let a mismatch look like a success.
        val answer = syncColony(repository, clock, DAVIDE, """{"apiVersion":1,"envelopes":[],"somethingNew":true}""")

        assertEquals(HttpStatusCode.BadRequest, answer.status)
        assertIs<ApiError.Malformed>(answer.error())
    }

    @Test
    fun `a body whose idempotency key was never minted is malformed`() = runTest {
        // The model's own guard rather than the codec's, which is why `admit` catches two things: a
        // blank key is malformed input and nothing else, where an out-of-window version has an
        // answer designed for it.
        val envelope = """{"verb":{"type":"ToggleFlightAlerts"},"clientInstant":"$TEST_NOW","idempotencyKey":""}"""

        val answer = syncColony(repository, clock, DAVIDE, """{"apiVersion":1,"envelopes":[$envelope]}""")

        assertEquals(HttpStatusCode.BadRequest, answer.status)
        assertIs<ApiError.Malformed>(answer.error())
    }

    @Test
    fun `a version beyond this build comes back with the window it does serve`() = runTest {
        val answer = syncColony(repository, clock, DAVIDE, body(version = ApiVersion(99)))

        assertEquals(HttpStatusCode.UpgradeRequired, answer.status)
        assertEquals(
            ApiError.UnsupportedApiVersion(ApiVersion.OLDEST_SERVED, ApiVersion.CURRENT),
            answer.error(),
        )
    }

    @Test
    fun `a version of zero is negotiated rather than failing to parse`() = runTest {
        // `ApiVersion` deliberately guards nothing on construction, so that this is a first-class
        // answer the client can act on instead of a parse failure it cannot.
        val answer = syncColony(repository, clock, DAVIDE, body(version = ApiVersion(0)))

        assertEquals(HttpStatusCode.UpgradeRequired, answer.status)
    }

    // ── Founding ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `founding a colony mints a galaxy and says it created one`() = runTest {
        val answer = foundColony(repository, clock, DAVIDE, body())

        assertEquals(HttpStatusCode.Created, answer.status)
        val snapshot = answer.response().snapshot
        assertEquals(TEST_NOW, snapshot.lastUpdatedAt)
        assertEquals(galaxySeedFor(PlayerId(DAVIDE), TEST_NOW), snapshot.state.galaxy.seed)
    }

    @Test
    fun `founding twice hands back the first galaxy rather than minting a second`() = runTest {
        val first = foundColony(repository, clock, DAVIDE, body()).response().snapshot
        clock.advanceBy(2.days)

        val again = foundColony(repository, clock, DAVIDE, body())

        assertEquals(HttpStatusCode.OK, again.status)
        assertEquals(first.state.galaxy.seed, again.response().snapshot.state.galaxy.seed)
        // Still brought up to date, though — founding an existing colony is a sync like any other.
        assertEquals(TEST_NOW + 2.days, again.response().snapshot.lastUpdatedAt)
    }

    @Test
    fun `a colony can be founded and acted on in one request`() = runTest {
        // The consequence of both routes taking a `SyncRequest`: nobody designed this, it falls out
        // of founding being a sync against a colony that does not exist yet.
        val answer = foundColony(
            repository,
            clock,
            DAVIDE,
            body(envelope(ClientVerb.ToggleFlightAlerts, at = TEST_NOW, key = "bell")),
        )

        assertEquals(setOf(IdempotencyKey("bell")), answer.response().applied)
        assertTrue(answer.response().snapshot.state.announceFlights)
    }

    // ── Syncing ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a sync before there is a colony says exactly that`() = runTest {
        val answer = syncColony(repository, clock, DAVIDE, body())

        assertEquals(HttpStatusCode.NotFound, answer.status)
        assertEquals(ApiError.NoColony, answer.error())
    }

    @Test
    fun `a colony is one player's and not another's`() = runTest {
        foundColony(repository, clock, DAVIDE, body())

        val answer = syncColony(repository, clock, "someone-else", body())

        assertEquals(HttpStatusCode.NotFound, answer.status)
    }

    @Test
    fun `a verb that landed and a verb that did not come back in one answer`() = runTest {
        foundColony(repository, clock, DAVIDE, body())

        val answer = syncColony(
            repository,
            clock,
            DAVIDE,
            body(
                envelope(ClientVerb.StartUpgrade(BuildingType.METAL_MINE), at = TEST_NOW, key = "mine"),
                envelope(ClientVerb.StartUpgrade(BuildingType.METAL_MINE), at = TEST_NOW, key = "mine-again"),
            ),
        )

        val response = answer.response()
        assertEquals(setOf(IdempotencyKey("mine")), response.applied)
        assertEquals(listOf(IdempotencyKey("mine-again")), response.rejected.map { it.envelope.idempotencyKey })
        assertEquals(RejectionReason.Refused(VerbRefusal.ALREADY_UPGRADING), response.rejected.single().reason)
    }

    @Test
    fun `a verb whose response was lost is not applied a second time`() = runTest {
        foundColony(repository, clock, DAVIDE, body())
        val request = body(envelope(ClientVerb.StartUpgrade(BuildingType.METAL_MINE), at = TEST_NOW, key = "mine"))
        syncColony(repository, clock, DAVIDE, request)

        // The train went into a tunnel; the client never saw that answer and sends it again.
        val retried = syncColony(repository, clock, DAVIDE, request).response()

        assertEquals(setOf(IdempotencyKey("mine")), retried.applied)
        assertEquals(emptyList(), retried.rejected)
        assertEquals(1, retried.snapshot.state.eventLog.count { it is Event.BuildStarted })
    }

    @Test
    fun `an empty request is the normal one and brings the colony up to date`() = runTest {
        foundColony(repository, clock, DAVIDE, body())
        clock.advanceBy(7.days)

        val answer = syncColony(repository, clock, DAVIDE, body())

        assertEquals(HttpStatusCode.OK, answer.status)
        assertEquals(TEST_NOW + 7.days, answer.response().snapshot.lastUpdatedAt)
    }

    @Test
    fun `what the colony came back as is what was persisted`() = runTest {
        foundColony(repository, clock, DAVIDE, body())

        val answered = syncColony(
            repository,
            clock,
            DAVIDE,
            body(envelope(ClientVerb.StartUpgrade(BuildingType.METAL_MINE), at = TEST_NOW, key = "mine")),
        ).response().snapshot

        assertEquals(answered, repository.colonyOf(PlayerId(DAVIDE)))
    }

    @Test
    fun `a store that cannot answer is reported as the server's fault rather than the player's`() = runTest {
        // `ApiError.Internal` is the one member of the taxonomy nothing in the happy path produces,
        // and a constant nothing can ever produce is a sentence the client will never draw. `#109`
        // puts a database behind this interface, which is exactly the thing that will be unreachable.
        val answer = foundColony(UnreachableColonyRepository(), clock, DAVIDE, body())

        assertEquals(HttpStatusCode.InternalServerError, answer.status)
        assertIs<ApiError.Internal>(answer.error())
    }

    // ── The harness ───────────────────────────────────────────────────────────────────────────

    private fun body(vararg envelopes: VerbEnvelope, version: ApiVersion = ApiVersion.CURRENT): String =
        Protocol.json.encodeToString(SyncRequest(version, envelopes.toList()))

    private fun Answer.response(): SyncResponse = assertIs<Answer.Colony>(this).response

    private fun Answer.error(): ApiError = assertIs<Answer.Failed>(this).error

    private companion object {

        const val DAVIDE = "davide"
    }
}
