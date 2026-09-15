package dev.fardavide.oltre.protocol

import dev.fardavide.oltre.core.Resources
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

// **The vocabulary every later alliance slice speaks, in the one module both ends read.** Slice 1 of
// `alliance-sheet.md`: pure data and its tests, no table, no route handler, no `core`. `alliance-sheet.md`
// §1.1 is why nothing here is spatial — every player is in their own galaxy, so an alliance links
// separate universes rather than neighbouring coordinates. It is a row that says who is in a group.
//
// **Every route this file names is new**, and `ApiVersion.CURRENT` does not move for it —
// `alliance-sheet.md` §1.3's rule, restated: a version moves for a field removed, made required, or a
// verb's payload reshaped, and this slice does none of those. **The corollary is the reason this file
// is argued to completeness now rather than grown a field at a time**: after the alliance's routes
// first ship, a field added to a response an installed build already decodes is a wire break, and a
// bound widened is one too. Tightening a bound, and adding a route, both stay free forever.
//
// **None of this is a `ClientVerb`.** `ClientVerb`'s rule is one mutating function in `core` per
// member; nothing here mutates a `GameState`, nothing has an instant to replay, and a membership
// belongs to `players.id`, which outlives every colony hung off it — `profile-sheet.md` §3's three
// tests, which an alliance fails exactly as a rename does. So an alliance control tapped with no
// signal refuses in the amber `held` vocabulary rather than queueing: the outbox exists for verbs the
// server can validate by replay, and there is nothing here to replay against.

// The alliance's own surrogate key, minted by the server. **Unlike `PlayerId`, this one crosses the
// wire** — the client names which alliance it is asking to join, having read the id out of a search
// result, so it has to be a type the client can hold.
@Serializable
@JvmInline
value class AllianceId(val value: String) {

    init {
        require(value.isNotBlank()) { "an alliance id is minted by the server and cannot be blank" }
    }
}

// A seat on the roster, minted by the server and meaningful only inside one alliance. **Its own type
// rather than `PlayerId` on the roster**, because a roster is read by every member and `PlayerId` is a
// value the server draws as a conclusion, never one a client is handed to hold on strangers' phones.
@Serializable
@JvmInline
value class AllianceMemberId(val value: String) {

    init {
        require(value.isNotBlank()) { "an alliance member id is minted by the server and cannot be blank" }
    }
}

// A pending petition, minted by the server. **A different type from `AllianceMemberId` on purpose:**
// approving a petition and kicking a member are opposite acts, and one id type would let a caller pass
// one where the other was meant with the compiler silent — exactly the ID-confusion bug a typed
// wrapper exists to prevent.
@Serializable
@JvmInline
value class JoinRequestId(val value: String) {

    init {
        require(value.isNotBlank()) { "a join request id is minted by the server and cannot be blank" }
    }
}

// What a player typed for their alliance. **The trim is the client's and the refusal is the
// contract's**, `CommanderName`'s division for the reason `ProfileTest` states beside it: a value that
// arrives untrimmed came from something that did not agree about the shape.
//
// **What this guard does *not* decide is uniqueness.** `alliance-sheet.md` §6 asks for a normalised
// column — trimmed, case-folded, whitespace-collapsed — under a unique index; a name that is
// well-formed and merely equal under normalisation to somebody else's is a refusal with a designed
// answer (`ApiError.AllianceNameTaken`), and §3.5's rule forbids throwing where an answer exists.
//
// **`MAX_LENGTH = 32`, generous rather than measured** — there is no frame for this yet, unlike
// `CommanderName`'s 24. A bound can only tighten for free after the alliance's routes ship; widening it
// is a wire break, so the cheap error is to be generous now.
@Serializable
@JvmInline
value class AllianceName(val value: String) {

    init {
        when (val refusal = refusalFor(value)) {
            null -> Unit
            else -> throw IllegalArgumentException(refusal.saidOf(value))
        }
    }

    companion object {

        const val MAX_LENGTH: Int = 32

        // **The rule as a question rather than as a raise**, so both ends can ask it — see
        // `AllianceNameRefusal` for why that matters and where it went wrong without it.
        fun refusalFor(value: String): AllianceNameRefusal? = when {
            value.isBlank() -> AllianceNameRefusal.BLANK
            value != value.trim() -> AllianceNameRefusal.UNTRIMMED
            value.length > MAX_LENGTH -> AllianceNameRefusal.TOO_LONG
            else -> null
        }
    }
}

