package dev.fardavide.oltre.server

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jwt.SignedJWT
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

// **The startup self-check, judged without a startup.** Step 45 asks for the `.p8` to be loaded and a
// throwaway ES256 token signed *before* the process binds the port, so that a broken key is a failed
// deploy rather than an outage — Cloud Run keeps the previous revision serving when a new one never
// becomes ready. What makes that check worth anything is that it fails for the right reasons, and
// every one of them is below.
//
// **No real key material is anywhere near this file.** The fixture is a P-256 keypair generated in
// the test process and written out as the same PKCS#8 PEM Apple hands over, which is the convention
// `Tokens.kt` set for the provider keypairs: nothing here reaches Apple and nothing in CI ever should.

private const val TEAM = "A7Q83J6LR4"
private const val KEY_ID = "77FXWGUFQY"
private const val SUBJECT = "dev.fardavide.oltre.signin"

private fun keyPairOf(algorithm: String, curve: String? = null): KeyPair =
    KeyPairGenerator.getInstance(algorithm)
        .apply { curve?.let { initialize(ECGenParameterSpec(it)) } ?: initialize(2048) }
        .generateKeyPair()

// The shape Apple's download actually has: a PKCS#8 body, base64 at 64 columns, between the two
// guard lines. Written out rather than hand-pasted so the test is about the parser rather than about
// one string somebody typed.
private fun pkcs8Pem(pair: KeyPair): String = buildString {
    appendLine("-----BEGIN PRIVATE KEY-----")
    Base64.getEncoder().encodeToString(pair.private.encoded).chunked(64).forEach(::appendLine)
    appendLine("-----END PRIVATE KEY-----")
}

private val P256: KeyPair = keyPairOf("EC", "secp256r1")

private fun environment(vararg entries: Pair<String, String>): (String) -> String? = entries.toMap()::get

class AppleKeyTest {

    @Test
    fun `a client secret names the team the key and the subject`() {
        val key = AppleSigningKey(teamId = TEAM, keyId = KEY_ID, privateKey = ecPrivateKeyFrom(pkcs8Pem(P256)))

        val claims = SignedJWT.parse(key.clientSecret(SUBJECT, TEST_NOW)).jwtClaimsSet

        assertEquals(TEAM, claims.issuer)
        assertEquals(SUBJECT, claims.subject)
        assertEquals(listOf("https://appleid.apple.com"), claims.audience)
    }

    // The `kid` is in the header rather than the claims, and Apple looks it up there. A secret signed
    // by the right key and labelled with the wrong id is refused with the same message as a forgery.
    @Test
    fun `a client secret names its key in the header`() {
        val key = AppleSigningKey(teamId = TEAM, keyId = KEY_ID, privateKey = ecPrivateKeyFrom(pkcs8Pem(P256)))

        val header = SignedJWT.parse(key.clientSecret(SUBJECT, TEST_NOW)).header

        assertEquals(KEY_ID, header.keyID)
        assertEquals(JWSAlgorithm.ES256, header.algorithm)
    }

    // **The assertion that makes the self-check a check.** Parsing proves the shape; verifying proves
    // the private half was actually usable, which is the whole of what a mounted secret can get
    // wrong — a truncated file, the public half by mistake, a key for the wrong curve.
    @Test
    fun `a client secret verifies against the public half`() {
        val key = AppleSigningKey(teamId = TEAM, keyId = KEY_ID, privateKey = ecPrivateKeyFrom(pkcs8Pem(P256)))

        val signed = SignedJWT.parse(key.clientSecret(SUBJECT, TEST_NOW))

        assertTrue(signed.verify(ECDSAVerifier(P256.public as ECPublicKey)))
    }

    // **Apple refuses a secret that outlives six months**, so a lifetime chosen here has to stay
    // under it. Pinned rather than trusted: the failure is a sign-in that stops working for everybody
    // at once, months after the line was written.
    @Test
    fun `a client secret expires well inside Apple's six months`() {
        val key = AppleSigningKey(teamId = TEAM, keyId = KEY_ID, privateKey = ecPrivateKeyFrom(pkcs8Pem(P256)))

        val claims = SignedJWT.parse(key.clientSecret(SUBJECT, TEST_NOW)).jwtClaimsSet

        val life = claims.expirationTime.time - claims.issueTime.time
        assertTrue(life > 0, "a secret that has already expired signs nothing")
        assertTrue(life <= 180.days.inWholeMilliseconds, "Apple refuses anything past six months")
    }

    @Test
    fun `a file that is not a pem is refused`() {
        assertFailsWith<IllegalArgumentException> { ecPrivateKeyFrom("this is not a key") }
    }

    // The two shapes a mount can be empty in, and they are different failures: a truncated file that
    // kept its guard lines, and a secret version that was created with nothing in it.
    @Test
    fun `a pem with nothing between the guards is refused`() {
        assertFailsWith<IllegalArgumentException> {
            ecPrivateKeyFrom("-----BEGIN PRIVATE KEY-----\n-----END PRIVATE KEY-----\n")
        }
    }

