package dev.fardavide.oltre.sim

import dev.fardavide.oltre.core.AdaptationBalance
import dev.fardavide.oltre.core.AdaptationLevels
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GalaxyState
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.HostilityAxis
import dev.fardavide.oltre.core.PlaceholderBalance
import dev.fardavide.oltre.core.ResearchBalance
import dev.fardavide.oltre.core.StarClass
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.Uniform
import dev.fardavide.oltre.core.WorldVerdict
import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.core.axisValue
import dev.fardavide.oltre.core.relayAt
import dev.fardavide.oltre.core.starClassAt
import dev.fardavide.oltre.core.startUpgrade
import dev.fardavide.oltre.core.verdictFor
import dev.fardavide.oltre.core.worldAt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// The harness is a balancing tool, so its galaxy is fixed rather than random: a report you cannot
// reproduce is not a measurement.
private const val SIM_GALAXY_SEED: Long = 20_260_807

// Headless balancing harness. Never ships. Prints the curve table that `.claude/docs/balance-log.md`
// carries, then fast-forwards a week of a greedy strategy that, once per simulated hour, starts
// every mine-or-plant upgrade it can afford — cheapest first, since builds now run in parallel
// and each start eats into the same stock.
fun main() {
    printCurveTable()
    printResearchTable()
    printAdaptationTable()
    printGalaxyReport()
    printGreedyWeek()
}

// The galaxy's actual distribution against the decision sheet's section 9 targets. Those targets
// are the design: if the constants miss them, the sheet says the constants move, not the targets.
// So this prints the measurement that decides it rather than the numbers anyone hoped for.
private fun printGalaxyReport() {
    val seed = GalaxySeed(SIM_GALAXY_SEED)
    val worlds = buildList {
        for (galaxy in 1..GalaxyBalance.GALAXIES) {
            for (system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
                for (slot in 1..GalaxyBalance.SLOTS_PER_SYSTEM) {
                    worldAt(seed, GalaxyCoordinate(galaxy, system, slot))?.let(::add)
                }
            }
        }
    }

    println("## Galaxy distribution")
    println()
    println("Seed $SIM_GALAXY_SEED, the whole coordinate space: ${GalaxyBalance.TOTAL_SLOTS.grouped()} slots hold " +
        "${worlds.size.grouped()} worlds (${percent(worlds.size, GalaxyBalance.TOTAL_SLOTS)} of slots).")
    println()

    val unaided = GalaxyBalance.tolerance(AdaptationLevels.NONE)
    val failureCounts = worlds.map { world ->
        HostilityAxis.entries.count { axis -> world.traits.axisValue(axis) !in unaided.bandOf(axis) }
    }
    val passes = failureCounts.count { it == 0 }
    val failsOne = failureCounts.count { it == 1 }
    val failsMore = failureCounts.count { it >= 2 }
    val worthTaking = worlds.count { world ->
        HostilityAxis.entries.all { axis -> world.traits.axisValue(axis) in unaided.bandOf(axis) } &&
            GalaxyBalance.yieldScore(world.traits).perMillion >= GalaxyBalance.WORTH_IT_THRESHOLD.perMillion
    }

    println("| Outcome | Target | Actual | Count |")
    println("|---|---|---|---|")
    println("| Passes every band | 1 – 2% | ${percent(passes, worlds.size)} | ${passes.grouped()} |")
    println("| Fails exactly one axis | 35 – 45% | ${percent(failsOne, worlds.size)} | ${failsOne.grouped()} |")
    println("| Fails two or three | the rest | ${percent(failsMore, worlds.size)} | ${failsMore.grouped()} |")
    println("| Passes and clears 0.90 | <= 0.5% | ${percent(worthTaking, worlds.size)} | ${worthTaking.grouped()} |")
    println()

    println("Per-axis pass rate at adaptation level 0 — the three numbers the two rows above are made of:")
    println()
    println("| Axis | Tolerated | Passes | Rich in |")
    println("|---|---|---|---|")
    for (axis in HostilityAxis.entries) {
        val band = unaided.bandOf(axis)
        val passing = worlds.count { it.traits.axisValue(axis) in band }
        println("| $axis | ${band.min} … ${band.max} | ${percent(passing, worlds.size)} | ${axis.richResource} |")
    }
    println()

    val passingYields = worlds
        .filter { world -> HostilityAxis.entries.all { world.traits.axisValue(it) in unaided.bandOf(it) } }
        .map { GalaxyBalance.yieldScore(it.traits).perMillion }
        .sorted()
    if (passingYields.isNotEmpty()) {
        println("Yield of a world that passes every band: median ${yieldLabel(passingYields[passingYields.size / 2])}, " +
            "best ${yieldLabel(passingYields.last())}, threshold ${yieldLabel(GalaxyBalance.WORTH_IT_THRESHOLD.perMillion)}. " +
            "The median must sit below the threshold, or surveying stops being a decision.")
        println()
    }

    println("What a level of adaptation buys — the sheet asks each one to roughly double the settleable count:")
    println()
    println("| All three ladders at | Passes every band | Settleable |")
    println("|---|---|---|")
    for (level in 0..5) {
        val tolerance = GalaxyBalance.tolerance(AdaptationLevels(level, level, level))
        val passing = worlds.filter { world ->
            HostilityAxis.entries.all { axis -> world.traits.axisValue(axis) in tolerance.bandOf(axis) }
        }
        val settleable = passing.count {
            GalaxyBalance.yieldScore(it.traits).perMillion >= GalaxyBalance.WORTH_IT_THRESHOLD.perMillion
        }
        println("| $level | ${passing.size.grouped()} (${percent(passing.size, worlds.size)}) | ${settleable.grouped()} |")
    }
    println()

    val hazardous = worlds.count { it.traits.hazards.isNotEmpty() }
    val relays = (1..GalaxyBalance.SYSTEMS_PER_GALAXY).count { relayAt(seed, galaxy = 1, system = it) != null }
    val home = GalaxyState.initial(seed).home
    println("Hazards on ${percent(hazardous, worlds.size)} of worlds. " +
        "Relays in $relays of ${GalaxyBalance.SYSTEMS_PER_GALAXY} systems of galaxy 1. " +
        "Home is [${home.galaxy}:${home.system}:${home.slot}].")
    println()

    printHomeSystem(seed)
}

