package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.AllianceSpeedup
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.priced
import dev.fardavide.oltre.protocol.AllianceLevel
import dev.fardavide.oltre.protocol.AllianceProgress
import dev.fardavide.oltre.protocol.AllianceProject

// **PLACEHOLDER balance, and every number in it is invented rather than measured** — in the words
// `session-roles.md` uses for the tilt constants, and for a reason that is worse than theirs.
// `alliance-sheet.md` §8: `:sim` drives one colony against no opponents, there is no bot that joins
// an alliance and no roster to measure, so **this is the first mechanic in Oltre with no simulator at
// all.** Every other balance object in this repository was fitted against a thirty-day run; these
// came from arithmetic and will come from a device. The balance round (`#145`) **ran on 2026-09-15
// and moved none of them** — see the table below. What would replace them is a roster with more than
// one person in it, and there is not one yet, so nothing here has become evidence.
//
// **It lives in `:server` and not in `:protocol` and certainly not in `core`.** The wire carries the
// level and the progress toward the next one, so the client renders what it is told and needs no
// ladder — which means a ladder nobody can simulate is retuned by a deploy instead of by a release.
// That is worth more here than anywhere else in the game, precisely because the first real reading
// will come from a roster that does not exist yet.
//
// **Where each dial stands, as of round 33 (2026-09-15).** Davide's call that day was *play first*;
// 0.25.1 went to TestFlight with every number below at its arithmetic value, he founded an alliance
// on it, and the reading came back **"they good"** — so nothing moved and `#145` closed.
//
// | Dial | Standing |
// |---|---|
// | `FOUNDING_PRICE` | **Davide's**, 2026-09-13. Confirmed on the install, 2026-09-15 |
// | the gate | **Davide's**, 2026-09-15: there is none |
// | `award` (`k = 1`) | fixed by identity — doubling it and the ladder together changes nothing |
// | `PROJECT_AWARD_PERCENT` | **arithmetic**, confirmed by play, unmoved |
// | `LEVEL_BASE` / `LEVEL_GROWTH_PERCENT` | **arithmetic**, confirmed by play, unmoved |
// | `SEAT_BASE` / `SEATS_PER_LEVEL` / `SEAT_CAP` | **arithmetic**, confirmed by play, unmoved |
//
// **They still say arithmetic rather than measured, and that is deliberate.** Confirmed-by-play and
// fitted-to-a-reading are different things: the confirmation was one sentence answering four
// separately-scoped questions, given by a founder on **a roster of one**. Nobody has yet filled a
// seat, bought a Charter Expansion with somebody else's contribution, or watched the gauge move on
// anything but their own income — so the seat dials in particular were confirmed by a player who
// could not yet feel them. The first measurement waits on a second member, not on a new round.
internal object AllianceBalance {

    // ── The ladder ───────────────────────────────────────────────────────────────────────────
    //
    // **Geometric, and that shape is a measurement even though the constants are not.**
    // `ExperienceBalance.LEVEL_STEP` is a straight line because a *player's* experience accrues
    // almost exactly linearly in time — about 4,600 a day, holding within a few percent from day one
    // to day thirty — while their income grows two orders of magnitude. An alliance's income is
    // contributions, contributions come out of member income, and member income compounds. So this
    // is the case `experience-sheet.md` §4 named as the other one: *"a geometric ladder is the right
    // shape for a game whose income is the score."*

    // What leaving level 0 costs, priced on the game's own 1 : 2 : 3. Roughly a fifth of the founding
    // price Davide set on 2026-09-13 (200,000 / 100,000 / 50,000, which prices at 550,000), so a
    // founder who paid for the charter is a level or two in by the time the second member arrives.
    const val LEVEL_BASE: Long = 100_000

    // Per level, as a percentage of the level below. 45% compounding: level 5 costs about 6.4 times
    // level 0 and level 10 about 41 times it, against member income which over the same span grows
    // faster than that. Stated as a percentage rather than a float because `core`'s own curves are
    // integer arithmetic and a ladder that answered differently on two machines would be a bug
    // nobody could reproduce.
    const val LEVEL_GROWTH_PERCENT: Long = 145

    // ── The founding price ───────────────────────────────────────────────────────────────────

