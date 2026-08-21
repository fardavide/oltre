package dev.fardavide.oltre.core

import kotlinx.serialization.Serializable
import kotlin.time.Instant

// Five constants, and **each one exists because a slice needs it** — Davide's call, 2026-08-10,
// choosing "four fixed types now, hulls + modules later", and 2026-08-16, adding the fifth:
//
// | Hull      | What it is for                                        | Ships in  |
// |-----------|-------------------------------------------------------|-----------|
// | SCOUT     | one verb, no hold. It flies a probe and comes home     | 0.15      |
// | SKIFF     | going far and going soon. One berth of hold, full speed| 0.7.0     |
// | HAULER    | the near rocks and the long stays. Four berths, half   | slice 4   |
// | ESCORT    | surviving what a hauler cannot — a combat model        | slice #8  |
// | SETTLER   | carrying a colony                                     | slice #10 |
//
// `FIGHTER` and `CRUISER` are gone because they differ only *inside* a combat model, so today they
// would be two rows with different numbers and identical behaviour — a fake decision on a new tab,
// which is the "boring idle" complaint with a fresh coat of paint.
//
// **The fifth broke Notion's "4 ship types", and that is allowed rather than overlooked.** Davide,
// 2026-08-16: *"Notion stuff is now very ancient."* The count was a year-zero scope note, not a
// ceiling — recorded in `brief.md` and `CLAUDE.md` so no later session re-raises it.
//
// **`SCOUT` is the odd one and `Ships` absorbs it anyway.** It carries no cargo, cannot be escorted
// and has exactly one verb, so every other consumer of this enum — which all assume a hull is
// something you can dispatch on a gathering run — would have been wrong about it. It is here rather
// than in a concept of its own because the alternative was a second pool, a second price path, a
// second yard and a second save field to say one word; what it costs instead is one guard, in
// `startRun`, which is where the assumption actually lives.
//
// **The rename was free exactly once and this is it.** The old `CARGO / FIGHTER / CRUISER /
// COLONY_SHIP` carried a comment warning that these names are on-disk identifiers in every save.
// True, and cheaper than it looked: nothing in the repository had ever *constructed* a
// `ReturningFleet` outside test code, so no save any player holds contains a `ShipType` string. From
// the first hull a real player owns, a rename is a schema break again — which is why `SCOUT` was
// Davide's to name and not the build's.
@Serializable
enum class ShipType { SCOUT, SKIFF, HAULER, ESCORT, SETTLER }

// A bundle of hulls you spend and get back, mirroring `Resources` deliberately — an init guard,
// `covers`, `plus`, `minus`, a companion — because it is the same kind of thing.
//
// A map rather than a flat record like `Buildings`: the ship set is scheduled to grow twice by
// design, and a flat record would be edited by slice #8, by slice #10 and by every save hop between.
// A map absorbs a constant for free.
@Serializable
data class Ships(val counts: Map<ShipType, Int>) {
    // **This is not hygiene, it is the composability property.** Both `mapOf(SKIFF to 0)` and
    // `emptyMap()` are reachable — a run that dispatches the last hull and later returns it reaches
    // both — and `advance`'s property is asserted with `assertEquals` on whole `GameState`s. A
    // non-canonical representation would make one span and two spans produce equal games that fail
    // equality, which is the property test failing on something that is not a bug. `minus` drops
    // zeroed entries so this guard can hold.
    init {
        require(counts.values.all { it > 0 }) { "ship counts must be positive, were $counts" }
    }

    val isEmpty: Boolean get() = counts.isEmpty()

    val total: Int get() = counts.values.sum()

    fun countOf(type: ShipType): Int = counts[type] ?: 0

    fun covers(other: Ships): Boolean = other.counts.all { (type, count) -> countOf(type) >= count }

    operator fun plus(other: Ships): Ships = Ships(
        (counts.keys + other.counts.keys)
            .associateWith { type -> countOf(type) + other.countOf(type) }
            .filterValues { it > 0 },
    )

    operator fun minus(other: Ships): Ships = Ships(
        (counts.keys + other.counts.keys)
            .associateWith { type -> countOf(type) - other.countOf(type) }
            .filterValues { it > 0 },
    )

    companion object {
        val NONE: Ships = Ships(emptyMap())

        fun of(type: ShipType, count: Int): Ships = Ships(mapOf(type to count))
    }
}