// **Why a typed string is not a name, as a value rather than as a thrown message** — Davide,
// 2026-09-14: *"we should run the check on the server and the client as well. Please make sure that
// we put this in a shared place so the client and the server share the same logic."*
//
// The value classes above are that shared place already, but only for a caller willing to catch: a
// client asking *may I send this?* had to construct one and swallow an `IllegalArgumentException`,
// and `App.onFound` did exactly that — so *Found it* was a control that silently did nothing when
// the tag was lower case. A question with an answer is what replaces the catch.
//
// **These never cross the wire**, which is what keeps them free of the rule `AllianceRole` and
// `AllianceProject` carry: a constant added to either of those after the alliance's routes shipped
// is a wire break, and a constant added here is not, because no build ever decodes one.
enum class AllianceNameRefusal {

    BLANK,
    UNTRIMMED,
    TOO_LONG,
}

private fun AllianceNameRefusal.saidOf(value: String): String = when (this) {
    AllianceNameRefusal.BLANK -> "an alliance has a name or has not been founded; blank is neither"
    AllianceNameRefusal.UNTRIMMED -> "a name is trimmed before it is sent: was '$value'"
    AllianceNameRefusal.TOO_LONG ->
        "a name longer than ${AllianceName.MAX_LENGTH} is one the field cannot produce: was ${value.length}"
}

// The short identifier beside the name, for a roster row where the full name does not fit —
// `alliance-sheet.md` §6. **Uppercase is refused, not applied**, on `AllianceName`'s reasoning again: a
// contract that upper-cased would let two clients disagree about whether `oltre` and `OLTRE` are one
// tag or two.
//
// **The alphabet is written out rather than delegated to `Char.isLetterOrDigit`**, which is
// Unicode-aware and would let `'Ä'`, an Arabic digit or a fullwidth letter through — none of which is
// typeable, comparable and four glyphs wide the way a roster row needs. This is the one place in the
// module where the alphabet narrows below what `CommanderName` allows, and it is worth naming what it
// costs: a player whose alphabet is not Latin will be typing somebody else's letters into this field.
// Recorded as accepted rather than unnoticed.
@Serializable
@JvmInline
value class AllianceTag(val value: String) {

    init {
        when (val refusal = refusalFor(value)) {
            null -> Unit
            else -> throw IllegalArgumentException(refusal.saidOf(value))
        }
    }

    companion object {

        // **Three to four — Davide, 2026-09-14**, tightened from the frame's 2–5. Tightening a bound
        // is the one direction that stays free forever (`alliance-sheet.md` §1.3), and it is taken
        // now rather than after launch: no alliance exists on any server yet, so nothing stored
        // becomes undecodable, which a widening-then-narrowing would have made unavoidable later.
        const val MIN_LENGTH: Int = 3
        const val MAX_LENGTH: Int = 4

        // The rule as a question, for `AllianceName.refusalFor`'s reason and the same caller.
        // **Length before alphabet**, so a player two letters in is told the tag is short rather
        // than accused of a character they have not typed.
        fun refusalFor(value: String): AllianceTagRefusal? = when {
            value.length !in MIN_LENGTH..MAX_LENGTH -> AllianceTagRefusal.WRONG_LENGTH
            !value.all { it in 'A'..'Z' || it in '0'..'9' } -> AllianceTagRefusal.NOT_UPPERCASE
            else -> null
        }
    }
}

// Why a typed string is not a tag. `AllianceNameRefusal`'s reasoning, for the other field.
enum class AllianceTagRefusal {

    WRONG_LENGTH,
    NOT_UPPERCASE,
}