// The four worlds the player can actually see on day one. Every other world on the Galaxy screen
// reads Unsurveyed, so these are the only surveyed rows that exist at ship time — which makes them
// the screen's real content rather than an example of it, and worth printing whenever the
// generation constants move.
private fun printHomeSystem(seed: GalaxySeed) {
    val galaxy = GalaxyState.initial(seed)
    val home = galaxy.home

    println("## The home system, world by world")
    println()
    println("Seed ${seed.value}, system ${home.galaxy}:${home.system}, " +
        "${starClassAt(seed, home.galaxy, home.system)} star. Surveyed at genesis; nothing else is.")
    println()
    println("| Coordinate | Verdict | Temp | Gravity | Pressure | Fields | Yield | Hazards |")
    println("|---|---|---|---|---|---|---|---|")
    for (slot in 1..GalaxyBalance.SLOTS_PER_SYSTEM) {
        val at = GalaxyCoordinate(galaxy = home.galaxy, system = home.system, slot = slot)
        val world = worldAt(seed, at) ?: continue
        val traits = world.traits
        val verdict = when (val v = verdictFor(world, galaxy, AdaptationLevels.NONE)) {
            is WorldVerdict.Settleable -> "Settleable"
            is WorldVerdict.Blocked -> "Blocked (${v.failures.size})"
            is WorldVerdict.Occupied -> "Occupied"
            WorldVerdict.Home -> "Home"
            WorldVerdict.Barren -> "Barren"
            WorldVerdict.Unsurveyed -> "Unsurveyed"
        }
        println(
            "| [${at.galaxy}:${at.system}:${at.slot}] | $verdict | ${traits.temperature.celsius} °C " +
                "| ${milli(traits.gravity.milliG)} g | ${milli(traits.pressure.milliAtm)} atm " +
                "| ${traits.fields} | ${yieldLabel(GalaxyBalance.yieldScore(traits).perMillion)} " +
                "| ${traits.hazards.joinToString().ifEmpty { "none" }} |",
        )
    }
    println()

    // Every sentence a `Blocked` row would render, which is the detail the screen is built around.
    for (slot in 1..GalaxyBalance.SLOTS_PER_SYSTEM) {
        val at = GalaxyCoordinate(galaxy = home.galaxy, system = home.system, slot = slot)
        val world = worldAt(seed, at) ?: continue
        val blocked = verdictFor(world, galaxy, AdaptationLevels.NONE) as? WorldVerdict.Blocked ?: continue
        for (failure in blocked.failures) {
            val axis = failure.axis.name.lowercase()
            println("[${at.galaxy}:${at.system}:${at.slot}] $axis ${failure.worldValue}, " +
                "you tolerate ${failure.toleratedBound} — ${failure.axis.adaptation.name.lowercase()
                    .replaceFirstChar { it.uppercase() }} ${failure.closedAtLevel} would land it")
        }
    }
    println()
}

