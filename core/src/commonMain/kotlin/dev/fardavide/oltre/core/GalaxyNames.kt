package dev.fardavide.oltre.core

// **Places, rather than addresses.** A coordinate is what a thing *is filed under*; a name is what
// you remember it by, and until 0.11 the map had only the first. `galaxy-identity-sheet.md` §2.
//
// Three properties, and each one is a decision rather than an implementation detail:
//
// 1. **Nothing is stored.** A name is regenerated from the seed exactly like a trait, so 4,700 of
//    them cost the save zero bytes and `galaxy-sheet.md` §7 is untouched.
// 2. **The region supplies the palette, not a prefix.** Systems in a region rhyme in *character* —
//    a Deep's names long and soft, a Burning's short and hard — which places you without two
//    systems reading as near-typos of each other. A literal shared prefix does the opposite.
// 3. **Uniqueness inside a region is structural.** Two of a name's three parts are pure functions
//    of the system's index within its region, five by five, which is exactly the 25 systems a
//    region holds. So a collision inside a region cannot be generated, rather than being unlikely.
//    Across regions it is allowed and is arguably right: Italy has more than one Marina di
//    Something.
//
// **The player cannot rename anything** — Davide's call, 2026-08-14, deferred to colonisation.
// Recorded honestly in the sheet as the strongest identity lever deliberately not pulled: a name
// you chose is the difference between an address and Fontane Bianche, and the reason to wait is
// that a name given to a world you cannot own yet is a name for a rock you visit.
object GalaxyNames {

    // Eight heads a palette, drawn from the stream, so the variety across a region comes from here
    // while the uniqueness comes from the two index-derived parts below.
    // **Every head ends on a consonant and every mid is a bare vowel**, which is not a stylistic
    // note but the thing that keeps 200 combinations readable: the first cut had a vowel-ending
    // head and an `au` mid in the same palette and generated `Oriiamira` and `Neraunova`. A palette
    // that can produce an unpronounceable name will produce one, because every combination is used.
    private val DEEP_HEADS = listOf("Cal", "Vel", "Mir", "Sel", "Tir", "Lum", "Alm", "Ner")
    private val SETTLED_HEADS = listOf("Ost", "Ard", "Ven", "Tal", "Corv", "Ely", "Bram", "Sor")
    private val BURNING_HEADS = listOf("Kar", "Tor", "Dax", "Zek", "Rax", "Krag", "Vok", "Tesh")

    // Five and five: `index / 5` and `index % 5` over the 0…24 of a region, which is injective and
    // is what makes the uniqueness above structural.
    private val DEEP_MIDS = listOf("a", "o", "e", "i", "ia")
    private val DEEP_TAILS = listOf("nova", "mira", "lis", "vae", "ren")

    private val SETTLED_MIDS = listOf("a", "e", "i", "o", "u")
    private val SETTLED_TAILS = listOf("ra", "dun", "mar", "tis", "vell")

    // Closed and short against the Deep's open and long, which is the whole of what a player hears.
    private val BURNING_MIDS = listOf("a", "e", "o", "u", "i")
    private val BURNING_TAILS = listOf("k", "x", "dra", "zon", "th")

    // The noun a region wears. A function of the temperament, so the label cannot lie about the
    // place — which is the difference between geography and decoration, and the reason
    // `galaxy-identity-sheet.md` §7 rejected regions that are names with no bias behind them.
    fun nounOf(temperament: RegionTemperament): String = when (temperament) {
        RegionTemperament.DEEP -> "Deep"
        RegionTemperament.SETTLED -> "Reach"
        RegionTemperament.BURNING -> "Blaze"
    }

    fun headsOf(temperament: RegionTemperament): List<String> = when (temperament) {
        RegionTemperament.DEEP -> DEEP_HEADS
        RegionTemperament.SETTLED -> SETTLED_HEADS
        RegionTemperament.BURNING -> BURNING_HEADS
    }

    internal fun midsOf(temperament: RegionTemperament): List<String> = when (temperament) {
        RegionTemperament.DEEP -> DEEP_MIDS
        RegionTemperament.SETTLED -> SETTLED_MIDS
        RegionTemperament.BURNING -> BURNING_MIDS
    }

    internal fun tailsOf(temperament: RegionTemperament): List<String> = when (temperament) {
        RegionTemperament.DEEP -> DEEP_TAILS
        RegionTemperament.SETTLED -> SETTLED_TAILS
        RegionTemperament.BURNING -> BURNING_TAILS
    }

