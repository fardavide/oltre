package dev.fardavide.oltre.server

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jwt.JWTClaimsSet
import dev.fardavide.oltre.protocol.IdToken
import dev.fardavide.oltre.protocol.SignInNonce
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

// **The seven cases `#110`'s Done-means names, and they are the whole reason this slice has a
// verifier of its own rather than a plugin.** Valid, expired, wrong audience, wrong issuer, tampered
// signature, unknown key id, rotated key — each one is a check that fails silently if it is missing,
// and "silently" is the operative word: a token that is not checked verifies beautifully.
//
// Everything here runs against a keypair generated in this process and served by a handwritten fake.
// Nothing reaches a real issuer.
class IdTokenVerifierTest {

    private val clock = MovableClock(TEST_NOW)
    private val source = FakeJwksSource(jwksOf(providerKey))

    private fun verifier(
        audiences: Set<String> = setOf(WEB_CLIENT, DESKTOP_CLIENT),
        keys: JwksKeys = JwksKeys(source, clock),
    ) = IdTokenVerifier(
        specs = mapOf(IdentityProvider.GOOGLE to testSpec(audiences = audiences)),
        keys = keys,
        clock = clock,
    )

    private suspend fun verify(token: IdToken, nonce: SignInNonce = TEST_NONCE, audiences: Set<String>? = null) =
        (if (audiences == null) verifier() else verifier(audiences))
            .verify(IdentityProvider.GOOGLE, token, nonce)

    // ── 1. Valid ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a token signed by a published key for a configured audience names its subject`() = runTest {
        // The case that proves the other six refuse for the right reason rather than refusing
        // everything. What comes back is the pair the `players` table is keyed on — the provider and
        // the subject, and **never the email** (`#106` §4).
        val verdict = verify(providerKey.sign(idTokenClaims()))

