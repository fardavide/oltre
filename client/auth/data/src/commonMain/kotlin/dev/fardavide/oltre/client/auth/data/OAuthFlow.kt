package dev.fardavide.oltre.client.auth.data

import dev.fardavide.oltre.protocol.SignInNonce

// **One sign-in attempt's four random values**, drawn together because they are drawn once and used
// in four different places, and a flow that redrew any of them mid-way would fail in a way nobody
// could reproduce.
//
// The bytes come in as a lambda rather than being read here, which is what makes every property below
// executable by a test: the source is `secureRandomBytes` in the app and a fixed array in
// `OAuthFlowTest`.
class SignInSecrets(random: (Int) -> ByteArray) {

    // **PKCE, RFC 7636.** The verifier never leaves the device until the exchange; the challenge is
    // what the browser carries. Without it, a code intercepted by another app on the same phone is a
    // sign-in somebody else completes.
    val verifier: String = random(VERIFIER_BYTES).base64Url()

    val challenge: String = sha256(verifier.encodeToByteArray()).base64Url()

    // **What the redirect has to carry back**, and the one check that makes a redirect arriving from
    // anywhere else useless. A browser flow's return leg is a URL the operating system hands to
    // whoever registered the scheme; this is how the app knows the one it got is the one it started.
    val state: String = random(STATE_BYTES).base64Url()

    // **What stops a stolen ID token being replayed**, and it is *two* values rather than one because
    // the providers disagree about which of them goes where.
    //
    // Google is handed the raw nonce and puts it in the claim, so the raw one is what the server is
    // told. Apple's native flow is handed the SHA-256 — as lower-case hex, which is Apple's own form —
    // and puts *that* in the claim, so the hash is what the server is told. `SignInNonce`'s KDoc says
    // exactly this and it is the reason the type is *"the value the client expects to find in the
    // token"* rather than *"the value the client drew"*.
    val raw: String = random(NONCE_BYTES).base64Url()

    val hashed: String = sha256(raw.encodeToByteArray()).hex()

    // Which of the two a provider is told about, said once here so that no platform has to remember.
    fun nonceFor(shape: NonceShape): SignInNonce =
        SignInNonce(if (shape == NonceShape.HASHED) hashed else raw)

    private companion object {

        // RFC 7636 allows 43–128 characters; 32 bytes base64url is 43, which is the floor and is
        // 256 bits of entropy. More would be more characters in a URL and not more security.
        const val VERIFIER_BYTES = 32
        const val STATE_BYTES = 16
        const val NONCE_BYTES = 32
    }
}

// Which form of the nonce a provider puts in the token it mints. Two constants rather than a boolean
// on the call, because *"true"* at a call site would not say which way round it was.
enum class NonceShape {

    // Google, and every plain OpenID Connect flow: the value handed over is the value in the claim.
    RAW,

    // Apple's native flow, which hashes what it is given.
    HASHED,
}

// **The authorize URL, built rather than templated**, and the reason it is a function on data instead
// of string concatenation at three call sites: the query has seven parameters, every one of them has
// to be percent-encoded the same way on three platforms, and a redirect URI encoded one way on
// Android and another on desktop is a mismatch the app cannot see and the provider answers with a
// blank page.
//
// Ordering is fixed rather than incidental so that the test can assert the whole string. Nothing in
// OAuth requires it; a test that had to parse the query back out to check it would be re-implementing
// the thing it is checking.
data class AuthorizeRequest(
    val endpoint: String,
    val clientId: String,
    val redirectUri: String,
    val scope: String,
    val secrets: SignInSecrets,
) {

    fun url(): String = buildString {
        append(endpoint)
        append('?')
        listOf(
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "response_type" to "code",
            "scope" to scope,
            "state" to secrets.state,
            "nonce" to secrets.raw,
            "code_challenge" to secrets.challenge,
            "code_challenge_method" to "S256",
        ).forEachIndexed { index, (name, value) ->
            if (index > 0) append('&')
            append(name)
            append('=')
            append(value.urlEncoded())
        }
    }
}

// **What came back on the return leg**, and it is the same three shapes `SignInAttempt` has because
// it becomes one: a code to exchange, a refusal the provider named, or something that cannot be read.
sealed interface RedirectResult {

    data class Code(val value: String) : RedirectResult

    // `error=access_denied` is what both providers send when the player backs out, and it is also
    // what they send when consent is declined. One member, for `SignInAttempt.Refused`'s reason.
    data object Refused : RedirectResult

    // **A redirect that is not this attempt's.** Either the `state` does not match or there is no
    // usable parameter at all. Its own member rather than folded into `Refused` because the sentence
    // the gate needs is different: nothing about the player's choice happened here.
    data object Unusable : RedirectResult
}

// Reading the return leg. **The `state` check is the load-bearing line and it comes first**: a
// redirect whose state does not match this attempt is not this attempt, and treating its code as ours
// is the whole of what CSRF against an OAuth client looks like.
//
// Parsed by hand rather than through a URL type, because the three platforms hand this back as three
// different things — a `String` on desktop, an `android.net.Uri`, an `NSURL` — and the one thing they
// all agree on is that it is characters. What is here is the query, the fragment, or both.
fun readRedirect(redirect: String, expected: SignInSecrets): RedirectResult {
    val query = redirect.substringAfter('?', missingDelimiterValue = "").substringBefore('#')
    val fragment = redirect.substringAfter('#', missingDelimiterValue = "")
    val parameters = (query.split('&') + fragment.split('&'))
        .filter { it.isNotEmpty() }
        .mapNotNull { pair ->
            val name = pair.substringBefore('=')
            val value = pair.substringAfter('=', missingDelimiterValue = "")
            if (name.isEmpty()) null else name to value.percentDecoded()
        }
        .toMap()

    if (parameters["state"] != expected.state) return RedirectResult.Unusable
    parameters["error"]?.let { return RedirectResult.Refused }
    val code = parameters["code"]
    return if (code.isNullOrEmpty()) RedirectResult.Unusable else RedirectResult.Code(code)
}

// The mirror of `urlEncoded`, and the same argument for writing it: it has to agree with what the two
// providers actually send, and `+` for a space is the one place every platform's decoder differs. A
// code is unreserved characters by construction, so this exists for the values around it.
private fun String.percentDecoded(): String {
    if ('%' !in this && '+' !in this) return this
    val bytes = ArrayList<Byte>(length)
    var index = 0
    while (index < length) {
        val char = this[index]
        when {
            char == '+' -> {
                bytes.add(' '.code.toByte())
                index++
            }

            char == '%' && index + 2 < length -> {
                val decoded = substring(index + 1, index + 3).toIntOrNull(radix = 16)
                if (decoded == null) {
                    bytes.add(char.code.toByte())
                    index++
                } else {
                    bytes.add(decoded.toByte())
                    index += 3
                }
            }

            else -> {
                char.toString().encodeToByteArray().forEach(bytes::add)
                index++
            }
        }
    }
    return bytes.toByteArray().decodeToString()
}

// **Cryptographically strong bytes**, and the one thing in this file that has to be a platform's.
// `kotlin.random.Random` is a linear generator seeded from the clock: a nonce drawn from it is
// predictable to anybody who knows roughly when the app launched, which is exactly the attacker a
// nonce exists to stop.
expect fun secureRandomBytes(count: Int): ByteArray
