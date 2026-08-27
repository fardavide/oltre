package dev.fardavide.oltre.client.auth.data

import dev.fardavide.oltre.protocol.SignInNonce
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class OAuthFlowTest {

    @Test
    fun `should draw four different values from one source`() {
        val secrets = SignInSecrets(::countingBytes)

        // Four draws from one stream, so no two of them can be the same value by construction — and
        // the one that must not repeat is the verifier, because the challenge is derived from it and
        // a reused pair is a reused proof.
        assertNotEquals(secrets.verifier, secrets.state)
        assertNotEquals(secrets.state, secrets.raw)
    }

    @Test
    fun `should derive the challenge from the verifier`() {
        val secrets = SignInSecrets(::countingBytes)

        assertEquals(sha256(secrets.verifier.encodeToByteArray()).base64Url(), secrets.challenge)
    }

    // The verifier is what RFC 7636 puts a length rule on, and 43 is its floor. A shorter one is
    // refused by the token endpoint, which is a failure that only shows up on the last leg.
    @Test
    fun `should draw a verifier the specification will accept`() {
        val secrets = SignInSecrets(::countingBytes)

        assertTrue(secrets.verifier.length in 43..128, "was ${secrets.verifier.length}")
    }

    // The whole of `SignInNonce`'s KDoc as a test: Google is told what was drawn and Apple is told the
    // hash, because Apple hashes what it is handed and the server compares against the claim.
    @Test
    fun `should tell each provider the nonce it will find in the token`() {
        val secrets = SignInSecrets(::countingBytes)

        assertEquals(SignInNonce(secrets.raw), secrets.nonceFor(NonceShape.RAW))
        assertEquals(SignInNonce(secrets.hashed), secrets.nonceFor(NonceShape.HASHED))
        assertEquals(sha256(secrets.raw.encodeToByteArray()).hex(), secrets.hashed)
    }

    @Test
    fun `should build an authorize url with every parameter encoded`() {
        val secrets = SignInSecrets(::countingBytes)
        val request = AuthorizeRequest(
            endpoint = "https://accounts.google.com/o/oauth2/v2/auth",
            clientId = "client-1",
            redirectUri = "http://127.0.0.1:8080/oauth2redirect",
            scope = "openid email",
            secrets = secrets,
        )

        assertEquals(
            "https://accounts.google.com/o/oauth2/v2/auth" +
                "?client_id=client-1" +
                "&redirect_uri=http%3A%2F%2F127.0.0.1%3A8080%2Foauth2redirect" +
                "&response_type=code" +
                "&scope=openid%20email" +
                "&state=${secrets.state.urlEncoded()}" +
                "&nonce=${secrets.raw.urlEncoded()}" +
                "&code_challenge=${secrets.challenge.urlEncoded()}" +
                "&code_challenge_method=S256",
            request.url(),
        )
    }

    @Test
    fun `should read the code off a matching redirect`() {
        val secrets = SignInSecrets(::countingBytes)

        val result = readRedirect("http://127.0.0.1:8080/?state=${secrets.state}&code=4%2F0Ab", secrets)

        assertEquals(RedirectResult.Code("4/0Ab"), result)
    }

    // The line the whole return leg hangs on: a redirect that is not this attempt's is not usable,
    // whatever it carries.
    @Test
    fun `should refuse a redirect whose state is somebody else's`() {
        val secrets = SignInSecrets(::countingBytes)

        val result = readRedirect("http://127.0.0.1:8080/?state=elsewhere&code=4%2F0Ab", secrets)

        assertEquals(RedirectResult.Unusable, result)
    }

    @Test
    fun `should read a declined consent as a refusal`() {
        val secrets = SignInSecrets(::countingBytes)

        val result = readRedirect("http://127.0.0.1:8080/?state=${secrets.state}&error=access_denied", secrets)

        assertEquals(RedirectResult.Refused, result)
    }

    // Apple's web flow and some of Google's answer in the fragment rather than in the query, and the
    // one thing every platform hands back is characters — so both halves are read.
    @Test
    fun `should read a code that arrives in the fragment`() {
        val secrets = SignInSecrets(::countingBytes)

        val result = readRedirect("oltre://auth#state=${secrets.state}&code=abc", secrets)

        assertEquals(RedirectResult.Code("abc"), result)
    }

    @Test
    fun `should refuse a redirect with nothing usable in it`() {
        val secrets = SignInSecrets(::countingBytes)

        assertEquals(RedirectResult.Unusable, readRedirect("http://127.0.0.1:8080/", secrets))
        assertEquals(
            RedirectResult.Unusable,
            readRedirect("http://127.0.0.1:8080/?state=${secrets.state}&code=", secrets),
        )
    }

    @Test
    fun `should send the secret only where the client type has one`() {
        assertEquals(
            "grant_type=authorization_code&client_id=c&code=4%2F0&code_verifier=v&redirect_uri=x%3A%2Fy",
            tokenRequestBody(clientId = "c", clientSecret = null, code = "4/0", verifier = "v", redirectUri = "x:/y"),
        )
        assertTrue(
            "client_secret=s" in tokenRequestBody(
                clientId = "c",
                clientSecret = "s",
                code = "4/0",
                verifier = "v",
                redirectUri = "x:/y",
            ),
        )
    }

    @Test
    fun `should read the id token out of a token response`() {
        assertEquals("header.body.sig", readIdToken("""{"access_token":"a","id_token":"header.body.sig"}"""))
    }

    // A token endpoint answers with JSON when it fails too, so "did it parse" is not the question.
    // The captive-portal case is the one that would otherwise throw out of a suspend function and
    // past the gate.
    @Test
    fun `should read anything without an id token as no token`() {
        assertEquals(null, readIdToken("""{"error":"invalid_grant"}"""))
        assertEquals(null, readIdToken("""{"id_token":""}"""))
        assertEquals(null, readIdToken("<html>Sign in to the wifi</html>"))
        assertEquals(null, readIdToken(""))
    }

    // A stream that never repeats, so four draws are four values. Deterministic, so the assertions
    // above are about the derivation rather than about a random source.
    private var drawn = 0

    private fun countingBytes(count: Int): ByteArray = ByteArray(count) { (drawn++ % 251).toByte() }
}