private fun milli(value: Int): String =
    "${value / 1_000}.${(value % 1_000).toString().padStart(3, '0')}"

private fun percent(part: Int, whole: Int): String {
    if (whole == 0) return "0.00%"
    val hundredths = part.toLong() * 10_000 / whole
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}%"
}

private fun yieldLabel(perMillion: Int): String =
    "${perMillion / 1_000_000}.${(perMillion % 1_000_000 / 10_000).toString().padStart(2, '0')}"

private fun Int.grouped(): String = toLong().grouped()

// The research branch's own tables, regenerated the same way the building curves are. The numbers
// come from the approved 0.1 decision sheet, so this prints what the game charges rather than what
// the sheet claimed — if the two ever disagree, that is the bug.
private fun printResearchTable() {
    println("## Research curves")
    println()
    for (technology in Technology.entries) {
        val requirement = ResearchBalance.requirementFor(technology)
        println("### $technology — requires $requirement")
        println()
        println("| Level | effect | metal | crystal | deuterium | at Robotics 0 | at Robotics 4 |")
        println("|---|---|---|---|---|---|---|")
        for (level in 1..10) {
            val techLevel = TechLevel(level)
            val cost = ResearchBalance.researchCost(technology, techLevel)
            println(
                "| $level | +${ResearchBalance.effectPercent(technology, techLevel)}% " +
                    "| ${cost.metal.grouped()} | ${cost.crystal.grouped()} | ${cost.deuterium.grouped()} " +
                    "| ${ResearchBalance.researchDuration(technology, techLevel, BuildingLevel(0)).label()} " +
                    "| ${ResearchBalance.researchDuration(technology, techLevel, BuildingLevel(4)).label()} |",
            )
        }
        println()
    }
}

// The adaptation branch's table, printed the same way and for the same reason. What a level *buys*
// is not here — it is in the galaxy report above, which already prints the settleable count at each
// level; this is what reaching that level costs and how long it takes, which is the half that was
// missing until 0.0.17.
//
// The priced column is the point of the table: the three ladders cost exactly the same at the
// game's 1 : 2 : 3, in three different currencies. If that column ever stops being flat across the
// three, the sheet's §4 argument has quietly stopped being true.
private fun printAdaptationTable() {
    println("## Adaptation ladders")
    println()
    println("Requires ${AdaptationBalance.requirementFor(AdaptationTechnology.THERMAL)}, all three.")
    println()
    for (technology in AdaptationTechnology.entries) {
        println("### $technology")
        println()
        println("| Level | metal | crystal | deuterium | priced 1:2:3 | at Robotics 4 | at Robotics 8 |")
        println("|---|---|---|---|---|---|---|")
        for (level in 1..10) {
            val techLevel = TechLevel(level)
            val cost = AdaptationBalance.adaptationCost(technology, techLevel)
            val priced = cost.metal + 2 * cost.crystal + 3 * cost.deuterium
            println(
                "| $level | ${cost.metal.grouped()} | ${cost.crystal.grouped()} | ${cost.deuterium.grouped()} " +
                    "| ${priced.grouped()} " +
                    "| ${AdaptationBalance.adaptationDuration(technology, techLevel, BuildingLevel(4)).label()} " +
                    "| ${AdaptationBalance.adaptationDuration(technology, techLevel, BuildingLevel(8)).label()} |",
            )
        }
        println()
    }
    // Where each ladder stops buying anything, measured off the *published* extremes of the
    // generator rather than off a sample — a sampled galaxy can miss its own coldest world by a
    // degree and report a level too low. `AdaptationBalanceTest` pins the same three numbers.
    println("Saturation — past this level every world the generator can produce already passes:")
    println()
    println("| Ladder | Saturates at |")
    println("|---|---|")
    for ((axis, extremes) in AXIS_EXTREMES) {
        val saturates = extremes.maxOf { GalaxyBalance.levelThatTolerates(axis, it) }
        println("| ${axis.adaptation} | $saturates |")
    }
    println()
}

// The ends of each axis's published range, from the sheet's §8 generation table: temperature is
// `220 - 28 x slot + starOffset + jitter` at its two corners, gravity is `0.15 + 2.6 u²` and
// pressure is `12 u³` at u = 0 and u = 1.
private val AXIS_EXTREMES: List<Pair<HostilityAxis, List<Int>>> = listOf(
    HostilityAxis.TEMPERATURE to listOf(
        GalaxyBalance.temperature(GalaxyBalance.SLOTS_PER_SYSTEM, StarClass.DIM, -GalaxyBalance.TEMPERATURE_JITTER)
            .celsius,
        GalaxyBalance.temperature(1, StarClass.BRIGHT, GalaxyBalance.TEMPERATURE_JITTER).celsius,
    ),
    HostilityAxis.GRAVITY to listOf(
        GalaxyBalance.gravity(Uniform(0)).milliG,
        GalaxyBalance.gravity(Uniform.MAX).milliG,
    ),
    HostilityAxis.PRESSURE to listOf(
        GalaxyBalance.pressure(Uniform(0)).milliAtm,
        GalaxyBalance.pressure(Uniform.MAX).milliAtm,
    ),
)