    // Fifteen slots and no more, so the numeral is a table rather than an algorithm — and a table
    // cannot be wrong at XIV the way a hand-rolled converter can.
    internal val SLOT_NUMERALS = listOf(
        "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV",
    )

    // Eight heads, five mids, five tails.
    internal const val SPACE: Int = 8 * 5 * 5

    // Odd and not divisible by five, so each is coprime with 200 and `index * m + offset` really is
    // a bijection of the space rather than something that collapses part of it.
    internal val MULTIPLIERS = listOf(3, 7, 9, 11, 13, 17, 19, 21, 23, 27, 29, 31, 33, 37, 39, 41)

    // Where a region's own name is drawn from. A temperament covers at most four regions, so its
    // systems occupy indices 0…99 — starting the regions at 100 is what stops a region wearing the
    // name of a system inside it, which would read as the map repeating itself.
    internal const val REGION_NAME_FIRST: Int =
        GalaxyBalance.SYSTEMS_PER_REGION * GalaxyBalance.REGIONS_PER_GALAXY / 2
}

// A name is a point in the 8 x 5 x 5 palette space, reached by a **seeded bijection** of that space
// rather than by three independent draws. That is what buys galaxy-wide uniqueness: a temperament
// covers at most four regions, so at most 100 systems are mapped into 200 slots, injectively — and
// two temperaments share no syllable, so a collision cannot cross one either.
//
// The bijection is `index * multiplier + offset` modulo the space, with the multiplier kept coprime
// to 200 so it really is one. Both come from the galaxy's own NAMES stream, so a different seed
// names the same coordinates differently.
private fun nameIn(seed: GalaxySeed, galaxy: Int, temperament: RegionTemperament, index: Int): String {
    val stream = streamOf(galaxySeed(seed, galaxy), GenerationAxis.NAMES)
    val multiplier = GalaxyNames.MULTIPLIERS[draw(stream, 0).boundedBy(GalaxyNames.MULTIPLIERS.size)]
    val offset = draw(stream, 1).boundedBy(GalaxyNames.SPACE)
    val point = (index * multiplier + offset).mod(GalaxyNames.SPACE)

    return GalaxyNames.headsOf(temperament)[point / 25] +
        GalaxyNames.midsOf(temperament)[point % 25 / 5] +
        GalaxyNames.tailsOf(temperament)[point % 5]
}

fun systemNameAt(seed: GalaxySeed, galaxy: Int, system: Int): String {
    val temperaments = temperamentsOf(seed, galaxy)
    val region = regionOf(system)
    val temperament = temperaments[region - 1]
    // How many systems of this temperament come before this one. Regions are contiguous blocks of
    // 25, so this is the count of earlier regions sharing the temperament plus the offset inside
    // this one — ten comparisons, and no neighbour is generated to answer it.
    val earlierRegions = (0 until region - 1).count { temperaments[it] == temperament }
    val index = earlierRegions * GalaxyBalance.SYSTEMS_PER_REGION +
        (system - 1) % GalaxyBalance.SYSTEMS_PER_REGION

    return nameIn(seed, galaxy, temperament, index)
}

// "Calanova VII" — the system, then the numeral of the *slot* rather than of the world's rank among
// its neighbours. One numbering on the screen and not two: the map already spaces bodies by rank and
// labels them by slot, so a second ordinal would make those two disagree.
fun worldNameAt(seed: GalaxySeed, at: GalaxyCoordinate): String =
    "${systemNameAt(seed, at.galaxy, at.system)} ${GalaxyNames.SLOT_NUMERALS[at.slot - 1]}"

// "the Calanova Deep". Same grammar as a system, indexed by the region rather than by a system
// within it — two mids and five tails over ten regions, which is injective, so a galaxy cannot
// generate the same region name twice.
fun regionNameAt(seed: GalaxySeed, galaxy: Int, region: Int): String {
    val temperaments = temperamentsOf(seed, galaxy)
    val temperament = temperaments[region - 1]
    // Indexed past the systems of its own temperament, so a region never wears the name of one of
    // the systems inside it — which would read as the map repeating itself rather than as a place
    // named after its capital.
    val amongItsKind = (0 until region - 1).count { temperaments[it] == temperament }
    val index = GalaxyNames.REGION_NAME_FIRST + amongItsKind

    return "${nameIn(seed, galaxy, temperament, index)} ${GalaxyNames.nounOf(temperament)}"
}