    @Test
    fun `an empty file is refused`() {
        assertFailsWith<IllegalArgumentException> { ecPrivateKeyFrom("") }
    }

    // The interesting wrong file rather than the obvious one: a perfectly good PKCS#8 PEM holding the
    // wrong kind of key. It base64-decodes, it parses as a private key, and it cannot sign ES256 —
    // so a check that stopped at "the file decoded" would pass it.
    @Test
    fun `a key that is not on an elliptic curve is refused`() {
        assertFailsWith<IllegalArgumentException> { ecPrivateKeyFrom(pkcs8Pem(keyPairOf("RSA"))) }
    }

    // Apple's download has no trailing newline in some browsers and CRLF in others, and Secret
    // Manager hands back exactly the bytes it was given. Whitespace must not be the difference
    // between a deploy and an outage.
    @Test
    fun `spacing around the body is not part of it`() {
        val padded = "\r\n  " + pkcs8Pem(P256).replace("\n", "\r\n").trim() + "  \r\n"

        assertEquals(ecPrivateKeyFrom(pkcs8Pem(P256)), ecPrivateKeyFrom(padded))
    }
}

// **How the three variables are read, and the rule is all or none.** Step 45: *"unlike `PORT`, none
// of these values may have a default"*. Absent altogether is the dev loop and is allowed; half-set is
// a deployment somebody misspelled a variable in, and it fails at boot rather than at the first
// revoke months later.
class AppleKeyConfigTest {

    @Test
    fun `with nothing set there is no key`() = assertNull(appleSigningKey(environment()) { error("no file") })

    @Test
    fun `with all three set the key loads`() {
        val key = appleSigningKey(
            environment(
                "APPLE_SIGNIN_KEY_FILE" to "/secrets/apple/signin.p8",
                "APPLE_TEAM_ID" to TEAM,
                "APPLE_KEY_ID" to KEY_ID,
            ),
        ) { pkcs8Pem(P256) }

        assertEquals(KEY_ID, SignedJWT.parse(checkNotNull(key).clientSecret(SUBJECT, TEST_NOW)).header.keyID)
    }

    // **The failure step 44 is most likely to produce.** `--set-secrets` is a dict flag, so a second
    // one silently replaces the first — and the shape that leaves behind is exactly this: the path
    // mounted and the plain variables beside it gone, or the reverse.
    @Test
    fun `a path with no team is refused`() {
        val thrown = assertFailsWith<IllegalArgumentException> {
            appleSigningKey(
                environment("APPLE_SIGNIN_KEY_FILE" to "/secrets/apple/signin.p8", "APPLE_KEY_ID" to KEY_ID),
            ) { pkcs8Pem(P256) }
        }

        assertTrue("APPLE_TEAM_ID" in thrown.message.orEmpty(), thrown.message.orEmpty())
    }

    @Test
    fun `a path with no key id is refused`() {
        val thrown = assertFailsWith<IllegalArgumentException> {
            appleSigningKey(
                environment("APPLE_SIGNIN_KEY_FILE" to "/secrets/apple/signin.p8", "APPLE_TEAM_ID" to TEAM),
            ) { pkcs8Pem(P256) }
        }

        assertTrue("APPLE_KEY_ID" in thrown.message.orEmpty(), thrown.message.orEmpty())
    }

    // The other direction, and it is not symmetric bookkeeping: a team and a key id with no file is a
    // deployment that thinks it can talk to Apple and holds nothing to talk with.
    @Test
    fun `a team with no path is refused`() {
        val thrown = assertFailsWith<IllegalArgumentException> {
            appleSigningKey(environment("APPLE_TEAM_ID" to TEAM, "APPLE_KEY_ID" to KEY_ID)) { pkcs8Pem(P256) }
        }

        assertTrue("APPLE_SIGNIN_KEY_FILE" in thrown.message.orEmpty(), thrown.message.orEmpty())
    }

    // The third arm of the same rule, and the one a reader would skip: a key id alone, with neither a
    // path nor a team. It is what `--set-env-vars` written the obvious way leaves behind when the
    // comma-delimited audiences swallow everything after them.
    @Test
    fun `a key id with nothing else is refused`() {
        val thrown = assertFailsWith<IllegalArgumentException> {
            appleSigningKey(environment("APPLE_KEY_ID" to KEY_ID)) { pkcs8Pem(P256) }
        }

        assertTrue("APPLE_SIGNIN_KEY_FILE" in thrown.message.orEmpty(), thrown.message.orEmpty())
    }