private fun AllianceTagRefusal.saidOf(value: String): String = when (this) {
    AllianceTagRefusal.WRONG_LENGTH ->
        "a tag is ${AllianceTag.MIN_LENGTH} to ${AllianceTag.MAX_LENGTH} characters: was ${value.length}"
    AllianceTagRefusal.NOT_UPPERCASE ->
        "a tag is uppercase ASCII letters and digits, refused rather than folded: was '$value'"
}

// The alliance's own level, on its own ladder — `alliance-sheet.md` §3: *"its own XP, paid by
// contributions and by finished projects."* Its own type for `PlayerLevel`'s reason, said again with a
// third claimant: this game already has two other things called a level, a facility's and a
// technology's, and the bug the wrapper prevents is an alliance's level reaching a player's badge.
@Serializable
@JvmInline
value class AllianceLevel(val value: Int) {

    init {
        require(value >= 0) { "a level counts up from zero: was $value" }
    }
}

// *"3 of 12"*, one type rather than two loose `Int`s so it cannot be drawn in the wrong order.
//
// **The guard assumes the cap cannot fall**, which holds while the level is monotonic
// (`alliance-sheet.md` §3). If a balance round ever lowers the base seat count, an existing roster
// could become undecodable rather than merely odd — the residual to revisit before that deploy, on
// `SyncResponse`'s own precedent for a guard that judges the far end: a response that cannot be read
// coherently is better refused than half-rendered.
@Serializable
data class AllianceSeats(val taken: Int, val cap: Int) {

    init {
        require(taken >= 0) { "seats taken counts up from zero: was $taken" }
        require(taken <= cap) { "the seats taken cannot exceed the cap: was $taken of $cap" }
    }
}

// Founder, admin, member — `alliance-sheet.md` §5.3's three, Davide's call. **Which powers each role
// carries is not in the contract**; the wire carries who somebody is, and the server decides what that
// lets them do.
//
// **Three forever, unless a release pays for a fourth** — this enum is *decoded* by every client, so a
// constant added to it after the alliance's routes ship is a wire break in the shape `JoinDecision`
// (send-only) does not risk.
@Serializable
enum class AllianceRole {

    FOUNDER,
    ADMIN,
    MEMBER,
}

// What a player reads about an alliance: its own five fields, and nothing a search result and a
// member's read do not equally have. **One type, not `Alliance` plus `AllianceSummary`** — the first
// cut had both and they came out with identical fields.
//
// **No treasury, no alliance experience, deliberately.** Those are the treasury slice's, and it cannot
// add them here after the alliance's routes ship — so it adds its own route and its own response type
// instead. A new route is free; a sixth field is not.
@Serializable
data class Alliance(
    val id: AllianceId,
    val name: AllianceName,
    val tag: AllianceTag,
    val level: AllianceLevel,
    val seats: AllianceSeats,
)

// **What a player's relationship to an alliance is, as a sealed triple rather than two nullables.**
// The rejected shape is `AllianceResponse(alliance: Alliance?, role: AllianceRole?)`, whose four
// combinations include two that mean nothing — a role with no alliance, and an alliance with no role.
// This makes both unspellable, and it is what lets one route serve both *"I withdraw my petition"* and
// *"I leave"* unambiguously.
@Serializable
sealed interface AllianceStanding {

    // In no alliance and asking nobody. A `data object` and not an empty `data class`, on
    // `ToggleFlightAlerts`' reason: a payload-less class would encode identically and let a caller
    // construct two of them, and `RequiredFieldsTest`'s helper needs a non-empty field set to assert
    // against.
    @Serializable
    @SerialName("Unaffiliated")
    data object Unaffiliated : AllianceStanding

    // Waiting on an answer. **Carries the alliance whole rather than its id**, because the screen that
    // says *"waiting on Ferro Alto"* has to draw a name, and a client forced to look it up would need a
    // second request for a row it has already been handed once.
    @Serializable
    @SerialName("Petitioning")
    data class Petitioning(val alliance: Alliance) : AllianceStanding

