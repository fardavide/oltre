package dev.fardavide.oltre.server

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// **When this server goes back to the provider, which is the whole of what `JwksKeys` decides.** The
// fetching itself is `JwksHttp.kt` and has no decision in it — the split `#108` made on the routes
// and `#109` made on the store, so that the policy is judged by a plain unit test and the socket by
// an integration one.
//
// `FakeJwksSource.fetches` is what every test here is actually asserting on: "did it ask again" is
// the behaviour, and a fake that could not be asked would leave it untested.
class JwksKeysTest {

    private val clock = MovableClock(TEST_NOW)
    private val source = FakeJwksSource(jwksOf(published))

    private fun keys() = JwksKeys(source, clock)

    @Test
    fun `the first ask fetches the key set and finds the key in it`() = runTest {
        val key = keys().keyFor(TEST_JWKS_URI, published.keyId)

        assertNotNull(key)
        assertEquals(1, source.fetches)
    }

    @Test
    fun `a second ask inside the freshness window is answered from what was already fetched`() = runTest {
        // The common case by a wide margin. Both providers publish a new key well before they sign
        // anything with it, so a cache this age is essentially never wrong — and asking Apple on
        // every sign-in is how a server earns a rate limit.
        val keys = keys()
        keys.keyFor(TEST_JWKS_URI, published.keyId)

        clock.advanceBy(59.minutes)
        assertNotNull(keys.keyFor(TEST_JWKS_URI, published.keyId))

        assertEquals(1, source.fetches)
    }

    @Test
    fun `a key set past its freshness window is fetched again`() = runTest {
        val keys = keys()
        keys.keyFor(TEST_JWKS_URI, published.keyId)

        clock.advanceBy(1.hours)
        keys.keyFor(TEST_JWKS_URI, published.keyId)

        assertEquals(2, source.fetches)
    }

    @Test
    fun `a key id that is not in the held set sends this server back to the provider`() = runTest {
        // **What makes a rotation a blip rather than an outage.** A TTL alone would refuse every
        // token signed with the new key until the hour was up, which on a provider that rotates
        // without warning is a sign-in outage nobody can act on.
        val keys = keys()
        keys.keyFor(TEST_JWKS_URI, published.keyId)
        source.nowServes(jwksOf(published, rotated))

        clock.advanceBy(2.minutes)
        val key = keys.keyFor(TEST_JWKS_URI, rotated.keyId)

        assertNotNull(key)
        assertEquals(2, source.fetches)
    }

    @Test
    fun `a stream of key ids nobody publishes cannot make this server hammer the provider`() = runTest {
        // The floor, and it is the reason the rule above is not simply "refetch on a miss". A client
        // sending invented key ids would otherwise be a client that makes this server call Apple
        // once per request, and Apple answers that with a rate limit that takes sign-in down for
        // everybody at once.
        val keys = keys()
        keys.keyFor(TEST_JWKS_URI, published.keyId)

        repeat(20) { attempt ->
            clock.advanceBy(1.seconds)
            assertNull(keys.keyFor(TEST_JWKS_URI, "invented-$attempt"))
        }

        assertEquals(1, source.fetches)
    }

    @Test
    fun `a key id that is still not there after the refetch is answered with nothing`() = runTest {
        val keys = keys()
        keys.keyFor(TEST_JWKS_URI, published.keyId)

        clock.advanceBy(2.minutes)

        assertNull(keys.keyFor(TEST_JWKS_URI, "never-published"))
        assertEquals(2, source.fetches)
    }

    @Test
    fun `two providers are cached separately rather than overwriting each other`() = runTest {
        // The cache is keyed on the URL because Apple and Google are two documents, and a single
        // held set would mean each sign-in evicting the other's keys — a fetch per request with a
        // cache in front of it.
        val keys = keys()

        keys.keyFor(TEST_JWKS_URI, published.keyId)
        keys.keyFor("https://other.example/keys", published.keyId)
        keys.keyFor(TEST_JWKS_URI, published.keyId)

        assertEquals(2, source.fetches)
    }

    @Test
    fun `a document that is not a key set raises rather than reading as no keys at all`() = runTest {
        // The distinction is the whole point. "No keys" would surface to every player as *"your
        // token is not valid"* and send them all to a sign-in screen that cannot help them; raising
        // reaches `ApiError.Internal`, which is a 500 the client retries and an operator can look at.
        val keys = JwksKeys(FakeJwksSource("<html>502 Bad Gateway</html>"), clock)

        assertFailsWith<Exception> { keys.keyFor(TEST_JWKS_URI, published.keyId) }
    }

    @Test
    fun `a key that does not say what it is for is taken as a signing key`() = runTest {
        // `use` is optional in JWKS and Apple's own set has left it out. Read as "not a signing key"
        // that would refuse every Apple token at once, so absent has to mean usable.
        val silent = JWKSet(listOf(RSAKey.Builder(published.published).keyUse(null).build())).toString()

        assertNotNull(JwksKeys(FakeJwksSource(silent), clock).keyFor(TEST_JWKS_URI, published.keyId))
    }

    @Test
    fun `a key with no id is not a key anything can name`() = runTest {
        // A token points at a key by `kid`, so a published key without one cannot be selected by
        // anybody. Keeping it would mean a set whose size and whose usable size differ silently —
        // and both shapes have to go, the field omitted and the field present but empty.
        val anonymous = JWKSet(listOf(RSAKey.Builder(published.published).keyID(null).build())).toString()
        val blank = JWKSet(listOf(RSAKey.Builder(published.published).keyID("  ").build())).toString()

        assertNull(JwksKeys(FakeJwksSource(anonymous), clock).keyFor(TEST_JWKS_URI, published.keyId))
        assertNull(JwksKeys(FakeJwksSource(blank), clock).keyFor(TEST_JWKS_URI, published.keyId))
    }

    @Test
    fun `a key published for encryption cannot verify a signature`() = runTest {
        // A `use: enc` key in a set is not a signing key, and letting one through would mean a key
        // id collision deciding which of the two answered.
        val encryption = JWKSet(listOf(RSAKey.Builder(published.published).keyUse(KeyUse.ENCRYPTION).build()))
        val keys = JwksKeys(FakeJwksSource(encryption.toString()), clock)

        assertNull(keys.keyFor(TEST_JWKS_URI, published.keyId))
    }

    private companion object {

        val published = ProviderKey("published")
        val rotated = ProviderKey("rotated")
    }
}
