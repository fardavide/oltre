package dev.fardavide.oltre.client.auth.data

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// **The desktop sign-in, driven across two real sockets.**
//
// Everything between *open a browser* and *reach Google* is ordinary code — bind a port, wait for a
// query string, post a form — and until #113 none of it was executed by anything. It was the largest
// unreachable block in the repository and the argument for excluding it from the coverage report was
// that a machine here cannot sign in to Google. True, and it does not cover the ninety per cent of
// the file that is not the sign-in: **the seam is what turns a platform excuse into a platform edge.**
//
// The two things replaced are the two things a build machine genuinely cannot do:
//
// - `Desktop.browse`, replaced by a real HTTP GET at the redirect the flow just printed. That is
//   precisely what a browser does with it, so what is skipped is the window opening and nothing else.
// - Google's token endpoint, replaced by a `com.sun.net.httpserver` this test owns — the same trick
//   `OltreApiIntegrationTest` uses next door, and for the same reason: the far end is the one thing a
//   test must not reach.
//
// **Integration and not unit**, by this repository's own taxonomy: the loopback server is real, both
// requests cross a real socket, and a fake is not a boundary. What it proves that no unit test could
// is that the redirect handler and the token POST agree with each other about a port that did not
// exist when either was written.
//
// **`runBlocking` and not `runTest`**, which is not a style choice: every request below is a blocking
// JDK call, and `runTest`'s scheduler is single-threaded — a blocking call on it starves the very
// watchdog that would have failed the test, so a wrong answer arrives as a hang rather than as a
// failure. The virtual clock is wrong here too: the flow's five-minute browser window is real time
// this test is genuinely inside of.
class DesktopLoopbackIntegrationTest {

    private lateinit var token: HttpServer
    private var tokenAnswer: String = ""
    private var tokenStatus: Int = 200
    private var tokenBody: String = ""

    @BeforeTest
    fun start() {
        token = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        token.createContext("/token") { exchange -> answerToken(exchange) }
        token.start()
    }

    @AfterTest
    fun stop() {
        token.stop(0)
    }

    // **The whole flow, once, end to end.** Every assertion below picks one thing out of it; this one
    // says the pieces compose — a code arrives on a port nobody knew in advance, is exchanged, and
    // comes back as the token the gate hands to the server.
    @Test
    fun `should sign in when the browser brings the code back`() = runBlocking {
        tokenAnswer = """{"id_token":"header.payload.signature"}"""

        val attempt = signIn { redirect -> visit("$redirect&code=the-code") }

        val signed = assertIs<SignInAttempt.Signed>(attempt)
        assertEquals("header.payload.signature", signed.idToken.value)
    }

    // **The nonce the token will be checked against is the one the browser was given**, which is the
    // property the whole mechanism exists for and the one that fails silently: a client that sent a
    // fresh nonce would work perfectly against a server that did not check.
    @Test
    fun `should report the nonce it put in the authorize request`() = runBlocking {
        tokenAnswer = """{"id_token":"a.b.c"}"""
        var asked = ""

        val attempt = signIn { redirect ->
            asked = redirect
            visit("$redirect&code=the-code")
        }

        val signed = assertIs<SignInAttempt.Signed>(attempt)
        assertTrue(signed.nonce.value.isNotEmpty(), "a sign-in with no nonce is a replayable one")
        assertTrue(asked.isNotEmpty())
    }

    // **PKCE, and the verifier is the half that never travels until now.** The challenge went to the
    // browser; this is the exchange proving the app can produce the secret behind it — which is the
    // only thing that stops a code intercepted on the way back being a sign-in somebody else finishes.
    @Test
    fun `should send the verifier and the redirect it was issued for`() = runBlocking {
        tokenAnswer = """{"id_token":"a.b.c"}"""
        var redirectUri = ""

        signIn { redirect ->
            redirectUri = redirect.substringBefore('?')
            visit("$redirect&code=the-code")
        }

        assertTrue("code_verifier=" in tokenBody, "what was posted: $tokenBody")
        assertTrue("grant_type=authorization_code" in tokenBody, "what was posted: $tokenBody")
        assertTrue(redirectUri.substringAfter("http://").isNotEmpty())
        assertTrue("127.0.0.1" in redirectUri, "the redirect must be a loopback: $redirectUri")
    }