    // A seat, and the role that seat carries.
    @Serializable
    @SerialName("Enlisted")
    data class Enlisted(val alliance: Alliance, val role: AllianceRole) : AllianceStanding
}

// What every mutation answers with — `AllianceResponse` or `AllianceRosterResponse`, never a bare
// success with nothing to draw, on `SyncResponse`'s own shape: the authoritative state comes back with
// the answer, so a control never has to fire a second request to know what it did.
@Serializable
data class AllianceResponse(
    val apiVersion: ApiVersion,
    val standing: AllianceStanding,
)

// `POST /v1/alliance`. **The price is not on the request and must never be**, which is the same
// division `AllianceProjectOffer` draws from the other side: a client that stated what it was willing
// to pay would be a client that could state the wrong number, and the server would have to either
// trust it or refuse it — neither of which is better than reading its own balance object. The request
// says which alliance to make; what it costs is `GET /v1/alliance/founding` and the server's to know.
@Serializable
data class CreateAllianceRequest(
    val apiVersion: ApiVersion,
    val name: AllianceName,
    val tag: AllianceTag,
)

// `GET /v1/alliance/founding` — what founding one costs today.
//
// **Its own route rather than a field on `AllianceResponse`**, which is `TreasuryResponse`'s own
// argument said for the other end of the same screen: a route is free forever and a field added to a
// response an installed build already decodes is a wire break. It is also the honest shape, because
// the price is only interesting to a player who is in no alliance, and `AllianceResponse` is what
// *every* alliance mutation answers with.
//
// **The price crosses the wire rather than living in the client**, for `AllianceProjectOffer.cost`'s
// reason stated again: the balance lives in `:server` so it can be retuned by a deploy rather than by
// a release, and a client that held its own copy would draw a figure the server had stopped charging.
@Serializable
data class FoundingPriceResponse(
    val apiVersion: ApiVersion,
    val price: Resources,
)

// `POST /v1/alliance/name`. **Carries the tag alongside the name**, on `SetProfileRequest`'s "the whole
// thing rather than the part that moved" reasoning, per Davide's call that the tag moves with the name
// rather than staying fixed at founding.
@Serializable
data class RenameAllianceRequest(
    val apiVersion: ApiVersion,
    val name: AllianceName,
    val tag: AllianceTag,
)

// `POST /v1/alliance/join`. **The subject goes in the body**, `alliance: AllianceId`, rather than in a
// path segment — every request type in this module carries its subject the same way, so a malformed id
// surfaces as `ApiError.Malformed` rather than as a route miss.
@Serializable
data class JoinAllianceRequest(
    val apiVersion: ApiVersion,
    val alliance: AllianceId,
)

// What a client only ever sends about a pending petition — `ADMITTED` or `DECLINED`. **A request-only
// enum, and that is what makes it the safe shape**: the server must understand every constant a client
// can send, and an older client simply never sends a newer one, which is `ApiVersion`'s "adding a verb
// is not one of those" in another costume. The reverse would not be safe, which is why `AllianceRole`
// — decoded by every client — stays three forever.
@Serializable
enum class JoinDecision {

    ADMITTED,
    DECLINED,
}

// `POST /v1/alliance/join/answer`. Founder or admin only; the server checks the role.
@Serializable
data class AnswerJoinRequest(
    val apiVersion: ApiVersion,
    val request: JoinRequestId,
    val decision: JoinDecision,
)

// `POST /v1/alliance/members/remove`. Founder or admin only.
@Serializable
data class KickMemberRequest(
    val apiVersion: ApiVersion,
    val member: AllianceMemberId,
)

// `POST /v1/alliance/members/role`. **`role = FOUNDER` is well-formed and left to the server to
// refuse** — `alliance-sheet.md` §3.5's line: a second enum of "promotable roles" would be a second
// wire vocabulary for the one `AllianceMember.role` already reads back with `FOUNDER` entirely legal
// there, and whether a founder may hand the alliance over by hand is a mechanic nobody has decided.
@Serializable
data class SetMemberRoleRequest(
    val apiVersion: ApiVersion,
    val member: AllianceMemberId,
    val role: AllianceRole,
)
