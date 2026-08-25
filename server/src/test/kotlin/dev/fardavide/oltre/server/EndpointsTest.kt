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

// What the two colony endpoints decide, with no HTTP anywhere in it. The routes that carry these to
// a socket are `OltreServerIntegrationTest`'s, and the line between the two suites is the line
// `Endpoints.kt` draws: the rules here, the wiring there.
//
// **Driven through `HeaderAuthenticator`**, which since `#110` is the shape a server with no session
// key has — `./gradlew :server:run` and nothing that is deployed. That is deliberate rather than
// leftover: what is under test here is what a *colony* endpoint does once it knows who is asking, and
// the header path is the cheapest way to say who that is. How a bearer token is judged is
// `SessionsTest`'s and `AuthEndpointsTest`'s.
class EndpointsTest {

    private val repository = InMemoryColonyRepository()
    private val players = InMemoryPlayerRepository(repository, ids = sequentialPlayerIds())
    private val authenticator = HeaderAuthenticator(players)
    private val clock = MovableClock(TEST_NOW)

    // ── Admission ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a request that names no player is unauthenticated`() = runTest {
        val answer = sync(player = null)

        assertEquals(HttpStatusCode.Unauthorized, answer.status)
        assertEquals(ApiError.Unauthenticated, answer.error())
    }

    @Test
    fun `a player header of nothing but spaces is unauthenticated`() = runTest {
        val answer = sync(player = "   ")

        assertEquals(HttpStatusCode.Unauthorized, answer.status)
        assertEquals(ApiError.Unauthenticated, answer.error())
    }

    @Test
    fun `a body that is not JSON at all is malformed`() = runTest {
        val answer = sync(body = "not a request")

        assertEquals(HttpStatusCode.BadRequest, answer.status)
        assertIs<ApiError.Malformed>(answer.error())
    }

    @Test
    fun `a body carrying a key this build does not know is malformed`() = runTest {
        // `Protocol.json` sets no `ignoreUnknownKeys`, deliberately: an unknown key is a
        // disagreement about the contract, and `apiVersion` is the field that exists to settle
        // those. Dropping it silently would let a mismatch look like a success.
        val answer = sync(body = """{"apiVersion":1,"envelopes":[],"somethingNew":true}""")

        assertEquals(HttpStatusCode.BadRequest, answer.status)
        assertIs<ApiError.Malformed>(answer.error())
    }

    @Test
    fun `a body whose idempotency key was never minted is malformed`() = runTest {
        // The model's own guard rather than the codec's, which is why `readRequest` catches two
        // things: a blank key is malformed input and nothing else, where an out-of-window version has
        // an answer designed for it.
        val envelope = """{"verb":{"type":"ToggleFlightAlerts"},"clientInstant":"$TEST_NOW","idempotencyKey":""}"""

        val answer = sync(body = """{"apiVersion":1,"envelopes":[$envelope]}""")

        assertEquals(HttpStatusCode.BadRequest, answer.status)
        assertIs<ApiError.Malformed>(answer.error())
    }

    @Test
    fun `a version beyond this build comes back with the window it does serve`() = runTest {
        val answer = sync(body = body(version = ApiVersion(99)))

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
        val answer = sync(body = body(version = ApiVersion(0)))

        assertEquals(HttpStatusCode.UpgradeRequired, answer.status)
    }

    // ── Founding ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `founding a colony mints a galaxy and says it created one`() = runTest {
        val answer = found()

        assertEquals(HttpStatusCode.Created, answer.status)
        val snapshot = answer.response().snapshot
        assertEquals(TEST_NOW, snapshot.lastUpdatedAt)
        assertEquals(galaxySeedFor(davide(), TEST_NOW), snapshot.state.galaxy.seed)
    }

    @Test
    fun `founding twice hands back the first galaxy rather than minting a second`() = runTest {
        val first = found().response().snapshot
        clock.advanceBy(2.days)

        val again = found()

        assertEquals(HttpStatusCode.OK, again.status)
        assertEquals(first.state.galaxy.seed, again.response().snapshot.state.galaxy.seed)
        // Still brought up to date, though — founding an existing colony is a sync like any other.
        assertEquals(TEST_NOW + 2.days, again.response().snapshot.lastUpdatedAt)
    }

    @Test
    fun `a colony can be founded and acted on in one request`() = runTest {
        // The consequence of both routes taking a `SyncRequest`: nobody designed this, it falls out
        // of founding being a sync against a colony that does not exist yet.
        val answer = found(body(envelope(ClientVerb.ToggleFlightAlerts, at = TEST_NOW, key = "bell")))

        assertEquals(setOf(IdempotencyKey("bell")), answer.response().applied)
        assertTrue(answer.response().snapshot.state.announceFlights)
    }

    // ── Syncing ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a sync before there is a colony says exactly that`() = runTest {
        val answer = sync()

        assertEquals(HttpStatusCode.NotFound, answer.status)
        assertEquals(ApiError.NoColony, answer.error())
    }

    @Test
    fun `a colony is one player's and not another's`() = runTest {
        found()

        val answer = sync(player = "someone-else")

        assertEquals(HttpStatusCode.NotFound, answer.status)
    }

    @Test
    fun `a verb that landed and a verb that did not come back in one answer`() = runTest {
        found()

        val answer = sync(
            body = body(
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
        found()
        val request = body(envelope(ClientVerb.StartUpgrade(BuildingType.METAL_MINE), at = TEST_NOW, key = "mine"))
        sync(body = request)

        // The train went into a tunnel; the client never saw that answer and sends it again.
        val retried = sync(body = request).response()

        assertEquals(setOf(IdempotencyKey("mine")), retried.applied)
        assertEquals(emptyList(), retried.rejected)
        assertEquals(1, retried.snapshot.state.eventLog.count { it is Event.BuildStarted })
    }

    @Test
    fun `an empty request is the normal one and brings the colony up to date`() = runTest {
        found()
        clock.advanceBy(7.days)

        val answer = sync()

        assertEquals(HttpStatusCode.OK, answer.status)
        assertEquals(TEST_NOW + 7.days, answer.response().snapshot.lastUpdatedAt)
    }

    @Test
    fun `what the colony came back as is what was persisted`() = runTest {
        found()

        val answered = sync(
            body = body(envelope(ClientVerb.StartUpgrade(BuildingType.METAL_MINE), at = TEST_NOW, key = "mine")),
        ).response().snapshot

        assertEquals(answered, repository.colonyOf(davide())?.snapshot)
    }

    // ── The compare-and-set ───────────────────────────────────────────────────────────────────

    @Test
    fun `a sync that loses the compare-and-set replays against the colony that won`() = runTest {
        found()
        val contended = ContendedColonyRepository(repository, contentions = 1)

        val answer = sync(
            body = body(envelope(ClientVerb.StartUpgrade(BuildingType.METAL_MINE), at = TEST_NOW, key = "mine")),
            colonies = contended,
        )

        assertEquals(HttpStatusCode.OK, answer.status)
        assertEquals(setOf(IdempotencyKey("mine")), answer.response().applied)
        assertEquals(2, contended.attempts)
        // And the mine is on the colony that was actually kept, not on the one that lost.
        assertEquals(answer.response().snapshot, repository.colonyOf(davide())?.snapshot)
    }

    @Test
    fun `a verb the other device already applied is not applied again by the retry`() = runTest {
        // The property the whole retry rests on, and the reason every read is inside the loop: the
        // second attempt re-reads the spent keys, so a verb delivered to both devices is paid for
        // once. Reusing the first attempt's reading of `appliedAmong` would double-spend it.
        found()
        val mine = IdempotencyKey("mine")
        val contended = ContendedColonyRepository(repository, contentions = 1, otherDeviceApplied = setOf(mine))

        val answer = sync(
            body = body(envelope(ClientVerb.StartUpgrade(BuildingType.METAL_MINE), at = TEST_NOW, key = "mine")),
            colonies = contended,
        )

        assertEquals(setOf(mine), answer.response().applied)
        assertEquals(emptyList(), answer.response().rejected)
        assertEquals(0, answer.response().snapshot.state.eventLog.count { it is Event.BuildStarted })
    }

    @Test
    fun `a sync that never wins the compare-and-set says the colony is stale rather than overwriting it`() = runTest {
        found()
        val contended = ContendedColonyRepository(repository, contentions = Int.MAX_VALUE)

        val answer = sync(
            body = body(envelope(ClientVerb.StartUpgrade(BuildingType.METAL_MINE), at = TEST_NOW, key = "mine")),
            colonies = contended,
        )

        assertEquals(HttpStatusCode.Conflict, answer.status)
        assertEquals(ApiError.StaleColony, answer.error())
        // Bounded, and nothing the player queued was applied — the outbox is still theirs to resend.
        assertEquals(3, contended.attempts)
        assertEquals(emptySet(), repository.appliedAmong(davide(), setOf(IdempotencyKey("mine"))))
    }

    @Test
    fun `a store that cannot answer is reported as the server's fault rather than the player's`() = runTest {
        // `ApiError.Internal` is the one member of the taxonomy nothing in the happy path produces,
        // and a constant nothing can ever produce is a sentence the client will never draw. There is
        // a database behind this interface now, which is exactly the thing that goes unreachable.
        val answer = found(colonies = UnreachableColonyRepository())

        assertEquals(HttpStatusCode.InternalServerError, answer.status)
        assertIs<ApiError.Internal>(answer.error())
    }

    @Test
    fun `a failure with nothing to say still comes back naming itself`() = runTest {
        // A `NullPointerException` from a driver carries no message, and `ApiError.Internal("null")`
        // is the one thing worse than nothing for whoever reads the log. The class name is the
        // fallback, and this is what says the fallback is reached.
        val answer = found(colonies = SpeechlessRepository())

        assertEquals("NullPointerException", assertIs<ApiError.Internal>(answer.error()).detail)
    }

    @Test
    fun `a store that cannot say who is asking is a 500 rather than an escaped exception`() = runTest {
        // **`#110` moved the caller lookup inside `served`'s one `catch`**, and this is what says so.
        // Identifying a caller now asks the store whether that player still exists, so the first
        // thing a request does is a query that can fail — and Ktor answering a bare 500 with no
        // `ApiError` in it reads to `#112`'s client as `Unreachable`, which it retries forever.
        val answer = syncColony(
            repository,
            HeaderAuthenticator(UnreachablePlayerRepository()),
            clock,
            Credentials(authorization = null, playerHeader = DAVIDE),
            body(),
        )

        assertEquals(HttpStatusCode.InternalServerError, answer.status)
        assertIs<ApiError.Internal>(answer.error())
    }

    // ── The harness ───────────────────────────────────────────────────────────────────────────

    private suspend fun found(
        body: String = body(),
        player: String? = DAVIDE,
        colonies: ColonyRepository = repository,
    ): Answer = foundColony(colonies, authenticator, clock, credentials(player), body)

    private suspend fun sync(
        body: String = body(),
        player: String? = DAVIDE,
        colonies: ColonyRepository = repository,
    ): Answer = syncColony(colonies, authenticator, clock, credentials(player), body)

    // The id `davide` resolves to, asked of the store rather than guessed. `players.id` is a
    // surrogate key since `#110`, so the header value is a subject and no longer the id.
    private suspend fun davide(): PlayerId = players.resolve(ProviderIdentity(ProviderName.HEADER, DAVIDE))

    private fun credentials(player: String?): Credentials =
        Credentials(authorization = null, playerHeader = player)

    private fun body(vararg envelopes: VerbEnvelope, version: ApiVersion = ApiVersion.CURRENT): String =
        Protocol.json.encodeToString(SyncRequest(version, envelopes.toList()))

    private fun Answer.response(): SyncResponse = assertIs<Answer.Colony>(this).response

    private fun Answer.error(): ApiError = assertIs<Answer.Failed>(this).error

    private companion object {

        const val DAVIDE = "davide"
    }
}
