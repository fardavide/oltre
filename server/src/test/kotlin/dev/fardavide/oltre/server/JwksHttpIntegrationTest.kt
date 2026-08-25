package dev.fardavide.oltre.server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

// **The one boundary this slice's identity half has, crossed for real.** Every other test of the key
// set drives `JwksKeys` through a handwritten `JwksSource`, which is a fake and therefore a unit test
// by this repository's taxonomy — *"a fake is not a boundary"*. What a fake cannot prove is what
// would be most expensive to have wrong, and it is the same shape `#112` found on the client side:
// a provider that answers something other than a key set must not be read as *"this provider
// publishes no keys"*, because that surfaces to every player at once as *"your token is not valid"*
// and sends them all to a sign-in screen that cannot help them.
//
// **`com.sun.net.httpserver` and not a dependency**, because it is in the JDK — the same choice
// `OltreApiIntegrationTest` made, and the same reason `httpJwksSource` uses `java.net.http` rather
// than adding a client to this module.
//
// It also answers the coverage question `#110`'s trap 4 poses without an eighth exclusion: a JWKS
// fetch is a socket exactly as the Postgres store is a connection, and the precedent set at `#112`
// is a real-socket `…IntegrationTest` rather than a filter.
class JwksHttpIntegrationTest {

    private lateinit var server: HttpServer
    private var status: Int = 200
    private var document: String = ""
    private var served: Int = 0

    @BeforeTest
    fun start() {
        // Port zero is the operating system picking a free one, so two of these can run at once and
        // nothing has to be reserved.
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange -> answer(exchange) }
        server.start()
        document = jwksOf(published)
    }

    @AfterTest
    fun stop() {
        server.stop(0)
    }

    @Test
    fun `a key set fetched over a real socket is the one the provider served`() = runTest {
        val keys = JwksKeys(httpJwksSource(), MovableClock(TEST_NOW))

        assertNotNull(keys.keyFor(url(), published.keyId))
        assertEquals(1, served)
    }

    @Test
    fun `a provider answering anything but 200 raises rather than reading as no keys`() = runTest {
        // The distinction this file exists for. `502` from a load balancer carrying HTML is what a
        // provider having a bad afternoon looks like, and it has to become `ApiError.Internal` — a
        // 500 the client retries and an operator can go and look at — rather than a refusal every
        // player is told is their own fault.
        status = 502
        document = "<html>Bad Gateway</html>"

        assertFailsWith<IllegalStateException> {
            JwksKeys(httpJwksSource(), MovableClock(TEST_NOW)).keyFor(url(), published.keyId)
        }
    }

    @Test
    fun `a 200 whose body is not a key set raises too`() = runTest {
        // The nastier shape of the same thing: a proxy that answers `200` with an error page.
        document = "<html>we are doing some maintenance</html>"

        assertFailsWith<Exception> {
            JwksKeys(httpJwksSource(), MovableClock(TEST_NOW)).keyFor(url(), published.keyId)
        }
    }

    @Test
    fun `a provider that has stopped answering at all raises rather than hanging forever`() = runTest {
        // A connection nobody accepts, which on Cloud Run must not become a held instance: the
        // client carries a timeout and a refusal comes back as an exception the route turns into a
        // 500.
        val address = url()
        server.stop(0)

        assertFailsWith<Exception> {
            JwksKeys(httpJwksSource(), MovableClock(TEST_NOW)).keyFor(address, published.keyId)
        }
    }

    private fun url(): String = "http://${server.address.hostString}:${server.address.port}/keys"

    private fun answer(exchange: HttpExchange) {
        served++
        val bytes = document.encodeToByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private companion object {

        val published = ProviderKey("published")
    }
}
