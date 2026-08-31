package dev.fardavide.oltre.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

// **Who the player is, as opposed to what their colony is.** Two facts — a name they typed and a
// mark they chose — and neither of them is a `ClientVerb`, which is the one thing about this file
// worth reading `profile-sheet.md` §3 for. A verb is a mutating function in `core` that the server
// can validate by replaying it against an authoritative colony; a rename mutates no `GameState`, has
// no instant to be replayed against, and belongs to an account that outlives every colony hung off
// it.
//
// **And it is not a field on `SyncResponse` either.** `Protocol.json` sets `encodeDefaults` and
// deliberately does not set `ignoreUnknownKeys`, and `RequiredFieldsTest` pins that nothing on this
// wire has a default — so a field added to a shipped response is a response the build already on
// somebody's phone cannot decode. A *route* added costs nothing, because an older client simply
// never calls it. That asymmetry is why this is `GET /v1/profile` and `POST /v1/profile`, and why
// `ApiVersion.CURRENT` does not move for it.

// The name a player chose, exactly as they will see it drawn.
//
// **The trim is the client's and the refusal is the contract's**, which is the same division every
// guard in this module is written on: a value that arrives untrimmed came from something that did
// not agree about the shape, and silently repairing it would let two clients disagree about whether
// `"Ada "` and `"Ada"` are one commander or two. `readRequest` turns the exception into
// `ApiError.Malformed`, which is the designed answer for input that makes no sense — as opposed to
// input that makes sense and is refused, which a name never is.
@Serializable
@JvmInline
value class CommanderName(val value: String) {

    init {
        require(value.isNotBlank()) { "a commander has a name or has not chosen one; blank is neither" }
        require(value == value.trim()) { "a name is trimmed before it is sent: was '$value'" }
        require(value.length <= MAX_LENGTH) {
            "a name longer than $MAX_LENGTH is one the field cannot produce: was ${value.length}"
        }
    }

    companion object {

        // **Twenty-four, and the field is what makes it true.** *A Name You Chose* §Three: the
        // counter is silent to 17, shows `18/24` from 18, and at 24 the field stops accepting rather
        // than refusing — *"the bound is a fact the field enforces, so there is nothing to say about
        // it and no state to draw"*. Stated here as well so a modified client meets `Malformed`
        // rather than writing a name the strip would have to ellipsise into meaninglessness.
        const val MAX_LENGTH: Int = 24
    }
}

// **The six drawn silhouettes**, each a different shape at 20dp: one diagonal, one centred disc, one
// horizontal, one corner, one nest of arcs, one vertical. Six rather than sixteen because every
// variant is a screenshot baseline and *"six distinguishable shapes is more identity than sixteen
// indistinguishable ones"*.
//
// Four of them are shapes the composer below cannot make — `TERMINATOR`'s centred disc, `APHELION`'s
// full-width ellipse, `SEXTANT`'s 12.4-unit arc, `SOUNDING`'s full-height plumb line — which is the
// whole argument for keeping both kinds of mark rather than replacing the set with the grammar.
@Serializable
enum class MarkPreset {

    THRESHOLD,
    TERMINATOR,
    APHELION,
    SEXTANT,
    WAKE,
    SOUNDING,
    ;

    // Which composition draws the same glyph, or null when none does. Only `THRESHOLD` answers, and
    // that is why the compose face opens on it.
    fun asComposed(): PlayerMark.Composed? = when (this) {
        THRESHOLD -> PlayerMark.Composed(MarkBody.LIMB, MarkPath.RISING, MarkTerminus.DOT)
        TERMINATOR, APHELION, SEXTANT, WAKE, SOUNDING -> null
    }
}

// **The composer's three slots, each drawn in a fixed region of the 24-unit box** — bodies inside
// the lower-left circle at 8.2/15.8, paths in the diagonal band from 13.4/10.6, termini in the
// corner at 19.9/4.1. No combination can put two strokes in the same place, so the gap that keeps
// `THRESHOLD` from reading as a magnifier is a property of the parts rather than of the pairing, and
// a legal mark is legal by construction.

@Serializable
enum class MarkBody {

    LIMB,
    TERMINATOR,
    ORBIT,
    WAKE,
}

@Serializable
enum class MarkPath {

    RISING,
    TRANSFER,
    TWIN,
    NONE,
}

@Serializable
enum class MarkTerminus {

    DOT,
    RING,
    NONE,
}

// **A mark is a preset or a composition, and the two are different kinds rather than one tuple with
// a flag.** The design's own phrasing is *"a tuple plus a preset id, not a tuple alone"*, because
// four of the six presets are not compositions at all — a sealed pair says that in the type system
// instead of leaving a `body` field meaningless for five of six values.
//
// The `@SerialName` values are wire identifiers, pinned by `ProfileTest` for `ClientVerb`'s reason:
// a server has to keep answering the build already on somebody's phone, and a phone that met a mark
// it has no path for would have nothing to draw.
@Serializable
sealed interface PlayerMark {

    @Serializable
    @SerialName("Preset")
    data class Preset(val preset: MarkPreset) : PlayerMark

    @Serializable
    @SerialName("Composed")
    data class Composed(
        val body: MarkBody,
        val path: MarkPath,
        val terminus: MarkTerminus,
    ) : PlayerMark {

        init {
            // **A terminus is the end of a path, so a mark with no path has none.** The compose face
            // does not draw the terminus ladder when the path is `NONE` — *"the ladder is not drawn
            // rather than disabled"* — so an illegal pair can only arrive from something that is not
            // the composer, which is exactly what this guard is for. It is also what makes the set
            // 4 × (3 × 3 + 1) = 40 rather than 4 × 4 × 3 = 48.
            require(path != MarkPath.NONE || terminus == MarkTerminus.NONE) {
                "a terminus is the end of a path: $body has no path and cannot wear $terminus"
            }
        }
    }
}

// What a player chose, with `null` meaning **has not chosen** rather than *this build does not say*.
// Every account founded before this slice reads both as null.
//
// **A default is a mark, not an absence** — *A Name You Chose*: a player who has never opened the
// editor wears `THRESHOLD` and is called `Dead Reckoning`, and neither is drawn differently from a
// chosen one. Two commanders may share a name, so a default is already indistinguishable from a
// deliberate identical choice, and marking it would be a claim the server cannot make. Which is why
// the substitution happens where it is drawn rather than here: this type says what the account
// holds, and the strip says what to draw when it holds nothing.
@Serializable
data class PlayerProfile(
    val name: CommanderName?,
    val mark: PlayerMark?,
)

// **The whole profile rather than the part that moved**, which is why this is a `POST` that replaces
// and not a `PATCH` that merges. With a merge, `null` would have to mean two things at once — *leave
// it alone* and *clear it* — and clearing is a thing a player must be able to do: it is the only way
// out of a name they regret, and the field's placeholder says so in as many words while they are
// looking at it.
@Serializable
data class SetProfileRequest(
    val apiVersion: ApiVersion,
    val profile: PlayerProfile,
)

// What both routes answer. The version is what this build speaks rather than an echo of what was
// asked for, exactly as `SyncResponse` states it and for the same reason.
@Serializable
data class ProfileResponse(
    val apiVersion: ApiVersion,
    val profile: PlayerProfile,
)
