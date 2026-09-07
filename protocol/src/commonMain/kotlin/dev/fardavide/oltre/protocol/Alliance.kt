package dev.fardavide.oltre.protocol

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
        require(value.isNotBlank()) { "an alliance has a name or has not been founded; blank is neither" }
        require(value == value.trim()) { "a name is trimmed before it is sent: was '$value'" }
        require(value.length <= MAX_LENGTH) {
            "a name longer than $MAX_LENGTH is one the field cannot produce: was ${value.length}"
        }
    }

    companion object {

        const val MAX_LENGTH: Int = 32
    }
}

// The short identifier beside the name, for a roster row where the full name does not fit —
// `alliance-sheet.md` §6. **Uppercase is refused, not applied**, on `AllianceName`'s reasoning again: a
// contract that upper-cased would let two clients disagree about whether `oltre` and `OLTRE` are one
// tag or two.
//
// **The alphabet is written out rather than delegated to `Char.isLetterOrDigit`**, which is
// Unicode-aware and would let `'Ä'`, an Arabic digit or a fullwidth letter through — none of which is
// typeable, comparable and five glyphs wide the way a roster row needs. This is the one place in the
// module where the alphabet narrows below what `CommanderName` allows, and it is worth naming what it
// costs: a player whose alphabet is not Latin will be typing somebody else's letters into this field.
// Recorded as accepted rather than unnoticed.
@Serializable
@JvmInline
value class AllianceTag(val value: String) {

    init {
        require(value.length in MIN_LENGTH..MAX_LENGTH) {
            "a tag is $MIN_LENGTH to $MAX_LENGTH characters: was ${value.length}"
        }
        require(value.all { it in 'A'..'Z' || it in '0'..'9' }) {
            "a tag is uppercase ASCII letters and digits, refused rather than folded: was '$value'"
        }
    }

    companion object {

        const val MIN_LENGTH: Int = 2
        const val MAX_LENGTH: Int = 5
    }
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

// `POST /v1/alliance`. The price is a balance-round number and is not here; this slice states only the
// name and the tag a founder chooses.
@Serializable
data class CreateAllianceRequest(
    val apiVersion: ApiVersion,
    val name: AllianceName,
    val tag: AllianceTag,
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
