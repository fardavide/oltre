package dev.fardavide.oltre.server

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import dev.fardavide.oltre.protocol.IdToken
import dev.fardavide.oltre.protocol.SignInNonce
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.Date
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// **A provider, generated here and reaching nothing.** `#110`'s Done-means asks for the seven
// verification cases to be driven by a **handwritten fake JWKS endpoint** over a locally generated
// keypair — fakes rather than a mocking framework, per the repository's convention — and this is
// that keypair. Nothing in this slice reaches Apple or Google, and nothing in CI ever should: a
// suite that talked to a real issuer would be a suite that fails when somebody else deploys.

// What a test says a provider is. Written down rather than borrowed from `IdentityProvider`, because
// a test that used the real issuer and the real key-set URL would be one line away from actually
// asking for them.
internal const val TEST_ISSUER = "https://issuer.example"
internal const val TEST_JWKS_URI = "https://issuer.example/keys"

// **Two audiences and this is the pair the whole trap is about.** There is one OAuth client per
// platform: the Web client answers for both phones, and the desktop dev loop has one of its own —
// which is the only build the behaviour and screenshot suites run on. A verifier that accepted one
// of these passes every other test in the file.
internal const val WEB_CLIENT = "web.apps.example.com"
internal const val DESKTOP_CLIENT = "desktop.apps.example.com"

internal val TEST_NONCE: SignInNonce = SignInNonce("drawn-by-this-sign-in")

internal const val TEST_SUBJECT = "provider-subject-0001"

// Long enough that HMAC will take it, and obviously not a real one.
internal val TEST_SIGNING_KEY: SessionSigningKey =
    SessionSigningKey("a-test-signing-key-of-at-least-32-bytes")

// 2048 bits: the shortest RSA key Nimbus will verify with, and generating a longer one would cost
// every test in this file a second for nothing.
private const val KEY_BITS = 2048

private val generator: KeyPairGenerator = KeyPairGenerator.getInstance("RSA").apply { initialize(KEY_BITS) }

// One keypair pretending to be one of a provider's signing keys. **Generating one is slow enough to
// notice**, so a test class holds its keys in a companion and reuses them.
internal class ProviderKey(val keyId: String, pair: KeyPair = generator.generateKeyPair()) {

    private val signer = RSASSASigner(pair.private as RSAPrivateKey)

    // The public half, which is all a key set ever publishes. `use` and `alg` are spelled out
    // because the real ones spell them out, and `rsaKeysFrom` reads the first of the two.
    val published: RSAKey = RSAKey.Builder(pair.public as RSAPublicKey)
        .keyID(keyId)
        .keyUse(KeyUse.SIGNATURE)
        .algorithm(JWSAlgorithm.RS256)
        .build()

    // `keyId` is a parameter rather than always this key's, so a test can sign with a key that is
    // real and *name* one that is not — which is the "unknown key id" case, and is what a rotation
    // looks like from the server's side before it refetches.
    fun sign(claims: JWTClaimsSet, keyId: String = this.keyId, algorithm: JWSAlgorithm = JWSAlgorithm.RS256): IdToken {
        val header = JWSHeader.Builder(algorithm).keyID(keyId).build()
        return IdToken(SignedJWT(header, claims).apply { sign(signer) }.serialize())
    }

    // A header with the field omitted rather than blank, which is a different shape on the wire and
    // a different arm in the verifier.
    fun signWithoutKeyId(claims: JWTClaimsSet): IdToken =
        IdToken(SignedJWT(JWSHeader(JWSAlgorithm.RS256), claims).apply { sign(signer) }.serialize())

    // **A signature over something that is not a claims set at all.** It is a real shape rather than
    // a contrivance: a JWS carries an arbitrary payload, so "the signature is genuine" and "the body
    // is a set of claims" are two separate facts and the second can fail on its own. What must not
    // happen is that reading it takes the request with it.
    fun signPayload(payload: String, keyId: String = this.keyId): IdToken {
        val header = JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).build()
        return IdToken(JWSObject(header, Payload(payload)).apply { sign(signer) }.serialize())
    }
}

// The document a provider serves at its key-set URL, with only the public halves in it.
internal fun jwksOf(vararg keys: ProviderKey): String = JWKSet(keys.map { it.published }).toString()

// **The handwritten fake, and it is a fake rather than a server.** A JWKS served in-process is not a
// boundary crossing, so every test standing on this is a plain `…Test` — see the `test-coverage`
// skill. What crosses a real socket is `JwksHttpIntegrationTest`, which is the other half and is
// named for it.
//
// `fetches` is what makes the cache testable at all: "did it ask again" is the entire behaviour of
// `JwksKeys`, and a fake that could not be asked would leave it untested.
internal class FakeJwksSource(private var document: String) : JwksSource {

    var fetches: Int = 0
        private set

    override suspend fun documentAt(uri: String): String {
        fetches++
        return document
    }

    // The provider rotating its keys, which from here is just a different document at the same URL.
    fun nowServes(document: String) {
        this.document = document
    }
}

// The claims a well-formed ID token carries. Every parameter defaults to the valid value, so each
// test names exactly the one thing it is making wrong — which is what makes the seven cases readable
// as a list rather than as seven near-identical fixtures.
internal fun idTokenClaims(
    issuer: String = TEST_ISSUER,
    audience: String = WEB_CLIENT,
    subject: String? = TEST_SUBJECT,
    nonce: String? = TEST_NONCE.value,
    expiresAt: Instant? = TEST_NOW + 10.minutes,
): JWTClaimsSet = JWTClaimsSet.Builder()
    .issuer(issuer)
    .audience(audience)
    .subject(subject)
    .claim("nonce", nonce)
    .issueTime(Date(TEST_NOW.toEpochMilliseconds()))
    .apply { expiresAt?.let { expirationTime(Date(it.toEpochMilliseconds())) } }
    .build()

// A spec for a generated provider. The audiences default to **both** clients, so a test that wants
// to prove the desktop one is accepted asks for the default and a test that wants to prove a
// single-audience server refuses it names one.
internal fun testSpec(
    provider: IdentityProvider = IdentityProvider.GOOGLE,
    audiences: Set<String> = setOf(WEB_CLIENT, DESKTOP_CLIENT),
): ProviderSpec = ProviderSpec(
    provider = provider,
    issuers = setOf(TEST_ISSUER),
    audiences = audiences,
    jwksUri = TEST_JWKS_URI,
)

// **A genuinely tampered token**, which is the attack rather than a corrupted string: the payload is
// rewritten and the *original* signature is kept, exactly as somebody who wanted to be a different
// subject would do it. Flipping a character in the signature segment would test base64 decoding
// instead, and would pass against a verifier that never checked the signature at all.
internal fun IdToken.withPayloadRewrittenTo(claims: JWTClaimsSet): IdToken {
    val (header, _, signature) = value.split('.')
    val rewritten = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(claims.toString().encodeToByteArray())
    return IdToken("$header.$rewritten.$signature")
}
