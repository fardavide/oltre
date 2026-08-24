package dev.fardavide.oltre.core

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.Instant

// Who holds a world. In 0.2 the only empire is the player, because the three scripted empires are
// slice #9 — but `Occupied` has to carry a holder for the verdict to mean anything, and a holder
// that is a real identity now is one slice #9 populates rather than replaces.
@Serializable
@JvmInline
value class EmpireId(val value: String) {
    companion object {
        val PLAYER: EmpireId = EmpireId("player")
    }
}

// One world and who holds it. A record rather than a map entry because a `GalaxyCoordinate` is a
// structured key, which JSON cannot use as an object key at all — the alternative was to turn on
// `allowStructuredMapKeys` and have every ownership entry written as a flat [key, value] array,
// which changes how the *whole* save format encodes maps to buy an unreadable save.
@Serializable
data class WorldOwnership(val at: GalaxyCoordinate, val holder: EmpireId)

// What a world still holds, and when that was last true. **An absent entry is a full world**, which
// is the rule that answers `fleet-sheet.md`'s objection to this whole mechanic — *"a counter for
// every world ever visited is a save that grows without bound."* An entry exists only while a world
// is short of full, a world is full again twenty days after it was stripped, and `prunedFull` drops
// it the moment it is. So the list holds worlds worked in the last twenty days and nothing else.
//
// Stored in fine units for `Resources`' own reason — five hundredths of a cap per day is not a whole
// number of units, and truncation would break the refill the way it would break accrual.
//
// One instant for both deposits rather than one each: `withTaken` brings both to `at` before it
// debits either, so the pair is always current as of the same moment.
@Serializable
data class WorldDeposit(
    val at: GalaxyCoordinate,
    val metalFine: Long,
    val crystalFine: Long,
    val asOf: Instant,
) {
    init {
        require(metalFine >= 0) { "a metal deposit cannot be negative, was $metalFine" }
        require(crystalFine >= 0) { "a crystal deposit cannot be negative, was $crystalFine" }
    }

    internal fun fineOf(gathering: ResourceKind): Long = when (gathering) {
        ResourceKind.METAL -> metalFine
        ResourceKind.CRYSTAL -> crystalFine
        ResourceKind.DEUTERIUM -> error("a world holds no deuterium deposit")
    }
}

// **How far the light reaches in one galaxy** — the ends of what the player has charted there, and
// the whole of what fog stores. Two integers a galaxy, four galaxies, eight numbers on day one and
// eight after a year.
//
// An interval rather than a set of flags, because the ribbon's path order *is* index order: a rule
// written in indices is a contiguous stretch of the drawn line, which is the thing a player can
// point at and call the area they opened. Two hundred and fifty flags would be a save that grows
// and a map with no areas in it.
//
// **Keyed on where a hull came down, never on what it found**, and that is the load-bearing half.
// `surveyed` records findings; fog is about journeys, and the two are the same object only when
// every journey finds something. A landing on a system whose fifteen slots are all empty writes
// nothing to `surveyed` — so a span derived from it would silently refuse to move, on the one
// flight the player most wants to have counted.
@Serializable
data class ChartedSpan(val galaxy: Int, val lo: Int, val hi: Int) {
    init {
        require(galaxy in 1..GalaxyBalance.GALAXIES) { "there is no galaxy $galaxy" }
        require(lo in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) { "there is no system $lo" }
        require(hi in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) { "there is no system $hi" }
        require(lo <= hi) { "a charted span cannot run backwards, was $lo..$hi" }
    }

    val systems: Int get() = hi - lo + 1

    operator fun contains(system: Int): Boolean = system in lo..hi
}

