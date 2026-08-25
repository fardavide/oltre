package dev.fardavide.oltre.server

import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import dev.fardavide.oltre.protocol.Protocol
import dev.fardavide.oltre.protocol.SessionToken
import java.text.ParseException
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// **What the app says about somebody after a provider has said it once.** A sign-in is a round trip
// to Apple or Google; everything after it is one of these, and the difference is the whole reason
// they exist — a game checked into for five minutes twice a day cannot re-run an OAuth dance each
// time.
//
// **HMAC and not a keypair.** There is one service, it both mints and reads these, and nobody else
// ever has to verify one — so a shared secret is the honest shape and an asymmetric key would be a
// second thing to hold for a property nothing needs. The day another service has to read a session
// is the day to argue for RS256, and it should have to be argued for then.

// Which of the two a token is. **The kinds are told apart by a claim rather than by a type**, so the
// distinction is one the *signature* enforces: a client that sent its refresh token on every request
// would otherwise be holding a ninety-day access token and nothing would say so.
internal enum class SessionKind(val claim: String) {

    ACCESS("access"),

    REFRESH("refresh"),
}

// A signed pair and when each half runs out. The expiries come back out because `SessionResponse`
// puts them on the wire: a client that had to decode a JWT to know when to refresh would be parsing
// a credential it is not the one that signed.
internal data class IssuedSession(
    val access: SessionToken,
    val accessExpiresAt: Instant,
    val refresh: SessionToken,
    val refreshExpiresAt: Instant,
)

// **Three answers and not two, because two of them get different words.** `Expired` is the case
// where the player did nothing wrong and the app can fix it by itself — `ApiError.SessionExpired`,
// then a refresh, then no screen at all. `Invalid` is everything else and means the sign-in screen.
internal sealed interface SessionVerdict {

    data class Valid(val player: PlayerId) : SessionVerdict

    data object Expired : SessionVerdict

    data class Invalid(val reason: String) : SessionVerdict
}

// **An hour, and it is short on purpose.** The access token is the credential that travels on every
// request, so its lifetime is the window in which a leaked one is worth anything. Making it long
// would buy one saved round trip a day and cost exactly that window.
private val ACCESS_LIFETIME: Duration = 1.hours

// **Ninety days, which is how long somebody can ignore this game and still not have to sign in
// again.** It is the number that decides whether the app feels like a game or like a banking app,
// and a check-in game whose whole premise is that you leave it alone should not punish leaving it
// alone. What makes it safe rather than merely convenient is that it is checked against the player
// row on every use: an account deleted yesterday cannot be refreshed today.
private val REFRESH_LIFETIME: Duration = 90.days

// Who minted it. One value, this server, and it is checked on the way back in — a token signed with
// the same key by something else would otherwise be one this server accepts.
private const val ISSUER = "oltre"

private const val KIND_CLAIM = "kind"

private val ALGORITHM: JWSAlgorithm = JWSAlgorithm.HS256

// Minting and reading the app's own tokens. The clock is a parameter for the reason it is one
// everywhere in this module: a test that had to wait an hour for an access token to run out is a
// test nobody runs, and expiry is the behaviour most worth pinning.
internal class Sessions(
    key: SessionSigningKey,
    private val clock: Clock,
    private val accessLifetime: Duration = ACCESS_LIFETIME,
    private val refreshLifetime: Duration = REFRESH_LIFETIME,
) {

    private val secret = key.value.encodeToByteArray()
    private val signer = MACSigner(secret)
    private val verifier = MACVerifier(secret)

    fun issue(player: PlayerId): IssuedSession {
        val now = clock.now()
        val accessExpiresAt = now + accessLifetime
        val refreshExpiresAt = now + refreshLifetime
        return IssuedSession(
            access = sign(player, SessionKind.ACCESS, now, accessExpiresAt),
            accessExpiresAt = accessExpiresAt,
            refresh = sign(player, SessionKind.REFRESH, now, refreshExpiresAt),
            refreshExpiresAt = refreshExpiresAt,
        )
    }

    // The order is the same one `IdTokenVerifier` uses and for the same reason: **signature before
    // claims**, so that a forged token is refused as forged rather than as expired. A token somebody
    // wrote themselves with an expiry in the past must not come back as `SessionExpired`, because
    // that is the answer the client silently retries.
    fun read(token: SessionToken, expected: SessionKind): SessionVerdict {
        val jwt = try {
            SignedJWT.parse(token.value)
        } catch (e: ParseException) {
            return SessionVerdict.Invalid("not a signed token: ${e.message}")
        }
        if (jwt.header.algorithm != ALGORITHM) {
            return SessionVerdict.Invalid("signed with ${jwt.header.algorithm} rather than $ALGORITHM")
        }
        val signed = try {
            jwt.verify(verifier)
        } catch (e: JOSEException) {
            return SessionVerdict.Invalid("the signature could not be checked: ${e.message}")
        }
        if (!signed) return SessionVerdict.Invalid("this server did not sign that")

        val claims = try {
            jwt.jwtClaimsSet
        } catch (e: ParseException) {
            return SessionVerdict.Invalid("the claims could not be read: ${e.message}")
        }
        if (claims.issuer != ISSUER) return SessionVerdict.Invalid("issued by ${claims.issuer}")

        val kind = try {
            claims.getStringClaim(KIND_CLAIM)
        } catch (e: ParseException) {
            return SessionVerdict.Invalid("the kind claim is not a string: ${e.message}")
        }
        // **A refresh token used as an access token is invalid rather than expired**, and the
        // distinction matters: telling the client to refresh would send it round a loop it can never
        // leave, where telling it to sign in ends.
        if (kind != expected.claim) return SessionVerdict.Invalid("a $kind token was sent where a ${expected.claim} was wanted")

        val expiry = claims.expirationTime?.let { Instant.fromEpochMilliseconds(it.time) }
            ?: return SessionVerdict.Invalid("no expiry")
        if (clock.now() >= expiry) return SessionVerdict.Expired

        val player = claims.subject?.takeIf { it.isNotBlank() }
            ?: return SessionVerdict.Invalid("no subject")
        return SessionVerdict.Valid(PlayerId(player))
    }

    private fun sign(player: PlayerId, kind: SessionKind, issuedAt: Instant, expiresAt: Instant): SessionToken {
        val claims = JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .subject(player.value)
            .claim(KIND_CLAIM, kind.claim)
            .issueTime(Date(issuedAt.toEpochMilliseconds()))
            .expirationTime(Date(expiresAt.toEpochMilliseconds()))
            .build()
        return SessionToken(SignedJWT(JWSHeader(ALGORITHM), claims).apply { sign(signer) }.serialize())
    }
}

// **The scheme, unwrapped in one place.** Case-insensitive because RFC 7235 says the scheme is, and
// a client's HTTP stack is entitled to send `bearer`. Null is *"there is no credential here"*, which
// the caller turns into `ApiError.Unauthenticated` — the same answer as a credential that is no
// good, because a request cannot be told the difference in any way that would help it.
internal fun bearerToken(header: String?): SessionToken? = header
    ?.takeIf { it.length > Protocol.BEARER_PREFIX.length }
    ?.takeIf { it.startsWith(Protocol.BEARER_PREFIX, ignoreCase = true) }
    ?.drop(Protocol.BEARER_PREFIX.length)
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?.let(::SessionToken)