// Which resource a run is out to fetch. Never `DEUTERIUM`, and the rule is checked rather than made
// unrepresentable because `ResourceKind` is the game's one resource enum and a second three-minus-one
// copy of it would be a type nobody could pass to `Resources`.
//
// The exclusion is load-bearing: deuterium buys the Robotics Factory, Robotics 1 opens research and
// Robotics 4 opens the adaptation ladders, and the interaction census puts 35% of all refused actions
// behind an unmet requirement with deuterium the shortage at 33 of 33. `SurveyBalance` refused to
// *price* a verb in deuterium for exactly this; this refuses to *pay out* in it, for the mirror
// reason. It also lands where the design wanted anyway — cold worlds are deuterium worlds, so **the
// fleet wants heavy and thick and the colony wants cold**, which leaves Thermal the one ladder with a
// prize the fleet can never undercut.
//
// A run in flight, and the fifth kind of job. It does **not** pretend to the `(subject, startedAt,
// completesAt)` shape the other four share, because it is genuinely a different animal: it is the
// only one that carries its own outcome.
//
// `cargo` is fixed at dispatch. That is the same rule every other verb follows one step further — a
// Robotics Factory finishing mid-flight must not retroactively shorten a build, and a mine level
// completing mid-flight must not retroactively enrich a run already out.
@Serializable
data class FleetRun(
    val target: GalaxyCoordinate,
    val ships: Ships,
    val gathering: ResourceKind,
    val cargo: Resources,
    val dispatchedAt: Instant,
    val returnsAt: Instant,
) {
    init {
        require(!ships.isEmpty) { "a run must carry at least one ship" }
        require(gathering != ResourceKind.DEUTERIUM) { "a run never gathers deuterium" }
        require(returnsAt > dispatchedAt) {
            "a run must return after it left: dispatched $dispatchedAt, returns $returnsAt"
        }
    }

    // Where the outbound leg ends and the inbound leg begins, derived rather than stored so `core`
    // holds one instant per end instead of three. The presentation layer renders the phase from
    // these; nothing in `advance` reads them, because a run has exactly one transition and it is the
    // return.
    // **The research is a parameter rather than a snapshot on the run, and that is a trade rather
    // than a free choice.** `cargo` is fixed at dispatch because it is the promise the sheet made
    // before the tap; `returnsAt` is stored for the same reason. These two only *split* that stored
    // span, and nothing in `advance` reads them — the presentation layer draws a phase from them.
    //
    // What it costs: a drive level completing mid-flight moves the split for a run already out, so a
    // card can show "returning" a little before the hull really turned round. The alternative is a
    // sixth field on this record and a migration that reconstructs the flight of every run in every
    // existing save, which is real save-format weight bought for a progress bar. **The error is
    // bounded and self-correcting** — levels only ever go up, so the split only ever moves inward
    // from both ends, it can never escape the window, and it is gone the moment the run lands.
    // The run's *own* manifest sets its clock — a hauler in it flies the whole run at the hauler's
    // pace — so unlike the research this needs no parameter: it is already stored.
    fun flightEndsAt(from: GalaxyCoordinate, research: Research): Instant =
        dispatchedAt + FleetBalance.flight(from, target, research, ships)

    fun inboundBeginsAt(from: GalaxyCoordinate, research: Research): Instant =
        returnsAt - FleetBalance.flight(from, target, research, ships)
}

// One hull on the slipway, and the sixth kind of job — Davide's call, 2026-08-13, overruling the
// fleet sheet's *"purchase is instant, and that is a sizing decision"*.
//
// **One entry per hull rather than one per order**, although `buildShips` takes a whole manifest.
// The price already walks the curve hull by hull, so the wait — which is taken from that price — has
// to as well: three skiffs ordered together are three different jobs of three different lengths, and
// an entry holding a `Ships` would have to carry a duration that is the sum of three rungs and then
// deliver them all at the end. A player who ordered three would watch nothing arrive for six hours
// instead of watching one arrive every two.
//
// It **does** take the `(subject, startedAt, completesAt)` shape the builds, the projects and the
// probes share, unlike `FleetRun` which carries its own outcome. There is no outcome here: the
// outcome is the hull, and the hull is the subject.
//
// **Absolute instants, chained at the order, rather than a duration served from the head.** The
// queue is serial, so entry n's start is entry n−1's finish — and storing that as arithmetic rather
// than as two stored instants would make `advance` re-derive the whole queue at every boundary and
// `futureEvents` promise to derive it the same way. Every other job in this game carries the instant
// it completes at; the queue is what puts them in order, not what computes them.
@Serializable
data class YardJob(
    val ship: ShipType,
    val startedAt: Instant,
    val completesAt: Instant,
) {
    init {
        require(completesAt > startedAt) {
            "a hull must finish after it was laid down: started $startedAt, completes $completesAt"
        }
    }
}
