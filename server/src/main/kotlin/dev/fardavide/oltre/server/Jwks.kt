package dev.fardavide.oltre.server

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.interfaces.RSAPublicKey
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// **The provider's public keys, and every decision about when to go and get them again.** The one
// thing that is *not* here is the getting: `JwksHttp.kt` holds the socket and decides nothing, which
// is the split `#108` made on the routes and `#109` made on the store — *a decision belongs where
// the kind of test that judges it can reach it.* Nimbus ships a `RemoteJWKSet` that would have been
// both halves in one object, and taking it would have put this whole file out of a unit test's
// reach.

// Where a JWKS document comes from. A `fun interface` because there is exactly one thing to ask, and
// a port rather than a class because the tests hand back a document they generated a keypair for —
// per the repository's fakes-not-mocks convention, and per `#110`: nothing in this slice reaches a
// real issuer, and nothing in CI ever should.
internal fun interface JwksSource {

    suspend fun documentAt(uri: String): String
}

// **How long a fetched key set is believed without asking again.** An hour, and the number is a
// judgement about the two providers rather than a measurement: both publish a new key well before
// they sign anything with it, so a cache this age is essentially never wrong — and at two to four
// requests per player per day, a shorter one would mean fetching Apple's key set more often than
// anyone signs in.
private val FRESH_FOR: Duration = 1.hours

// **The floor under a refetch provoked by a key id nobody has heard of.** Without one, a client
// sending random `kid`s is a client that makes this server call Apple once per request — and Apple
// answers that with a rate limit that takes sign-in down for everybody. A minute is short enough
// that a genuine rotation costs at most one minute of refusals and long enough that a storm costs
// sixty fetches an hour.
private val REFETCH_NO_OFTENER_THAN: Duration = 1.minutes

// A key set as it was when it was fetched. The instant travels with it because every decision below
// is about age, and a set without one cannot be asked how old it is.
private data class HeldKeys(val keys: Map<String, RSAPublicKey>, val fetchedAt: Instant)

// **The cache, and the three reasons to go back to the provider.** Stale, empty, or asked for a key
// id it does not hold — the third is what makes a rotation recoverable without a deploy, and it is
// the one a TTL alone would not give.
//
// The clock is a parameter for the reason every seam in this repository is one: a class that read
// `Clock.System` could not be asked whether it refetched, only whether it eventually did.
internal class JwksKeys(
    private val source: JwksSource,
    private val clock: Clock,
    private val freshFor: Duration = FRESH_FOR,
    private val refetchNoOftenerThan: Duration = REFETCH_NO_OFTENER_THAN,
) {

    // One provider's fetch must not be able to start a second one for the same URI, and Ktor serves
    // requests concurrently — so two sign-ins arriving together on a cold instance would otherwise
    // both go to Apple. The lock is coarse on purpose: contention here is measured in fetches per
    // hour, and one map guarded once is easier to be right about than a map of locks.
    private val lock = Mutex()
    private val held = mutableMapOf<String, HeldKeys>()

    // Null is **"this key id is not one of theirs"**, which the caller turns into a refusal rather
    // than into a failure: an ID token naming a key the provider does not publish is a token this
    // server has no business trusting, and saying so is the answer. A document that will not parse
    // is a different thing entirely and throws — see `rsaKeysFrom`.
    suspend fun keyFor(uri: String, keyId: String): RSAPublicKey? = lock.withLock {
        val now = clock.now()
        val current = held[uri]
            ?.takeIf { now - it.fetchedAt < freshFor }
            ?: refetch(uri, now)

        current.keys[keyId]?.let { return@withLock it }

        // **Unknown key id: the provider may have rotated since this set was fetched.** Asking again
        // is what turns a rotation into a blip instead of an outage, and the floor is what stops a
        // stream of invented key ids turning it into a denial of service against the provider.
        if (now - current.fetchedAt < refetchNoOftenerThan) return@withLock null
        refetch(uri, now).keys[keyId]
    }

    private suspend fun refetch(uri: String, now: Instant): HeldKeys =
        HeldKeys(rsaKeysFrom(source.documentAt(uri)), fetchedAt = now).also { held[uri] = it }
}

// **What a JWKS document means**, and it throws rather than shrugging. A provider serving something
// that is not a key set is this server being unable to verify anybody — an outage on our side of the
// wire, which reaches `ApiError.Internal` and a 500 an operator can go and look at. Answering
// "no keys" instead would surface as *"your token is not valid"* to every player at once, which is
// the one sentence that would send them all to a sign-in screen that cannot help them.
//
// **Signature keys only.** A key published for encryption cannot verify anything, and letting one
// through would mean a `kid` collision decided which of the two answered.
internal fun rsaKeysFrom(document: String): Map<String, RSAPublicKey> = JWKSet.parse(document)
    .keys
    .filterIsInstance<RSAKey>()
    .filter { it.keyUse == null || it.keyUse == KeyUse.SIGNATURE }
    .mapNotNull { key -> key.keyID?.takeIf { it.isNotBlank() }?.let { it to key.toRSAPublicKey() } }
    .toMap()
