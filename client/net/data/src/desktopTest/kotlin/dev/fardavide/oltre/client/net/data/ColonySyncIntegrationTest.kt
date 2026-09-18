package dev.fardavide.oltre.client.net.data

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.ClientVerb
import dev.fardavide.oltre.protocol.IdempotencyKey
import dev.fardavide.oltre.protocol.Protocol
import dev.fardavide.oltre.protocol.SessionResponse
import dev.fardavide.oltre.protocol.SessionToken
import dev.fardavide.oltre.protocol.SyncRequest
import dev.fardavide.oltre.protocol.SyncResponse
import kotlinx.coroutines.test.runTest
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// **The sync loop over a real socket, which `ColonySyncTest` cannot be.** That file drives the same
// class through `FakeOltreApi` — a fake is not a boundary, so it is a unit test by this repository's
// taxonomy — and what it therefore cannot say is that the decisions in `ColonySync` survive a real
// engine, a real request body and a real status line.
//
// **The case that earned the file is the 426.** A refusal the transport has to decode before
// `ColonySync` can even see it is exactly the shape that passes against a fake and fails on a wire,
// and it is the one that locked a player out of 0.24 — so it is asserted here against a server that
// really sends it rather than against a constant this repository wrote for itself.
//
// `com.sun.net.httpserver` and not a dependency, for `OltreApiIntegrationTest`'s reason: it is in
// the JDK, and the real thing under it exists only on a platform.
class ColonySyncIntegrationTest {

    private lateinit var server: HttpServer
    private var lastBody: String = ""
    private var status: Int = 200
    private var answer: String = ""

    @BeforeTest
    fun start() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange -> answer(exchange) }
        server.start()
    }

    @AfterTest
    fun stop() {
        server.stop(0)
    }

    @Test
    fun `a tap over a real socket sends the verb and comes back with the authoritative colony`() = runTest {
        // given
        answer = Protocol.json.encodeToString(colony())
        val file = FakeOutboxFile()

        // when
        val outcome = sync(file).act(ClientVerb.StartUpgrade(BuildingType.METAL_MINE))

        // then — the verb crossed the wire inside a real request
        val sent = Protocol.json.decodeFromString<SyncRequest>(lastBody)
        assertEquals(listOf(ClientVerb.StartUpgrade(BuildingType.METAL_MINE)), sent.envelopes.map { it.verb })

        // and — the colony that came back is the server's, and nothing is left outstanding
        assertEquals(ActOutcome.Synced(fakeColony(NOW), emptyList()), outcome)
        assertEquals(null, file.content)
    }

    // **The refusal that is about the build rather than about the verb**, over a server that really
    // answers `426`. It reaches the caller whole — the shell needs the window to tell *update the
    // app* from *the server has not caught up*, and a transport that flattened it into a bare
    // failure would take that choice away before anything could make it.
    @Test
    fun `a version the server no longer serves comes back whole, and the queue is untouched`() = runTest {
        // given — the body `api.oltre.space` sends, verbatim
        status = 426
        answer = Protocol.json.encodeToString<ApiError>(
            ApiError.UnsupportedApiVersion(oldestServed = ApiVersion(3), current = ApiVersion(3)),
        )
        val file = FakeOutboxFile()

        // when
        val outcome = sync(file).act(ClientVerb.StartUpgrade(BuildingType.METAL_MINE))

        // then — the error arrives with the window in it
        assertEquals(
            ActOutcome.Failed(ApiError.UnsupportedApiVersion(oldestServed = ApiVersion(3), current = ApiVersion(3))),
            outcome,
        )

        // and — nothing was dropped. A build that is refused today is answered tomorrow by an
        // update, and the tap it made in between is still owed.
        assertNotNull(file.content)
    }

    // **A server that is not there at all**, which is the property the outbox exists for: the tap is
    // kept rather than lost, and the caller is told it was held rather than told it failed. Over a
    // real socket, because what makes it true is that a refused connection arrives as an
    // `IOException` and not as something this module does not catch.
    @Test
    fun `a tap made against nothing at all is queued rather than lost`() = runTest {
        // given — a real address with nothing behind it any more
        val address = "http://${server.address.hostString}:${server.address.port}"
        server.stop(0)
        val file = FakeOutboxFile()

        // when
        val outcome = sync(file, address).act(ClientVerb.StartUpgrade(BuildingType.METAL_MINE))

        // then
        assertEquals(ActOutcome.Queued, outcome)
        assertNotNull(file.content)
    }

    private fun sync(
        file: FakeOutboxFile,
        address: String = "http://${server.address.hostString}:${server.address.port}",
    ): ColonySync {
        val api = KtorOltreApi(oltreHttpClient(), address)
        return ColonySync(
            api = api,
            outbox = Outbox(file),
            keys = FakeIdempotencyKeys("key"),
            clock = FixedClock,
            retry = RetryPolicy.ONCE,
            sessions = SessionKeeper(api = api, store = FakeSessionStore(signedIn()), clock = FixedClock),
        )
    }

    private fun answer(exchange: HttpExchange) {
        lastBody = exchange.requestBody.readBytes().decodeToString()
        val bytes = answer.encodeToByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun colony(): SyncResponse = SyncResponse(
        apiVersion = ApiVersion.CURRENT,
        snapshot = fakeColony(NOW),
        applied = setOf(IdempotencyKey("key-1")),
        rejected = emptyList(),
    )

    // Signed in and in date, so that a test about a sync is not quietly a test about a refresh.
    private fun signedIn(): SessionResponse = SessionResponse(
        apiVersion = ApiVersion.CURRENT,
        accessToken = SessionToken("davide"),
        accessExpiresAt = NOW + 1.hours,
        refreshToken = SessionToken("davide.refresh"),
        refreshExpiresAt = NOW + 90.days,
    )

    private companion object {

        val NOW: Instant = Instant.parse("2026-08-25T09:00:00Z")
    }
}

// `clientInstant` is stamped from this, so the request body is byte-identical on every run — which
// is what lets the first test assert on what the server received rather than on its shape.
private object FixedClock : Clock {

    override fun now(): Instant = Instant.parse("2026-08-25T09:00:00Z")
}