// **The galaxy is never serialised.** 4,700 worlds of traits would dwarf the entire rest of the
// snapshot, so what the save carries is the seed plus what the player has changed: which worlds
// they have surveyed, who holds what, and what has been taken out of them. Every trait is
// regenerated from the seed on demand — see `GalaxyGeneration.kt`, and the locality property that
// makes it affordable.
@Serializable
data class GalaxyState(
    val seed: GalaxySeed,
    val home: GalaxyCoordinate,
    // Per world rather than per system, because surveying is a per-world fleet action from slice #7
    // onwards. At genesis this holds the worlds of the home system and nothing else, so almost
    // everything on the screen reads `Unsurveyed` — the honest state, not a placeholder.
    val surveyed: Set<GalaxyCoordinate>,
    val ownership: List<WorldOwnership>,
    // Sparse, and pruned back to nothing as worlds refill. See `WorldDeposit`.
    val deposits: List<WorldDeposit>,
    // **The only thing the galaxy identity slice writes to disk.** Names, epithets, portraits,
    // regions and the ledger's own sort and filters are all derived; a pin is the one fact about the
    // map that exists nowhere but in the player's head until it is stored.
    //
    // A set of coordinates, which is `surveyed`'s shape rather than `ownership`'s — a pin carries
    // nothing beside itself. Bounded by the player's own patience, so `WorldDeposit`'s objection to
    // per-world records ("a save that grows without bound") does not reach it: nothing pins a world
    // except a tap.
    //
    // **Filters and the sort deliberately do not persist.** Claude Design, 2026-08-14: *"a filter
    // that outlives the check-in that set it is a screen lying about what it holds."*
    val pinned: Set<GalaxyCoordinate>,
    // **The fog, and it is the whole of it.** One entry per galaxy a hull has actually landed in —
    // so an untouched galaxy is *absent* rather than empty, which is the difference between "you
    // have been here and charted nothing" and "you have never been". See `ChartedSpan`.
    //
    // Last in the constructor deliberately: `GameSaveTest.the on-disk shape is pinned` asserts the
    // encoded string, and kotlinx writes fields in declaration order, so appending is an append.
    val charted: List<ChartedSpan>,
) {
    init {
        require(ownership.distinctBy { it.at }.size == ownership.size) {
            "a world cannot have two holders, was $ownership"
        }
        // A pin is a bookmark into what you know, and the ledger — the only surface that shows one —
        // draws a pinned world as a full row. A pin on an unsurveyed coordinate is therefore a row
        // the screen cannot draw, so it is refused here rather than filtered there.
        require(pinned.all { it in surveyed }) {
            "a world cannot be pinned before it is surveyed, was ${pinned - surveyed}"
        }
        require(holderOf(home) == EmpireId.PLAYER) {
            "the home world must be held by the player, was ${holderOf(home)}"
        }
        require(deposits.distinctBy { it.at }.size == deposits.size) {
            "a world cannot have two deposits, was ${deposits.map { it.at }}"
        }
        require(charted.distinctBy { it.galaxy }.size == charted.size) {
            "a galaxy cannot have two charted spans, was ${charted.map { it.galaxy }}"
        }
    }

    // How deep this world's vein is when it is full — null where there is no world at all. The
    // danger is measured from `home`, which is what makes the cap and the rate carry one multiplier;
    // `DepositBalance.cap` states what that costs and why it is worth it.
    fun depositCap(at: GalaxyCoordinate, gathering: ResourceKind): Long? {
        val world = worldAt(seed, at) ?: return null
        return DepositBalance.cap(world, gathering, FleetBalance.danger(from = home, world = world))
    }

    // What is in the ground right now, in whole units — the one figure the row and the sheet both
    // read. Zero where there is no world; the full cap where nobody has worked it.
    fun remaining(at: GalaxyCoordinate, gathering: ResourceKind, now: Instant): Long =
        remainingFine(at, gathering, now) / Resources.FINE_PER_UNIT

    private fun remainingFine(at: GalaxyCoordinate, gathering: ResourceKind, now: Instant): Long {
        val capFine = (depositCap(at, gathering) ?: return 0) * Resources.FINE_PER_UNIT
        val entry = deposits.firstOrNull { it.at == at } ?: return capFine
        return DepositBalance.regenerated(entry.fineOf(gathering), capFine, now - entry.asOf)
    }

    // When this world will hold `wanted` of a resource — zero if it already does, and **null when it
    // never will**, because the ask is bigger than the world. The null is the dispatch sheet's
    // waiting state doing its job: a full fleet's ask is routinely one no world can satisfy, and the
    // honest answer is "ask for less" rather than a date.
    //
    // Here rather than on `DepositBalance` so that fine units stay inside `core`. A caller in
    // presentation asks in whole units, which is what every figure it prints is in.
    fun timeUntil(at: GalaxyCoordinate, gathering: ResourceKind, wanted: Long, now: Instant): Duration? {
        val cap = depositCap(at, gathering) ?: return null
        return DepositBalance.timeUntil(
            storedFine = remainingFine(at, gathering, now),
            capFine = cap * Resources.FINE_PER_UNIT,
            wanted = wanted,
        )
    }

    // The debit, applied at dispatch. Both deposits are brought forward to `at` before either is
    // touched, so the record's single instant is honest about both.
    fun withTaken(
        target: GalaxyCoordinate,
        gathering: ResourceKind,
        taken: Long,
        at: Instant,
    ): GalaxyState {
        require(taken >= 0) { "a run cannot take a negative hold, was $taken" }
        val available = remaining(target, gathering, at)
        require(taken <= available) { "a run cannot take $taken from a world holding $available" }
        val takenFine = taken * Resources.FINE_PER_UNIT
        val metalFine = remainingFine(target, ResourceKind.METAL, at)
        val crystalFine = remainingFine(target, ResourceKind.CRYSTAL, at)
        val entry = WorldDeposit(
            at = target,
            metalFine = if (gathering == ResourceKind.METAL) metalFine - takenFine else metalFine,
            crystalFine = if (gathering == ResourceKind.CRYSTAL) crystalFine - takenFine else crystalFine,
            asOf = at,
        )
        return copy(deposits = deposits.filterNot { it.at == target } + entry)
    }

    // **Monotone, which is what lets `advance` do it at the end of a span.** A world that is full at
    // one instant is full at every later one, so pruning at each boundary and pruning once at the end
    // land on the same state — and the composability property survives a second thing being settled
    // after the span rather than inside it.
    fun prunedFull(now: Instant): GalaxyState {
        if (deposits.isEmpty()) return this
        val kept = deposits.filterNot { entry ->
            ResourceKind.entries.filter { it != ResourceKind.DEUTERIUM }.all { kind ->
                val capFine = (depositCap(entry.at, kind) ?: 0) * Resources.FINE_PER_UNIT
                DepositBalance.regenerated(entry.fineOf(kind), capFine, now - entry.asOf) >= capFine
            }
        }
        return if (kept.size == deposits.size) this else copy(deposits = kept)
    }

    // Linear over a list that holds one entry in 0.2 and a handful once slice #9 lands three
    // scripted empires. If it ever holds enough to matter, the fix is an index built here, not a
    // map on disk.
    fun holderOf(at: GalaxyCoordinate): EmpireId? = ownership.firstOrNull { it.at == at }?.holder

    // Whether a probe would learn anything there. Asked of the *occupied* slots only, because an
    // empty slot has nothing to know — so a system whose worlds are all known is complete even
    // though eleven of its fifteen slots are absent from the set.
    fun hasSurveyed(system: SystemAddress): Boolean =
        occupiedWorldsIn(seed, system).all { it in surveyed }

    // How far the light reaches in one galaxy, or null where no hull has ever landed in it.
    fun spanIn(galaxy: Int): ChartedSpan? = charted.firstOrNull { it.galaxy == galaxy }

    // **Whether the light reaches this star at all**, and note what it is not: unlike `hasSurveyed`
    // directly above, this is never vacuously true. A span contains an index or it does not, so a
    // system with nothing around it is uncharted exactly as long as anything else is.
    fun hasCharted(system: SystemAddress): Boolean =
        spanIn(system.galaxy)?.let { system.system in it } == true

    // How many of a galaxy's systems the player has charted. Zero where nothing has landed.
    fun chartedCountIn(galaxy: Int): Int = spanIn(galaxy)?.systems ?: 0

    // **The widen, and it is the only writer.** Idempotent and monotone: a landing inside what is
    // already charted returns `this` unchanged, which is what lets `advance` apply it at every
    // boundary without the composability property caring where the boundary fell.
    fun withCharted(system: SystemAddress): GalaxyState {
        val reached = ChartedSpan(
            galaxy = system.galaxy,
            lo = (system.system - SurveyBalance.GRACE_SYSTEMS).coerceAtLeast(1),
            hi = (system.system + SurveyBalance.GRACE_SYSTEMS)
                .coerceAtMost(GalaxyBalance.SYSTEMS_PER_GALAXY),
        )
        val held = spanIn(system.galaxy)
        val widened = when (held) {
            null -> reached
            else -> ChartedSpan(
                galaxy = system.galaxy,
                lo = minOf(held.lo, reached.lo),
                hi = maxOf(held.hi, reached.hi),
            )
        }
        if (widened == held) return this
        // Sorted because list order is on disk and `GameSaveTest` pins the byte string.
        return copy(
            charted = (charted.filterNot { it.galaxy == system.galaxy } + widened).sortedBy { it.galaxy },
        )
    }

    // **The fog yield** — how many systems a landing here would add to the map, which is what the
    // caption quotes on an uncharted star. Defined as a difference of the widen rather than
    // re-derived, so the number the player is shown and the number the landing writes cannot drift.
    fun wouldChart(system: SystemAddress): Int =
        withCharted(system).chartedCountIn(system.galaxy) - chartedCountIn(system.galaxy)

    companion object {

        // Genesis. The seed comes from outside core — the shell mints it, because core cannot read
        // a clock or a random source — and everything else about the map follows from it.
        fun initial(seed: GalaxySeed): GalaxyState {
            val home = homeFor(seed)
            return GalaxyState(
                seed = seed,
                home = home,
                surveyed = occupiedWorldsIn(seed, SystemAddress.of(home)),
                ownership = listOf(WorldOwnership(at = home, holder = EmpireId.PLAYER)),
                // Nothing has been taken out of anything yet, and an empty list says exactly that.
                deposits = emptyList(),
                // Nobody has pinned anything on their first launch, and the ledger's genesis frame
                // is five rows with no PINNED section — which is the honest state rather than an
                // empty heading.
                pinned = emptySet(),
                // Empty, and then widened once below — so the clamp arithmetic lives in exactly one
                // place and genesis is not a special case of the rule but an instance of it.
                charted = emptyList(),
            ).withCharted(SystemAddress.of(home))
        }

        // Every slot of a system that actually holds a world. Genesis surveys the home system with
        // it — you can see your own neighbours, and nothing else — and a landing probe surveys its
        // target with the same call. That those two are one function is not a tidy-up: the set a
        // survey writes has to be exactly the set the player already has for their own system, or
        // "surveyed" would quietly mean two different things depending on how it got there.
        //
        // Only occupied slots are recorded, so the set stays the handful of entries a system really
        // has rather than a fixed fifteen.
        internal fun occupiedWorldsIn(seed: GalaxySeed, system: SystemAddress): Set<GalaxyCoordinate> =
            (1..GalaxyBalance.SLOTS_PER_SYSTEM)
                .map { slot -> GalaxyCoordinate(galaxy = system.galaxy, system = system.system, slot = slot) }
                .filter { at -> worldAt(seed, at) != null }
                .toSet()
    }
}