    // **The player closed the consent screen**, which arrives as `error=` on the redirect rather than
    // as silence. A refusal and a cancellation are one sentence at the gate, and this is where the
    // first of the two is produced.
    @Test
    fun `should refuse when the redirect carries an error`() = runBlocking {
        val attempt = signIn { redirect -> visit("$redirect&error=access_denied") }

        assertEquals(SignInAttempt.Refused, attempt)
    }

    // **A redirect from anywhere else is not a sign-in**, and the state check is the only thing that
    // can tell. Unusable rather than refused: nobody said no, this simply is not the flow that was
    // started — which is what an interception looks like from in here.
    @Test
    fun `should not accept a redirect whose state it never issued`() = runBlocking {
        val attempt = signIn { redirect ->
            visit(redirect.substringBefore("state=") + "state=somebody-elses&code=the-code")
        }

        assertEquals(SignInAttempt.Unreachable, attempt)
    }

    // A machine with no browser to open is a machine this build cannot sign in on, and the gate can
    // say so. `false` rather than a throw is what makes that possible.
    @Test
    fun `should be unreachable when there is no browser to open`() = runBlocking {
        val attempt = signIn { false }

        assertEquals(SignInAttempt.Unreachable, attempt)
    }

    // **Google read the request and said no**, which on this endpoint is a `400` with JSON in the
    // error stream — so the body is read either way and `readIdToken` decides whether there is a
    // token in it. A client that only read `inputStream` would throw here instead of answering.
    @Test
    fun `should be unreachable when the token endpoint returns no token`() = runBlocking {
        tokenStatus = 400
        tokenAnswer = """{"error":"invalid_grant"}"""

        val attempt = signIn { redirect -> visit("$redirect&code=the-code") }

        assertEquals(SignInAttempt.Unreachable, attempt)
    }

    // Nobody is listening on the token endpoint at all — a captive portal, a DNS failure, a refused
    // connection. `IOException` rather than a token, and the gate says *the server did not answer*
    // rather than accusing the player of anything.
    @Test
    fun `should be unreachable when nobody answers the token endpoint`() = runBlocking {
        val nowhere = "http://127.0.0.1:${freePort()}/token"

        val attempt = loopbackSignIn(
            clientSecret = "not-a-secret",
            tokenEndpoint = nowhere,
            openBrowser = { redirect -> visit("${redirect.redirectBack()}&code=the-code") },
        )

        assertEquals(SignInAttempt.Unreachable, attempt)
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────

    // The flow, with the browser replaced by whatever the test wants to do with the redirect it was
    // handed. `redirect` is `redirect_uri` plus `state=`, ready for a `&code=` or a `&error=`.
    private suspend fun signIn(browse: (redirect: String) -> Boolean): SignInAttempt = loopbackSignIn(
        clientSecret = "not-a-secret",
        tokenEndpoint = "http://127.0.0.1:${token.address.port}/token",
        openBrowser = { authorize -> browse(authorize.redirectBack()) },
    )

    // **What a browser is actually given.** The authorize URL carries the loopback the app is
    // listening on and the state it will check; a browser sends the player at Google and Google sends
    // them back to that address with the two parameters appended. This does the appending.
    private fun String.redirectBack(): String {
        val query = substringAfter('?').split('&').associate {
            it.substringBefore('=') to it.substringAfter('=')
        }
        val redirect = URI(query.getValue("redirect_uri").replace("%3A", ":").replace("%2F", "/"))
        return "$redirect?state=${query.getValue("state")}"
    }

    private fun visit(url: String): Boolean {
        (URI(url).toURL().openConnection() as HttpURLConnection).run {
            requestMethod = "GET"
            inputStream.use { it.readBytes() }
            disconnect()
        }
        return true
    }

    private fun answerToken(exchange: HttpExchange) {
        tokenBody = exchange.requestBody.use { it.readBytes().decodeToString() }
        val bytes = tokenAnswer.toByteArray()
        exchange.sendResponseHeaders(tokenStatus, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    // **A port nothing is bound to**, which is what *nobody is listening* has to look like: a
    // connection **refused**, straight away, rather than a connect that succeeds into a backlog and
    // then waits. `HttpServer.create` binds on creation and `stop` on one that was never started
    // does not reliably unbind it — which is a hang rather than a failure, and is how this test
    // first ran for ten minutes.
    private fun freePort(): Int = ServerSocket(0, 0, InetAddress.getLoopbackAddress()).use { it.localPort }
}