    // **A variable set to the empty string is not the same as unset to `System.getenv`**, and it is
    // what a `--set-env-vars` typo produces. Treated as a value, a blank path would be read as a file
    // and a blank team would sign a client secret Apple refuses; treated as unset without the
    // all-or-none rule above, the whole key would silently not be configured. All three are checked
    // because a blank in any one of them is the same mistake.
    @Test
    fun `a blank value is not a value`() {
        val blanks = listOf(
            mapOf("APPLE_SIGNIN_KEY_FILE" to "  ", "APPLE_TEAM_ID" to TEAM, "APPLE_KEY_ID" to KEY_ID),
            mapOf("APPLE_SIGNIN_KEY_FILE" to "/secrets/apple/signin.p8", "APPLE_TEAM_ID" to "  ", "APPLE_KEY_ID" to KEY_ID),
            mapOf("APPLE_SIGNIN_KEY_FILE" to "/secrets/apple/signin.p8", "APPLE_TEAM_ID" to TEAM, "APPLE_KEY_ID" to ""),
        )

        blanks.forEach { entries ->
            assertFailsWith<IllegalArgumentException>(entries.toString()) {
                appleSigningKey(entries::get) { pkcs8Pem(P256) }
            }
        }
    }

    // **A driver's own internal error routinely carries no message**, which `SpeechlessRepository`
    // exists for one file over. Without the elvis the diagnostic would be the string `"null"` beside
    // the path, which is the one thing worse than nothing for whoever is reading a deploy log.
    @Test
    fun `a file that cannot be read and will not say why still names the path`() {
        val thrown = assertFailsWith<IllegalStateException> {
            appleSigningKey(
                environment(
                    "APPLE_SIGNIN_KEY_FILE" to "/secrets/apple/signin.p8",
                    "APPLE_TEAM_ID" to TEAM,
                    "APPLE_KEY_ID" to KEY_ID,
                ),
            ) { throw NullPointerException() }
        }

        assertTrue("/secrets/apple/signin.p8" in thrown.message.orEmpty(), thrown.message.orEmpty())
        assertTrue("NullPointerException" in thrown.message.orEmpty(), thrown.message.orEmpty())
    }

    // **A mount that is not there yet is the one failure Cloud Run produces on its own** — a secret
    // whose version was deleted, or a volume named differently from the flag. The path belongs in the
    // message, because the thing to go and look at is the deploy command.
    @Test
    fun `a file that cannot be read names the path`() {
        val thrown = assertFailsWith<IllegalStateException> {
            appleSigningKey(
                environment(
                    "APPLE_SIGNIN_KEY_FILE" to "/secrets/apple/signin.p8",
                    "APPLE_TEAM_ID" to TEAM,
                    "APPLE_KEY_ID" to KEY_ID,
                ),
            ) { throw java.io.FileNotFoundException(it) }
        }

        assertTrue("/secrets/apple/signin.p8" in thrown.message.orEmpty(), thrown.message.orEmpty())
    }

    // A file that decodes into the wrong kind of key never reaches the signer at all, which is the
    // cheapest of the three failures to diagnose.
    @Test
    fun `a file holding the wrong kind of key is refused at load`() {
        assertFailsWith<IllegalArgumentException> {
            appleSigningKey(
                environment(
                    "APPLE_SIGNIN_KEY_FILE" to "/secrets/apple/signin.p8",
                    "APPLE_TEAM_ID" to TEAM,
                    "APPLE_KEY_ID" to KEY_ID,
                ),
            ) { pkcs8Pem(keyPairOf("RSA")) }
        }
    }
}

// **What `Main.kt` runs before it binds the port**, and the reason step 45 asks for it: Cloud Run
// keeps the previous revision serving when a new one never becomes ready, so a key that cannot sign
// is a failed deploy instead of an outage. Loading it is not enough on its own — the two tests below
// are a pair, and the second is the one that says why.
class AppleSelfCheckTest {

    @Test
    fun `the self-check signs something that verifies`() {
        val key = AppleSigningKey(teamId = TEAM, keyId = KEY_ID, privateKey = ecPrivateKeyFrom(pkcs8Pem(P256)))

        val signed = SignedJWT.parse(key.selfCheck(TEST_NOW))

        assertTrue(signed.verify(ECDSAVerifier(P256.public as ECPublicKey)))
        assertTrue(signed.jwtClaimsSet.expirationTime.time > (TEST_NOW + 1.minutes).toEpochMilliseconds())
    }

    // **The gap loading alone leaves.** A P-384 key is a real EC private key: the PEM decodes, the
    // cast holds, and every check up to this point passes — and then ES256 cannot be signed with it,
    // because the algorithm names the curve as well as the hash. Without a signature attempt at boot
    // this is a deploy that goes green and a `/auth/revoke` that fails the first time somebody
    // deletes their account, months later.
    @Test
    fun `a key on the wrong curve cannot sign and says so at boot`() {
        val wrongCurve = ecPrivateKeyFrom(pkcs8Pem(keyPairOf("EC", "secp384r1")))
        val key = AppleSigningKey(teamId = TEAM, keyId = KEY_ID, privateKey = wrongCurve)

        assertFailsWith<IllegalStateException> { key.selfCheck(TEST_NOW) }
    }
}
