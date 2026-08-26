package dev.fardavide.oltre.server

import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.Date
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// **The `.p8`, which this slice mounts and does not yet use.** `#110` established that verifying an
// ID token needs no client secret at all — it is a signature check against Apple's public key set —
// so the only thing this key is ever for is the REST API: `/auth/revoke`, which Apple has required
// since June 2022 of any app offering both account deletion and Sign in with Apple. Davide's call,
// 2026-08-25: **that lands in `#113`**, because the blocker is the authorization code and only the
// sign-in flow can produce one.
//
// **So why is the key here at all.** Step 44 mounts it as a file, and step 45 asks that it be loaded
// and *used* before the process binds the port. The reason is the host: Cloud Run keeps the previous
// revision serving when a new one never becomes ready, so a key that is truncated, mounted from the
// wrong secret or generated on the wrong curve is a **failed deploy** rather than an outage that
// starts the day somebody first deletes their account. A secret nobody exercises is a secret nobody
// knows is broken.
//
// **Mounted as a file and not as an environment variable**, which is step 44's third flag and the one
// with no error message attached to getting it wrong: a PEM carries newlines, and a mounted secret is
// re-read on every access where an env-var secret is resolved once before the instance starts and is
// then fixed for that instance's life.

private const val KEY_FILE_VARIABLE = "APPLE_SIGNIN_KEY_FILE"
private const val TEAM_VARIABLE = "APPLE_TEAM_ID"
private const val KEY_ID_VARIABLE = "APPLE_KEY_ID"

// Apple's audience for every one of its REST endpoints, and a fact about Apple rather than
// configuration — the same call `IdentityProvider` makes about issuers and key-set URLs.
private const val APPLE_AUDIENCE = "https://appleid.apple.com"

// **Apple refuses a client secret that outlives six months.** A hundred and eighty days is inside
// that with a fortnight to spare, and the margin is the point: the recurring cost this key carries is
// regenerating the secret before it expires, and a lifetime sitting exactly on the limit turns a
// clock-skew argument into an outage for every player at once.
private val CLIENT_SECRET_LIFETIME: Duration = 180.days

// **The one claim this slice cannot know.** Which `client_id` `/auth/revoke` wants depends on which
// flow produced the token being revoked — the Services ID for the web flow, the bundle ID for the
// native one — and that is `#113`'s to settle with the screen that triggers it. The token below is
// signed and thrown away without ever leaving the process, so the subject only has to be a string; it
// is spelled out rather than left blank so that a copy of it turning up in a log is obviously not a
// real secret.
private const val SELF_CHECK_SUBJECT = "startup-self-check"

// ES256 and nothing else. Apple names the algorithm in its own documentation and the `.p8` is a P-256
// key; pinning it here means a key on another curve fails at boot rather than at the first call.
private val ALGORITHM: JWSAlgorithm = JWSAlgorithm.ES256

// Apple's client secret, which is a JWT rather than a string somebody stores — the reason it expires,
// and the reason there is a recurring cost at all.
//
// The clock is a parameter for the reason it is one everywhere else in this module: the lifetime is
// the property most worth pinning, and a test that waited six months for one to run out is a test
// nobody runs.
internal class AppleSigningKey(
    private val teamId: String,
    val keyId: String,
    private val privateKey: ECPrivateKey,
) {

    fun clientSecret(subject: String, now: Instant, lifetime: Duration = CLIENT_SECRET_LIFETIME): String {
        val claims = JWTClaimsSet.Builder()
            .issuer(teamId)
            .subject(subject)
            .audience(APPLE_AUDIENCE)
            .issueTime(Date(now.toEpochMilliseconds()))
            .expirationTime(Date((now + lifetime).toEpochMilliseconds()))
            .build()

        val signed = SignedJWT(JWSHeader.Builder(ALGORITHM).keyID(keyId).build(), claims)
        try {
            signed.sign(ECDSASigner(privateKey))
        } catch (e: JOSEException) {
            // **Everything a mounted key can be wrong about that parsing cannot see.** The commonest
            // is the curve: a P-384 key is a perfectly good `ECPrivateKey` and cannot sign ES256,
            // because the algorithm names the curve as well as the hash. Raised as a failure rather
            // than returned, because there is no caller who could do anything but stop.
            error("$KEY_FILE_VARIABLE holds a key that cannot sign $ALGORITHM: ${e.message}")
        }
        return signed.serialize()
    }

    // **Step 45, and it is called before the port is bound.** A short life because nothing ever reads
    // it — what is being proven is that the private half is usable, which is the whole of what a
    // mounted secret can get wrong and the whole of what a deploy should fail on.
    fun selfCheck(now: Instant): String = clientSecret(SELF_CHECK_SUBJECT, now, lifetime = 5.minutes)
}