// Rounded to the nearest minute, matching how the decision sheet's tables are written. The UI
// ceils instead, so a chip can read a minute longer — deliberate, and the colony's convention.
private fun Duration.label(): String {
    val totalMinutes = (inWholeSeconds + 30) / 60
    return "${totalMinutes / 60}h ${(totalMinutes % 60).toString().padStart(2, '0')}m"
}

// The balance log is only useful if its numbers can be regenerated rather than retyped. Markdown
// rows, so a round of tuning is a copy-paste rather than an arithmetic exercise.
private fun printCurveTable() {
    println("## Current curves")
    println()
    println("| Level | metal/h | crystal/h | deut/h | metal mine cost (m/c) | payback of the next level |")
    println("|---|---|---|---|---|---|")
    for (level in listOf(1, 2, 3, 5, 8, 10, 12, 15, 18, 20)) {
        val here = BuildingLevel(level)
        val next = BuildingLevel(level + 1)
        val cost = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, here)
        val gain = PlaceholderBalance.metalProductionPerHour(next) -
            PlaceholderBalance.metalProductionPerHour(here)
        val payback = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, next).metal / gain
        println(
            "| $level | ${PlaceholderBalance.metalProductionPerHour(here).grouped()} " +
                "| ${PlaceholderBalance.crystalProductionPerHour(here).grouped()} " +
                "| ${PlaceholderBalance.deuteriumProductionPerHour(here).grouped()} " +
                "| ${cost.metal.grouped()} / ${cost.crystal.grouped()} | ${payback}h |",
        )
    }
    val daily = listOf(1, 5, 10, 15)
        .joinToString(", ") { (PlaceholderBalance.metalProductionPerHour(BuildingLevel(it)) * 24).grouped() }
    println()
    println("Daily metal at levels 1, 5, 10, 15: $daily.")
    val starting = GameState.initial(GalaxySeed(SIM_GALAXY_SEED)).resources
    println("Starting stock: ${starting.metal.grouped()} metal, ${starting.crystal.grouped()} crystal.")
    println()
}

private fun Long.grouped(): String = toString().reversed().chunked(3).joinToString(",").reversed()

private fun printGreedyWeek() {
    val start = Instant.fromEpochMilliseconds(0)
    var state = GameState.initial(GalaxySeed(SIM_GALAXY_SEED))
    var now = start
    // A power shortage scales every mine silently, so a week that looks slow can be a week spent
    // throttled rather than a curve that is too flat. Counting it is what tells the two apart.
    var throttledHours = 0

    repeat(7 * 24) {
        val next = now + 1.hours
        state = advance(state, from = now, to = next)
        now = next
        if (PlaceholderBalance.energyBalance(state.buildings, state.research).isDeficit) throttledHours++

        val candidates = listOf(BuildingType.METAL_MINE, BuildingType.CRYSTAL_MINE, BuildingType.SOLAR_PLANT)
        candidates
            .map { it to PlaceholderBalance.upgradeCost(it, BuildingLevel(state.buildings.levelOf(it).value + 1)) }
            .sortedBy { (_, cost) -> cost.metal + cost.crystal }
            .forEach { (building, cost) ->
                if (!state.resources.covers(cost)) return@forEach
                when (val result = startUpgrade(state, building, at = now)) {
                    is StartUpgradeResult.Started -> state = result.state
                    StartUpgradeResult.AlreadyUpgrading,
                    StartUpgradeResult.InsufficientResources,
                    StartUpgradeResult.RequirementsNotMet,
                    -> Unit // already building this facility, or outbid by an earlier start
                }
            }
    }

    val energy = PlaceholderBalance.energyBalance(state.buildings, state.research)
    println("after 7 days (parallel builds):")
    println("  metal=${state.resources.metal} crystal=${state.resources.crystal} deuterium=${state.resources.deuterium}")
    println("  buildings=${state.buildings}")
    println("  energy=${energy.produced}/${energy.consumed} (mines at ${energy.outputPercent}%)")
    println("  hours throttled by power: $throttledHours of ${7 * 24}")
    println("  events=${state.eventLog.size} (starts + completions)")
}