        assertEquals(
            TokenVerdict.Trusted(ProviderIdentity(ProviderName("google"), TEST_SUBJECT)),
            verdict,
        )
    }

    // ── 2. Expired ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a token whose expiry has passed is refused`() = runTest {
        val token = providerKey.sign(idTokenClaims(expiresAt = TEST_NOW + 10.minutes))

        clock.advanceBy(11.minutes)

        assertIs<TokenVerdict.Refused>(verify(token))
    }

    @Test
    fun `a token is refused the instant it expires rather than a moment after`() = runTest {
        // The boundary, because an off-by-one here is a window that only exists at the instant
        // nobody tests. `expiresAt` is exclusive: at exactly that instant the token is done.
        val token = providerKey.sign(idTokenClaims(expiresAt = TEST_NOW + 10.minutes))

        clock.advanceBy(10.minutes)

        assertIs<TokenVerdict.Refused>(verify(token))
    }

    @Test
    fun `a token that never expires is refused rather than trusted forever`() = runTest {
        assertIs<TokenVerdict.Refused>(verify(providerKey.sign(idTokenClaims(expiresAt = null))))
    }

    // ── 3. Wrong audience ─────────────────────────────────────────────────────────────────────

    @Test
    fun `a token minted for somebody else's client is refused`() = runTest {
        val token = providerKey.sign(idTokenClaims(audience = "somebody.else.example.com"))

        assertIs<TokenVerdict.Refused>(verify(token))
    }

    // **The trap, and it is a pair of tests because one of them alone proves nothing.**
    //
    // The Web client is the audience for both phones and the Desktop client is a second one — and
    // desktop is the only build the behaviour and screenshot suites run on. A single-audience check
    // passes every other test in this file and then refuses the dev loop, which is exactly the kind
    // of failure that is found by installing rather than by testing.
    @Test
    fun `the desktop client is accepted alongside the web one`() = runTest {
        val token = providerKey.sign(idTokenClaims(audience = DESKTOP_CLIENT))

        assertIs<TokenVerdict.Trusted>(verify(token))
    }

    @Test
    fun `a server configured with only the web client refuses the desktop one`() = runTest {
        // Without this the test above would pass against a verifier that ignored the audience
        // entirely, which is a worse bug than the one it is guarding.
        val token = providerKey.sign(idTokenClaims(audience = DESKTOP_CLIENT))

        assertIs<TokenVerdict.Refused>(verify(token, audiences = setOf(WEB_CLIENT)))
    }

    // ── 4. Wrong issuer ───────────────────────────────────────────────────────────────────────

    @Test
    fun `a token from an issuer this server does not know is refused`() = runTest {
        val token = providerKey.sign(idTokenClaims(issuer = "https://not-the-issuer.example"))

        assertIs<TokenVerdict.Refused>(verify(token))
    }

    @Test
    fun `google's two spellings of its own issuer are both accepted`() = runTest {
        // Google's discovery document says `https://accounts.google.com` and its tokens have carried
        // the bare host for years. A verifier that pinned one would work until the day it did not.
        val spec = testSpec().copy(issuers = IdentityProvider.GOOGLE.issuers)
        val verifier = IdTokenVerifier(mapOf(IdentityProvider.GOOGLE to spec), JwksKeys(source, clock), clock)

        IdentityProvider.GOOGLE.issuers.forEach { issuer ->
            val token = providerKey.sign(idTokenClaims(issuer = issuer))
            assertIs<TokenVerdict.Trusted>(verifier.verify(IdentityProvider.GOOGLE, token, TEST_NONCE), issuer)
        }
    }

    // ── 5. Tampered signature ─────────────────────────────────────────────────────────────────

    @Test
    fun `a token whose payload was rewritten under its own signature is refused`() = runTest {
        // The actual attack: keep the signature and become somebody else. A verifier that read the
        // claims and forgot to check the signature would let this through and there would be nothing
        // anywhere to say so.
        val genuine = providerKey.sign(idTokenClaims())
        val tampered = genuine.withPayloadRewrittenTo(idTokenClaims(subject = "somebody-else"))

        assertIs<TokenVerdict.Refused>(verify(tampered))
    }

    // **A signature that is not a signature**, which is a different input from the two around it: not
    // somebody else's and not a rewritten payload, but bytes of the wrong length entirely. An RSA
    // signature is exactly as long as the modulus, so the JCA refuses this before any arithmetic
    // happens — and what must not follow is the failure leaving the verifier, because a request that
    // dies mid-verification is a bare 500 with no `ApiError` in it, which `#112`'s client reads as
    // `Unreachable` and retries forever rather than as a server having said something.
    //
    // Nimbus turns that particular refusal into `false` rather than an exception, so this reaches the
    // same arm as the impostor below. **The `JOSEException` arm one line above it stays uncovered and
    // that is honest**: `Signature.initVerify` does not fail for a well-formed RSA public key, so
    // there is no token that provokes it and no test worth writing that pretends there is.
    @Test
    fun `a token whose signature is not a signature is refused rather than raising`() = runTest {
        val mangled = providerKey.sign(idTokenClaims()).withSignatureTruncated()

        assertIs<TokenVerdict.Refused>(verify(mangled))
    }

    @Test
    fun `a token signed by a key the provider does not publish is refused`() = runTest {
        // The other half of the same property, and the one a `kid` lookup alone would miss: the key
        // id is one the provider *does* publish, and the signature is somebody else's.
        val impostor = ProviderKey(providerKey.keyId)

        assertIs<TokenVerdict.Refused>(verify(impostor.sign(idTokenClaims())))
    }

    @Test
    fun `a token that is not a signed token at all is refused`() = runTest {
        assertIs<TokenVerdict.Refused>(verify(IdToken("not.a.jwt")))
    }

    @Test
    fun `a genuine signature over something that is not a claims set is refused`() = runTest {
        // A JWS carries an arbitrary payload, so "the signature is the provider's" and "the body is
        // a set of claims" are two separate facts — and the second can fail on its own, with a
        // perfectly valid signature over it. Reading it must not take the request down.
        assertIs<TokenVerdict.Refused>(verify(providerKey.signPayload("this is not a claims set")))
    }

    // ── The algorithm, which is not on the list and would be the worst one to miss ─────────────

    @Test
    fun `a token signed with anything but RS256 is refused before its key is even looked up`() = runTest {
        // The oldest hole in JWT, in two shapes: a header saying `none`, and one saying `HS256` so
        // that a **public** key gets used as a shared secret. Nimbus verifies whatever it is handed
        // a verifier for, so the refusal has to be this server's.
        val token = providerKey.sign(idTokenClaims(), algorithm = JWSAlgorithm.RS512)

        assertIs<TokenVerdict.Refused>(verify(token))
        assertEquals(0, source.fetches, "the key set was fetched for a token that was never eligible")
    }

    // ── 6. Unknown key id ─────────────────────────────────────────────────────────────────────

    @Test
    fun `a token naming a key id nobody publishes is refused`() = runTest {
        val token = providerKey.sign(idTokenClaims(), keyId = "a-key-that-was-never-published")

        assertIs<TokenVerdict.Refused>(verify(token))
    }

    @Test
    fun `a token with no key id at all is refused`() = runTest {
        // Both shapes: a header that carries the field blank, and one that omits it outright.
        // Neither says which key to look up, and guessing at the only one published would be a
        // verifier that stops checking the moment a provider publishes two.
        assertIs<TokenVerdict.Refused>(verify(providerKey.sign(idTokenClaims(), keyId = " ")))
        assertIs<TokenVerdict.Refused>(verify(providerKey.signWithoutKeyId(idTokenClaims())))
    }

    // ── 7. Rotated key ────────────────────────────────────────────────────────────────────────

    @Test
    fun `a token signed with a key published after the last fetch is trusted once the set is refreshed`() = runTest {
        // **What a rotation looks like from here.** The cache holds the old set; a token arrives
        // naming a key id that is not in it; the answer is to go and ask again rather than to refuse
        // everybody until a deploy. `JwksKeys` owns that decision and `JwksKeysTest` pins the floor
        // under it; this is the property from the outside.
        val keys = JwksKeys(source, clock)
        val verifier = verifier(keys = keys)
        assertIs<TokenVerdict.Trusted>(verifier.verify(IdentityProvider.GOOGLE, providerKey.sign(idTokenClaims()), TEST_NONCE))

        source.nowServes(jwksOf(providerKey, rotatedKey))
        clock.advanceBy(2.minutes)
        val token = rotatedKey.sign(idTokenClaims(expiresAt = TEST_NOW + 1.hours))

        assertIs<TokenVerdict.Trusted>(verifier.verify(IdentityProvider.GOOGLE, token, TEST_NONCE))
        assertEquals(2, source.fetches, "the rotation was answered without asking the provider again")
    }

    // ── The nonce, which is the replay defence and fails nothing when it is missing ────────────

    @Test
    fun `a token carrying a different nonce is refused`() = runTest {
        // A token captured from somebody else's sign-in verifies perfectly. What it does not carry
        // is the nonce this sign-in drew a moment ago.
        val token = providerKey.sign(idTokenClaims(nonce = "somebody-else's-sign-in"))

        assertIs<TokenVerdict.Refused>(verify(token))
    }

    @Test
    fun `a token carrying no nonce is refused rather than waved through`() = runTest {
        // Apple only includes the claim when it was asked to, so "absent" is a shape a real token
        // takes — and treating absent as "nothing to compare" would remove the defence for anybody
        // who simply left the nonce out.
        assertIs<TokenVerdict.Refused>(verify(providerKey.sign(idTokenClaims(nonce = null))))
    }

    @Test
    fun `a token whose nonce is not even a string is refused rather than raised over`() = runTest {
        // Every claim in a token is something the sender chose the *type* of as well as the value,
        // so reading one has to be able to fail without taking the request with it.
        val token = providerKey.sign(JWTClaimsSet.Builder(idTokenClaims()).claim("nonce", 42).build())

        assertIs<TokenVerdict.Refused>(verify(token))
    }

    // ── The rest of the shape ─────────────────────────────────────────────────────────────────

    @Test
    fun `a token with no subject is refused because nothing in it says who it is`() = runTest {
        // Absent and blank, for the key id's reason: a blank subject would key a player row nobody
        // could ever resolve to again.
        assertIs<TokenVerdict.Refused>(verify(providerKey.sign(idTokenClaims(subject = null))))
        assertIs<TokenVerdict.Refused>(verify(providerKey.sign(idTokenClaims(subject = "   "))))
    }

    @Test
    fun `a provider this server has no spec for is refused rather than trusted by default`() = runTest {
        // `specs` holds Google only here. Apple asking is a misconfiguration, and the safe answer to
        // a misconfiguration is no.
        val verdict = verifier().verify(IdentityProvider.APPLE, providerKey.sign(idTokenClaims()), TEST_NONCE)

        assertIs<TokenVerdict.Refused>(verdict)
    }

    private companion object {

        // Generated once for the class. A 2048-bit keypair takes long enough that one per test would
        // be the slowest file in the module by an order of magnitude.
        val providerKey = ProviderKey("the-published-key")
        val rotatedKey = ProviderKey("the-key-published-this-morning")
    }
}