    // **Davide's, 2026-09-13**, and the one number in this file that is not invented here —
    // `alliance-sheet.md` §5.1 records it and this is where it is finally charged. Priced on the
    // game's own 1 : 2 : 3 at 550,000, which is *"a real commitment, not a formality"*: a fortnight
    // of production at mid-game rates rather than an afternoon's, so founding is a week-two decision.
    //
    // **It lives here rather than in `:protocol` for `LEVEL_BASE`'s reason**, and the reason bites
    // harder for this one than for the ladder: the client draws whatever `GET /v1/alliance/founding`
    // tells it, so a balance round can retune the price with a deploy. A constant in the contract
    // would need a release on both platforms to move a number that has never been measured against a
    // real roster.
    //
    // **What is not here is the gate, and that is now a decision rather than a gap.** §5.1 floated a
    // player-level gate on founding alongside the price; **Davide refused it on 2026-09-15** and the
    // price does the work — 550,000 priced is about 75% of a colony's first week of production, so
    // founding lands days ten to fourteen on its own. The reason a gate was not merely unnecessary:
    // round 32 measured surveys at 36.3% of a month's experience, so a level threshold charges a
    // player who works the colony about a week more than one who flies probes, while a price charges
    // everyone the same thing. The player level still gates nothing anywhere in this game
    // (`experience-sheet.md` §5). See `balance-log.md` round 33.
    val FOUNDING_PRICE: Resources = Resources.of(metal = 200_000, crystal = 100_000, deuterium = 50_000)

    // ── The seats ────────────────────────────────────────────────────────────────────────────

    // What an alliance opens with. Five is a raiding party rather than a guild — enough that the
    // first project is worth wanting, few enough that it is wanted early.
    const val SEAT_BASE: Int = 5

    // One more seat per level, free, on top of whatever the projects bought. **This is what makes the
    // first contribution do something on the day it lands** (`#144` §8): the level moves, the cap
    // moves with it, and the roster header says so before any project has been bought at all.
    const val SEATS_PER_LEVEL: Int = 1

    // What `CHARTER_EXPANSION` adds each time it is bought. Two rather than one so the purchase reads
    // as a decision rather than as a rounding error against the free seat a level already grants.
    const val SEATS_PER_CHARTER: Int = 2

    // The ceiling, which is a product decision rather than a balance one and is the one number here
    // inherited rather than invented: 20 is what the alliance-exists slice shipped as a flat cap.
    // It stays as the roof over the sum below, so no ladder and no purchase can grow a roster past
    // what the roster screen was drawn for.
    const val SEAT_CAP: Int = 20

    // ── The catalogue ────────────────────────────────────────────────────────────────────────

    // What the first Charter Expansion costs, priced at the same 1 : 2 : 3 as everything else and set
    // at about a fifth of a level so a project is a thing an alliance does on the way up rather than
    // instead of levelling.
    val CHARTER_BASE: Resources = Resources.of(metal = 20_000, crystal = 10_000, deuterium = 5_000)

    // And what the next one costs. ×1.5, which is the multiplier every cost curve in this game
    // already uses — facilities, research, adaptation and hulls — so a player who has learned one
    // curve has learned this one.
    const val CHARTER_GROWTH_PERCENT: Long = 150

    // What finishing a project pays the alliance. **A lump, larger than what it cost**, which is
    // `alliance-sheet.md` §3.1's whole reason for having two sources rather than one: on
    // contributions alone the level would track how much an alliance has hoarded, and on projects
    // alone contributing would feel like paying into a hole. Together the level reads as *what we
    // chose to build*, with the saving as progress toward it.
    //
    // 130% of the priced cost. Enough to be felt on the gauge the moment the project lands, not so
    // much that buying and re-buying beats contributing.
    const val PROJECT_AWARD_PERCENT: Long = 130

    // ── The arithmetic ───────────────────────────────────────────────────────────────────────

    // What a contribution is worth on the alliance's ladder: the game's own 1 : 2 : 3, through
    // `core`'s single statement of it rather than a second copy of the weights. That function was
    // `internal` until this slice and is public for exactly this caller — two copies of the ratio
    // with no test comparing them is the drift `LevelPurpose.kt` already complains about.
    fun award(contribution: Resources): Long = contribution.priced()

    fun projectAward(cost: Resources): Long = cost.priced() * PROJECT_AWARD_PERCENT / 100

    // What it costs to leave `level` behind. Never zero, so the gauge always has somewhere to go —
    // `ExperienceBalance.spanOf`'s own guarantee, which `AllianceProgress` turns into a `require`.
    //
    // **It saturates rather than overflowing, and that is a fix rather than a flourish.** A 45%
    // compounding curve leaves `Long` somewhere around level 110, and the first cut of this multiplied
    // straight through: `spanOf` went *negative*, `AllianceProgress`'s own guard rejected it, and a
    // route raised `IllegalArgumentException` on a number read out of a column. No treasury in this
    // game can reach that by play — but `experience` is a stored `bigint`, and a total the server
    // will not read is worse than one it reads as very large.
    fun spanOf(level: AllianceLevel): Long {
        var span = LEVEL_BASE
        repeat(level.value) {
            span = if (span > MAX_SPAN / LEVEL_GROWTH_PERCENT) MAX_SPAN else span * LEVEL_GROWTH_PERCENT / 100
        }
        return span
    }

