package dev.fardavide.oltre.server

import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import dev.fardavide.oltre.protocol.IdToken
import dev.fardavide.oltre.protocol.SignInNonce
import java.text.ParseException
import kotlin.time.Clock
import kotlin.time.Instant

// **The one thing in this slice that a mistake in is a real-world compromise**, so every check is
// spelled out and each one has a test that fails without it. `#110`'s Done-means names seven, and
// they are the seven ways a token can be wrong: expired, wrong audience, wrong issuer, tampered
// signature, unknown key id, rotated key — and valid, which is the one that proves the other six are
// refusing for the right reason rather than refusing everything.
//
// **Nothing here reaches Apple or Google.** The keys come from `JwksKeys`, which is a cache over a
// port; the tests hand that port a document they minted a keypair for. That is the repository's
// fakes-not-mocks convention and it is also the only responsible thing to do: a suite that talked to
// a real issuer would be a suite that fails when somebody else deploys.

// What verification concluded. **`Refused` carries a diagnostic and never player copy** — every word
// the game says is a `TextRes` built through `Strings`, and a server cannot build one — so the
// string here is for a log, and what the player is shown is `ApiError.Unauthenticated`.
internal sealed interface TokenVerdict {

    data class Trusted(val identity: ProviderIdentity) : TokenVerdict

    data class Refused(val reason: String) : TokenVerdict
}

// **RS256 and nothing else.** Both providers sign with it, and pinning the algorithm rather than
// reading it out of the token is what closes the oldest hole in JWT: a header saying `none`, or one
// saying `HS256` so that a *public* key gets used as a shared secret. Nimbus verifies whatever it is
// handed a verifier for, so the refusal has to be here.
private val ACCEPTED_ALGORITHM: JWSAlgorithm = JWSAlgorithm.RS256

private const val NONCE_CLAIM = "nonce"

// Verifying what a provider said about somebody, and turning it into the pair this server stores.
//
// The clock is a parameter for the reason it is one everywhere else here: expiry is the check most
// worth testing, and a test that waited for a real token to run out would be a test nobody runs.
internal class IdTokenVerifier(
    private val specs: Map<IdentityProvider, ProviderSpec>,
    private val keys: JwksKeys,
    private val clock: Clock,
) {

    // The order below is deliberate and each step is only reached because the one before it held.
    // Signature before claims, so that a tampered token is refused as tampered rather than as
    // whatever its rewritten claims happen to say; claims before subject, so a token that is not for
    // us never contributes an identity at all.
    suspend fun verify(provider: IdentityProvider, token: IdToken, nonce: SignInNonce): TokenVerdict {
        val spec = specs[provider]
            ?: return TokenVerdict.Refused("${provider.providerName.value} is not configured on this server")

        val jwt = try {
            SignedJWT.parse(token.value)
        } catch (e: ParseException) {
            return TokenVerdict.Refused("not a signed token: ${e.message}")
        }

        if (jwt.header.algorithm != ACCEPTED_ALGORITHM) {
            return TokenVerdict.Refused("signed with ${jwt.header.algorithm} rather than $ACCEPTED_ALGORITHM")
        }
        val keyId = jwt.header.keyID?.takeIf { it.isNotBlank() }
            ?: return TokenVerdict.Refused("no key id: nothing says which key to look up")
        val key = keys.keyFor(spec.jwksUri, keyId)
            ?: return TokenVerdict.Refused("$keyId is not a key ${spec.provider.providerName.value} publishes")

        // `verify` answers false for a signature that does not match and throws for one it cannot
        // even attempt. Both are the same answer to a caller: this token was not signed by the key
        // it names.
        val signed = try {
            jwt.verify(RSASSAVerifier(key))
        } catch (e: JOSEException) {
            return TokenVerdict.Refused("the signature could not be checked: ${e.message}")
        }
        if (!signed) return TokenVerdict.Refused("the signature does not match $keyId")

        // Read after the signature held, and not before. A claims set that will not parse in a token
        // whose signature *did* is a provider bug rather than an attack, but reading it first would
        // mean deciding things about a body nobody has vouched for yet.
        val claims = try {
            jwt.jwtClaimsSet
        } catch (e: ParseException) {
            return TokenVerdict.Refused("the claims could not be read: ${e.message}")
        }

        return claims.judge(spec, nonce)
    }

    private fun JWTClaimsSet.judge(spec: ProviderSpec, nonce: SignInNonce): TokenVerdict {
        if (issuer !in spec.issuers) {
            return TokenVerdict.Refused("issued by $issuer rather than by ${spec.issuers}")
        }
        // **Any of the accepted audiences, not all of them.** There is one OAuth client per platform
        // — the Web client answers for both phones and the desktop dev loop has its own — so a token
        // carries exactly one of them, and a check that demanded the whole set would refuse every
        // token ever minted. See `ProviderSpec`.
        if (spec.audiences.none { it in audience }) {
            return TokenVerdict.Refused("minted for $audience which is none of ${spec.audiences}")
        }

        val expiry = expirationTime?.let { Instant.fromEpochMilliseconds(it.time) }
            ?: return TokenVerdict.Refused("no expiry: a token that never runs out is a token forever")
        // No leeway for clock skew, deliberately. Both providers mint these minutes out, this server
        // runs on a host whose clock is disciplined, and a tolerance is a window somebody has to
        // justify the width of. If a deployment ever needs one it should arrive with a measurement.
        if (clock.now() >= expiry) return TokenVerdict.Refused("expired at $expiry")

        // **The replay defence, and the check most easily left out because nothing fails without
        // it.** A token captured from somebody else's sign-in verifies perfectly; what it does not
        // carry is the nonce *this* client drew a moment ago.
        val carried = try {
            getStringClaim(NONCE_CLAIM)
        } catch (e: ParseException) {
            return TokenVerdict.Refused("the nonce claim is not a string: ${e.message}")
        }
        if (carried != nonce.value) {
            return TokenVerdict.Refused("carries a nonce that is not the one this sign-in drew")
        }

        val who = subject?.takeIf { it.isNotBlank() }
            ?: return TokenVerdict.Refused("no subject: nothing in it says who this is")
        return TokenVerdict.Trusted(ProviderIdentity(spec.provider.providerName, who))
    }
}