// **How the three variables are read, and the rule is all or none** — step 45: *"unlike `PORT`, none
// of these values may have a default"*. `identityConfig`'s shape one file over, and the same three
// states: absent altogether is the dev loop and is allowed; half-set is a deployment with a variable
// misspelled in it, and it fails at boot.
//
// **Null is "Apple's REST API is not configured", which a *deployed* server is allowed to be today**
// and will not be after `#113`. The asymmetry with `SESSION_SIGNING_KEY` is deliberate and is not an
// oversight: a missing session key on a deployed server means anybody can name any player, which is a
// compromise; a missing `.p8` means one obligation to Apple goes unmet on a route that does not exist
// yet. `Main.kt` refuses to boot for the first and logs the second.
//
// `readFile` is a parameter rather than a call for `identityConfig`'s reason: the rules below are
// judged by a plain unit test, and a test that had to write a `.p8` to disk to check that a blank
// team id is refused would be testing the filesystem.
internal fun appleSigningKey(read: (String) -> String?, readFile: (String) -> String): AppleSigningKey? {
    val path = read(KEY_FILE_VARIABLE)?.takeIf { it.isNotBlank() }
    val teamId = read(TEAM_VARIABLE)?.takeIf { it.isNotBlank() }
    val keyId = read(KEY_ID_VARIABLE)?.takeIf { it.isNotBlank() }
    if (path == null && teamId == null && keyId == null) return null

    require(path != null) { "$TEAM_VARIABLE or $KEY_ID_VARIABLE is set but $KEY_FILE_VARIABLE is not" }
    require(teamId != null) { "$KEY_FILE_VARIABLE is set but $TEAM_VARIABLE is not" }
    require(keyId != null) { "$KEY_FILE_VARIABLE is set but $KEY_ID_VARIABLE is not" }

    // **The path is in the message because the thing to go and look at is the deploy command.** A
    // secret whose version was deleted, or a volume named differently from the `--set-secrets` flag,
    // both arrive here and neither says anything about itself.
    val pem = try {
        readFile(path)
    } catch (e: Exception) {
        error("$KEY_FILE_VARIABLE points at $path which could not be read: ${e.message ?: e::class.simpleName}")
    }

    return AppleSigningKey(teamId = teamId, keyId = keyId, privateKey = ecPrivateKeyFrom(pem))
}

// **What Apple's download is**: a PKCS#8 body, base64 wrapped at 64 columns, between two guard lines.
// Nothing here is Apple-specific except that expectation, which is why it is a function rather than a
// method — a test hands it a keypair it generated a moment earlier, and no real key material is ever
// in this repository or in a session.
//
// **Whitespace is stripped rather than trusted.** Apple's download has no trailing newline in some
// browsers and CRLF in others, and Secret Manager hands back exactly the bytes it was given — so
// spacing must not be the difference between a deploy and an outage.
internal fun ecPrivateKeyFrom(pem: String): ECPrivateKey {
    val body = pem
        .replace(BEGIN, "")
        .replace(END, "")
        .filterNot { it.isWhitespace() }
    require(body.isNotEmpty()) { "$KEY_FILE_VARIABLE holds no key: expected a $BEGIN block" }

    val decoded = try {
        Base64.getDecoder().decode(body)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("$KEY_FILE_VARIABLE is not a PKCS#8 PEM: ${e.message}", e)
    }

    return try {
        // **A plain cast and not a safe one**, which is the rare case where the checked version would
        // be the misleading one: a `KeyFactory` obtained for `"EC"` returns an `ECPrivateKey` or
        // throws, so an `as?` here would add an arm no test could ever reach and a message describing
        // a state the JCA does not have. The interesting wrong file — a perfectly good PKCS#8 PEM
        // holding an *RSA* key — base64-decodes and is refused by the catch below, which is where
        // that check actually lives.
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(decoded)) as ECPrivateKey
    } catch (e: GeneralSecurityException) {
        throw IllegalArgumentException("$KEY_FILE_VARIABLE does not hold an elliptic-curve key: ${e.message}", e)
    }
}

private const val BEGIN = "-----BEGIN PRIVATE KEY-----"
private const val END = "-----END PRIVATE KEY-----"