    // **Walked rather than closed, unlike `ExperienceBalance`, and the difference is the shape.** A
    // straight line has a closed-form inverse worth solving for; a geometric one would need a
    // logarithm, which is a float, and a float root landing a hair under a boundary would hand two
    // machines different levels for the same alliance. The walk is bounded by `MAX_LEVEL` and runs
    // once per treasury read.
    fun progressOf(earned: Long): AllianceProgress {
        require(earned >= 0) { "an alliance's experience counts up from zero: was $earned" }
        var level = 0
        var floor = 0L
        var span = spanOf(AllianceLevel(0))
        while (level < MAX_LEVEL && earned - floor >= span) {
            floor += span
            level += 1
            span = spanOf(AllianceLevel(level))
        }
        return AllianceProgress(
            level = AllianceLevel(level),
            earned = earned,
            // Clamped at the top rung so the gauge reads full-but-not-past rather than overflowing
            // its own span. Reaching it takes a treasury nothing in this game can currently fill;
            // it exists so the walk above is a total function rather than one that is merely
            // unlikely to run long.
            intoLevel = (earned - floor).coerceAtMost(span - 1),
            span = span,
        )
    }

    // ── What the level takes off every member's next build ───────────────────────────────────

    // Two percent a level, which puts an alliance at the floor at level 15. Davide's call,
    // 2026-09-18, and the floor is the load-bearing half of it: `alliance-sheet.md` §4.3's stated
    // risk is membership becoming mandatory rather than attractive, and a divisor with no ceiling is
    // how that happens.
    const val SPEEDUP_PER_LEVEL: Int = 2

    // **The one place an alliance's level becomes a number `core` understands.** `core` is handed a
    // percentage and never learns an alliance exists — no level, no roster, no `:protocol` type —
    // so this function is the whole of the translation, and it is on the server precisely because
    // the ladder is: a deploy can retune both together without a release.
    //
    // Clamped by `AllianceSpeedup`'s own ceiling rather than by a second constant here. The type
    // refuses anything past it, so the `coerceAtMost` is what makes this total rather than what
    // makes it safe — an alliance walking past level 15 buys seats and standing and no more speed.
    fun speedupOf(earned: Long): AllianceSpeedup = AllianceSpeedup(
        (SPEEDUP_PER_LEVEL * progressOf(earned).level.value).coerceAtMost(AllianceSpeedup.MAX_PERCENT),
    )

    // How many seats the roster has: the free ones the level granted, plus the ones the pool bought,
    // under the screen's own roof.
    fun seatCap(level: AllianceLevel, seatsBought: Int): Int =
        (SEAT_BASE + SEATS_PER_LEVEL * level.value + SEATS_PER_CHARTER * seatsBought).coerceAtMost(SEAT_CAP)

    // What the next one costs, given how many have already been bought.
    // **It saturates too, and for the same reason `spanOf` does** — `Resources.of` refuses a figure
    // past what its fine-unit backing can hold, so a price that ran away would not be an odd number
    // on a screen but a raise inside a route.
    fun costOf(project: AllianceProject, timesBought: Int): Resources = when (project) {
        AllianceProject.CHARTER_EXPANSION -> Resources.of(
            metal = CHARTER_BASE.metal.grownBy(timesBought),
            crystal = CHARTER_BASE.crystal.grownBy(timesBought),
            deuterium = CHARTER_BASE.deuterium.grownBy(timesBought),
        )
    }

    private fun Long.grownBy(times: Int): Long {
        var value = this
        repeat(times.coerceAtMost(MAX_REPEATS)) {
            value = if (value > MAX_UNITS / CHARTER_GROWTH_PERCENT) MAX_UNITS else value * CHARTER_GROWTH_PERCENT / 100
        }
        return value
    }

    // Whether buying it again would change anything. **A project that cannot move its own number is
    // absent from the catalogue rather than offered and refused** — an entry that takes the pool and
    // does nothing is the dead control wearing a price tag, which is the failure `#144` §8 names.
    fun isExhausted(project: AllianceProject, level: AllianceLevel, seatsBought: Int): Boolean = when (project) {
        AllianceProject.CHARTER_EXPANSION -> seatCap(level, seatsBought) >= SEAT_CAP
    }

    // A roof on the walk and on the price curve. Both are loops over an alliance-supplied number, and
    // an unbounded one is an unbounded loop; neither is reachable by play.
    private const val MAX_LEVEL: Int = 200
    private const val MAX_REPEATS: Int = 64

    // Where the two curves saturate. `MAX_SPAN` is far below `Long.MAX_VALUE` so the sum of every
    // span below a level cannot overflow either; `MAX_UNITS` is what `Resources.of` will accept,
    // which is `Long.MAX_VALUE` divided by the fine-unit scale. Both are ceilings for arithmetic
    // rather than balance numbers — no treasury in this game reaches within many orders of either.
    private const val MAX_SPAN: Long = Long.MAX_VALUE / 1_000_000
    private const val MAX_UNITS: Long = Long.MAX_VALUE / 3_600_000
}
