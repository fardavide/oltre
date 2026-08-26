package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.Protocol
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// **The endpoint Apple has known about since step 22 and that nothing answered.**
// `https://api.oltre.space/v1/auth/apple/notifications` was entered into the App ID months before
// there was a server, because `api.oltre.space` is permanent and re-entering it later is another trip
// through a Save/Confirm flow the walkthrough warns drops values silently. It is harmless today —
// nobody has signed in, so there is nothing to notify about — and it starts 404ing the day `#113`
// ships. Davide's call, 2026-08-26: it lands here rather than there, because it is server code and
// `#113` is a local session already carrying a design round trip.
//
// **Two facts pull in opposite directions and between them they are the whole design.** It is a POST
// target anybody on the internet can reach, and one of the things it is asked to do is delete an
// account — so nothing acts before Apple's signature has been checked. And it is Apple's only way of
// saying that a player's Apple Account is gone, so refusing everything would be its own failure.

// What Apple sends. Four types, and the enum is closed on purpose: a type this build has never heard
// of is refused rather than shrugged at, so that Apple retries it and a line lands in the log — see
// `NotificationVerdict`.
internal enum class AppleEvent(val wire: String) {

    // **The Apple Account itself is gone**, so the subject can never sign in again and a colony left
    // behind is a colony nobody can reach.
    ACCOUNT_DELETE("account-delete"),

    // **The player turned Sign in with Apple off for this app**, which is an unlink and not a
    // deletion — and the difference is a year of play. Signing in again re-consents and hands back
    // *the same subject*, so the colony has to still be there when they do.
    CONSENT_REVOKED("consent-revoked"),

    // The private-relay address was switched off or back on. Nothing here stores an email — `#106`
    // §4 chose the subject precisely so there would be less to hold — so both are acknowledged and
    // are about nothing this server knows.
    EMAIL_DISABLED("email-disabled"),

    EMAIL_ENABLED("email-enabled");

    companion object {

        fun of(wire: String): AppleEvent? = entries.firstOrNull { it.wire == wire }
    }
}

// What verification concluded. `Refused` carries a diagnostic for a log and never player copy, which
// is `TokenVerdict`'s rule and holds here for the stronger reason that there is no player on the
// other end of this request at all — only Apple.
internal sealed interface NotificationVerdict {

    data class Trusted(val event: AppleEvent, val subject: String) : NotificationVerdict

    data class Refused(val reason: String) : NotificationVerdict
}

// **Apple's notification tokens carry no `exp`, unlike its ID tokens**, so freshness has to be read
// off `iat` or not at all. A day is generous against Apple's own retry schedule and short enough that
// a captured notification stops being usable long before a subject could be reused.
private val FRESH_FOR: Duration = 1.days

// Clocks disagree by seconds. A notification refused for arriving four seconds early would be a
// deletion this server never hears about, and the window is one-sided because a token from an hour in
// the future is not skew.
private val SKEW: Duration = 5.minutes

private const val PAYLOAD_FIELD = "payload"
private const val EVENTS_CLAIM = "events"
private const val TYPE_FIELD = "type"
private const val SUBJECT_FIELD = "sub"

// Verifying what Apple said, and turning it into the pair the route acts on.
//
// **It shares its signature check with `IdTokenVerifier` and its claims rules with nothing**, which
// is the split `signedClaims` exists for: the two tokens are signed the same way by the same keys at
// the same URL, and carry entirely different claims. A second copy of the signature machinery is the
// one thing in this slice a mistake in would be a real-world compromise.
internal class AppleNotificationVerifier(
    private val spec: ProviderSpec,
    private val keys: JwksKeys,
    private val clock: Clock,
) {

    suspend fun verify(body: String): NotificationVerdict {
        val payload = payloadOf(body)
            ?: return NotificationVerdict.Refused("the body is not an envelope with a $PAYLOAD_FIELD in it")

        val claims = when (val read = signedClaims(keys, spec, payload)) {
            is Signed.Refused -> return NotificationVerdict.Refused(read.reason)
            is Signed.Claims -> read.claims
        }

        if (claims.issuer !in spec.issuers) {
            return NotificationVerdict.Refused("issued by ${claims.issuer} rather than by ${spec.issuers}")
        }
        // Any of the accepted audiences, not all of them — `IdTokenVerifier`'s rule and its reason.
        // Apple sends one notification endpoint's worth for the whole app group, so the audience is
        // whichever client the player signed in with.
        if (spec.audiences.none { it in claims.audience }) {
            return NotificationVerdict.Refused("minted for ${claims.audience} which is none of ${spec.audiences}")
        }

        val issuedAt = claims.issueTime?.let { Instant.fromEpochMilliseconds(it.time) }
            ?: return NotificationVerdict.Refused("no issue time: nothing says when this happened")
        val now = clock.now()
        if (issuedAt < now - FRESH_FOR) return NotificationVerdict.Refused("issued at $issuedAt which is too long ago")
        if (issuedAt > now + SKEW) return NotificationVerdict.Refused("issued at $issuedAt which is ahead of now")

        return claims.getStringClaim(EVENTS_CLAIM)?.let(::eventIn)
            ?: NotificationVerdict.Refused("no $EVENTS_CLAIM claim: nothing says what happened")
    }
}

// **The `payload` field, read without a `@Serializable` of this module's own.** `:server`'s build
// file says in as many words that it declares none and that the day it needs one is the day the
// compiler plugin goes in — and this does not need one: `parseToJsonElement` is plain
// kotlinx-serialization and reads a document nobody has a class for, which is exactly what an
// envelope from somebody else's API is.
private fun payloadOf(body: String): String? = try {
    (Protocol.json.parseToJsonElement(body) as? JsonObject)
        ?.get(PAYLOAD_FIELD)
        ?.let { it as? JsonPrimitive }
        ?.takeIf { it.isString }
        ?.content
        ?.takeIf { it.isNotBlank() }
} catch (_: SerializationException) {
    null
}

// **The `events` claim is a JSON *string*, not a JSON object**, which is the one detail of this
// format most easily got wrong by reading the field names alone: it arrives as a quoted blob that has
// to be parsed a second time.
private fun eventIn(events: String): NotificationVerdict {
    val fields = try {
        Protocol.json.parseToJsonElement(events) as? JsonObject
    } catch (_: SerializationException) {
        null
    } ?: return NotificationVerdict.Refused("the $EVENTS_CLAIM claim is not a json object")

    val type = (fields[TYPE_FIELD] as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?: return NotificationVerdict.Refused("the event has no $TYPE_FIELD")
    // **Refused rather than ignored, and the direction is deliberate.** Apple adds types; a server
    // that answered "fine" to one it could not read would leave nothing anywhere saying it had
    // arrived, and Apple would stop sending it.
    val event = AppleEvent.of(type)
        ?: return NotificationVerdict.Refused("$type is not an event this build knows about")

    val subject = (fields[SUBJECT_FIELD] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
        ?: return NotificationVerdict.Refused("the event names nobody")

    return NotificationVerdict.Trusted(event, subject)
}
