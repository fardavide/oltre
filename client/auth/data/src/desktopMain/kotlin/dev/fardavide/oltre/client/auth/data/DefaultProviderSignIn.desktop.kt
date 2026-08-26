package dev.fardavide.oltre.client.auth.data

import com.sun.net.httpserver.HttpServer
import dev.fardavide.oltre.protocol.AuthProvider
import dev.fardavide.oltre.protocol.IdToken
import java.awt.Desktop
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.security.SecureRandom
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

actual fun secureRandomBytes(count: Int): ByteArray = ByteArray(count).also { SecureRandom().nextBytes(it) }

// **Google alone, and only when the machine has the credential.**
//
// *Apple is absent and this is the honest form of a gap rather than a decision.* Sign in with Apple
// away from an Apple platform is a browser flow whose Return URL Apple insists is `https`, and the one
// registered is `https://api.oltre.space/v1/auth/apple/callback` — a **server** endpoint, which
// `#113` puts out of scope by name. A button that opened a browser which never came back would be the
// worst control this app has ever shipped, so there is no button. See the pull request.
//
// *Google is conditional*, because Google calls this an **installed application** and its token
// endpoint still wants the client secret. That secret is documented as not confidential and it is
// still not going in the repository, so the desktop build reads it from the environment the dev loop
// already sources: `source ~/.oltre/identity.env && ./gradlew :client:shell:run`. Absent, there is no
// button — not a button that fails.
actual fun signInProviders(): Set<AuthProvider> =
    if (desktopClientSecret() == null) emptySet() else setOf(AuthProvider.GOOGLE)

actual fun defaultProviderSignIn(): ProviderSignIn = ProviderSignIn { provider ->
    val secret = desktopClientSecret()
    // Unreachable rather than refused: nothing was asked and nobody said no. It is also unreachable
    // in the ordinary sense — this build cannot reach Google at all.
    if (provider != AuthProvider.GOOGLE || secret == null) return@ProviderSignIn SignInAttempt.Unreachable
    withContext(Dispatchers.IO) { loopbackSignIn(secret) }
}

// **The loopback flow, which is what Google's own guidance for a desktop app describes**: bind a port
// on `127.0.0.1`, send the system browser at the authorize endpoint, and let the browser bring the
// code back to a socket only this process can be listening on. Any port is allowed for a loopback
// redirect, which is why nothing here is registered anywhere.
private suspend fun loopbackSignIn(clientSecret: String): SignInAttempt {
    val secrets = SignInSecrets(::secureRandomBytes)
    val arrived = CompletableDeferred<String>()
    // Port 0 asks the operating system for a free one. A fixed port would be a second copy of the app
    // failing to start rather than falling back.
    val server = try {
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    } catch (_: IOException) {
        return SignInAttempt.Unreachable
    }

    return try {
        server.createContext("/oauth2redirect") { exchange ->
            // The whole URI, query and all, because `readRedirect` is what decides which parts of it
            // matter — and it is the one thing all three platforms hand over in the same form.
            arrived.complete(exchange.requestURI.toString())
            val body = CLOSE_PAGE.toByteArray()
            exchange.sendResponseHeaders(HTTP_OK, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        val redirectUri = "http://127.0.0.1:${server.address.port}/oauth2redirect"
        val authorize = AuthorizeRequest(
            endpoint = OltreOAuth.GOOGLE_AUTHORIZE,
            clientId = OltreOAuth.GOOGLE_DESKTOP_CLIENT_ID,
            redirectUri = redirectUri,
            scope = OltreOAuth.GOOGLE_SCOPE,
            secrets = secrets,
        ).url()

        if (!openBrowser(authorize)) return SignInAttempt.Unreachable

        // **A window rather than a wait for ever**, because the browser may simply be closed and the
        // gate has to be able to answer. Long enough to read a consent screen and sign in to Google
        // from cold; short enough that a forgotten tab does not leave the app waiting all afternoon.
        val redirect = withTimeoutOrNull(BROWSER_WINDOW) { arrived.await() }
            ?: return SignInAttempt.Unreachable

        when (val result = readRedirect(redirect, secrets)) {
            RedirectResult.Refused -> SignInAttempt.Refused
            RedirectResult.Unusable -> SignInAttempt.Unreachable
            is RedirectResult.Code -> exchange(
                code = result.value,
                verifier = secrets.verifier,
                redirectUri = redirectUri,
                clientSecret = clientSecret,
                nonce = secrets.nonceFor(NonceShape.RAW).value,
            )
        }
    } finally {
        server.stop(0)
    }
}

private fun exchange(
    code: String,
    verifier: String,
    redirectUri: String,
    clientSecret: String,
    nonce: String,
): SignInAttempt {
    val body = tokenRequestBody(
        clientId = OltreOAuth.GOOGLE_DESKTOP_CLIENT_ID,
        clientSecret = clientSecret,
        code = code,
        verifier = verifier,
        redirectUri = redirectUri,
    )
    val answer = try {
        (URI(OltreOAuth.GOOGLE_TOKEN).toURL().openConnection() as HttpURLConnection).run {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            outputStream.use { it.write(body.toByteArray()) }
            // The error stream on a `4xx`, which is where the token endpoint puts its JSON. Read
            // either way, because `readIdToken` is what decides whether there is a token in it.
            val stream = if (responseCode in HTTP_OK..HTTP_LAST_SUCCESS) inputStream else errorStream
            stream?.use { it.readBytes().decodeToString() }.orEmpty()
        }
    } catch (_: IOException) {
        // A refused connection, a captive portal, a DNS failure. Nobody answered, which is a
        // different sentence from *Google said no* and is the reason `SignInAttempt` splits them.
        return SignInAttempt.Unreachable
    }

    val idToken = readIdToken(answer) ?: return SignInAttempt.Unreachable
    return SignInAttempt.Signed(IdToken(idToken), dev.fardavide.oltre.protocol.SignInNonce(nonce))
}

// `false` rather than a throw when there is no browser to open — a headless machine is a machine
// this build cannot sign in on, which is a thing the gate can say.
private fun openBrowser(url: String): Boolean = try {
    val desktop = Desktop.getDesktop().takeIf { Desktop.isDesktopSupported() }
    desktop?.takeIf { it.isSupported(Desktop.Action.BROWSE) }?.browse(URI(url)) != null
} catch (_: IOException) {
    false
} catch (_: UnsupportedOperationException) {
    false
}

// **Read every time rather than once**, so a dev loop that sources the environment and restarts picks
// it up without a rebuild — and so that this file holds no credential even in a field.
private fun desktopClientSecret(): String? =
    System.getenv("OLTRE_GOOGLE_DESKTOP_CLIENT_SECRET")?.takeIf { it.isNotBlank() }

// The page the browser is left on. Plain, and in the app's own voice: a blank tab reads like the flow
// failed at the last step.
private const val CLOSE_PAGE =
    "<!doctype html><meta charset=utf-8><title>Oltre</title>" +
        "<body style=\"background:#05070D;color:#E9EDF5;font:14px ui-monospace,monospace;" +
        "display:flex;align-items:center;justify-content:center;height:100vh;margin:0\">" +
        "You are signed in. Go back to Oltre.</body>"

private const val HTTP_OK = 200
private const val HTTP_LAST_SUCCESS = 299
private val BROWSER_WINDOW = 5.minutes
