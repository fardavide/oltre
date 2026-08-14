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
            )
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
