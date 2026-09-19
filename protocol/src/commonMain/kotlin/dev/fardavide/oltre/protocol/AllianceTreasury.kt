package dev.fardavide.oltre.protocol

import dev.fardavide.oltre.core.Resources
import kotlinx.serialization.Serializable

// **The pool, what it has bought, and what it could buy next** — `alliance-sheet.md` §2 and §4, and
// the one surface in this feature where resources are the subject rather than people.
//
// **Its own route rather than fields on `Alliance`**, and that file says why from the other end: its
// five fields were argued to completeness before the alliance's routes shipped, precisely so that the
// treasury would have to add a route instead of a sixth field. A route is free forever; a field
// added to a response an installed build already decodes is a wire break. So `GET /v1/alliance/
// treasury` and `POST /v1/alliance/projects` are new doors onto the same alliance.
//
// **Nothing here is a `ClientVerb` except the contribution, which is not here.** Buying a project
// mutates no `GameState` — the pool is a column and the seats are the alliance's — so it is a route
// like the other eight acts. The contribution is the one act that takes resources out of a colony,
// and that is `ClientVerb.Contribute`, replayed through the sync pair like every other verb.

// **What the level and the projects have bought.** There is no withdrawal, ever, so everything here
// is one-way: the pool goes in and standing, seats or speed come out.
//
// **A `Serializable` enum decoded by every client, so a constant costs a release** — `AllianceRole`'s
// rule, which this is the second instance of. A constant added after these routes ship is a wire
// break, because a treasury read lists what is on sale and an installed build handed a name it has
// never heard cannot decode the 200 at all; `JoinDecision` escapes it only by being send-only, and
// this one is not. **`SHARED_LOGISTICS` is the release that paid for it** — `ApiVersion` 4 → 5,
// `OLDEST_SERVED` with it, which is the same forced update the boon itself cost one release earlier
// and is affordable for the same reason: the installed population is one person. After launch this
// enum stops being cheap to grow.
@Serializable
enum class AllianceProject {

    // Two more seats on the roster, buyable again and again at a rising price. **The first thing an
    // alliance could own that a player could see move**: the roster header reads `4 of 20` and this
    // makes it `4 of 22`, on the same check-in, with no other mechanic involved.
    CHARTER_EXPANSION,

    // **A percentage point off every member's builds and research, bought outright rather than
    // levelled into.** The one candidate in `#168` whose effect was already being built: the boon is
    // `AllianceSpeedup`, the ceiling is that type's own `MAX_PERCENT`, and the screens that state it
    // — the colony's section label, research's, and the facility sheet's `→`-pair — shipped in
    // 0.27.0 and need no line changing. So this entry is a value here, a curve in `AllianceBalance`,
    // an increment in both stores and two strings, and what reads differently the next check-in is a
    // number that was already on screen.
    //
    // **Why the other two the frame sketched are still not here**, recorded so the shortness stays a
    // decision: a *second admin seat* would be a new rule invented in order to be sold — there is no
    // admin cap in the shipped role logic — and an *alliance mark* is a route, a picker face and its
    // own baselines, which is a slice rather than an entry. Both objections are unchanged.
    SHARED_LOGISTICS,
}

// One row on the project list: what it is, what it costs *now*, and whether the pool covers it.
//
// **The price is on the offer rather than derived by the client**, which is the same division the
// roster uses for a member's level: the ladder lives in `:server` so it can be retuned by a deploy
// rather than by a release, and a client that computed a price would be a second copy of a curve
// nobody can simulate. `alliance-sheet.md` §8 is the reason there is no simulator to fit it against.
//
// `affordable` is stated rather than left to a comparison, because the comparison is not the obvious
// one: the pool covers a cost when it covers **every** resource in it, and a client that compared
// one total against another would call a pool rich in metal and empty of deuterium affordable.
@Serializable
data class AllianceProjectOffer(
    val project: AllianceProject,
    val cost: Resources,
    val affordable: Boolean,
    // How many times this alliance has already bought it. Drawn as the `owned` state's one tracked
    // word on a repeatable row, and it is what the rising price is computed from — carried so the
    // screen can say *bought twice* without a second request.
    val timesBought: Int,
)

// **What a contribution is worth to the alliance's own ladder, and where that ladder stands.**
//
// Carried beside the level rather than folded into it, because `AllianceLevel` is a whole number and
// a gauge needs the fraction. `Alliance.level` is the badge; this is the bar under it.
//
// **`earned` and `intoLevel` and `span`, the three `PlayerProgress` already carries**, and
// deliberately the same three: the two gauges in this app should not need two readings to draw. What
// is *not* shared is the ladder itself — a player's is a straight line because their experience
// accrues linearly in time, and an alliance's is geometric because its income is contributions and
// contributions come out of member income, which compounds. See `experience-sheet.md` §4, which
// named this as the other case.
@Serializable
data class AllianceProgress(
    val level: AllianceLevel,
    val earned: Long,
    val intoLevel: Long,
    val span: Long,
) {

    init {
        require(earned >= 0) { "an alliance's experience counts up from zero: was $earned" }
        require(span > 0) { "a level always costs something to leave: was $span" }
        require(intoLevel in 0 until span) { "progress is a share of this level: was $intoLevel of $span" }
    }
}

// `GET /v1/alliance/treasury`, and what `POST /v1/alliance/projects` answers with — the whole face in
// one read, on `AllianceResponse`'s own shape: the authoritative state comes back with the answer, so
// a control never has to fire a second request to know what it did.
//
// **`contributed` is this member's own total, priced on the game's 1 : 2 : 3.** One number rather
// than a basket, because it is not a stock anybody can spend — it is a standing, and it is the column
// the succession rule reads when it looks for the best contributor.
@Serializable
data class TreasuryResponse(
    val apiVersion: ApiVersion,
    val pool: Resources,
    val contributed: Long,
    val progress: AllianceProgress,
    val projects: List<AllianceProjectOffer>,
)

// `POST /v1/alliance/projects`. Founder or admin only — `alliance-sheet.md` §5.3 gives admins the
// treasury, and the server checks the role.
//
// **The subject goes in the body**, as every request type in this module does, so a project name this
// build does not know surfaces as `ApiError.Malformed` rather than as a route miss.
@Serializable
data class BuyProjectRequest(
    val apiVersion: ApiVersion,
    val project: AllianceProject,
)
