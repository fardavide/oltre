package dev.fardavide.oltre.sim

import dev.fardavide.oltre.core.AdaptationBalance
import dev.fardavide.oltre.core.AdaptationLevels
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.BuildShipsResult
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.Buildings
import dev.fardavide.oltre.core.DepositBalance
import dev.fardavide.oltre.core.Event
import dev.fardavide.oltre.core.FleetBalance
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GalaxyState
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.HostilityAxis
import dev.fardavide.oltre.core.PlaceholderBalance
import dev.fardavide.oltre.core.Research
import dev.fardavide.oltre.core.ResearchBalance
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.SurveyBalance
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.StarClass
import dev.fardavide.oltre.core.StartAdaptationResult
import dev.fardavide.oltre.core.StartResearchResult
import dev.fardavide.oltre.core.StartRunResult
import dev.fardavide.oltre.core.StartSurveyResult
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.Uniform
import dev.fardavide.oltre.core.World
import dev.fardavide.oltre.core.WorldTraits
import dev.fardavide.oltre.core.WorldVerdict
import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.core.axisValue
import dev.fardavide.oltre.core.buildShips
import dev.fardavide.oltre.core.futureEvents
import dev.fardavide.oltre.core.relayAt
import dev.fardavide.oltre.core.starClassAt
import dev.fardavide.oltre.core.startAdaptation
import dev.fardavide.oltre.core.startResearch
import dev.fardavide.oltre.core.startRun
import dev.fardavide.oltre.core.startSurvey
import dev.fardavide.oltre.core.startUpgrade
import dev.fardavide.oltre.core.verdictFor
import dev.fardavide.oltre.core.worldAt
import dev.fardavide.oltre.core.YardJob
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
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
    printEarlyBuildTable()
    printFirstSitting()
    printResearchTable()
    printAdaptationTable()
    printGalaxyReport()
    printDemandReport()
    printOpeningReport()
    printFleetReport()
    printDepositReport()
    printCheckInPressureReport()
    printInteractionCensus()
    printGateClock()
    printProgressionMilestones()
    printGreedyWeek()
    printWholeTreeRun()
}

// What the game *sells* against what the colony *makes*, in the same currency.
//
// The production ratio was set at 0.0.12 to match the early building tree's ~3:1 metal:crystal, and
// `BalanceCurveTest` still pins it there. Two branches have shipped since — applied research at
// 0.0.13 and the adaptation ladders at 0.0.17 — and neither costs anything like 3:1. This table is
// what says whether the ratio the mines were tuned against is still the ratio the game charges.
private fun printDemandReport() {
    println("## Demand against income")
    println()
    println("Every purchasable thing in the game at its base (level-1) cost, by branch. The last")
    println("column is the one that matters: how much metal the game asks for per unit of crystal.")
    println()
    println("| What you can buy | metal | crystal | deuterium | metal : crystal |")
    println("|---|---|---|---|---|")

    // The Nanite Factory is left out for the reason `BalanceCurveTest` leaves it out: at 20k/10k it
    // is a different economy and would drown the ratio it is not part of.
    val earlyTree = listOf(
        BuildingType.METAL_MINE,
        BuildingType.CRYSTAL_MINE,
        BuildingType.DEUTERIUM_SYNTHESIZER,
        BuildingType.SOLAR_PLANT,
        BuildingType.ROBOTICS_FACTORY,
    )
    val branches = listOf(
        "Buildings (the early tree)" to earlyTree.map { PlaceholderBalance.upgradeCost(it, BuildingLevel(1)) },
        "Applied research" to Technology.entries.map { ResearchBalance.researchCost(it, TechLevel(1)) },
        "Adaptation ladders" to AdaptationTechnology.entries.map { AdaptationBalance.adaptationCost(it, TechLevel(1)) },
    )
    for ((label, costs) in branches) {
        printDemandRow(label, costs)
    }
    printDemandRow("**Everything, together**", branches.flatMap { it.second })
    println()

    val metal = PlaceholderBalance.metalProductionPerHour(BuildingLevel(1))
    val crystal = PlaceholderBalance.crystalProductionPerHour(BuildingLevel(1))
    val deuterium = PlaceholderBalance.deuteriumProductionPerHour(BuildingLevel(1))
    println("Income, per hour at level 1 — and at every other level, since all three mines share " +
        "the one +25% curve: **$metal / $crystal / $deuterium**, a metal : crystal of ${ratio(metal, crystal)}.")
    println()

    // The two mines put side by side in one currency, which is the only way to compare them: they
    // cost different things and make different things. Priced at the game's own 1 : 2 : 3, the
    // question "which upgrade is the better buy" has a single answer at every level.
    println("### The two mines, priced against each other at 1 : 2 : 3")
    println()
    println("| Level | metal mine cost | pays back in | crystal mine cost | pays back in | crystal mine is |")
    println("|---|---|---|---|---|---|")
    for (level in listOf(1, 3, 5, 8, 10, 12, 15, 18, 20)) {
        val metalPayback = pricedPaybackHours(BuildingType.METAL_MINE, level)
        val crystalPayback = pricedPaybackHours(BuildingType.CRYSTAL_MINE, level)
        println(
            "| $level -> ${level + 1} | ${priced(nextCost(BuildingType.METAL_MINE, level)).grouped()} " +
                "| ${metalPayback}h | ${priced(nextCost(BuildingType.CRYSTAL_MINE, level)).grouped()} " +
                "| ${crystalPayback}h | ${ratio(crystalPayback, metalPayback)} worse |",
        )
    }
    println()
    println("Both curves are the same shape, so that last column is a constant: the crystal mine is")
    println("the worse buy by the same factor at level 1 and at level 20. A player following payback")
    println("never upgrades it, and a player who upgrades it anyway is paying a premium to do so.")
    println()
}

private fun printDemandRow(label: String, costs: List<Resources>) {
    val metal = costs.sumOf { it.metal }
    val crystal = costs.sumOf { it.crystal }
    val deuterium = costs.sumOf { it.deuterium }
    println("| $label | ${metal.grouped()} | ${crystal.grouped()} | ${deuterium.grouped()} | ${ratio(metal, crystal)} |")
}

private fun nextCost(building: BuildingType, level: Int): Resources =
    PlaceholderBalance.upgradeCost(building, BuildingLevel(level + 1))

// The game's own exchange rate, the one the research and adaptation sheets price everything at.
private fun priced(resources: Resources): Long =
    resources.metal + 2 * resources.crystal + 3 * resources.deuterium

private fun pricedPaybackHours(building: BuildingType, level: Int): Long {
    val perHour = { at: Int ->
        when (building) {
            BuildingType.CRYSTAL_MINE -> 2 * PlaceholderBalance.crystalProductionPerHour(BuildingLevel(at))
            else -> PlaceholderBalance.metalProductionPerHour(BuildingLevel(at))
        }
    }
    return priced(nextCost(building, level)) / (perHour(level + 1) - perHour(level))
}

// One decimal place, which is all the precision a ratio like this carries meaning at.
private fun ratio(numerator: Long, denominator: Long): String {
    if (denominator == 0L) return "n/a"
    val tenths = numerator * 10 / denominator
    return "${tenths / 10}.${tenths % 10} : 1"
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

    // ── Does the map give a probe a reason to prefer one target over another? ────────────────
    //
    // The question design call 1 turns on, and it can only be answered by measurement. A system
    // index enters none of `GalaxyBalance`'s trait functions, so the only thing that varies from
    // system to system is the **star class** — which is charted before anything is surveyed, and is
    // therefore the one prior a player could act on when aiming a probe. If the three classes
    // produce materially different worlds, the gradient already exists and the work is to surface
    // it. If they do not, "where to look" has one correct answer forever and the verb is scheduling
    // rather than exploration.
    println("### What a star class is worth knowing")
    println()
    println("| Star | Worlds | Passes every band | Settleable | Mean metal | Mean crystal | Mean deuterium |")
    println("|---|---|---|---|---|---|---|")
    for (starClass in StarClass.entries) {
        val of = worlds.filter { it.starClass == starClass }
        if (of.isEmpty()) continue
        val passing = of.filter { world ->
            HostilityAxis.entries.all { axis -> world.traits.axisValue(axis) in unaided.bandOf(axis) }
        }
        val settleable = passing.count {
            GalaxyBalance.yieldScore(it.traits).perMillion >= GalaxyBalance.WORTH_IT_THRESHOLD.perMillion
        }
        println(
            "| $starClass | ${of.size.grouped()} | ${percent(passing.size, of.size)} " +
                "| ${percent(settleable, of.size)} " +
                "| ${meanRichness(of) { it.metalRichness.perMillion }} " +
                "| ${meanRichness(of) { it.crystalRichness.perMillion }} " +
                "| ${meanRichness(of) { it.deuteriumRichness.perMillion }} |",
        )
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
    printDoorstepReport()
}

// ── The doorstep — the only worlds a player can act on before a probe lands ──────────────────
//
// Every other galaxy report in this file measures the *map*. This one measures the **opening**,
// which is a different quantity: the home system is the only system surveyed at genesis, so its
// non-home worlds are the entire content of the Galaxy screen on day one, and an adaptation level
// is the only verb that can change what any of them says.
//
// It sweeps seeds rather than reading the harness's one, and that is the whole reason it exists.
// Which home system you are dealt is the roll nobody re-rolls, so a single seed says what one
// player saw — and the feedback that prompted this report ("I need to upgrade at least 4
// adaptations for the easier planet") is a claim about the *distribution*, which one seed cannot
// confirm or refute.
private const val DOORSTEP_SEEDS: Int = 1_000

// What it would take to make one world stop reading `Blocked`: the level of each ladder that
// closes its own axis, and the whole bill for climbing to those levels from zero. Priced at
// 1 : 2 : 3 and clocked at Robotics 4, because that is the divisor the published tables use — the
// gate itself is Robotics 2 since 0.5.1, where the same first level is 21 minutes rather than 18.
private data class Doorstep(
    val at: GalaxyCoordinate,
    val levels: AdaptationLevels,
    val priced: Long,
    val minutes: Long,
    val settleable: Boolean,
) {
    val totalLevels: Int get() = levels.thermal + levels.gravitic + levels.atmospheric

    // How many ladders the bill spans. Load-bearing rather than decorative: the shared research
    // slot means two ladders is two projects run one after the other, and a world that needs two
    // ladders cannot be unblocked by any single purchase however cheap.
    val ladders: Int get() = AdaptationTechnology.entries.count { levels.levelOf(it) > 0 }
}

private fun doorstepFor(world: World): Doorstep {
    val levels = AdaptationLevels(
        thermal = GalaxyBalance.levelThatTolerates(HostilityAxis.TEMPERATURE, world.traits.temperature.celsius),
        gravitic = GalaxyBalance.levelThatTolerates(HostilityAxis.GRAVITY, world.traits.gravity.milliG),
        atmospheric = GalaxyBalance.levelThatTolerates(HostilityAxis.PRESSURE, world.traits.pressure.milliAtm),
    )
    var priced = 0L
    var minutes = 0L
    for (ladder in AdaptationTechnology.entries) {
        for (level in 1..levels.levelOf(ladder)) {
            val cost = AdaptationBalance.adaptationCost(ladder, TechLevel(level))
            priced += cost.metal + 2 * cost.crystal + 3 * cost.deuterium
            minutes += AdaptationBalance.adaptationDuration(ladder, TechLevel(level), BuildingLevel(4)).inWholeMinutes
        }
    }
    return Doorstep(
        at = world.at,
        levels = levels,
        priced = priced,
        minutes = minutes,
        settleable = GalaxyBalance.yieldScore(world.traits).perMillion >= GalaxyBalance.WORTH_IT_THRESHOLD.perMillion,
    )
}

// The cheapest neighbour in one seed's home system — cheapest by the bill rather than by the level
// count, since a single Gravitic 4 and four levels spread over three ladders are the same number
// and nothing like the same purchase.
private fun doorstepOf(seed: GalaxySeed): Doorstep? {
    val galaxy = GalaxyState.initial(seed)
    return galaxy.surveyed
        .filter { it != galaxy.home }
        .mapNotNull { at -> worldAt(seed, at) }
        .map(::doorstepFor)
        .minByOrNull { it.priced }
}

// ── The rest of the screen, which the doorstep report above does not measure ─────────────────
//
// `printDoorstepReport` asks how far the **cheapest** neighbour is, and 0.5.1 was shipped on that
// number alone. A player does not read the cheapest row; they read the whole list. This measures
// what the Galaxy screen actually says on day one: how many rows, how many of them are blocked,
// and how far away the ones that are not the doorstep sit.
private fun printWholeHomeSystem() {
    val rows = (0 until DOORSTEP_SEEDS).map { index ->
        val seed = GalaxySeed(SIM_GALAXY_SEED + index)
        val galaxy = GalaxyState.initial(seed)
        val others = galaxy.surveyed
            .filter { it != galaxy.home }
            .mapNotNull { at -> worldAt(seed, at) }
            .map { GalaxyBalance.levelsToTolerate(it.traits) }
            .sorted()
        others
    }

    println("### What the whole screen says, not just its cheapest row")
    println()
    println("| Reading | Value |")
    println("|---|---|")
    println("| Median non-home worlds on the screen | **${rows.map { it.size }.median()}** |")
    println("| Median of them still blocked | **${rows.map { o -> o.count { it > 0 } }.median()}** |")
    println("| Median levels, cheapest neighbour | **${rows.mapNotNull { it.firstOrNull() }.median()}** |")
    println("| Median levels, second cheapest | **${rows.mapNotNull { it.getOrNull(1) }.median()}** |")
    println("| Median levels, third cheapest | **${rows.mapNotNull { it.getOrNull(2) }.median()}** |")
    println("| Median levels across every non-home world | " +
        "**${rows.flatten().map { it.toLong() }.median()}** |")
    val allBlocked = rows.count { o -> o.isNotEmpty() && o.all { it > 0 } }
    println("| Screens where every non-home world is still blocked | ${percent(allBlocked, rows.size)} |")
    println()
}

private fun printDoorstepReport() {
    val doorsteps = (0 until DOORSTEP_SEEDS).mapNotNull { index -> doorstepOf(GalaxySeed(SIM_GALAXY_SEED + index)) }

    println("## The doorstep — what the home system offers on day one")
    println()
    println("$DOORSTEP_SEEDS seeds. For each, the **cheapest** non-home world of the home system:")
    println("the adaptation levels that would take it out of `Blocked`, and what buying them from")
    println("zero costs at 1 : 2 : 3 and takes at Robotics 4. This is the first galaxy interaction")
    println("the game offers, so it is the one the opening is judged on.")
    println()

    println("| Levels to the cheapest neighbour | Seeds | Share |")
    println("|---|---|---|")
    for (levels in 0..5) {
        val of = doorsteps.count { it.totalLevels == levels }
        val label = if (levels == 0) "0 — already tolerable" else "$levels"
        println("| $label | ${of.grouped()} | ${percent(of, doorsteps.size)} |")
    }
    val deep = doorsteps.count { it.totalLevels > 5 }
    println("| 6 or more | ${deep.grouped()} | ${percent(deep, doorsteps.size)} |")
    println()

    println("| Reading | Value |")
    println("|---|---|")
    println("| Median levels to the cheapest neighbour | **${doorsteps.map { it.totalLevels }.median()}** |")
    println("| Median bill, priced 1:2:3 | **${doorsteps.map { it.priced }.median().grouped()}** |")
    println("| Median research time at Robotics 4 | **${doorsteps.map { it.minutes }.median().asWait()}** |")
    val oneLadder = doorsteps.count { it.ladders <= 1 }
    println("| Cheapest neighbour needs one ladder only | ${percent(oneLadder, doorsteps.size)} |")
    println("| ... needs two or three ladders | ${percent(doorsteps.size - oneLadder, doorsteps.size)} |")
    val worthIt = doorsteps.count { it.settleable }
    println("| ... and is Settleable rather than Barren once landed | ${percent(worthIt, doorsteps.size)} |")
    println()

    val withinOne = doorsteps.count { it.totalLevels <= 1 }
    val withinTwo = doorsteps.count { it.totalLevels <= 2 }
    val withinThree = doorsteps.count { it.totalLevels <= 3 }
    println("Cumulative: **${percent(withinOne, doorsteps.size)}** of players can change a verdict in " +
        "their own system for one adaptation level, ${percent(withinTwo, doorsteps.size)} for two, " +
        "${percent(withinThree, doorsteps.size)} for three. The rest are asked for four or more " +
        "before the Galaxy screen says anything different.")
    println()

    // How much room the rule has to work with — whether a system that qualifies is common enough
    // that genesis can simply be told to start in one, and how far it has to walk to find it.
    val seed = GalaxySeed(SIM_GALAXY_SEED)
    var systems = 0
    var habitable = 0
    val withinLevels = IntArray(4)
    for (galaxy in 1..GalaxyBalance.GALAXIES) {
        for (system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
            val worlds = (1..GalaxyBalance.SLOTS_PER_SYSTEM)
                .mapNotNull { slot -> worldAt(seed, GalaxyCoordinate(galaxy, system, slot)) }
            if (worlds.isEmpty()) continue
            systems++
            val steps = worlds.map { doorstepFor(it).totalLevels }.sorted()
            if (steps.first() != 0) continue
            habitable++
            // The second-cheapest world, because the cheapest is the one genesis would take as
            // home — a habitable world cannot also be the neighbour it is measured against.
            val neighbour = steps.drop(1).minOrNull() ?: continue
            for (n in 1..3) if (neighbour <= n) withinLevels[n]++
        }
    }
    println("Across seed ${seed.value}'s whole coordinate space, $systems of " +
        "${(GalaxyBalance.GALAXIES * GalaxyBalance.SYSTEMS_PER_GALAXY).grouped()} systems hold a world at all, " +
        "and ${percent(habitable, systems)} of those hold one the unaided species already tolerates — which is " +
        "what genesis walks for. Of *those*:")
    println()
    printWholeHomeSystem()

    println("| A system genesis would accept | Also holds a neighbour within | Systems | Share of habitable |")
    println("|---|---|---|---|")
    for (n in 1..3) {
        println("| — | $n level${if (n == 1) "" else "s"} | ${withinLevels[n].grouped()} | " +
            "${percent(withinLevels[n], habitable)} |")
    }
    println()
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

// ── What one tap actually makes you wait ─────────────────────────────────────────────────────
//
// Round 10 made a build take as long as it costs. Cost compounds at +50% a level and production at
// +25%, so the wait after a tap pulls away from the income that pays for it by ~20% a level, level
// after level, from level one. The Robotics Factory's divisor is the only thing pushing back — and
// it is the one facility that produces nothing, is priced in the resource that arrives slowest, and
// is therefore the last thing a player who has not read the code buys.
//
// Every other table in this harness is about a curve. This one is about a single moment: the player
// taps a row and closes the app. It prints what that costs them in waiting, at the Robotics levels
// an opening colony really has, for the four facilities a session actually repeats.
// ── The first sitting, minute by minute ──────────────────────────────────────────────────────
//
// Every other report in this file models a player who taps and leaves: the cadence is three hours,
// so a facility advances at most one level per visit and the *duration* of a build can only matter
// if it exceeds the gap. Which means none of them can see the thing 0.2.7 was asked for — Davide,
// 2026-08-09: *"I want a 2/3 min build time at the very first levels ... we need to give some
// adrenaline to users."* Adrenaline is a property of a session the player stays inside, and against
// a three-hour cadence a two-minute build and a fifty-minute one are the same reading.
//
// So this one keeps the player in the app. One minute of resolution, buying anything affordable on
// any free facility, for an hour. What it counts is what the phone is actually doing while it is
// being held: completions the player *watches* land rather than finds waiting next time.
private fun printFirstSitting() {
    val plan = listOf(
        BuildingType.METAL_MINE,
        BuildingType.CRYSTAL_MINE,
        BuildingType.DEUTERIUM_SYNTHESIZER,
        BuildingType.SOLAR_PLANT,
        BuildingType.ROBOTICS_FACTORY,
    )
    val genesis = Instant.fromEpochMilliseconds(0)
    var state = GameState.initial(GalaxySeed(SIM_GALAXY_SEED))
    var now = genesis
    val log = mutableListOf<String>()
    var completions = 0
    val completionsBy = mutableMapOf(10 to 0, 30 to 0, 60 to 0)
    val levelsBy = mutableMapOf<Int, Int>()
    var longestStare = 0L
    var lastSomethingHappened = 0

    for (minute in 0..60) {
        val at = genesis + minute.minutes
        val before = state
        state = advance(state, from = now, to = at)
        now = at

        val finished = finishedBetween(before, state)
        if (finished.isNotEmpty()) {
            completions += finished.size
            completionsBy.keys.filter { minute <= it }.forEach { completionsBy[it] = completionsBy.getValue(it) + finished.size }
            log += "| ${minute}m | ${finished.joinToString(", ")} | — |"
            longestStare = maxOf(longestStare, (minute - lastSomethingHappened).toLong())
            lastSomethingHappened = minute
        }

        val bought = mutableListOf<String>()
        for ((building, cost) in optionsFor(state, plan, withProjects = true).buildings) {
            if (!state.resources.covers(cost)) continue
            val name = nameOf(building, state)
            (startUpgrade(state, building, at = now) as? StartUpgradeResult.Started)?.let {
                state = it.state
                bought += name
            }
        }
        if (bought.isNotEmpty()) {
            log += "| ${minute}m | — | ${bought.joinToString(", ")} |"
            lastSomethingHappened = minute
        }
        levelsBy[minute] = BuildingType.entries.sumOf { state.buildings.levelOf(it).value }
    }

    println("## The first sitting — a player who does not put the phone down")
    println()
    println("One-minute resolution, everything affordable started on any free facility, for one")
    println("hour from genesis. Every other report here checks in every three hours, which cannot")
    println("tell a two-minute build from a fifty-minute one; this is the one that can.")
    println()
    println("| Reading | Value |")
    println("|---|---|")
    println("| Completions watched inside 10 minutes | **${completionsBy.getValue(10)}** |")
    println("| inside 30 minutes | **${completionsBy.getValue(30)}** |")
    println("| inside the hour | **${completionsBy.getValue(60)}** |")
    println("| Building levels after 10 minutes | ${levelsBy.getValue(10)} |")
    println("| after 30 minutes | ${levelsBy.getValue(30)} |")
    println("| after the hour | ${levelsBy.getValue(60)} |")
    println("| Longest stretch with nothing landing | ${longestStare}m |")
    println("| Facilities the colony ends the hour with | ${state.buildings.summary()} |")
    println()
    println("Minute by minute — what landed while they watched, and what they started because of it:")
    println()
    println("| At | Landed | Started |")
    println("|---|---|---|")
    log.take(40).forEach(::println)
    if (log.size > 40) println("| … | ${log.size - 40} more rows | |")
    println()
}

private fun printEarlyBuildTable() {
    val repeating = listOf(
        BuildingType.METAL_MINE,
        BuildingType.CRYSTAL_MINE,
        BuildingType.SOLAR_PLANT,
        BuildingType.DEUTERIUM_SYNTHESIZER,
    )
    println("## What a tap makes you wait, in the first eight levels")
    println()
    println("Build duration at Robotics 0 / 1 / 2 — the levels a colony in its first two days has.")
    println()
    println("| Level | ${repeating.joinToString(" | ") { short(it) }} |")
    println("|---|---|---|---|---|")
    for (level in 1..8) {
        val cells = repeating.joinToString(" | ") { building ->
            (0..2).joinToString(" / ") { robotics ->
                PlaceholderBalance
                    .upgradeDuration(building, BuildingLevel(level), BuildingLevel(robotics), BuildingLevel(0))
                    .label()
            }
        }
        println("| $level | $cells |")
    }
    println()

    // The two clocks the player is actually caught between, side by side. Everything about the
    // opening's pacing is in the gap between these two columns: while the build is the shorter one
    // the colony is waiting for money, and once it is the longer one the colony is waiting for the
    // build — and the second regime never ends, because the two curves diverge by construction.
    println("The Metal Mine's two waits, at Robotics 0, from a colony producing at the level below:")
    println()
    println("| Level | cost (m+c) | build | hours of income to afford it |")
    println("|---|---|---|---|")
    for (level in 2..10) {
        val cost = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(level))
        val perHour = PlaceholderBalance.metalProductionPerHour(BuildingLevel(level - 1))
        val afford = (cost.metal * 60 / perHour).minutes
        println(
            "| $level | ${(cost.metal + cost.crystal).grouped()} " +
                "| ${PlaceholderBalance.upgradeDuration(BuildingType.METAL_MINE, BuildingLevel(level), BuildingLevel(0), BuildingLevel(0)).label()} " +
                "| ${afford.label()} |",
        )
    }
    println()
}

private fun Long.grouped(): String = toString().reversed().chunked(3).joinToString(",").reversed()

// The three resources, as an answer to "what is stopping me buying this".
private enum class Blocker { METAL, CRYSTAL, DEUTERIUM }

private fun shortagesOf(cost: Resources, stock: Resources): Set<Blocker> = buildSet {
    if (stock.metal < cost.metal) add(Blocker.METAL)
    if (stock.crystal < cost.crystal) add(Blocker.CRYSTAL)
    if (stock.deuterium < cost.deuterium) add(Blocker.DEUTERIUM)
}

// What a run measured, beyond the closing stock. `soleBlockerHours` is the reading this whole
// harness was extended for: an hour counts for a resource when some purchase the strategy wants is
// short of *that resource and nothing else* — the player has the rest of the price in the bank and
// is waiting on one mine. A resource that never appears here is never what anyone is waiting for.
private class Ledger {
    var throttledHours: Int = 0
    val soleBlockerHours: MutableMap<Blocker, Int> = Blocker.entries.associateWith { 0 }.toMutableMap()

    // The same question asked without the word *alone*, and it exists because the sole-blocker
    // ledger turned out to be brittle. Round 12 swept deuterium income by **one unit** — 15 to 16,
    // a 6.7% change — and crystal's sole-blocker count went from 58 hours of 336 to 200. That is
    // not a curve responding; it is a different trajectory. The cause is structural: "short of this
    // resource *and nothing else*" is a knife-edge on which purchase happens to be next, and a
    // small income change reorders the queue, so the reading jumps rather than moves.
    //
    // This one counts an hour for a resource whenever *some* wanted purchase is short of it, alone
    // or not. It cannot say who to blame, which is what the sole ledger was for — but it does not
    // flip on a single unit, so it is the one to tune against and the sole ledger is the one to
    // read afterwards.
    val shortHours: MutableMap<Blocker, Int> = Blocker.entries.associateWith { 0 }.toMutableMap()

    // What the strategy actually paid, summed as it paid it. The ratio between these is the number
    // the income curve should be tuned against — not the unweighted sum of base costs, which is a
    // basket nobody buys in those proportions.
    var spentMetal: Long = 0
    var spentCrystal: Long = 0
    var spentDeuterium: Long = 0

    fun spend(cost: Resources) {
        spentMetal += cost.metal
        spentCrystal += cost.crystal
        spentDeuterium += cost.deuterium
    }

    fun record(costs: List<Resources>, stock: Resources) {
        for (blocker in Blocker.entries) {
            if (costs.any { shortagesOf(it, stock) == setOf(blocker) }) {
                soleBlockerHours[blocker] = soleBlockerHours.getValue(blocker) + 1
            }
            if (costs.any { blocker in shortagesOf(it, stock) }) {
                shortHours[blocker] = shortHours.getValue(blocker) + 1
            }
        }
    }
}

// Everything a strategy might buy this hour, priced, so the runner can both spend on it and report
// what it could not afford. Buildings and projects are separate because the game buys them under
// different rules — facilities in parallel, projects one at a time, empire-wide.
private class Options(val buildings: List<Pair<BuildingType, Resources>>, val projects: List<Pair<Any, Resources>>)

private fun optionsFor(state: GameState, plan: List<BuildingType>, withProjects: Boolean): Options {
    // Sorted on raw metal + crystal rather than on the priced total, because this is the key the
    // 0.0.12 greedy week was generated with and the balance log's closing line is quoted against
    // it. A different key buys a different colony — switching to priced moved the week's closing
    // metal by a third — so it stays put, and the whole-tree run below inherits it for the same
    // reason: two runs that sort differently are not comparable with each other either.
    val buildings = plan
        .map { it to PlaceholderBalance.upgradeCost(it, BuildingLevel(state.buildings.levelOf(it).value + 1)) }
        .sortedBy { (_, cost) -> cost.metal + cost.crystal }
    if (!withProjects) return Options(buildings, emptyList())

    // Still one list sorted by one key — cheapest first, the same rule the buildings follow, a rule
    // rather than a judgement about which branch is better. What changed at 0.12.1 is that a branch
    // is emptied out **on its own slot** rather than both on one: a project in flight says nothing
    // about whether a ladder can start, and a filter that asked one question for both would leave
    // whichever branch happened to be idle permanently unbought.
    val applied = if (state.activeResearch != null) {
        emptyList()
    } else {
        Technology.entries
            .filter { ResearchBalance.requirementFor(it).isMetBy(state) }
            .map { it as Any to ResearchBalance.researchCost(it, TechLevel(state.research.levelOf(it).value + 1)) }
    }
    val ladders = if (state.activeAdaptation != null) {
        emptyList()
    } else {
        AdaptationTechnology.entries
            .filter { AdaptationBalance.requirementFor(it).isMetBy(state) }
            .map { it as Any to AdaptationBalance.adaptationCost(it, TechLevel(state.research.levelOf(it).value + 1)) }
    }
    return Options(buildings, (applied + ladders).sortedBy { (_, cost) -> priced(cost) })
}

// Whether the branch this project belongs to is already busy. Asked per project rather than once per
// visit because a loop that starts one project has filled one slot and left the other open — which
// is the whole of what two queues changed for every runner below.
private fun GameState.slotBusyFor(project: Any): Boolean = when (project) {
    is Technology -> activeResearch != null
    is AdaptationTechnology -> activeAdaptation != null
    else -> true
}

// When the slot this project takes frees, read *after* the start — so it is the job just booked and
// never whatever the other branch happens to be holding. Null when the start was refused, which is
// what the callers below use to tell a purchase from a skip.
private fun GameState.slotFreesAfter(project: Any): Instant? = when (project) {
    is Technology -> activeResearch?.completesAt
    is AdaptationTechnology -> activeAdaptation?.completesAt
    else -> null
}

// One hour-stepped run of one strategy. Shared by both reports below so the two differ in what they
// buy and in nothing else — a comparison between two runners would measure the runners.
private fun run(days: Int, plan: List<BuildingType>, withProjects: Boolean): Pair<GameState, Ledger> {
    var state = GameState.initial(GalaxySeed(SIM_GALAXY_SEED))
    var now = Instant.fromEpochMilliseconds(0)
    val ledger = Ledger()

    repeat(days * 24) {
        val next = now + 1.hours
        state = advance(state, from = now, to = next)
        now = next
        // A power shortage scales every mine silently, so a week that looks slow can be a week
        // spent throttled rather than a curve that is too flat. Counting it tells the two apart.
        if (PlaceholderBalance.energyBalance(state.buildings, state.research).isDeficit) ledger.throttledHours++

        val options = optionsFor(state, plan, withProjects)
        for ((building, cost) in options.buildings) {
            if (!state.resources.covers(cost)) continue
            (startUpgrade(state, building, at = now) as? StartUpgradeResult.Started)?.let {
                state = it.state
                ledger.spend(cost)
            }
        }
        for ((project, cost) in options.projects) {
            if (state.slotBusyFor(project) || !state.resources.covers(cost)) continue
            when (project) {
                is Technology -> (startResearch(state, project, at = now) as? StartResearchResult.Started)
                    ?.let { state = it.state; ledger.spend(cost) }
                is AdaptationTechnology -> (startAdaptation(state, project, at = now) as? StartAdaptationResult.Started)
                    ?.let { state = it.state; ledger.spend(cost) }
            }
        }

        // Measured *after* spending, so what remains is genuinely unaffordable rather than merely
        // not yet bought.
        val remaining = optionsFor(state, plan, withProjects)
        ledger.record((remaining.buildings + remaining.projects).map { it.second }, state.resources)
    }
    return state to ledger
}

private fun report(
    label: String,
    days: Int,
    state: GameState,
    ledger: Ledger,
    plan: List<BuildingType>,
    withProjects: Boolean,
) {
    val energy = PlaceholderBalance.energyBalance(state.buildings, state.research)
    println("$label — after $days days:")
    println("  metal=${state.resources.metal} crystal=${state.resources.crystal} deuterium=${state.resources.deuterium}")
    println("  buildings=${state.buildings}")
    if (withProjects) println("  research=${state.research}")
    println("  energy=${energy.produced}/${energy.consumed} (mines at ${energy.outputPercent}%)")
    println("  hours throttled by power: ${ledger.throttledHours} of ${days * 24}")
    println("  events=${state.eventLog.size} (starts + completions)")
    println("  spent: metal=${ledger.spentMetal.grouped()} crystal=${ledger.spentCrystal.grouped()} " +
        "deuterium=${ledger.spentDeuterium.grouped()} " +
        "— a metal : crystal of ${ratio(ledger.spentMetal, ledger.spentCrystal)}, against income at " +
        "${ratio(PlaceholderBalance.metalProductionPerHour(BuildingLevel(1)),
            PlaceholderBalance.crystalProductionPerHour(BuildingLevel(1)))}")
    println("  hours with a purchase blocked by that resource *alone*, of ${days * 24}:")
    for (blocker in Blocker.entries) {
        println("    ${blocker.name.lowercase().padEnd(9)} ${ledger.soleBlockerHours.getValue(blocker)}")
    }
    // Printed beside the sole ledger rather than instead of it: the sole one answers "who is to
    // blame" and jumps on a single unit of income, this one answers "what is ever short" and does
    // not. Tune against the second, read the first.
    println("  hours with a purchase short of that resource *at all*, of ${days * 24}:")
    for (blocker in Blocker.entries) {
        println("    ${blocker.name.lowercase().padEnd(9)} ${ledger.shortHours.getValue(blocker)}")
    }

    // The closing snapshot: every purchase still on the table and what is short for it. This is the
    // sentence the player would write the complaint from.
    println("  what it could buy next, and what is missing:")
    val options = optionsFor(state, plan, withProjects)
    for ((what, cost) in options.buildings + options.projects) {
        val short = shortagesOf(cost, state.resources)
        val name = when (what) {
            is BuildingType -> "${what.name} ${state.buildings.levelOf(what).value + 1}"
            else -> "$what"
        }
        val missing = if (short.isEmpty()) "affordable" else short.joinToString { blocker ->
            val need = when (blocker) {
                Blocker.METAL -> cost.metal - state.resources.metal
                Blocker.CRYSTAL -> cost.crystal - state.resources.crystal
                Blocker.DEUTERIUM -> cost.deuterium - state.resources.deuterium
            }
            "${need.grouped()} more ${blocker.name.lowercase()}"
        }
        println("    ${name.padEnd(34)} $missing")
    }
    println()
}

// The four times a day the brief designs for, as offsets from an 08:00 genesis: morning, lunch,
// evening, bedtime. The overnight gap is the long one, which is the point — it is the gap the
// notification loop has to survive.
private val CHECK_IN_HOURS = listOf(0, 5, 11, 15)

// What one check-in was worth, in the terms the player would describe it in: what had finished
// while they were away, what they could choose between, and whether anything was still running
// when they closed the app.
private class CheckIn(
    val label: String,
    val finished: List<String>,
    val couldBuy: List<String>,
    // The same options as `couldBuy`, kept typed, because *how many* things are on the table and
    // *how many kinds* of thing are on the table are different questions and only the second one
    // answers "there is nothing to do but press a button". Five facility rows are five taps of one
    // verb; a facility and a technology are a choice between two shapes of decision.
    val kinds: Set<String>,
    val bought: List<String>,
    val leftRunning: Int,
    val nextLandsInMinutes: Long?,
    // How far ahead this check-in booked the colony: the *last* thing it set running, not the
    // first. This is the number the notification loop lives or dies on — a check-in that books
    // 40 minutes of work cannot pull the player back at any useful hour, however many jobs it
    // started.
    val bookedMinutes: Long,
) {
    // The trip that was not worth making: nothing had happened, and nothing could be done about it.
    val isDead: Boolean get() = finished.isEmpty() && couldBuy.isEmpty()

    // A check-in with one option is not a decision, it is a chore. Counted separately from dead
    // ones because the fix is different: a dead check-in wants more income, a forced one wants
    // more things worth buying at that moment.
    val isForced: Boolean get() = couldBuy.size == 1
}

// ── The opening, as the player meets it ──────────────────────────────────────────────────────
//
// Every other run in this harness is an hour-stepped bot: it acts 24 times a day and spends the
// instant it can afford anything. That shape structurally cannot answer a complaint about the
// *opening*, because what is being complained about is what a **check-in** offers — and a runner
// that acts every hour never has one. It also cannot see idleness at all: a bot that buys hourly
// keeps something running by construction, so the one thing the brief says the whole game is made
// of ("local notifications at computed completion timestamps are the entire check-in loop") is the
// one thing no report has ever measured.
//
// This runner acts only at the four times a day the brief designs for. The buying rule is the
// greedy runs' rule, unchanged — everything affordable, cheapest first — so the only difference
// between this and the fortnight below is *when* the player is allowed to act.
private fun printOpeningReport() {
    openingReport(withProbes = false)
    openingReport(withProbes = true)
}

// A player who understands the verb aims it at the gap they are about to leave: the target whose
// flight is as long as possible without overshooting the next check-in. That is the strategy the
// mechanic exists for, and it is the one worth measuring — a greedy "dispatch everything
// affordable" rule would measure a bot draining the colony's metal into probes it will never read.
//
// One probe per check-in, for the same reason. Nothing in the rules caps them; this is a statement
// about how a person plays, not about what the game allows.
private fun probeTargetFor(state: GameState, gapMinutes: Long): SystemAddress? {
    val home = SystemAddress.of(state.galaxy.home)
    var best: SystemAddress? = null
    var bestMinutes = -1L
    for (galaxy in 1..GalaxyBalance.GALAXIES) {
        for (system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
            val candidate = SystemAddress(galaxy = galaxy, system = system)
            val minutes = SurveyBalance.duration(home, candidate).inWholeMinutes
            if (minutes > gapMinutes || minutes <= bestMinutes) continue
            if (state.surveys.any { it.target == candidate }) continue
            if (state.galaxy.hasSurveyed(candidate)) continue
            best = candidate
            bestMinutes = minutes
        }
    }
    return best
}

private fun openingReport(withProbes: Boolean) {
    val days = 2
    val plan = listOf(
        BuildingType.METAL_MINE,
        BuildingType.CRYSTAL_MINE,
        BuildingType.DEUTERIUM_SYNTHESIZER,
        BuildingType.SOLAR_PLANT,
        BuildingType.ROBOTICS_FACTORY,
    )

    var state = GameState.initial(GalaxySeed(SIM_GALAXY_SEED))
    val genesis = Instant.fromEpochMilliseconds(0)
    var now = genesis
    // Two ledgers, deliberately. `colonyBusy` is what round 8 measured — mines and the research
    // slot — and a probe must not be allowed to improve it, because a probe in flight does not make
    // a mine busier. `probeBusy` is the player's attention, which is the thing the notification loop
    // actually covers. Reporting one number for both would let the new verb take credit for fixing
    // the old complaint.
    val colonyBusy = mutableListOf<Pair<Instant, Instant>>()
    val probeBusy = mutableListOf<Pair<Instant, Instant>>()
    val checkIns = mutableListOf<CheckIn>()
    var dispatched = 0
    var spentOnProbes = 0L

    val offsets = (0 until days).flatMap { day -> CHECK_IN_HOURS.map { day * 24 + it } }
    for ((index, offset) in offsets.withIndex()) {
        val at = genesis + offset.hours
        val before = state
        state = advance(state, from = now, to = at)
        now = at

        val finished = finishedBetween(before, state)
        val options = optionsFor(state, plan, withProjects = true)
        val affordable = (options.buildings + options.projects)
            .filter { (_, cost) -> state.resources.covers(cost) }
            .map { (what, _) -> what }
        val couldBuy = affordable.map { nameOf(it, state) }.toMutableList()
        val kinds = affordable.map { what ->
            when (what) {
                is BuildingType -> "build"
                is AdaptationTechnology -> "adapt"
                else -> "research"
            }
        }.toMutableSet()

        // The gap this check-in is about to leave behind. The last one of the run has no next
        // check-in, so it is measured against the end of the window.
        val gapMinutes = ((offsets.getOrNull(index + 1) ?: days * 24) - offset) * 60L
        val probeTarget = if (withProbes) probeTargetFor(state, gapMinutes) else null
        if (probeTarget != null && state.resources.covers(SurveyBalance.cost())) {
            couldBuy += "probe ${probeTarget.galaxy}:${probeTarget.system}"
            kinds += "survey"
        }

        val bought = mutableListOf<String>()
        // The probe is bought **first**, which is the pessimistic ordering on purpose: it models a
        // player who decides to cover the gap and then spends what is left, so the levels it costs
        // show up in the count below rather than being hidden behind a full building queue. If
        // progression survives this ordering it survives any.
        if (probeTarget != null) {
            (startSurvey(state, probeTarget, at = now) as? StartSurveyResult.Started)?.let { started ->
                state = started.state
                val job = state.surveys.last()
                bought += "probe ${probeTarget.galaxy}:${probeTarget.system}"
                probeBusy += now to job.completesAt
                dispatched++
                spentOnProbes += SurveyBalance.COST_METAL
            }
        }
        for ((building, cost) in options.buildings) {
            if (!state.resources.covers(cost)) continue
            (startUpgrade(state, building, at = now) as? StartUpgradeResult.Started)?.let { started ->
                state = started.state
                state.builds[building]?.let { job ->
                    bought += "${short(building)} ${job.toLevel.value}"
                    colonyBusy += now to job.completesAt
                }
            }
        }
        for ((project, cost) in options.projects) {
            if (state.slotBusyFor(project) || !state.resources.covers(cost)) continue
            when (project) {
                is Technology -> (startResearch(state, project, at = now) as? StartResearchResult.Started)
                    ?.let { state = it.state }
                is AdaptationTechnology -> (startAdaptation(state, project, at = now) as? StartAdaptationResult.Started)
                    ?.let { state = it.state }
            }
            state.slotFreesAfter(project)?.let { freesAt ->
                bought += "$project"
                colonyBusy += now to freesAt
            }
        }

        val pending = futureEvents(state, now = now)
        checkIns += CheckIn(
            label = clockLabel(offset),
            finished = finished,
            couldBuy = couldBuy,
            kinds = kinds,
            bought = bought,
            leftRunning = pending.size,
            nextLandsInMinutes = pending.firstOrNull()?.let { (it.at - now).inWholeMinutes },
            bookedMinutes = pending.lastOrNull()?.let { (it.at - now).inWholeMinutes } ?: 0,
        )
    }

    println("## The opening, as the player meets it${if (withProbes) " — with probes" else ""}")
    println()
    println("Two days, four check-ins a day (08:00 / 13:00 / 19:00 / 23:00), buying everything")
    println("affordable cheapest-first — the greedy runs' rule, restricted to when a player is")
    println("actually looking. Genesis is the first check-in, so the colony starts with the player.")
    if (withProbes) {
        println()
        println("Plus one probe per check-in, aimed at the longest flight that still lands before the")
        println("next one — and bought **before** the buildings, so the levels it costs are visible")
        println("rather than hidden behind a full queue.")
    }
    println()
    println("| Check-in | Finished while away | Could buy | Kinds | Bought | Left running | Next lands in |")
    println("|---|---|---|---|---|---|---|")
    for (checkIn in checkIns) {
        val next = checkIn.nextLandsInMinutes?.let { minutes -> "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}m" }
            ?: "**nothing**"
        println(
            "| ${checkIn.label} | ${checkIn.finished.joinToString().ifEmpty { "—" }} " +
                "| ${checkIn.couldBuy.size} | ${checkIn.kinds.sorted().joinToString("+").ifEmpty { "—" }} " +
                "| ${checkIn.bought.joinToString().ifEmpty { "**nothing**" }} " +
                "| ${checkIn.leftRunning} | $next |",
        )
    }
    println()

    val totalMinutes = days * 24 * 60L
    val end = genesis + (days * 24).hours
    val idle = idleMinutes(colonyBusy, genesis, end)
    val longestIdle = longestIdleRun(colonyBusy + probeBusy, genesis, end)
    val nothingAtAll = idleMinutes(colonyBusy + probeBusy, genesis, end)
    println("| Reading | Value |")
    println("|---|---|")
    println("| Check-ins | ${checkIns.size} |")
    println("| **Dead check-ins** (nothing finished, nothing affordable) | **${checkIns.count { it.isDead }}** |")
    println("| Check-ins offering exactly one thing | ${checkIns.count { it.isForced }} |")
    println("| Check-ins that left nothing running | ${checkIns.count { it.leftRunning == 0 }} |")
    println("| Median options on the table | ${checkIns.map { it.couldBuy.size }.sorted()[checkIns.size / 2]} |")
    // The count above and the count below are the difference between "there is plenty on the
    // table" and "there is plenty to *do*". Five facility rows are one verb pressed five times.
    println("| **Check-ins offering one kind of thing only** | **${checkIns.count { it.kinds.size <= 1 }} of ${checkIns.size}** |")
    val secondVerb = offsets.zip(checkIns).firstOrNull { (_, checkIn) -> checkIn.kinds.size >= 2 }
    println("| A second kind of decision first exists | " +
        "${secondVerb?.let { "${it.first}h in (${it.second.label})" } ?: "**never, in 48h**"} |")
    println("| Hours **the colony** had nothing in flight | ${idle / 60}h of ${totalMinutes / 60}h " +
        "(${percent((idle / 60).toInt(), (totalMinutes / 60).toInt())}) |")
    println("| **Hours with nothing at all in flight** | **${nothingAtAll / 60}h of ${totalMinutes / 60}h " +
        "(${percent((nothingAtAll / 60).toInt(), (totalMinutes / 60).toInt())})** |")
    println("| Longest unbroken silence | ${longestIdle / 60}h ${(longestIdle % 60).toString().padStart(2, '0')}m |")
    val booked = checkIns.map { it.bookedMinutes }
    println("| Work the busiest check-in booked | ${booked.max()} min |")
    println("| Median work a check-in booked | ${booked.sorted()[booked.size / 2]} min |")
    if (withProbes) {
        println("| Probes dispatched | $dispatched, for ${spentOnProbes.grouped()} metal |")
        println("| Systems known at 48h | ${state.galaxy.surveyed.size} worlds |")
    }
    println()
    if (withProbes) {
        println("**The first two rows are the honest pair.** A probe in flight does not make a mine")
        println("busier, so the colony's own idleness is exactly what it was — the second row is the")
        println("one the new verb moves, and what it measures is the player's attention rather than")
        println("the colony's. Reporting one number for both would let the probe take credit for")
        println("fixing a complaint it does not touch.")
        println()
    }
    println("That last pair is the reading the notification loop lives on. The brief calls local")
    println("notifications *the entire check-in loop*: if the deepest a session can book is under an")
    println("hour, every notification the game will ever send arrives while the player is still")
    println("holding the phone, and nothing at all fires across the gap that needs covering.")
    println()

    val levels = BuildingType.entries.sumOf { state.buildings.levelOf(it).value }
    println("After 48 hours: ${state.buildings.summary()} — $levels levels total, " +
        "research ${state.research.appliedSummary()}.")
    println("Stock: ${state.resources.metal.grouped()} metal, ${state.resources.crystal.grouped()} crystal, " +
        "${state.resources.deuterium.grouped()} deuterium.")
    println()

    // When each branch opens, which is the other half of "what is there to do". The Research tab is
    // an empty room until the first Robotics Factory, and the adaptation ladders — the only thing
    // that makes the Galaxy screen's blocked rows buyable — need Robotics 4 on top of that.
    println("Gates, and when this run cleared them:")
    println()
    println("| Gate | Opens | Cleared |")
    println("|---|---|---|")
    val robotics = state.buildings.roboticsFactory.value
    // Read off `AdaptationBalance.GATE` rather than written out. It was written out until 0.5.1
    // moved the gate from 4 to 2, at which point this table went on labelling the wrong row — a
    // report that quietly disagrees with the game is worse than no report, because every balance
    // round in `balance-log.md` is argued from these readings.
    val gate = AdaptationBalance.GATE.value
    println("| Robotics Factory 1 | the Research tab | ${if (robotics >= 1) "yes" else "**no — still level $robotics at 48h**"} |")
    println("| Robotics Factory $gate | the adaptation ladders, so every Blocked world | " +
        "${if (robotics >= gate) "yes" else "**no — still level $robotics at 48h**"} |")
    println()
}

// ── The fleet, measured ──────────────────────────────────────────────────────────────────────
//
// `EXTRACTION_PER_HOUR = 40` was written at a keyboard against a colony 0.2.7 deleted, and the fleet
// sheet's §9 says in as many words that it *"must not ship unswept"*. This report is the sweep.
//
// **Three rules it inherits from this file's own mistakes**, all three from the sheet's §6:
//
// 1. **The no-fleet column is in the same run.** A gathering fleet moves every other reading in the
//    harness — levels at 48h, the gate clock, blocker hours — so a run that measures idleness while
//    also getting richer cannot say which change did what. Probes are on in *both* columns, because
//    probes shipped: the fleet is the only variable.
// 2. **A third ledger.** `colonyBusy` and `probeBusy` are kept apart so a new verb cannot take credit
//    for fixing a complaint it does not touch; `fleetBusy` joins them, and what is printed is the
//    share of covered time each one is the **only** thing covering. A fleet that only flies while a
//    build is running has bought nothing back, and no total can show that.
// 3. **Kinds first, count second.** A dispatch is one verb with many targets, exactly as a probe is.
//
// **Fleet income is reported against metal and against crystal separately, never against the priced
// basket.** That is the correction that matters most and the draft got it wrong: 34 priced/hour taken
// as crystal is ~47% of a genesis colony's crystal income and only ~16% of the basket.
//
// **The instrument caveat, stated where it bites.** Every report in this file except
// `printFirstSitting` checks in every three hours or less often, so all of them are structurally
// blind to anything shorter than the gap — and the 1h and 3h window rungs are shorter. Nothing below
// measures the 1h rung at all, and the 3h rung only appears when a gap is short enough to ask for it.

// The two numbers under sweep. `EXTRACTION_PER_HOUR` and the hull base are `core` constants, so the
// harness carries a replica of the two arithmetics they enter and **checks the replica against `core`
// on every single call at the shipped values** — see `cargoAt` and `hullCostAt`. A sweep that quietly
// disagreed with the game would be worse than no sweep.
private class FleetTuning(
    val extractionPerHour: Long,
    val hullBaseMetal: Long,
) {
    // The sheet's 1 : 4, held across the sweep so the hull base is one dial rather than two.
    val hullBaseCrystal: Long get() = hullBaseMetal / 4

    val isShippedExtraction: Boolean get() = extractionPerHour == FleetBalance.EXTRACTION_PER_HOUR
    val isShippedHull: Boolean get() = hullBaseMetal == FleetBalance.HULL_BASE_METAL

    val label: String get() = "extraction $extractionPerHour, hull ${hullBaseMetal}m/${hullBaseCrystal}c"
}

private val SHIPPED_FLEET = FleetTuning(
    extractionPerHour = FleetBalance.EXTRACTION_PER_HOUR,
    hullBaseMetal = FleetBalance.HULL_BASE_METAL,
)

// **The rate candidates, in one place because they were in four and one of them went stale.** Round
// 17 swept {10, 20, 30, 40} and round 21 shipped 60 without widening them, so every sweep in this
// file printed a grid that did not contain the constant the game was running on — and the row a
// reader most wanted was the row that was missing. Named here so a candidate list cannot drift from
// the shipped value again, and `check`ed for the same reason `cargoAt` checks its replica.
private val RATE_CANDIDATES: List<Long> = listOf(10L, 20L, 30L, 40L, 60L).also { candidates ->
    check(FleetBalance.EXTRACTION_PER_HOUR in candidates) {
        "the sweep must contain the shipped rate ${FleetBalance.EXTRACTION_PER_HOUR}"
    }
}

// **The same defect, one dial along, and it went unnoticed for exactly as long.** Round 22 named the
// rate candidates because four tables were printing a grid that did not contain the shipped rate;
// the hull base was `listOf(40L, 80L, 140L)` inline in two places and went stale the moment 0.9.0
// raised the base tenfold — so the sweep would have printed three rows about a game nobody is
// playing, which is precisely what the rate list was fixed for.
//
// The span is Davide's question rather than a neat geometric series: he asked for *"at least 10x"*,
// so the list runs from the old base through half his floor to twice it, and 800 is his floor and the
// shipped value. `check`ed for the reason the rates are.
private val HULL_BASE_CANDIDATES: List<Long> = listOf(80L, 400L, 800L, 1_200L, 1_600L).also { candidates ->
    check(FleetBalance.HULL_BASE_METAL in candidates) {
        "the sweep must contain the shipped hull base ${FleetBalance.HULL_BASE_METAL}"
    }
}

private val FULL_PLAN = listOf(
    BuildingType.METAL_MINE,
    BuildingType.CRYSTAL_MINE,
    BuildingType.DEUTERIUM_SYNTHESIZER,
    BuildingType.SOLAR_PLANT,
    BuildingType.ROBOTICS_FACTORY,
)

// `FleetBalance.cargo` with the extraction rate lifted out. Every term and the single trailing
// division are the production function's, copied rather than approximated — and the `check` below is
// what keeps "copied" true: at the shipped rate this must agree with `core` to the unit, on every
// dispatch of every run of every sweep row.
private fun cargoAt(
    tuning: FleetTuning,
    world: World,
    gathering: ResourceKind,
    ships: Ships,
    station: Duration,
    danger: Int,
    // The fourth technology multiplies the rate, so a replica that assumed nothing was researched
    // would disagree with `core` the moment the bot bought a level of it — and the `check` below is
    // the whole point of the replica existing.
    research: Research = Research.initial(),
): Resources {
    val stationMinutes = station.inWholeMinutes
    if (stationMinutes <= 0 || ships.isEmpty) return Resources.of()
    // **Danger pays, and this line was left behind when it started to.** Round 21 inverted the term
    // in `core` — `100 − 10 × danger` became `100 + 35 × danger` — and the replica kept subtracting,
    // so the `check` below fired the first time the bot chose any target with a hazard or outside the
    // home system and `:sim:run` died on the whole report. Copied rather than approximated is the
    // rule this file states two paragraphs up; the cost of breaking it is that the harness cannot run
    // at all, which is the loud failure this discipline is chosen for.
    val paid = 100L + 35L * danger.coerceAtLeast(0)
    val richness = when (gathering) {
        ResourceKind.METAL -> world.traits.metalRichness
        ResourceKind.CRYSTAL -> world.traits.crystalRichness
        ResourceKind.DEUTERIUM -> error("a run never gathers deuterium")
    }
    val pricePerUnit = when (gathering) {
        ResourceKind.METAL -> 1L
        ResourceKind.CRYSTAL -> 2L
        ResourceKind.DEUTERIUM -> 3L
    }
    val rate = tuning.extractionPerHour *
        ResearchBalance.multiplier(Technology.PROSPECTING, research.levelOf(Technology.PROSPECTING)) /
        ResearchBalance.MULTIPLIER_BASIS
    val numerator = ships.total.toLong() * rate * stationMinutes *
        richness.perMillion.toLong() * paid
    val whole = numerator / (60L * GalaxyBalance.RICHNESS_BASIS * 100L * pricePerUnit)
    val cargo = when (gathering) {
        ResourceKind.METAL -> Resources.of(metal = whole)
        ResourceKind.CRYSTAL -> Resources.of(crystal = whole)
        ResourceKind.DEUTERIUM -> error("unreachable")
    }
    if (tuning.isShippedExtraction) {
        check(cargo == FleetBalance.cargo(world, gathering, ships, station, danger, research)) {
            "the harness's hold replica disagrees with FleetBalance.cargo: $cargo"
        }
    }
    return cargo
}

// **Flat since 0.10.1**, so the replica is the base itself. It carried a copy of `Curves.compound`
// until then — `internal`, hence the copy — and the copy is gone with the curve rather than left
// behind a tuning flag: a sweep row asking what a x1.5 hull would do is asking about a design that
// was withdrawn, and the harness's job is to measure the one that shipped. What the sweep still
// varies is the base, which is the number rounds 23 and 24 were about.
//
// Checked against `FleetBalance.shipCost` at the shipped base the same way the hold is.
private fun hullCostAt(tuning: FleetTuning): Resources {
    val cost = Resources.of(metal = tuning.hullBaseMetal, crystal = tuning.hullBaseCrystal)
    if (tuning.isShippedHull) {
        check(cost == FleetBalance.shipCost(ShipType.SKIFF)) {
            "the harness's hull replica disagrees with FleetBalance.shipCost: $cost"
        }
    }
    return cost
}

// The yard's clock, with the hull base lifted out the way `cargoAt` lifts the extraction rate — four
// minutes per root of the hull's own price, divided by the Robotics Factory, floored last. Newton's
// root rather than `sqrt` for `core`'s own reason: an integer answer that is the same on every
// target. Checked against `FleetBalance.buildDuration` at the shipped base on every call.
private fun buildDurationAt(tuning: FleetTuning, roboticsFactory: BuildingLevel): Duration {
    val cost = hullCostAt(tuning)
    var root = (cost.metal + cost.crystal).coerceAtLeast(0)
    if (root > 0) {
        var next = (root + 1) / 2
        while (next < root) {
            root = next
            next = (root + (cost.metal + cost.crystal) / root) / 2
        }
    }
    val wait = maxOf(FleetBalance.MINIMUM_YARD_DURATION, (4L * root).minutes / (1 + roboticsFactory.value))
    if (tuning.isShippedHull) {
        check(wait == FleetBalance.buildDuration(ShipType.SKIFF, roboticsFactory)) {
            "the harness's yard replica disagrees with FleetBalance.buildDuration: $wait"
        }
    }
    return wait
}

// Minute-resolution coverage, one array per ledger. The interval helpers above answer "how much of
// the window was covered"; this one answers "covered by *which*", which is the question the third
// ledger exists to make askable and which no merged total can reach.
private class Coverage(val minutes: Int) {
    val on: BooleanArray = BooleanArray(minutes)

    fun add(from: Instant, to: Instant, origin: Instant) {
        val start = (from - origin).inWholeMinutes.coerceIn(0, minutes.toLong()).toInt()
        val end = (to - origin).inWholeMinutes.coerceIn(0, minutes.toLong()).toInt()
        for (minute in start until end) on[minute] = true
    }

    fun soleAgainst(first: Coverage, second: Coverage): Int =
        (0 until minutes).count { on[it] && !first.on[it] && !second.on[it] }

    val covered: Int get() = on.count { it }
}

private class FleetOutcome(
    val days: Int,
    val withFleet: Boolean,
    val tuning: FleetTuning,
    val levels: Int,
    val robotics: Int,
    val roboticsFourAtHour: Int?,
    val dispatches: Int,
    val hullsOwned: Int,
    val hullSpendPriced: Long,
    val shipMinutesCommitted: Long,
    val shipMinutesOwned: Long,
    val fleetMetal: Long,
    val fleetCrystal: Long,
    val colonyMetal: Long,
    val colonyCrystal: Long,
    val colonyIdleMinutes: Long,
    val nothingAtAllMinutes: Long,
    val longestSilenceMinutes: Long,
    val colonyCover: Coverage,
    val probeCover: Coverage,
    val fleetCover: Coverage,
    val targetsChosen: Map<GalaxyCoordinate, Int>,
    val windowsChosen: Map<Duration, Int>,
    val bandsChosen: Map<Int, Int>,
    val bandsSurveyed: Map<Int, Int>,
    val gatheringChosen: Map<ResourceKind, Int>,
    val ledger: Ledger,
    val bookedMinutes: List<Long>,
    val censusBarriers: Map<String, MutableMap<Barrier, Int>>,
    val closingStock: Resources,
) {
    val dutyCycle: String
        get() = if (shipMinutesOwned == 0L) "—" else share(shipMinutesCommitted, shipMinutesOwned)

    val fleetMetalShare: String get() = share(fleetMetal, colonyMetal)
    val fleetCrystalShare: String get() = share(fleetCrystal, colonyCrystal)

    val coveredMinutes: Int
        get() = (0 until colonyCover.minutes).count { colonyCover.on[it] || probeCover.on[it] || fleetCover.on[it] }
}

// One decimal, which is all the precision any of these ratios carries.
private fun share(part: Long, whole: Long): String {
    if (whole <= 0) return "—"
    val tenths = part * 1_000 / whole
    return "${tenths / 10}.${tenths % 10}%"
}

// What the run picked, so the dispatch can be priced before it is committed.
private class Dispatch(
    val target: GalaxyCoordinate,
    val window: Duration,
    val gathering: ResourceKind,
    val cargo: Resources,
)

// **The strategy, stated rather than implied.** A player who understands the verb picks the window
// from the absence they are about to take, then sends every hull sitting in dock to the world that
// fills them fullest at that window, in the currency the colony is short of.
//
// **The window is chosen first, and that ordering is load-bearing.** Choosing it per target instead
// lets a world whose only rung is 24h claim a 24-hour station against a four-hour gap and win on
// station time alone — which is the strategy overshooting the absence, not the map being interesting.
// With the rung fixed first, distance and richness compete on the same clock.
//
// The target search maximises the hold in the chosen currency, which is the same as maximising the
// priced hold once the currency is fixed. Ties break on the coordinate, so the run is reproducible.
// The two readings of *"the window that best matches the gap"*, and they are not the same strategy.
//
// `HOME_WHEN_I_LOOK` takes the longest rung that still fits inside the absence, so the hulls are in
// dock at the next check-in and can go straight back out. `COVER_THE_GAP` takes the shortest rung that
// outlasts it, so nothing is ever idle but the cargo lands while the player is away and the hulls are
// unavailable at the next visit. Both are defensible; they differ by ~20 points of duty cycle and by
// which rungs of the ladder are ever used at all, so the report prints both rather than choosing.
private enum class WindowPolicy { HOME_WHEN_I_LOOK, COVER_THE_GAP }

private fun windowFor(policy: WindowPolicy, gapMinutes: Long): Duration = when (policy) {
    WindowPolicy.HOME_WHEN_I_LOOK ->
        FleetBalance.WINDOWS.lastOrNull { it.inWholeMinutes <= gapMinutes } ?: FleetBalance.WINDOWS.first()
    WindowPolicy.COVER_THE_GAP ->
        FleetBalance.WINDOWS.firstOrNull { it.inWholeMinutes >= gapMinutes } ?: FleetBalance.WINDOWS.last()
}

private fun bestDispatch(
    state: GameState,
    ships: Ships,
    window: Duration,
    gathering: ResourceKind,
    tuning: FleetTuning,
): Dispatch? {
    val home = state.galaxy.home
    var best: Dispatch? = null
    val candidates = state.galaxy.surveyed.sortedWith(compareBy({ it.galaxy }, { it.system }, { it.slot }))
    for (target in candidates) {
        if (target == home || state.galaxy.holderOf(target) != null) continue
        val world = worldAt(state.galaxy.seed, target) ?: continue
        val offered = FleetBalance.windowsFor(home, target)
        // A target that cannot be reached inside the chosen absence is simply not on the list, which
        // is what the narrowing ladder says on the screen.
        if (window !in offered) continue
        val station = FleetBalance.stationFor(home, target, window)
        // `state.research` rather than nothing researched: the bot buys Prospecting like any other
        // row, and a replica that ignored it would price every hold below what `startRun` charges.
        val cargo = cargoAt(
            tuning,
            world,
            gathering,
            ships,
            station,
            FleetBalance.danger(home, world),
            state.research,
        )
        if (best == null || priced(cargo) > priced(best.cargo)) {
            best = Dispatch(target = target, window = window, gathering = gathering, cargo = cargo)
        }
    }
    return best
}

private fun ownedSkiffs(state: GameState): Int =
    state.ships.countOf(ShipType.SKIFF) + state.runs.sumOf { it.ships.countOf(ShipType.SKIFF) }

// One hull, bought the way the player buys it. Null when the colony cannot pay, which is the loop's
// stop condition.
//
// **At the shipped base it goes through `buildShips`, and everywhere else it cannot.** The verb
// prices from `FleetBalance.shipCost`, so a sweep row asking what a 40-metal hull would do has no
// way to ask it through the verb — those rows keep the `state.copy` the whole loop used until 0.8.0,
// and `hullCostAt`'s own `check` is what keeps the two prices identical where they overlap. What the
// verb buys the harness that the copy did not is the rest of it: the log entry, the refusal
// branches, and the fact that the shipped column now measures the code a tap actually runs.
private fun buyOneHull(state: GameState, tuning: FleetTuning, at: Instant): GameState? {
    val cost = hullCostAt(tuning)
    if (!state.resources.covers(cost)) return null
    if (!tuning.isShippedHull) {
        // **The sweep rows queue too, and they have to.** Until 0.9.0 this branch differed from the
        // shipped one only in the price; if it kept handing the hull over at once it would now also
        // differ in *when the fleet exists*, which is the single largest term in a duty cycle. So it
        // carries the yard replica as well, chained onto the tail exactly as `buildShips` does.
        val startsAt = maxOf(at, state.yard.lastOrNull()?.completesAt ?: at)
        return state.copy(
            resources = state.resources - cost,
            yard = state.yard + YardJob(
                ship = ShipType.SKIFF,
                startedAt = startsAt,
                completesAt = startsAt + buildDurationAt(tuning, state.buildings.roboticsFactory),
            ),
        )
    }
    val result = buildShips(state, Ships.of(ShipType.SKIFF, 1), at = at)
    check(result is BuildShipsResult.Started) { "the harness could afford a hull and buildShips said $result" }
    return result.state
}

// One runner, two lengths, one variable. The colony's rule is the harness's usual one — everything
// affordable, cheapest first — and the fleet's is the paragraph above. Measurement is hourly; acting
// is at the four times a day the brief designs for.
//
// **A hull is bought before the buildings, at most one per check-in.** Both halves are deliberate and
// both follow a precedent already in this file: the probe is bought first *"which is the pessimistic
// ordering on purpose"*, so what it costs the colony shows up in the level count rather than hiding
// behind a full queue; and one per check-in is the probe's rule too — a statement about how a person
// plays rather than about what the game allows.
//
// **The purchase goes through `buildShips` at the shipped price**, which it could not before 0.8.0 —
// the verb did not exist and the harness bought hulls with a raw `state.copy`. The sweep rows that
// ask what a 40-metal hull would do still cannot use the verb, because it prices from
// `FleetBalance.shipCost`; see `buyOneHull` for the split and for what the check between them keeps
// true.
//
// **`hourlyColony` is the one knob that is not about the fleet, and it exists because the two
// questions want different colonies.** The opening's readings — idleness, what a check-in booked,
// duty cycle — are only meaningful for a player who acts when they look, so they are measured at four
// a day. The fortnight's blocker ledger is quoted in the balance log against the hour-stepped
// whole-tree bot, and at four a day that reading *inverts*: the colony sits on banked stock between
// visits and reads metal-blocked where the hourly bot reads crystal-blocked. So the fortnight column
// keeps the hourly colony and reproduces the number it is being compared with. The fleet and the
// probe act four times a day in both.
private fun fleetRun(
    days: Int,
    withFleet: Boolean,
    tuning: FleetTuning,
    hourlyColony: Boolean = false,
    withProbes: Boolean = true,
    policy: WindowPolicy = WindowPolicy.HOME_WHEN_I_LOOK,
    // Null is the adaptive rule — crystal when the colony is short of it, metal otherwise — which is
    // what a player does. Forcing one is how the sweep gets a **crystal** income share at all: at four
    // a day the opening colony is never crystal-short, so the adaptive player gathers metal for the
    // whole of the first 48 hours and §4's binding row would read 0.0% at every candidate rate.
    forceGathering: ResourceKind? = null,
    hullsFirst: Boolean = false,
): FleetOutcome {
    var state = GameState.initial(GalaxySeed(SIM_GALAXY_SEED))
    val genesis = Instant.fromEpochMilliseconds(0)
    val end = genesis + (days * 24).hours
    val totalMinutes = days * 24 * 60
    var now = genesis
    val startingStock = state.resources

    val ledger = Ledger()
    val colonyBusy = mutableListOf<Pair<Instant, Instant>>()
    val probeBusy = mutableListOf<Pair<Instant, Instant>>()
    val fleetBusy = mutableListOf<Pair<Instant, Instant>>()
    val colonyCover = Coverage(totalMinutes)
    val probeCover = Coverage(totalMinutes)
    val fleetCover = Coverage(totalMinutes)

    var probeSpendMetal = 0L
    var hullSpendMetal = 0L
    var hullSpendCrystal = 0L
    var dispatches = 0
    var shipMinutesCommitted = 0L
    var shipMinutesOwned = 0L
    var roboticsFourAtHour: Int? = null
    val targetsChosen = linkedMapOf<GalaxyCoordinate, Int>()
    val windowsChosen = linkedMapOf<Duration, Int>()
    val bandsChosen = linkedMapOf<Int, Int>()
    val gatheringChosen = linkedMapOf<ResourceKind, Int>()
    val bookedMinutes = mutableListOf<Long>()
    val censusBarriers = linkedMapOf<String, MutableMap<Barrier, Int>>()

    val offsets = (0 until days).flatMap { day -> CHECK_IN_HOURS.map { day * 24 + it } }
    val checkIns = offsets.toSet()
    // Read at the top of a check-in — the moment the player is looking at, before anything is spent —
    // and consumed further down when the dispatch picks its currency.
    var shortOfCrystal = false

    for (hour in 0 until days * 24) {
        val at = genesis + hour.hours
        state = advance(state, from = now, to = at)
        now = at
        if (state.buildings.roboticsFactory.value >= 4 && roboticsFourAtHour == null) roboticsFourAtHour = hour

        if (hour in checkIns) {
            val gapMinutes = ((offsets.firstOrNull { it > hour } ?: (days * 24)) - hour) * 60L

            // Read before the check-in spends anything — the moment the player is looking at, which
            // is the harness's own convention for every screen-shaped reading.
            val visible = optionsFor(state, FULL_PLAN, withProjects = true)
            val wantedNow = (visible.buildings + visible.projects).map { it.second }
            shortOfCrystal = wantedNow.any { Blocker.CRYSTAL in shortagesOf(it, state.resources) }

            for ((kind, row) in censusOf(state, if (withFleet) tuning else null).byKind) {
                val into = censusBarriers.getOrPut(kind) { Barrier.entries.associateWith { 0 }.toMutableMap() }
                for (barrier in Barrier.entries) into[barrier] = into.getValue(barrier) + row.getValue(barrier)
            }

            (if (withProbes) probeTargetFor(state, gapMinutes) else null)?.let { target ->
                (startSurvey(state, target, at = now) as? StartSurveyResult.Started)?.let { started ->
                    state = started.state
                    val job = state.surveys.last()
                    probeBusy += now to job.completesAt
                    probeCover.add(now, job.completesAt, genesis)
                    probeSpendMetal += SurveyBalance.COST_METAL
                }
            }

            if (withFleet && hullsFirst) {
                while (true) {
                    val hullCost = hullCostAt(tuning)
                    state = buyOneHull(state, tuning, at = now) ?: break
                    hullSpendMetal += hullCost.metal
                    hullSpendCrystal += hullCost.crystal
                }
            }
        }

        // The colony's own buying. **Hulls come out of what is left after it**, which is the sheet's
        // own account of why the fleet gets bought at all: *"`startUpgrade` refuses a facility that is
        // already building, so a check-in that has tapped all six has nowhere left to put its metal."*
        // The probe's pessimistic ordering does not transfer — a probe is one fixed 150-metal purchase
        // and a greedy hull loop in front of the buildings would take every spare unit the colony has,
        // which measures the ordering rather than the price. `hullsFirst` prints that variant so the
        // difference is a number instead of an argument.
        if (hourlyColony || hour in checkIns) {
            for ((building, cost) in optionsFor(state, FULL_PLAN, withProjects = true).buildings) {
                if (!state.resources.covers(cost)) continue
                (startUpgrade(state, building, at = now) as? StartUpgradeResult.Started)?.let { started ->
                    state = started.state
                    ledger.spend(cost)
                    state.builds[building]?.let { job ->
                        colonyBusy += now to job.completesAt
                        colonyCover.add(now, job.completesAt, genesis)
                    }
                }
            }
            for ((project, cost) in optionsFor(state, FULL_PLAN, withProjects = true).projects) {
                if (state.slotBusyFor(project) || !state.resources.covers(cost)) continue
                when (project) {
                    is Technology -> (startResearch(state, project, at = now) as? StartResearchResult.Started)
                        ?.let { state = it.state; ledger.spend(cost) }
                    is AdaptationTechnology ->
                        (startAdaptation(state, project, at = now) as? StartAdaptationResult.Started)
                            ?.let { state = it.state; ledger.spend(cost) }
                }
                state.slotFreesAfter(project)?.let { freesAt ->
                    colonyBusy += now to freesAt
                    colonyCover.add(now, freesAt, genesis)
                }
            }
        }

        if (hour in checkIns) {
            val gapMinutes = ((offsets.firstOrNull { it > hour } ?: (days * 24)) - hour) * 60L
            if (withFleet) {
                // **Greedy, not one per check-in**, and the difference decides whether the hull-base
                // sweep measures anything: capped at one a visit the fleet reaches the same size at
                // every price, so the curve's own bound is never tested and the sweep reports the cap.
                if (!hullsFirst) {
                    while (true) {
                        val hullCost = hullCostAt(tuning)
                        state = buyOneHull(state, tuning, at = now) ?: break
                        hullSpendMetal += hullCost.metal
                        hullSpendCrystal += hullCost.crystal
                    }
                }

                val idle = state.ships.countOf(ShipType.SKIFF)
                if (idle > 0) {
                    val manifest = Ships.of(ShipType.SKIFF, idle)
                    // Crystal when the colony is short of crystal for something it wants right now,
                    // metal otherwise. A rule rather than a judgement, and it is the whole reason the
                    // payout is one currency: strip that away and there is nothing to choose.
                    val gathering = forceGathering
                        ?: if (shortOfCrystal) ResourceKind.CRYSTAL else ResourceKind.METAL
                    val choice = bestDispatch(state, manifest, windowFor(policy, gapMinutes), gathering, tuning)
                    if (choice != null) {
                        // Read before the dispatch, because the dispatch is what debits it.
                        val inTheGround = state.galaxy.remaining(choice.target, gathering, now)
                        val result = startRun(
                            state = state,
                            target = choice.target,
                            gathering = choice.gathering,
                            ships = manifest,
                            window = choice.window,
                            at = now,
                        )
                        if (result is StartRunResult.Started) {
                            var next = result.state
                            val run = next.runs.last()
                            // **The vein clamps this sweep too, and it cannot be switched off.**
                            // Emptying `galaxy.deposits` does not help: at 0.9 a grown fleet outlifts
                            // a world's *whole* cap, so the clamp bites on an untouched vein. So the
                            // replica is compared against what `startRun` may legitimately have done
                            // — priced the hold, then taken the smaller of it and what was there —
                            // and the rows below are lower than round 22's for that reason rather
                            // than because a rate moved.
                            val clampedTo = minOf(choice.cargo.of(gathering), inTheGround)
                            if (tuning.isShippedExtraction) {
                                check(run.cargo.of(gathering) == clampedTo) {
                                    "startRun priced the hold at ${run.cargo.of(gathering)}, " +
                                        "the sweep at $clampedTo"
                                }
                            } else {
                                val held = if (gathering == ResourceKind.METAL) {
                                    Resources.of(metal = clampedTo)
                                } else {
                                    Resources.of(crystal = clampedTo)
                                }
                                next = next.copy(runs = next.runs.dropLast(1) + run.copy(cargo = held))
                            }
                            state = next
                            val returnsAt = now + choice.window
                            fleetBusy += now to returnsAt
                            fleetCover.add(now, returnsAt, genesis)
                            dispatches++
                            shipMinutesCommitted += manifest.total *
                                (minOf(returnsAt, end) - now).inWholeMinutes
                            targetsChosen[choice.target] = (targetsChosen[choice.target] ?: 0) + 1
                            windowsChosen[choice.window] = (windowsChosen[choice.window] ?: 0) + 1
                            val band = FleetBalance.distanceBand(state.galaxy.home, choice.target)
                            bandsChosen[band] = (bandsChosen[band] ?: 0) + 1
                            gatheringChosen[gathering] = (gatheringChosen[gathering] ?: 0) + 1
                        }
                    }
                }
            }

            bookedMinutes += futureEvents(state, now = now).lastOrNull()?.let { (it.at - now).inWholeMinutes } ?: 0L
        }

        shipMinutesOwned += ownedSkiffs(state) * 60L
        val remaining = optionsFor(state, FULL_PLAN, withProjects = true)
        ledger.record((remaining.buildings + remaining.projects).map { it.second }, state.resources)
    }

    state = advance(state, from = now, to = end)
    if (state.buildings.roboticsFactory.value >= 4 && roboticsFourAtHour == null) roboticsFourAtHour = days * 24

    // What actually landed, read off the log rather than off the dispatches — a run still in flight at
    // the end of the window has delivered nothing and must not be counted as if it had.
    val delivered = state.eventLog.filterIsInstance<Event.FleetReturned>()
    val fleetMetal = delivered.sumOf { it.cargo.metal }
    val fleetCrystal = delivered.sumOf { it.cargo.crystal }

    // **The colony's own income, by the accrual identity rather than by sampling a rate.** Everything
    // the mines made is either still in the store, already spent, or was never theirs — so
    // `(closing − opening) + spent − delivered` is exact, where a per-hour sample would miss every
    // level that completed between two samples. Safe because the store never approaches its cap: the
    // deepest run in this file closes on 293k against a 10,000,000 ceiling.
    val spentMetal = ledger.spentMetal + probeSpendMetal + hullSpendMetal
    val spentCrystal = ledger.spentCrystal + hullSpendCrystal
    val colonyMetal = state.resources.metal - startingStock.metal + spentMetal - fleetMetal
    val colonyCrystal = state.resources.crystal - startingStock.crystal + spentCrystal - fleetCrystal

    return FleetOutcome(
        days = days,
        withFleet = withFleet,
        tuning = tuning,
        levels = BuildingType.entries.sumOf { state.buildings.levelOf(it).value },
        robotics = state.buildings.roboticsFactory.value,
        roboticsFourAtHour = roboticsFourAtHour,
        dispatches = dispatches,
        hullsOwned = ownedSkiffs(state),
        hullSpendPriced = hullSpendMetal + 2 * hullSpendCrystal,
        shipMinutesCommitted = shipMinutesCommitted,
        shipMinutesOwned = shipMinutesOwned,
        fleetMetal = fleetMetal,
        fleetCrystal = fleetCrystal,
        colonyMetal = colonyMetal,
        colonyCrystal = colonyCrystal,
        colonyIdleMinutes = idleMinutes(colonyBusy, genesis, end),
        nothingAtAllMinutes = idleMinutes(colonyBusy + probeBusy + fleetBusy, genesis, end),
        longestSilenceMinutes = longestIdleRun(colonyBusy + probeBusy + fleetBusy, genesis, end),
        colonyCover = colonyCover,
        probeCover = probeCover,
        fleetCover = fleetCover,
        targetsChosen = targetsChosen,
        windowsChosen = windowsChosen,
        bandsChosen = bandsChosen,
        // What the probes actually put within reach, so the chosen-band row can be read against the
        // available ones rather than against a hope. The probe aims at the longest flight that still
        // lands before the next check-in, so the surveyed set is the home system plus a scatter of
        // distant ones — there is very little in between, and that is a property of the probe's own
        // strategy rather than of the map.
        bandsSurveyed = state.galaxy.surveyed
            .groupingBy { FleetBalance.distanceBand(state.galaxy.home, it) }
            .eachCount()
            .toSortedMap(),
        gatheringChosen = gatheringChosen,
        ledger = ledger,
        bookedMinutes = bookedMinutes,
        censusBarriers = censusBarriers,
        closingStock = state.resources,
    )
}

private fun printFleetReport() {
    println("## The fleet, as the player meets it")
    println()
    println("Four check-ins a day (08:00 / 13:00 / 19:00 / 23:00), measured hourly. The colony's rule")
    println("is the harness's usual one — everything affordable, cheapest first — and probes are on in")
    println("**both** columns, because probes shipped. The fleet is the only variable.")
    println()
    println("The fleet's rule, in one sentence: **buy hulls while the next one is affordable, before")
    println("anything else, then pick the longest window rung that still fits inside the gap until the")
    println("next check-in, and send every idle skiff to the world that fills them fullest at that")
    println("rung, in the currency the colony is short of.** Greedy and bought first, which is the")
    println("harness's rule for everything else and the probe's pessimistic ordering — so what the")
    println("fleet costs shows up in the level count rather than hiding behind a full build queue, and")
    println("the size of the fleet is set by the hull curve rather than by a cap this file invented.")
    println("The alternative reading of \"match the gap\" is measured two tables down.")
    println()
    println("**Caveat, and it is structural.** This runner checks in four times a day, so the gaps it")
    println("has to fill are 5h, 6h, 4h and 9h — which means it **never asks for the 1h rung and")
    println("never asks for the 24h one.** The opening arc of the ladder and the frontier end of it")
    println("are both outside this instrument, and no report in this file except `printFirstSitting`")
    println("can see anything shorter than its own gap.")
    println()

    val control = fleetRun(days = 2, withFleet = false, tuning = SHIPPED_FLEET)
    val fleet = fleetRun(days = 2, withFleet = true, tuning = SHIPPED_FLEET)
    val controlFortnight = fleetRun(days = 14, withFleet = false, tuning = SHIPPED_FLEET, hourlyColony = true)
    val fleetFortnight = fleetRun(days = 14, withFleet = true, tuning = SHIPPED_FLEET, hourlyColony = true)

    println("| Reading, over 48h | no fleet | **with fleet** |")
    println("|---|---|---|")
    printFleetPair("Hours the **colony** had nothing in flight", control, fleet) {
        "${it.colonyIdleMinutes / 60}h (${percent((it.colonyIdleMinutes / 60).toInt(), it.days * 24)})"
    }
    printFleetPair("Hours with **nothing at all** in flight", control, fleet) {
        "${it.nothingAtAllMinutes / 60}h (${percent((it.nothingAtAllMinutes / 60).toInt(), it.days * 24)})"
    }
    printFleetPair("Longest unbroken silence", control, fleet) { it.longestSilenceMinutes.asWait() }
    printFleetPair("**Fleet duty cycle** — ship-hours committed / owned", control, fleet) { it.dutyCycle }
    printFleetPair("Dispatches", control, fleet) { "${it.dispatches}" }
    printFleetPair("Hulls owned at 48h", control, fleet) { "${it.hullsOwned}" }
    printFleetPair("Priced spent on hulls", control, fleet) { it.hullSpendPriced.grouped() }
    printFleetPair("Fleet metal delivered", control, fleet) { it.fleetMetal.grouped() }
    printFleetPair("Fleet crystal delivered", control, fleet) { it.fleetCrystal.grouped() }
    printFleetPair("**Fleet metal as a share of colony metal income**", control, fleet) { it.fleetMetalShare }
    printFleetPair("**Fleet crystal as a share of colony crystal income**", control, fleet) { it.fleetCrystalShare }
    printFleetPair("Median work a check-in booked", control, fleet) { "${it.bookedMinutes.median()} min" }
    printFleetPair("Building levels at 48h", control, fleet) { "${it.levels} (robotics ${it.robotics})" }
    printFleetPair("Closing metal", control, fleet) { it.closingStock.metal.grouped() }
    printFleetPair("Closing crystal", control, fleet) { it.closingStock.crystal.grouped() }
    println()

    println("Same pair over a fortnight, **with the hour-stepped colony** — the whole-tree bot the")
    println("balance log's blocker numbers are quoted against, so `no fleet` here should reproduce")
    println("them. The fleet still acts four times a day. At four a day the colony banks stock between")
    println("visits and the ledger reads metal-blocked where the hourly one reads crystal-blocked, so")
    println("this column would say the opposite of the log's about the same game.")
    println()
    println("| Reading, over 14 days | no fleet | **with fleet** |")
    println("|---|---|---|")
    printFleetPair("Robotics 4 reached", controlFortnight, fleetFortnight) {
        it.roboticsFourAtHour?.let { hour -> "hour $hour" } ?: "**never**"
    }
    for (blocker in Blocker.entries) {
        printFleetPair("Hours blocked by ${blocker.name.lowercase()} **alone**, of 336", controlFortnight, fleetFortnight) {
            "${it.ledger.soleBlockerHours.getValue(blocker)}"
        }
    }
    for (blocker in Blocker.entries) {
        printFleetPair("Hours short of ${blocker.name.lowercase()} at all, of 336", controlFortnight, fleetFortnight) {
            "${it.ledger.shortHours.getValue(blocker)}"
        }
    }
    printFleetPair("Building levels at 14d", controlFortnight, fleetFortnight) { "${it.levels}" }
    printFleetPair("Dispatches", controlFortnight, fleetFortnight) { "${it.dispatches}" }
    printFleetPair("Hulls owned", controlFortnight, fleetFortnight) { "${it.hullsOwned}" }
    printFleetPair("Fleet duty cycle", controlFortnight, fleetFortnight) { it.dutyCycle }
    printFleetPair("Fleet metal / colony metal", controlFortnight, fleetFortnight) { it.fleetMetalShare }
    printFleetPair("Fleet crystal / colony crystal", controlFortnight, fleetFortnight) { it.fleetCrystalShare }
    println()

    // **What the probe costs the blocker ledger, isolated — because it is not small.** The balance
    // log's 273 of 336 comes from a fortnight with no probes in it, and the sole-blocker reading is
    // the brittle one the harness's own comment warns about: *"'short of this resource and nothing
    // else' is a knife-edge on which purchase happens to be next."* Sixty-odd probes at 150 metal
    // each is 0.9% of the metal this run spends and it reorders the queue anyway.
    val noProbes = fleetRun(days = 14, withFleet = false, tuning = SHIPPED_FLEET, hourlyColony = true, withProbes = false)
    println("Control, hour-stepped colony, **no probes either** — the closest this runner gets to the")
    println("`printWholeTreeRun` the log's numbers come from: crystal alone " +
        "**${noProbes.ledger.soleBlockerHours.getValue(Blocker.CRYSTAL)}**, metal alone " +
        "**${noProbes.ledger.soleBlockerHours.getValue(Blocker.METAL)}**, crystal short at all " +
        "**${noProbes.ledger.shortHours.getValue(Blocker.CRYSTAL)}**, of 336. The log's whole-tree run " +
        "reads 273 / 39 / 320; the residual gap is that this runner acts at hour 0 where that one " +
        "first acts at hour 1. **So read the crystal column against the control in this table, never " +
        "against 273.**")
    println()

    printThreeLedgers(fleet)
    printWindowPolicies()
    printFleetSpread(fleet, fleetFortnight)
    printFleetCensus(fleet)
    printSkiffAgainstColony()
    printHullAgainstMine(fleet)
    printFleetSweeps()
}

// **§4's own invariant, measured — and it is the sharpest thing in this report.** The sheet says
// *"the mine is the better rate buy, permanently and by construction — and the fleet is bought
// anyway"*, and §6 asks for a `BalanceCurveTest` that pins it: *the fleet's priced return per priced
// unit spent stays below a mine level's at every depth.* Neither the sheet nor the test says what the
// rate has to be for that to be **true**, and it is not true at every rate.
//
// A hull returns `EXTRACTION_PER_HOUR x dutyCycle` priced units an hour and costs what the curve says,
// so its payback is a division. The mine it competes with is the Metal Mine level the colony actually
// holds at the check-in where that hull gets bought — read off the run rather than assumed.
private fun printHullAgainstMine(measured: FleetOutcome) {
    val duty = if (measured.shipMinutesOwned == 0L) 0.0
    else measured.shipMinutesCommitted.toDouble() / measured.shipMinutesOwned

    // Which Metal Mine level the colony is on at each check-in, from the no-fleet control — so the
    // comparison is against the buy the player would otherwise have made at that exact moment.
    var state = GameState.initial(GalaxySeed(SIM_GALAXY_SEED))
    val genesis = Instant.fromEpochMilliseconds(0)
    var now = genesis
    val mineLevelAt = mutableListOf<Int>()
    val offsets = (0 until 2).flatMap { day -> CHECK_IN_HOURS.map { day * 24 + it } }
    for ((index, offset) in offsets.withIndex()) {
        val at = genesis + offset.hours
        state = advance(state, from = now, to = at)
        now = at
        mineLevelAt += state.buildings.levelOf(BuildingType.METAL_MINE).value
        val gapMinutes = ((offsets.getOrNull(index + 1) ?: 48) - offset) * 60L
        probeTargetFor(state, gapMinutes)?.let { target ->
            (startSurvey(state, target, at = now) as? StartSurveyResult.Started)?.let { state = it.state }
        }
        for ((building, cost) in optionsFor(state, FULL_PLAN, withProjects = true).buildings) {
            if (!state.resources.covers(cost)) continue
            (startUpgrade(state, building, at = now) as? StartUpgradeResult.Started)?.let { state = it.state }
        }
    }

    println("### The hull against the mine of the day")
    println()
    println("A hull returns `EXTRACTION_PER_HOUR x duty cycle` priced units an hour — the measured duty")
    println("cycle is ${share(measured.shipMinutesCommitted, measured.shipMinutesOwned)} — so its payback")
    println("is a division. The last column is the payback of the **Metal Mine level the colony is")
    println("actually on** at the check-in where that hull gets bought, which is the buy the fleet is")
    println("competing with at that exact moment.")
    println()
    println("**The hull column is flat since 0.10.1 and the mine column is not, which is the whole of")
    println("what this table now measures**: the hull's payback is a constant and the buy it competes")
    println("with gets dearer every level, so the crossover this table used to find at the sixth or")
    println("seventh hull moves outward on its own as the colony grows. A cell in bold is a cell where")
    println("the mine loses — the rule the sheet asserts is that it never does.")
    println()
    println("| Nth skiff | priced cost | payback at 10 | at 20 | at 30 | at 40 | the mine that day |")
    println("|---|---|---|---|---|---|---|")
    for (nth in 2..9) {
        val cost = priced(FleetBalance.shipCost(ShipType.SKIFF))
        val mineLevel = mineLevelAt.getOrElse(nth - 2) { mineLevelAt.last() }
        val minePayback = pricedPaybackHours(BuildingType.METAL_MINE, mineLevel)
        val cells = listOf(10L, 20L, 30L, 40L).joinToString(" | ") { rate ->
            val payback = cost / (rate * duty)
            val hours = (payback * 10).toLong()
            val text = "${hours / 10}.${hours % 10}h"
            if (payback < minePayback) "**$text**" else text
        }
        println("| $nth | ${cost.grouped()} | $cells | Metal Mine $mineLevel -> ${mineLevel + 1}: ${minePayback}h |")
    }
    println()
}

// **The sizing measurement, and it depends on no strategy at all.** Every reading above is a property
// of the scripted player: how many hulls it bought, which currency it happened to want, how full its
// duty cycle ran. This one is a property of the *constant* — what one hull brings home against what
// the colony makes at that moment — which is the question §4 says the draft answered against a colony
// 0.2.7 deleted and against the priced basket rather than the chosen currency.
//
// The crystal column is the binding one. A hold of N priced units is N metal **or N/2 crystal**, and
// the colony makes metal 2.5 : 1 over crystal — so the same run is worth ~1.25x more of the colony's
// crystal hour than of its metal hour. Reading the basket hides exactly that factor.
private fun printSkiffAgainstColony() {
    println("### What one skiff is worth against the colony that receives it")
    println()
    println("A single hull, the 6h rung, at the best surveyed neighbour of the moment, against the")
    println("colony's own hourly income at the same moment. No strategy in it: this is the constant")
    println("measured rather than the bot. The colony is the four-a-day no-fleet control.")
    println()
    println("| At | colony metal/h | colony crystal/h | one skiff, 6h, as metal | as crystal " +
        "| = hours of metal income | = hours of **crystal** income |")
    println("|---|---|---|---|---|---|---|")

    var state = GameState.initial(GalaxySeed(SIM_GALAXY_SEED))
    val genesis = Instant.fromEpochMilliseconds(0)
    var now = genesis
    val marks = listOf(0, 5, 11, 24, 48, 96, 168)
    val samples = mutableListOf<Triple<Int, Long, Long>>()
    val offsets = (0 until 8).flatMap { day -> CHECK_IN_HOURS.map { day * 24 + it } }
    for ((index, offset) in offsets.withIndex()) {
        val at = genesis + offset.hours
        state = advance(state, from = now, to = at)
        now = at

        if (offset in marks) {
            val metalPerHour = PlaceholderBalance.effectiveMetalProductionPerHour(state.buildings, state.research)
            val crystalPerHour = PlaceholderBalance.effectiveCrystalProductionPerHour(state.buildings, state.research)
            val one = Ships.of(ShipType.SKIFF, 1)
            val asMetal = bestDispatch(state, one, 6.hours, ResourceKind.METAL, SHIPPED_FLEET)?.cargo?.metal ?: 0
            val asCrystal = bestDispatch(state, one, 6.hours, ResourceKind.CRYSTAL, SHIPPED_FLEET)?.cargo?.crystal ?: 0
            samples += Triple(offset, metalPerHour, crystalPerHour)
            println("| hour $offset | $metalPerHour | $crystalPerHour | ${asMetal.grouped()} " +
                "| ${asCrystal.grouped()} | ${asMetal * 10 / metalPerHour / 10}." +
                "${asMetal * 10 / metalPerHour % 10}h | **${asCrystal * 10 / crystalPerHour / 10}." +
                "${asCrystal * 10 / crystalPerHour % 10}h** |")
        }

        val gapMinutes = ((offsets.getOrNull(index + 1) ?: (8 * 24)) - offset) * 60L
        probeTargetFor(state, gapMinutes)?.let { target ->
            (startSurvey(state, target, at = now) as? StartSurveyResult.Started)?.let { state = it.state }
        }
        for ((building, cost) in optionsFor(state, FULL_PLAN, withProjects = true).buildings) {
            if (!state.resources.covers(cost)) continue
            (startUpgrade(state, building, at = now) as? StartUpgradeResult.Started)?.let { state = it.state }
        }
        for ((project, cost) in optionsFor(state, FULL_PLAN, withProjects = true).projects) {
            if (state.slotBusyFor(project) || !state.resources.covers(cost)) continue
            when (project) {
                is Technology -> (startResearch(state, project, at = now) as? StartResearchResult.Started)
                    ?.let { state = it.state }
                is AdaptationTechnology -> (startAdaptation(state, project, at = now) as? StartAdaptationResult.Started)
                    ?.let { state = it.state }
            }
        }
    }
    println()

    // The same thing as the share §4 actually asks for, at every candidate rate, so the constant can
    // be read straight off the row rather than inferred from the bot's behaviour.
    println("A 6h run by one skiff, as a share of one **hour** of the colony's crystal income —")
    println("§4's binding row, at each candidate rate. A skiff away for six hours delivering six")
    println("hours of the colony's crystal output is a second colony; delivering one is a top-up.")
    println()
    println("| `EXTRACTION_PER_HOUR` | hour 0 | hour 24 | hour 48 | hour 168 |")
    println("|---|---|---|---|---|")
    // The hold is linear in the rate and floors once, so scaling the measured 40-rate hold is exact
    // to within a unit — and the shape is what is being read here rather than the last digit.
    for (rate in RATE_CANDIDATES) {
        val cells = listOf(0, 24, 48, 168).joinToString(" | ") { mark ->
            val sample = samples.firstOrNull { it.first == mark }
            if (sample == null) "—" else {
                // 5h40m of station at the 6h rung to an own-system neighbour, at richness r.
                val hold = rate * 340 / 60
                "${hold * 10 / sample.third / 10}.${hold * 10 / sample.third % 10}h"
            }
        }
        println("| $rate | $cells |")
    }
    println()
    println("*(The second table prices the hold at richness 1.00 and danger 0, so it is the floor of")
    println("what a real neighbour delivers — the first table's own numbers are the measured ones.)*")
    println()
}

// **Is the window a dial or is it decoration?** The two readings of "match the gap" differ by which
// rungs are ever used, and at a four-a-day cadence the longest gap is nine hours — so under either
// policy the 24h rung is only reachable by a player who deliberately overshoots. That is what decides
// whether a far world can ever be a target, because a far world offers no shorter rung.
private fun printWindowPolicies() {
    println("### The two ways to read \"the window that best matches the gap\"")
    println()
    println("`home when I look` takes the longest rung that fits inside the absence; `cover the gap`")
    println("takes the shortest rung that outlasts it. Both are honest players. 48h, shipped tuning.")
    println()
    println("| Policy | duty cycle | dispatches | rungs used | bands used | fleet metal / colony metal |")
    println("|---|---|---|---|---|---|")
    for (policy in WindowPolicy.entries) {
        val outcome = fleetRun(days = 2, withFleet = true, tuning = SHIPPED_FLEET, policy = policy)
        val rungs = outcome.windowsChosen.entries.sortedBy { it.key }
            .joinToString(" ") { "${it.key.label()}×${it.value}" }
        val bands = outcome.bandsChosen.entries.sortedBy { it.key }.joinToString(" ") { "b${it.key}×${it.value}" }
        println("| ${policy.name.lowercase().replace('_', ' ')} | ${outcome.dutyCycle} " +
            "| ${outcome.dispatches} | $rungs | $bands | ${outcome.fleetMetalShare} |")
    }
    println()

    // The other free choice in the strategy, printed for the same reason: it moves `levels @48h`,
    // which is the colony guardrail, so a reader has to be able to see how much of that number is the
    // hull price and how much is the order the bot spends in.
    println("And the other free choice — whether hulls are bought **before** the buildings or out of")
    println("what is left after them. The sheet's own account of why the fleet gets bought is the")
    println("second one; the probe's precedent is the first.")
    println()
    println("**This is the bracket the sizing recommendation has to survive.** A fleet-first player")
    println("owns more hulls sooner, so the crystal column triples — and a rate is only safe if even")
    println("that player cannot out-produce their own colony in the currency they chose.")
    println()
    println("**Read this table before any other.** Until the Shipyard shipped there was no")
    println("fleet-first player to measure — `buildShips` did not exist, so the hull count was one")
    println("and the last column was a reading of a game nobody could play. It is a real player now,")
    println("and a cell over 100% is a fleet that has become the economy.")
    println()
    println("| Order | rate | levels @48h | hulls @48h | priced on hulls " +
        "| fleet metal / colony metal | **fleet crystal / colony crystal** |")
    println("|---|---|---|---|---|---|---|")
    for (rate in RATE_CANDIDATES.filter { it >= 20L }) {
        val tuning = FleetTuning(rate, FleetBalance.HULL_BASE_METAL)
        for (first in listOf(false, true)) {
            val outcome = fleetRun(days = 2, withFleet = true, tuning = tuning, hullsFirst = first)
            val crystal = fleetRun(
                days = 2,
                withFleet = true,
                tuning = tuning,
                forceGathering = ResourceKind.CRYSTAL,
                hullsFirst = first,
            )
            println("| ${if (first) "hulls first" else "hulls from what is left"} | $rate " +
                "| ${outcome.levels} | ${outcome.hullsOwned} | ${outcome.hullSpendPriced.grouped()} " +
                "| ${outcome.fleetMetalShare} | **${crystal.fleetCrystalShare}** |")
        }
    }
    println()
}

private fun printFleetPair(label: String, control: FleetOutcome, fleet: FleetOutcome, of: (FleetOutcome) -> String) {
    println("| $label | ${of(control)} | **${of(fleet)}** |")
}

// **The reading the sheet says decides it, and no total can show it.** A fleet that only flies while a
// build is running has bought nothing back: the hours were already covered. What is printed is the
// share of *covered* time each ledger is the only thing covering.
private fun printThreeLedgers(outcome: FleetOutcome) {
    println("### Three ledgers, and which one is alone")
    println()
    println("`colonyBusy` is mines and the research slot; `probeBusy` is surveys in flight; `fleetBusy`")
    println("is runs in flight. They are kept apart so a new verb cannot take credit for a complaint it")
    println("does not touch. Minute resolution over 48 hours.")
    println()
    val total = outcome.days * 24 * 60
    val covered = outcome.coveredMinutes
    println("| Ledger | Covers | Is the **only** cover for |")
    println("|---|---|---|")
    val rows = listOf(
        "colony" to outcome.colonyCover,
        "probes" to outcome.probeCover,
        "fleet" to outcome.fleetCover,
    )
    for ((name, cover) in rows) {
        val others = rows.filter { it.second !== cover }.map { it.second }
        val sole = cover.soleAgainst(others[0], others[1])
        println("| $name | ${percent(cover.covered, total)} of the window | " +
            "**${percent(sole, covered)}** of covered time (${sole / 60}h ${sole % 60}m) |")
    }
    println("| *any of them* | ${percent(covered, total)} of the window | — |")
    println()
}

// **Wide, or the map is decoration.** If every dispatch goes to the same world on the same rung then
// distance and the window buy nothing and this is a probe with a cargo hold.
private fun printFleetSpread(short: FleetOutcome, long: FleetOutcome) {
    println("### The spread of what was chosen")
    println()
    println("| Over | Distinct targets | Distinct windows | Distinct distance bands | Currencies |")
    println("|---|---|---|---|---|")
    for (outcome in listOf(short, long)) {
        println("| ${outcome.days} days, ${outcome.dispatches} dispatches | ${outcome.targetsChosen.size} " +
            "| ${outcome.windowsChosen.size} | ${outcome.bandsChosen.size} | ${outcome.gatheringChosen.size} |")
    }
    println()
    println("Over the fortnight, in full — targets, then windows, then bands:")
    println()
    println("- targets: " + long.targetsChosen.entries
        .sortedByDescending { it.value }
        .joinToString { "[${it.key.galaxy}:${it.key.system}:${it.key.slot}] ×${it.value}" })
    println("- windows: " + long.windowsChosen.entries
        .sortedBy { it.key }
        .joinToString { "${it.key.label()} ×${it.value}" })
    println("- bands chosen: " + long.bandsChosen.entries
        .sortedBy { it.key }
        .joinToString { "band ${it.key} ×${it.value}" })
    println("- bands **available**, of ${long.bandsSurveyed.values.sum()} surveyed worlds: " +
        long.bandsSurveyed.entries.joinToString { "band ${it.key}: ${it.value}" })
    println("- gathered: " + long.gatheringChosen.entries
        .joinToString { "${it.key.name.lowercase()} ×${it.value}" })
    println()
}

// Kinds first, count second — the lesson round 8 taught about "median options: 5", applied to the
// newest verb. A dispatch is one verb with many targets, so `gather` is one row and not twenty-seven.
private fun printFleetCensus(outcome: FleetOutcome) {
    println("### The census, with the fleet's two verbs in it")
    println()
    println("Counted at the four-a-day cadence over 48 hours, ${outcome.bookedMinutes.size} check-ins.")
    println("`gather` is **one** verb however many worlds are surveyed, exactly as `survey` is one verb")
    println("however many systems there are. Its refusals are the informative part: a gather refused")
    println("for a **slot** means every hull is away, and a gather is never refused for a price.")
    println()
    println("| Kind | Offered | Price | Slot | Gate |")
    println("|---|---|---|---|---|")
    for ((kind, row) in outcome.censusBarriers) {
        println("| $kind | ${row.getValue(Barrier.OFFERED)} | ${row.getValue(Barrier.PRICE)} " +
            "| ${row.getValue(Barrier.SLOT)} | ${row.getValue(Barrier.GATE)} |")
    }
    println()
}

// ── The sweeps ───────────────────────────────────────────────────────────────────────────────
//
// One row per candidate, in the shape `SurveyBalance.COST_METAL`'s own comment table takes.
//
// **Two instruments, named in the header of the table.** The 48h columns come from the four-a-day
// player, which is the one the mechanic is designed for and the one "building levels at 48h" has
// always been quoted from. The fortnight columns come from the hour-stepped colony, which is the one
// the balance log's blocker numbers are quoted from. Every row differs from every other in exactly
// one constant, and each column is comparable down its own length.
private fun printFleetSweeps() {
    println("### Sweep: `EXTRACTION_PER_HOUR`, at the shipped hull base")
    println()
    printSweepTable(RATE_CANDIDATES.map { FleetTuning(it, FleetBalance.HULL_BASE_METAL) })

    println("### Sweep: the hull base, at the shipped extraction rate")
    println()
    printSweepTable(HULL_BASE_CANDIDATES.map { FleetTuning(FleetBalance.EXTRACTION_PER_HOUR, it) })

    println("### The two dials together, because they do not separate")
    println()
    println("The hull is paid for in metal and paid back in cargo, so lowering the extraction rate")
    println("without lowering the hull price makes the fleet a **bad buy** and the colony carries it.")
    println("That is why the single-dial tables above disagree about `levels @48h`: the rate that")
    println("delivers least costs the most levels. Neither dial can be chosen alone.")
    println()
    val controlShort = fleetRun(days = 2, withFleet = false, tuning = SHIPPED_FLEET)
    val controlLong = fleetRun(days = 14, withFleet = false, tuning = SHIPPED_FLEET, hourlyColony = true)
    println("Control, no fleet: **${controlShort.levels} levels @48h**, crystal short-at-all " +
        "**${controlLong.ledger.shortHours.getValue(Blocker.CRYSTAL)} of 336**, crystal alone " +
        "**${controlLong.ledger.soleBlockerHours.getValue(Blocker.CRYSTAL)}**.")
    println()
    println("Each cell: `levels @48h · hulls @48h · crystal-seeking fleet as a share of colony crystal")
    println("income @48h · crystal short-at-all @14d`. **A difference under ~50 hours in the last one")
    println("is not a signal** — round 12 swept deuterium income by one unit and crystal's sole count")
    println("jumped from 58 to 200.")
    println()
    println("| extraction \\ hull base | " + HULL_BASE_CANDIDATES.joinToString(" | ") { "$it metal" } + " |")
    println("|---" + "|---".repeat(HULL_BASE_CANDIDATES.size) + "|")
    for (extraction in RATE_CANDIDATES) {
        val cells = HULL_BASE_CANDIDATES.joinToString(" | ") { base ->
            val tuning = FleetTuning(extraction, base)
            val short = fleetRun(days = 2, withFleet = true, tuning = tuning)
            val crystal = fleetRun(days = 2, withFleet = true, tuning = tuning, forceGathering = ResourceKind.CRYSTAL)
            val long = fleetRun(days = 14, withFleet = true, tuning = tuning, hourlyColony = true)
            "${short.levels} · ${short.hullsOwned}h · ${crystal.fleetCrystalShare} · " +
                "${long.ledger.shortHours.getValue(Blocker.CRYSTAL)}"
        }
        println("| $extraction | $cells |")
    }
    println()
}

private fun printSweepTable(candidates: List<FleetTuning>) {
    println("48h columns: four-a-day player. Fortnight columns: hour-stepped colony, fleet still four")
    println("a day. `hulls` is the count at the end of each run, so the two differ by design.")
    println()
    println("The two income shares come from a **metal-seeking** and a **crystal-seeking** run of the")
    println("same 48 hours, because the adaptive player never wants crystal in the opening and that")
    println("column would otherwise read 0.0% at every rate. `crystal runs` is the share of the")
    println("fortnight's dispatches the *adaptive* player sent for crystal — the feedback loop that")
    println("makes the blocker column flatter than it looks.")
    println()
    println("| Candidate | levels @48h | hulls @48h | dispatches @48h | duty cycle @48h " +
        "| fleet metal / colony **metal** | fleet crystal / colony **crystal** " +
        "| crystal **short-at-all**, 336h | crystal sole-blocker, 336h | crystal runs " +
        "| hulls @14d | Robotics 4 at |")
    println("|---|---|---|---|---|---|---|---|---|---|---|---|")
    val control = fleetRun(days = 2, withFleet = false, tuning = SHIPPED_FLEET)
    val controlLong = fleetRun(days = 14, withFleet = false, tuning = SHIPPED_FLEET, hourlyColony = true)
    println("| **no fleet — the control** | ${control.levels} | 1 | 0 | — | — | — " +
        "| ${controlLong.ledger.shortHours.getValue(Blocker.CRYSTAL)} " +
        "| ${controlLong.ledger.soleBlockerHours.getValue(Blocker.CRYSTAL)} | — | 1 " +
        "| ${controlLong.roboticsFourAtHour?.let { "hour $it" } ?: "never"} |")
    for (tuning in candidates) {
        val short = fleetRun(days = 2, withFleet = true, tuning = tuning)
        val forMetal = fleetRun(days = 2, withFleet = true, tuning = tuning, forceGathering = ResourceKind.METAL)
        val forCrystal = fleetRun(days = 2, withFleet = true, tuning = tuning, forceGathering = ResourceKind.CRYSTAL)
        val long = fleetRun(days = 14, withFleet = true, tuning = tuning, hourlyColony = true)
        val crystalRuns = long.gatheringChosen[ResourceKind.CRYSTAL] ?: 0
        println("| ${tuning.label} | ${short.levels} | ${short.hullsOwned} | ${short.dispatches} " +
            "| ${short.dutyCycle} | ${forMetal.fleetMetalShare} | ${forCrystal.fleetCrystalShare} " +
            "| ${long.ledger.shortHours.getValue(Blocker.CRYSTAL)} " +
            "| ${long.ledger.soleBlockerHours.getValue(Blocker.CRYSTAL)} " +
            "| ${percent(crystalRuns, long.dispatches)} | ${long.hullsOwned} " +
            "| ${long.roboticsFourAtHour?.let { "hour $it" } ?: "never"} |")
    }
    println()
}

// ── The screen, as a colour ──────────────────────────────────────────────────────────────────
//
// Every reading above counts what a strategy *could buy*. None of them can see the thing a player
// describes first, because a bot that spends everything affordable never notices that five of the
// six rows said no. This is the Colony screen read the way a person reads it at arm's length: one
// row is in progress, one or two are a colour you can tap, the rest are red.
//
// Deliberately taken **before** the check-in spends anything, because that is the moment the player
// is looking at.
// `locked` is counted apart from `red` because the screen draws them apart: the Nanite Factory
// below Robotics 10 renders as `FacilityActionUiState.Locked("Requires Robotics 10")`, dimmed with
// its requirement, and never as a price the colony is short of. Folding it into the red count would
// manufacture one permanently-red row out of a row that is honestly saying "not yet".
private class Rows(val inProgress: Int, val affordable: Int, val red: Int, val locked: Int)

private fun rowsFor(state: GameState): Rows {
    var inProgress = 0
    var affordable = 0
    var red = 0
    var locked = 0
    for (type in BuildingType.entries) {
        val next = BuildingLevel(state.buildings.levelOf(type).value + 1)
        val gated = type == BuildingType.NANITE_FACTORY &&
            state.buildings.roboticsFactory.value < PlaceholderBalance.NANITE_ROBOTICS_REQUIREMENT
        when {
            type in state.builds -> inProgress++
            gated -> locked++
            state.resources.covers(PlaceholderBalance.upgradeCost(type, next)) -> affordable++
            else -> red++
        }
    }
    return Rows(inProgress, affordable, red, locked)
}

// Every three hours from waking to bed — the cadence Davide described himself playing at, twice, in
// his own words: *"apri il gioco ogni 2/3 ore"* (round 8) and *"I have to wait 2/3 hours"* (round
// 11). It is nearly twice the four-a-day rhythm the brief designs for, and the difference is not
// cosmetic: a colony visited twice as often has banked half as much each time, so the same curve
// shows a very different number of affordable rows.
private val FREQUENT_CHECK_IN_HOURS = listOf(0, 3, 6, 9, 12, 15)

// The complaint, as three numbers a curve can be tuned against: how much of the screen is red, how
// often there is only one thing to press, and how long the press makes you wait.
//
// Run at two cadences and with two players, because all three readings move with both and quoting
// one figure would hide which. The player who never buys the Robotics Factory is not a straw man —
// it is the only facility that raises no rate, its cost is in the resource that arrives slowest,
// and nothing on the row says it is the building that halves every wait in the game.
private fun printCheckInPressureReport() {
    println("## How much of the screen is red, and how long a tap costs")
    println()
    println("The Colony screen has six rows. This reads them the way a player does — before the")
    println("check-in spends anything — and then times what the check-in books. Every other report")
    println("in this harness counts what a bot could buy, which is a different question and not the")
    println("one being complained about.")
    println()
    checkInPressure("four a day, buying Robotics when affordable", CHECK_IN_HOURS, buysRobotics = true)
    checkInPressure("every three hours, buying Robotics when affordable", FREQUENT_CHECK_IN_HOURS, buysRobotics = true)
    checkInPressure("every three hours, never buying Robotics", FREQUENT_CHECK_IN_HOURS, buysRobotics = false)
}

private fun checkInPressure(label: String, hours: List<Int>, buysRobotics: Boolean) {
    val days = 2
    val plan = listOfNotNull(
        BuildingType.METAL_MINE,
        BuildingType.CRYSTAL_MINE,
        BuildingType.DEUTERIUM_SYNTHESIZER,
        BuildingType.SOLAR_PLANT,
        BuildingType.ROBOTICS_FACTORY.takeIf { buysRobotics },
    )

    var state = GameState.initial(GalaxySeed(SIM_GALAXY_SEED))
    val genesis = Instant.fromEpochMilliseconds(0)
    var now = genesis
    val redPerCheckIn = mutableListOf<Int>()
    val affordablePerCheckIn = mutableListOf<Int>()
    val boughtPerCheckIn = mutableListOf<Int>()
    val startedMinutes = mutableListOf<Long>()
    val lines = mutableListOf<String>()

    val offsets = (0 until days).flatMap { day -> hours.map { day * 24 + it } }
    for (offset in offsets) {
        val at = genesis + offset.hours
        state = advance(state, from = now, to = at)
        now = at

        // Read the screen first. What the check-in then buys changes every one of these numbers,
        // which is exactly why the reading is taken before it.
        val rows = rowsFor(state)
        redPerCheckIn += rows.red
        affordablePerCheckIn += rows.affordable

        val bought = mutableListOf<String>()
        val booked = mutableListOf<Long>()
        for ((building, cost) in optionsFor(state, plan, withProjects = false).buildings) {
            if (!state.resources.covers(cost)) continue
            (startUpgrade(state, building, at = now) as? StartUpgradeResult.Started)?.let { started ->
                state = started.state
                state.builds[building]?.let { job ->
                    val minutes = (job.completesAt - now).inWholeMinutes
                    bought += "${short(building)} ${job.toLevel.value} (${minutes.asWait()})"
                    booked += minutes
                    startedMinutes += minutes
                }
            }
        }

        boughtPerCheckIn += bought.size
        lines += "| ${clockLabel(offset)} | ${rows.inProgress} | ${rows.affordable} | **${rows.red}** " +
            "| ${rows.locked} | ${bought.joinToString().ifEmpty { "**nothing**" }} " +
            "| ${booked.maxOrNull()?.asWait() ?: "—"} |"
    }

    println("### $label")
    println()
    println("| Check-in | Building | Tappable | Red | Locked | Bought | Longest wait it booked |")
    println("|---|---|---|---|---|---|---|")
    lines.forEach(::println)
    println()

    val robotics = state.buildings.roboticsFactory.value
    val levels = BuildingType.entries.sumOf { state.buildings.levelOf(it).value }
    println("| Reading | Value |")
    println("|---|---|")
    println("| Median red rows, of six | **${redPerCheckIn.median()} of 6** |")
    println("| Worst check-in | ${redPerCheckIn.max()} of 6 red |")
    println("| Median tappable rows | **${affordablePerCheckIn.median()}** |")
    // Tappable counts each row against the whole stock; bought counts what the stock actually
    // stretched to. The gap between them is most of "most of the thing are red" — four rows can
    // each be affordable on their own and still leave the session buying one.
    println("| Median rows the stock actually stretched to | **${boughtPerCheckIn.median()}** |")
    println("| Check-ins offering one row or none | " +
        "**${affordablePerCheckIn.count { it <= 1 }} of ${affordablePerCheckIn.size}** |")
    println("| Median wait a tap booked | **${startedMinutes.median().asWait()}** |")
    println("| Longest wait a tap booked | **${startedMinutes.max().asWait()}** |")
    println("| Taps that booked over two hours | " +
        "${startedMinutes.count { it > 120 }} of ${startedMinutes.size} |")
    println("| Building levels at 48h | $levels (robotics $robotics) |")
    println()
}

// ── The interaction census ───────────────────────────────────────────────────────────────────
//
// Davide's idea, 2026-08-09: *"count the possible interactions in the benchmarks, to make sure
// users have things to do."* The reports above count what one **strategy** wanted to buy; this one
// enumerates every call `core` would accept, whether or not anybody wants to make it, and says why
// it would refuse the rest.
//
// **The trap this is built around.** Round 8's harness printed "median options on the table: 5" for
// the exact opening Davide called boring, because five facility rows counted as five options when
// they were one verb pressed five times. A raw count is therefore not a safety net — it is a number
// that goes up when you add rows. So the census reports **kinds** first and the count second, and
// it counts a probe as *one* verb with a thousand targets rather than as a thousand actions, which
// is the same lesson applied to the newest verb rather than the oldest.
//
// **The reading that is actually new is the barrier.** An action the game refuses is refused for
// one of three reasons, and each has a different fix: the stock is short (a curve), a slot is
// occupied (a rule), or a requirement is unmet (a gate). "Nothing to do" is the same sentence in
// all three cases and three different bugs, and no report before this one could tell them apart.
private enum class Barrier { OFFERED, PRICE, SLOT, GATE }

private class Census {
    // kind -> barrier -> how many subjects sit there
    val byKind: MutableMap<String, MutableMap<Barrier, Int>> = linkedMapOf()

    fun add(kind: String, barrier: Barrier) {
        val row = byKind.getOrPut(kind) { Barrier.entries.associateWith { 0 }.toMutableMap() }
        row[barrier] = row.getValue(barrier) + 1
    }

    fun count(barrier: Barrier): Int = byKind.values.sumOf { it.getValue(barrier) }
    fun kindsOffered(): List<String> = byKind.filterValues { it.getValue(Barrier.OFFERED) > 0 }.keys.toList()
}

// `fleet` is null for every report that predates the fleet, and that is not tidiness: adding two rows
// unconditionally would move the barrier percentages of a census whose numbers are already quoted in
// the balance log, so the old reports would appear to change when nothing about them had.
private fun censusOf(state: GameState, fleet: FleetTuning?): Census {
    val census = Census()

    for (type in BuildingType.entries) {
        val next = BuildingLevel(type.let { state.buildings.levelOf(it).value } + 1)
        val gated = type == BuildingType.NANITE_FACTORY &&
            state.buildings.roboticsFactory.value < PlaceholderBalance.NANITE_ROBOTICS_REQUIREMENT
        census.add("build", when {
            gated -> Barrier.GATE
            // One job per facility. A row already building is not a thing to do, and it is refused
            // by a rule rather than by poverty — which is why it is not counted as a price.
            type in state.builds -> Barrier.SLOT
            state.resources.covers(PlaceholderBalance.upgradeCost(type, next)) -> Barrier.OFFERED
            else -> Barrier.PRICE
        })
    }

    // **A slot each since 0.12.1, so the ceiling this reports is two rather than one.** The old note
    // here said at most one of the six could ever be OFFERED at once however many were affordable,
    // and called that the single most important thing the census said about the mid game. It is now
    // one applied row and one ladder — a smaller claim, and still one no count of rows would show.
    val projectBusy = state.activeResearch != null
    val ladderBusy = state.activeAdaptation != null
    for (technology in Technology.entries) {
        val cost = ResearchBalance.researchCost(technology, TechLevel(state.research.levelOf(technology).value + 1))
        census.add("research", when {
            !ResearchBalance.requirementFor(technology).isMetBy(state) -> Barrier.GATE
            projectBusy -> Barrier.SLOT
            state.resources.covers(cost) -> Barrier.OFFERED
            else -> Barrier.PRICE
        })
    }
    for (ladder in AdaptationTechnology.entries) {
        val cost = AdaptationBalance.adaptationCost(ladder, TechLevel(state.research.levelOf(ladder).value + 1))
        census.add("adapt", when {
            !AdaptationBalance.requirementFor(ladder).isMetBy(state) -> Barrier.GATE
            ladderBusy -> Barrier.SLOT
            state.resources.covers(cost) -> Barrier.OFFERED
            else -> Barrier.PRICE
        })
    }

    // **One verb, not a thousand.** There are ~1,000 dispatchable systems and every one of them is
    // the same decision with a different number on it. Counting them as a thousand actions would
    // drown every other row in this table and would make "add more systems" read as "add more to
    // do", which is precisely the mistake round 8 caught in the old options column.
    census.add("survey", if (state.resources.covers(SurveyBalance.cost())) Barrier.OFFERED else Barrier.PRICE)

    // **One verb, not twenty-seven**, which is the same lesson applied to the newest verb rather than
    // the oldest: a dispatch is one decision with a different number on each world, so counting the
    // surveyed set would make "buy another probe" read as "add more to do".
    //
    // A gather is never refused for a **price** — the hull is the cost and it was paid once — so the
    // barrier it can carry is a **slot**, meaning every hull is away. That is the single most
    // informative thing this census says about the mechanic.
    if (fleet != null) {
        val reachable = state.galaxy.surveyed.any { at ->
            at != state.galaxy.home &&
                state.galaxy.holderOf(at) == null &&
                FleetBalance.windowsFor(state.galaxy.home, at).isNotEmpty()
        }
        census.add("gather", when {
            !reachable -> Barrier.GATE
            state.ships.countOf(ShipType.SKIFF) == 0 -> Barrier.SLOT
            else -> Barrier.OFFERED
        })
        val hull = hullCostAt(fleet)
        census.add("hulls", if (state.resources.covers(hull)) Barrier.OFFERED else Barrier.PRICE)
    }
    return census
}

private fun printInteractionCensus() {
    println("## The interaction census")
    println()
    println("Every call `core` would accept at each check-in, not just the ones a strategy wanted —")
    println("and for the ones it would refuse, *why*. Counted at the three-hour cadence, with a probe")
    println("dispatched into the gap ahead. A probe counts as **one** verb rather than as the ~1,000")
    println("systems it could be aimed at: they are the same decision with a different number on it,")
    println("and counting targets would make \"add more systems\" read as \"add more to do\".")
    println()
    interactionCensus(days = 2, showTable = true)
    // The same census over a week, because a gate that is shut for two days and open on the third
    // is a very different complaint from one that is shut for a fortnight, and the two-day figure
    // alone cannot tell them apart.
    interactionCensus(days = 7, showTable = false)
}

private fun interactionCensus(days: Int, showTable: Boolean) {
    val plan = listOf(
        BuildingType.METAL_MINE,
        BuildingType.CRYSTAL_MINE,
        BuildingType.DEUTERIUM_SYNTHESIZER,
        BuildingType.SOLAR_PLANT,
        BuildingType.ROBOTICS_FACTORY,
    )

    var state = GameState.initial(GalaxySeed(SIM_GALAXY_SEED))
    val genesis = Instant.fromEpochMilliseconds(0)
    var now = genesis
    val offered = mutableListOf<Int>()
    val kinds = mutableListOf<Int>()
    val taken = mutableListOf<Int>()
    val barriers = Barrier.entries.associateWith { 0 }.toMutableMap()
    val lines = mutableListOf<String>()

    val offsets = (0 until days).flatMap { day -> FREQUENT_CHECK_IN_HOURS.map { day * 24 + it } }
    for ((index, offset) in offsets.withIndex()) {
        val at = genesis + offset.hours
        state = advance(state, from = now, to = at)
        now = at

        val census = censusOf(state, fleet = null)
        offered += census.count(Barrier.OFFERED)
        kinds += census.kindsOffered().size
        for (barrier in Barrier.entries) barriers[barrier] = barriers.getValue(barrier) + census.count(barrier)

        // What the stock actually stretched to, which is the number the player experiences. Every
        // action above is priced against the *whole* stock on its own; buying one changes what the
        // rest cost against.
        var acted = 0
        val gapMinutes = ((offsets.getOrNull(index + 1) ?: days * 24) - offset) * 60L
        probeTargetFor(state, gapMinutes)?.let { target ->
            (startSurvey(state, target, at = now) as? StartSurveyResult.Started)?.let { state = it.state; acted++ }
        }
        for ((building, cost) in optionsFor(state, plan, withProjects = true).buildings) {
            if (!state.resources.covers(cost)) continue
            (startUpgrade(state, building, at = now) as? StartUpgradeResult.Started)?.let { state = it.state; acted++ }
        }
        for ((project, cost) in optionsFor(state, plan, withProjects = true).projects) {
            if (state.slotBusyFor(project) || !state.resources.covers(cost)) continue
            when (project) {
                is Technology -> (startResearch(state, project, at = now) as? StartResearchResult.Started)
                    ?.let { state = it.state; acted++ }
                is AdaptationTechnology -> (startAdaptation(state, project, at = now) as? StartAdaptationResult.Started)
                    ?.let { state = it.state; acted++ }
            }
        }
        taken += acted

        val cells = listOf("build", "research", "adapt", "survey").joinToString(" | ") { kind ->
            val row = census.byKind[kind] ?: return@joinToString "—"
            val marks = Barrier.entries.filter { it != Barrier.OFFERED && row.getValue(it) > 0 }
                .joinToString("") { barrier ->
                    "${row.getValue(barrier)}${barrier.name.first().lowercase()}"
                }
            "${row.getValue(Barrier.OFFERED)}${if (marks.isEmpty()) "" else " ($marks)"}"
        }
        lines += "| ${clockLabel(offset)} | $cells | **${census.count(Barrier.OFFERED)}** " +
            "| ${census.kindsOffered().joinToString("+").ifEmpty { "—" }} | $acted |"
    }

    if (showTable) {
        println("Cells are `offered (Np = short of the price, Ns = a slot is busy, Ng = requirement unmet)`.")
        println()
        println("| Check-in | build | research | adapt | survey | Offered | Kinds | Taken |")
        println("|---|---|---|---|---|---|---|---|")
        lines.forEach(::println)
        println()
    }

    val total = barriers.values.sum()
    println("### Over $days days, ${offered.size} check-ins")
    println()
    println("| Reading | Value |")
    println("|---|---|")
    println("| Median actions offered | **${offered.median()}** |")
    println("| Median *kinds* offered | **${kinds.median()}** |")
    println("| Check-ins offering one kind only | **${kinds.count { it <= 1 }} of ${kinds.size}** |")
    println("| Check-ins offering nothing at all | ${offered.count { it == 0 }} of ${offered.size} |")
    println("| Median actions the stock stretched to | **${taken.median()}** |")
    println("| Refused for the price | ${percent(barriers.getValue(Barrier.PRICE), total)} of all actions |")
    println("| Refused by a busy slot | ${percent(barriers.getValue(Barrier.SLOT), total)} |")
    println("| Refused by an unmet requirement | ${percent(barriers.getValue(Barrier.GATE), total)} |")
    println()
    if (showTable) {
        println("**Read the last three together.** \"Nothing to do\" is one sentence and three different")
        println("bugs: a price is a curve, a slot is a rule, a requirement is a gate — and only the first")
        println("of those is fixed by tuning a number.")
        println()
    }
}

// ── The gate clock ───────────────────────────────────────────────────────────────────────────
//
// The census says 47% of the opening's actions are refused by an unmet requirement against 5% by
// price. This is the follow-up question: *when do those requirements clear, and what is actually
// holding them?*
//
// The answer is one building and one resource. Every gate in the game below Nanite is a Robotics
// Factory level — 1 opens the Research tab, 4 opens all three adaptation ladders, 10 opens Nanite —
// and the Robotics Factory is the only repeating row priced in deuterium, which round 7 nominated
// as the worst blocker in the game and rounds 8 through 11 all left alone. So the second and third
// verbs of a five-verb game are behind a single resource, and this table is how far behind.
private fun printGateClock() {
    val days = 7
    val plan = listOf(
        BuildingType.METAL_MINE,
        BuildingType.CRYSTAL_MINE,
        BuildingType.DEUTERIUM_SYNTHESIZER,
        BuildingType.SOLAR_PLANT,
        BuildingType.ROBOTICS_FACTORY,
    )

    var state = GameState.initial(GalaxySeed(SIM_GALAXY_SEED))
    val genesis = Instant.fromEpochMilliseconds(0)
    var now = genesis
    val firstMet = mutableMapOf<String, Int>()
    val firstOffered = mutableMapOf<String, Int>()
    val roboticsReached = mutableMapOf<Int, Int>()
    // What the Robotics Factory was short of, each check-in it was not bought — the question
    // "is it the deuterium?" asked of every visit rather than of the closing snapshot.
    val roboticsShort = Blocker.entries.associateWith { 0 }.toMutableMap()
    var roboticsBlockedCheckIns = 0

    val offsets = (0 until days).flatMap { day -> FREQUENT_CHECK_IN_HOURS.map { day * 24 + it } }
    for ((index, offset) in offsets.withIndex()) {
        val at = genesis + offset.hours
        state = advance(state, from = now, to = at)
        now = at

        for (level in 1..PlaceholderBalance.NANITE_ROBOTICS_REQUIREMENT) {
            if (state.buildings.roboticsFactory.value >= level) roboticsReached.putIfAbsent(level, offset)
        }
        for (technology in Technology.entries) {
            val met = ResearchBalance.requirementFor(technology).isMetBy(state)
            val cost = ResearchBalance.researchCost(technology, TechLevel(state.research.levelOf(technology).value + 1))
            if (met) firstMet.putIfAbsent("$technology", offset)
            if (met && state.resources.covers(cost)) firstOffered.putIfAbsent("$technology", offset)
        }
        for (ladder in AdaptationTechnology.entries) {
            val met = AdaptationBalance.requirementFor(ladder).isMetBy(state)
            val cost = AdaptationBalance.adaptationCost(ladder, TechLevel(state.research.levelOf(ladder).value + 1))
            if (met) firstMet.putIfAbsent("$ladder", offset)
            if (met && state.resources.covers(cost)) firstOffered.putIfAbsent("$ladder", offset)
        }

        val roboticsCost = PlaceholderBalance
            .upgradeCost(BuildingType.ROBOTICS_FACTORY, BuildingLevel(state.buildings.roboticsFactory.value + 1))
        val short = shortagesOf(roboticsCost, state.resources)
        if (short.isNotEmpty() && BuildingType.ROBOTICS_FACTORY !in state.builds) {
            roboticsBlockedCheckIns++
            for (blocker in short) roboticsShort[blocker] = roboticsShort.getValue(blocker) + 1
        }

        val gapMinutes = ((offsets.getOrNull(index + 1) ?: days * 24) - offset) * 60L
        probeTargetFor(state, gapMinutes)?.let { target ->
            (startSurvey(state, target, at = now) as? StartSurveyResult.Started)?.let { state = it.state }
        }
        for ((building, cost) in optionsFor(state, plan, withProjects = true).buildings) {
            if (!state.resources.covers(cost)) continue
            (startUpgrade(state, building, at = now) as? StartUpgradeResult.Started)?.let { state = it.state }
        }
        for ((project, cost) in optionsFor(state, plan, withProjects = true).projects) {
            if (state.slotBusyFor(project) || !state.resources.covers(cost)) continue
            when (project) {
                is Technology -> (startResearch(state, project, at = now) as? StartResearchResult.Started)
                    ?.let { state = it.state }
                is AdaptationTechnology -> (startAdaptation(state, project, at = now) as? StartAdaptationResult.Started)
                    ?.let { state = it.state }
            }
        }
    }

    fun hour(value: Int?): String = value?.let { "hour $it (d${it / 24 + 1})" } ?: "**never in ${days}d**"

    println("## The gate clock")
    println()
    println("When each gate opened, at the three-hour cadence over $days days. \"Met\" is the")
    println("requirement clearing; \"offered\" is the first check-in where it was also affordable.")
    println()
    println("| Robotics Factory level | Reached | Opens |")
    println("|---|---|---|")
    // The gate's own level is in the list and labelled from `AdaptationBalance.GATE`, so this
    // table cannot drift from the game the way it did between 0.5.1 and the round that caught it.
    val gate = AdaptationBalance.GATE.value
    for (level in (listOf(1, gate, 4, 5, PlaceholderBalance.NANITE_ROBOTICS_REQUIREMENT)).distinct().sorted()) {
        val opens = when (level) {
            1 -> "Photovoltaics, Extraction — the Research tab"
            gate -> "all three adaptation ladders — every Blocked world"
            PlaceholderBalance.NANITE_ROBOTICS_REQUIREMENT -> "the Nanite Factory"
            else -> "—"
        }
        println("| $level | ${hour(roboticsReached[level])} | $opens |")
    }
    println()
    println("| Subject | Requirement met | First affordable |")
    println("|---|---|---|")
    for (technology in Technology.entries) {
        println("| $technology | ${hour(firstMet["$technology"])} | ${hour(firstOffered["$technology"])} |")
    }
    for (ladder in AdaptationTechnology.entries) {
        println("| $ladder | ${hour(firstMet["$ladder"])} | ${hour(firstOffered["$ladder"])} |")
    }
    println()
    println("The Robotics Factory was unaffordable at **$roboticsBlockedCheckIns** of " +
        "${offsets.size} check-ins. What it was short of, counted per check-in (a visit can be " +
        "short of more than one):")
    println()
    for (blocker in Blocker.entries) {
        println("- ${blocker.name.lowercase().padEnd(9)} ${roboticsShort.getValue(blocker)}")
    }
    println()
}

// ── How far a colony gets, day by day ────────────────────────────────────────────────────────
//
// Davide named the window himself — *"the first 2/3/4 days"* — and no report had it. The opening
// report stops at 48 hours, the greedy week only prints its closing line, and "levels at 48h" alone
// cannot say whether a change made day one faster or merely moved day two's purchases into it.
private fun printProgressionMilestones() {
    val days = 7
    val plan = listOf(
        BuildingType.METAL_MINE,
        BuildingType.CRYSTAL_MINE,
        BuildingType.DEUTERIUM_SYNTHESIZER,
        BuildingType.SOLAR_PLANT,
        BuildingType.ROBOTICS_FACTORY,
    )

    var state = GameState.initial(GalaxySeed(SIM_GALAXY_SEED))
    val genesis = Instant.fromEpochMilliseconds(0)
    var now = genesis
    val marks = listOf(24, 48, 72, 96, 168)
    val rows = mutableListOf<String>()

    val offsets = (0 until days).flatMap { day -> FREQUENT_CHECK_IN_HOURS.map { day * 24 + it } }
    for ((index, offset) in offsets.withIndex()) {
        val at = genesis + offset.hours
        state = advance(state, from = now, to = at)
        now = at

        val gapMinutes = ((offsets.getOrNull(index + 1) ?: days * 24) - offset) * 60L
        probeTargetFor(state, gapMinutes)?.let { target ->
            (startSurvey(state, target, at = now) as? StartSurveyResult.Started)?.let { state = it.state }
        }
        for ((building, cost) in optionsFor(state, plan, withProjects = true).buildings) {
            if (!state.resources.covers(cost)) continue
            (startUpgrade(state, building, at = now) as? StartUpgradeResult.Started)?.let { state = it.state }
        }
        for ((project, cost) in optionsFor(state, plan, withProjects = true).projects) {
            if (state.slotBusyFor(project) || !state.resources.covers(cost)) continue
            when (project) {
                is Technology -> (startResearch(state, project, at = now) as? StartResearchResult.Started)
                    ?.let { state = it.state }
                is AdaptationTechnology -> (startAdaptation(state, project, at = now) as? StartAdaptationResult.Started)
                    ?.let { state = it.state }
            }
        }

        val nextOffset = offsets.getOrNull(index + 1) ?: Int.MAX_VALUE
        for (mark in marks) {
            if (offset < mark && nextOffset >= mark) {
                val levels = BuildingType.entries.sumOf { state.buildings.levelOf(it).value }
                val projects = (Technology.entries.sumOf { state.research.levelOf(it).value } +
                    AdaptationTechnology.entries.sumOf { state.research.levelOf(it).value })
                rows += "| day ${mark / 24} | **$levels** | ${state.buildings.summary()} | $projects |"
            }
        }
    }

    println("## How far a colony gets, day by day")
    println()
    println("Three-hour cadence, everything affordable cheapest-first, one probe into each gap —")
    println("the same player as the census. Levels are the sum over all six facilities.")
    println()
    println("| At | Building levels | Facilities | Projects finished |")
    println("|---|---|---|---|")
    rows.forEach(::println)
    println()
}

private fun Long.asWait(): String = "${this / 60}h ${(this % 60).toString().padStart(2, '0')}m"

// The middle value, which is the honest summary of a lumpy list — a mean over eight check-ins is
// dragged around by whichever one happened to catch a completion.
private fun List<Long>.median(): Long = if (isEmpty()) 0 else sorted()[size / 2]

@JvmName("medianOfInts")
private fun List<Int>.median(): Int = if (isEmpty()) 0 else sorted()[size / 2]

private fun nameOf(what: Any, state: GameState): String = when (what) {
    is BuildingType -> "${short(what)} ${state.buildings.levelOf(what).value + 1}"
    else -> "$what"
}

private fun short(building: BuildingType): String = when (building) {
    BuildingType.METAL_MINE -> "Metal"
    BuildingType.CRYSTAL_MINE -> "Crystal"
    BuildingType.DEUTERIUM_SYNTHESIZER -> "Deut"
    BuildingType.SOLAR_PLANT -> "Solar"
    BuildingType.ROBOTICS_FACTORY -> "Robotics"
    BuildingType.NANITE_FACTORY -> "Nanite"
}

// Read off the records rather than off the event log, so this says what the player would *see* on
// the facility rows — a level that went up — rather than what was appended.
private fun finishedBetween(before: GameState, after: GameState): List<String> {
    val buildings = BuildingType.entries
        .filter { after.buildings.levelOf(it).value > before.buildings.levelOf(it).value }
        .map { "${short(it)} ${after.buildings.levelOf(it).value}" }
    val applied = Technology.entries
        .filter { after.research.levelOf(it).value > before.research.levelOf(it).value }
        .map { "$it ${after.research.levelOf(it).value}" }
    val ladders = AdaptationTechnology.entries
        .filter { after.research.levelOf(it).value > before.research.levelOf(it).value }
        .map { "$it ${after.research.levelOf(it).value}" }
    return buildings + applied + ladders
}

private fun Buildings.summary(): String = BuildingType.entries
    .joinToString(" · ") { "${short(it).lowercase()} ${levelOf(it).value}" }

private fun Research.appliedSummary(): String = Technology.entries
    .joinToString(", ") { "${it.name.lowercase()} ${levelOf(it).value}" }

// Total minutes inside the window covered by no job at all. Intervals are merged rather than
// summed, because parallel builds overlap and a naive sum would report a busier colony than there
// ever was.
private fun idleMinutes(busy: List<Pair<Instant, Instant>>, from: Instant, to: Instant): Long {
    var covered = 0L
    var cursor = from
    for ((start, end) in busy.sortedBy { it.first }) {
        val effectiveStart = maxOf(start, cursor)
        if (end <= effectiveStart) continue
        val cappedEnd = minOf(end, to)
        if (cappedEnd <= effectiveStart) continue
        covered += (cappedEnd - effectiveStart).inWholeMinutes
        cursor = cappedEnd
    }
    return (to - from).inWholeMinutes - covered
}

// The worst of it, rather than the average: two hours idle four times over reads very differently
// from eight hours in one stretch, and it is the single stretch that decides whether the player
// has a reason to open the app.
private fun longestIdleRun(busy: List<Pair<Instant, Instant>>, from: Instant, to: Instant): Long {
    var longest = 0L
    var cursor = from
    for ((start, end) in busy.sortedBy { it.first }) {
        if (start > cursor) longest = maxOf(longest, (start - cursor).inWholeMinutes)
        if (end > cursor) cursor = minOf(end, to)
    }
    return maxOf(longest, (to - cursor).inWholeMinutes)
}

// Day and wall-clock time from an offset in hours, so a row reads like a session rather than
// like an index.
private fun clockLabel(offsetHours: Int): String {
    val hour = (8 + offsetHours) % 24
    val day = 1 + (8 + offsetHours) / 24
    return "d$day ${hour.toString().padStart(2, '0')}:00"
}

// The 0.0.12 baseline, unchanged in what it buys so its closing line stays comparable with every
// round of the balance log: upgrade anything affordable once an hour, cheapest first, mines *and*
// plant. What is new is the ledger underneath it.
private fun printGreedyWeek() {
    println("## A greedy week, mines and plant only")
    println()
    val plan = listOf(BuildingType.METAL_MINE, BuildingType.CRYSTAL_MINE, BuildingType.SOLAR_PLANT)
    val (state, ledger) = run(days = 7, plan = plan, withProjects = false)
    report("greedy week (parallel builds)", 7, state, ledger, plan, withProjects = false)
}

// The same greedy rule let loose on everything the game actually sells — the Robotics Factory and
// the Deuterium Synthesizer as well as the mines, and the shared research slot kept busy with
// whichever project of either branch is cheapest.
//
// This is the run the crystal question needed. The week above buys only mines and plant, which are
// the two most metal-heavy things in the game; it cannot see the demand that applied research and
// the adaptation ladders put on crystal, because it never buys either. Fourteen days rather than
// seven because the research branch does not open until the first Robotics Factory, which is
// itself gated behind deuterium.
private fun printWholeTreeRun() {
    println("## A fortnight buying everything the game sells")
    println()
    val plan = listOf(
        BuildingType.METAL_MINE,
        BuildingType.CRYSTAL_MINE,
        BuildingType.DEUTERIUM_SYNTHESIZER,
        BuildingType.SOLAR_PLANT,
        BuildingType.ROBOTICS_FACTORY,
    )
    val (state, ledger) = run(days = 14, plan = plan, withProjects = true)
    report("whole tree (parallel builds, one research slot)", 14, state, ledger, plan, withProjects = true)
}

// Mean of one richness across a set of worlds, in the same 1.00 units the yield table reads in.
private fun meanRichness(worlds: List<World>, of: (WorldTraits) -> Int): String {
    if (worlds.isEmpty()) return "—"
    val mean = worlds.sumOf { of(it.traits).toLong() } / worlds.size
    return yieldLabel(mean.toInt())
}

// ── The depletion sweep, which `.claude/docs/deposit-sheet.md` §9 makes a merge condition ────────
//
// Davide took the harshest cell of the grid and asked for a brake — *"please don't allow me to screw
// up"*. This is that brake, and it has already caught one thing: his first cap of 1,000 is below a
// single skiff's day, so the deposit binds on essentially every dispatch, and when the deposit binds
// **nothing else does** — the window ladder and the hull stepper stop changing the answer. That is
// what the `clamped` column is for, and no other reading in this file would have shown it.
//
// **The vein is modelled here rather than read off `GameState`**, for the reason the rate sweep
// carries its own `cargoAt`: `DepositBalance.BASE_PRICED` is a `const val`, so a swept row cannot ask
// `core` what a world holds. The replica is checked against `core` on every call at the shipped
// values, and the bot's own deposits are the authority — `state.galaxy.deposits` is cleared each
// check-in so the two cannot both debit the same world.
private class DepositTuning(val basePriced: Long, val refillPercent: Long) {

    val isShipped: Boolean
        get() = basePriced == DepositBalance.BASE_PRICED && refillPercent == DepositBalance.REFILL_PERCENT_PER_DAY

    val label: String get() = "$basePriced · ${refillPercent}%/day"
}

// **The ladder is Davide's multiple rather than a geometric series**, and it moved once. Round 24
// swept {1,000 … 2,500} around a cap derived for *one ship*; issue #68 re-derived the rule against a
// *fleet* — *"a typical fleet takes about two runs"* — which puts the answer at 4–6× of 1,450. So the
// grid runs the incumbent and then 2× through 6×, and the two rows below 1,450 are gone: round 24
// measured 1,000 and rejected it, and re-printing a settled rejection every run is a row nobody reads.
// The floor it established still binds and is stated in the sheet's 2.5.
private val DEPOSIT_CANDIDATES: List<Long> = listOf(1_450L, 2_900L, 4_350L, 5_800L, 7_250L, 8_700L).also {
    check(DepositBalance.BASE_PRICED in it) {
        "the sweep must contain the shipped cap ${DepositBalance.BASE_PRICED}"
    }
}

// `DepositBalance.cap` with the base lifted out, checked against `core` at the shipped base.
private fun capAt(tuning: DepositTuning, world: World, gathering: ResourceKind, danger: Int): Long {
    val richness = when (gathering) {
        ResourceKind.METAL -> world.traits.metalRichness
        ResourceKind.CRYSTAL -> world.traits.crystalRichness
        ResourceKind.DEUTERIUM -> error("a world holds no deuterium deposit")
    }
    val price = if (gathering == ResourceKind.METAL) 1L else 2L
    val cap = tuning.basePriced * richness.perMillion.toLong() * (100L + 35L * danger) /
        (GalaxyBalance.RICHNESS_BASIS.toLong() * 100L * price)
    if (tuning.isShipped) {
        check(cap == DepositBalance.cap(world, gathering, danger)) {
            "the harness's cap replica disagrees with DepositBalance.cap: $cap"
        }
    }
    return cap
}

// One world's two veins, in whole units, refilled on read. Whole units rather than `core`'s fine ones
// because nothing here needs sub-unit precision over fourteen days and a harness that carried a
// second fine-unit convention would be a second place for it to drift.
private class Vein(val cap: Long, var remaining: Long, var asOfHour: Int)

private class Veins(private val tuning: DepositTuning, private val state: GameState) {

    private val worlds = linkedMapOf<Pair<GalaxyCoordinate, ResourceKind>, Vein>()

    val touched: Int get() = worlds.count { it.value.remaining < it.value.cap }

    fun remaining(at: GalaxyCoordinate, gathering: ResourceKind, hour: Int, home: GalaxyCoordinate): Long {
        val world = worldAt(state.galaxy.seed, at) ?: return 0
        val danger = FleetBalance.danger(home, world)
        val vein = worlds.getOrPut(at to gathering) {
            val cap = capAt(tuning, world, gathering, danger)
            Vein(cap = cap, remaining = cap, asOfHour = hour)
        }
        val perDay = vein.cap * tuning.refillPercent / 100
        val hours = (hour - vein.asOfHour).coerceAtLeast(0)
        vein.remaining = minOf(vein.cap, vein.remaining + perDay * hours / 24)
        vein.asOfHour = hour
        return vein.remaining
    }

    fun take(at: GalaxyCoordinate, gathering: ResourceKind, amount: Long) {
        worlds.getValue(at to gathering).remaining -= amount
    }
}

private class DepositDay(var metal: Long = 0, var crystal: Long = 0, var dispatches: Int = 0, var clamped: Int = 0)

// **The reading issue #68 decides on, and it is a count of worlds rather than an income.** Davide's
// complaint was *"I'm so much out of planets to gather resources from"*, which no income row can
// answer: a fleet that strips six worlds in an hour and then idles for twenty-three earns a
// respectable daily figure and still leaves the player with nothing to tap. What says it is the
// standing count — how many of the worlds within the player's reach still hold a hull's worth.
//
// **`worthIt` is one hull's lift at the player's own rung**, which is the smallest unit of dispatch
// the spread strategy ever sends, and it needs no new constant: a world holding less than that sends
// a hull out for a whole window to come home part-empty. `reachable` is the denominator — every
// surveyed world the rung can actually get to — so the pair reads as *how much of my map is still
// worth flying to*, and `hulls` beside it is what the answer has to feed.
//
// **`hullsFed` is there because `worthIt` saturates and the complaint does not.** Reach bounds the
// count at six worlds on this rung, so every cap deep enough to keep the doorstep standing reads an
// identical 6 — and a column that stops moving stops deciding. What still moves is how many hulls
// those six can fill: `floor(remaining / one hull's lift)`, summed. Read the two together as *how
// many worlds are worth a visit* and *how much of my fleet can go on it*.
private class Standing(
    val hulls: Int,
    val reachable: Int,
    val worthIt: Int,
    val hullsFed: Int,
    val pricedStanding: Long,
)

private class DepositOutcome(
    val days: List<DepositDay>,
    val entriesHeld: Int,
    val systemsSurveyed: Int,
    val targetsReached: Int,
    val colonyMetalPerDay: Long,
    val colonyCrystalPerDay: Long,
    val standing: Standing?,
)

// One player, fourteen days, spreading one hull per world — which is the strategy the sheet's §4.1
// measures as absence-neutral, and therefore the one a veto has to be read against. Concentrating is
// printed separately because it is the trap rather than the play.
private fun depositRun(
    tuning: DepositTuning,
    days: Int = 14,
    checkInHours: List<Int> = CHECK_IN_HOURS,
    spread: Boolean = true,
    // Null is the adaptive rule — crystal while the colony is short of it, metal otherwise — which is
    // what a player does and what every row above uses. Forcing one is the only way to get a crystal
    // reading at all: at four check-ins a day this colony is rarely crystal-short, so the adaptive bot
    // gathers metal for almost the whole fortnight. `printFleetReport` needed the same escape hatch
    // for the same reason and for the same currency.
    forceGathering: ResourceKind? = null,
    // The hour to photograph the map at, or null for none. Taken at that hour's check-in **after the
    // hulls are bought and before this visit's dispatches**, which is the instant the player is
    // actually looking at the screen and asking where to send them.
    standingAtHour: Int? = null,
    // **The complainant's purchase order.** The default here is *hulls from what is left*, which is
    // what every fortnight row below has always measured; but the player who reported being out of
    // planets is by construction the one who buys hulls, and round 25's bracket says which rule that
    // player follows — **one hull before the buildings, at most one a check-in**. `fleetRun` states
    // the reasoning: buying first is the pessimistic ordering, so what the fleet costs the colony
    // shows up in the level count rather than hiding behind a full queue.
    hullsFirst: Boolean = false,
): DepositOutcome {
    var state = GameState.initial(GalaxySeed(SIM_GALAXY_SEED))
    val genesis = Instant.fromEpochMilliseconds(0)
    var now = genesis
    val fleet = SHIPPED_FLEET
    val veins = Veins(tuning, state)
    val ledger = List(days) { DepositDay() }
    val offsets = (0 until days).flatMap { day -> checkInHours.map { day * 24 + it } }
    val checkIns = offsets.toSet()
    var shortOfCrystal = false
    val targets = mutableSetOf<GalaxyCoordinate>()
    var standing: Standing? = null

    for (hour in 0 until days * 24) {
        val at = genesis + hour.hours
        state = advance(state, from = now, to = at)
        now = at
        // The harness owns the vein; `core`'s copy is emptied so the two cannot both debit a world.
        state = state.copy(galaxy = state.galaxy.copy(deposits = emptyList()))

        if (hour !in checkIns) continue
        val gapMinutes = ((offsets.firstOrNull { it > hour } ?: (days * 24)) - hour) * 60L
        val visible = optionsFor(state, FULL_PLAN, withProjects = true)
        shortOfCrystal = (visible.buildings + visible.projects)
            .any { Blocker.CRYSTAL in shortagesOf(it.second, state.resources) }

        probeTargetFor(state, gapMinutes)?.let { target ->
            (startSurvey(state, target, at = now) as? StartSurveyResult.Started)?.let { state = it.state }
        }
        if (hullsFirst) state = buyOneHull(state, fleet, at = now) ?: state
        for ((building, cost) in optionsFor(state, FULL_PLAN, withProjects = true).buildings) {
            if (!state.resources.covers(cost)) continue
            (startUpgrade(state, building, at = now) as? StartUpgradeResult.Started)?.let { state = it.state }
        }
        for ((project, cost) in optionsFor(state, FULL_PLAN, withProjects = true).projects) {
            if (state.slotBusyFor(project) || !state.resources.covers(cost)) continue
            when (project) {
                is Technology -> (startResearch(state, project, at = now) as? StartResearchResult.Started)
                    ?.let { state = it.state }
                is AdaptationTechnology ->
                    (startAdaptation(state, project, at = now) as? StartAdaptationResult.Started)
                        ?.let { state = it.state }
            }
        }
        if (!hullsFirst) {
            while (true) {
                state = buyOneHull(state, fleet, at = now) ?: break
            }
        }

        val gathering = forceGathering ?: if (shortOfCrystal) ResourceKind.CRYSTAL else ResourceKind.METAL
        val window = windowFor(WindowPolicy.HOME_WHEN_I_LOOK, gapMinutes)
        if (hour == standingAtHour) {
            standing = standingAt(state, window, gathering, fleet, veins, hour)
        }
        val idle = state.ships.countOf(ShipType.SKIFF)
        val manifests = if (spread) List(idle) { 1 } else listOf(idle)
        for (hulls in manifests) {
            if (hulls <= 0) continue
            val ships = Ships.of(ShipType.SKIFF, hulls)
            val choice = bestVein(state, ships, window, gathering, fleet, veins, hour) ?: continue
            val result = startRun(state, choice.target, gathering, ships, choice.window, at = now)
            if (result !is StartRunResult.Started) continue
            var next = result.state
            val run = next.runs.last()
            next = next.copy(runs = next.runs.dropLast(1) + run.copy(cargo = choice.cargo))
            state = next
            veins.take(choice.target, gathering, priced(choice.cargo).let { choice.cargo.of(gathering) })
            targets += choice.target
            val day = hour / 24
            ledger[day].dispatches++
            if (choice.clamped) ledger[day].clamped++
        }
    }
    state = advance(state, from = now, to = genesis + (days * 24).hours)

    for (event in state.eventLog.filterIsInstance<Event.FleetReturned>()) {
        val day = ((event.at - genesis).inWholeHours / 24).toInt().coerceIn(0, days - 1)
        ledger[day].metal += event.cargo.metal
        ledger[day].crystal += event.cargo.crystal
    }
    return DepositOutcome(
        days = ledger,
        entriesHeld = veins.touched,
        systemsSurveyed = state.galaxy.surveyed.map { it.galaxy to it.system }.distinct().size,
        targetsReached = targets.size,
        colonyMetalPerDay = PlaceholderBalance.effectiveMetalProductionPerHour(state.buildings, state.research) * 24,
        colonyCrystalPerDay =
            PlaceholderBalance.effectiveCrystalProductionPerHour(state.buildings, state.research) * 24,
        standing = standing,
    )
}

// The map as the player meets it at one check-in: every surveyed world the chosen rung can reach, and
// how many of them still hold what a single hull would lift there.
//
// **It asks `bestVein`'s own questions in `bestVein`'s own order** — the same reachability filter, the
// same `cargoAt`, the same `veins.remaining` — because a standing count derived from a second reading
// of the map would be measuring the harness rather than the game.
private fun standingAt(
    state: GameState,
    window: Duration,
    gathering: ResourceKind,
    fleet: FleetTuning,
    veins: Veins,
    hour: Int,
): Standing {
    val home = state.galaxy.home
    val one = Ships.of(ShipType.SKIFF, 1)
    var reachable = 0
    var worthIt = 0
    var hullsFed = 0
    var pricedStanding = 0L
    for (target in state.galaxy.surveyed.sortedWith(compareBy({ it.galaxy }, { it.system }, { it.slot }))) {
        if (target == home || state.galaxy.holderOf(target) != null) continue
        val world = worldAt(state.galaxy.seed, target) ?: continue
        if (window !in FleetBalance.windowsFor(home, target)) continue
        reachable++
        val station = FleetBalance.stationFor(home, target, window)
        val lift = cargoAt(
            fleet,
            world,
            gathering,
            one,
            station,
            FleetBalance.danger(home, world),
            state.research,
        ).of(gathering)
        val inTheGround = veins.remaining(target, gathering, hour, home)
        pricedStanding += inTheGround * if (gathering == ResourceKind.METAL) 1L else 2L
        if (lift <= 0) continue
        if (inTheGround >= lift) worthIt++
        hullsFed += (inTheGround / lift).toInt()
    }
    return Standing(
        hulls = ownedSkiffs(state),
        reachable = reachable,
        worthIt = worthIt,
        hullsFed = hullsFed,
        pricedStanding = pricedStanding,
    )
}

private class VeinChoice(
    val target: GalaxyCoordinate,
    val window: Duration,
    val cargo: Resources,
    val clamped: Boolean,
)

// The richest *remaining* world rather than the richest world, which is the whole behavioural change
// the mechanic asks of a player: what is in the ground is now part of choosing where to send a hull.
private fun bestVein(
    state: GameState,
    ships: Ships,
    window: Duration,
    gathering: ResourceKind,
    fleet: FleetTuning,
    veins: Veins,
    hour: Int,
): VeinChoice? {
    val home = state.galaxy.home
    var best: VeinChoice? = null
    for (target in state.galaxy.surveyed.sortedWith(compareBy({ it.galaxy }, { it.system }, { it.slot }))) {
        if (target == home || state.galaxy.holderOf(target) != null) continue
        val world = worldAt(state.galaxy.seed, target) ?: continue
        if (window !in FleetBalance.windowsFor(home, target)) continue
        val station = FleetBalance.stationFor(home, target, window)
        val lift = cargoAt(
            fleet,
            world,
            gathering,
            ships,
            station,
            FleetBalance.danger(home, world),
            state.research,
        ).of(gathering)
        val inTheGround = veins.remaining(target, gathering, hour, home)
        val taken = minOf(lift, inTheGround)
        if (taken <= 0) continue
        val cargo = if (gathering == ResourceKind.METAL) {
            Resources.of(metal = taken)
        } else {
            Resources.of(crystal = taken)
        }
        if (best == null || taken > best.cargo.of(gathering)) {
            best = VeinChoice(target = target, window = window, cargo = cargo, clamped = lift > inTheGround)
        }
    }
    return best
}

private fun Resources.of(kind: ResourceKind): Long = when (kind) {
    ResourceKind.METAL -> metal
    ResourceKind.CRYSTAL -> crystal
    ResourceKind.DEUTERIUM -> deuterium
}

// **The reading issue #68 says decides the cap**, printed first because it is the one the decision
// turns on: *"how many worth-it worlds a 3h-rung player has standing at hour 48, not total income."*
//
// Hour 48 because that is where the fleet first outgrows the map — the manifest is already several
// hulls by then — and the 3h rung because that player is the one the complaint came from: the window
// decides reach, and a three-hour absence reaches the doorstep and nothing else.
private fun printStandingTable() {
    println("### Worth-it worlds standing at hour 48, on the 3h rung — **the reading that decides**")
    println()
    println("Every surveyed world the 3h rung can reach, and how many of them still hold what one hull")
    println("would lift there. `hulls` is what that has to feed: a fleet larger than the standing count")
    println("is a check-in with idle hulls and nowhere to send them, which is the complaint verbatim.")
    println()
    println("| cap | ×1,450 | hulls | reachable | worth it | hulls fed | priced standing | clamped |")
    println("|---|---|---|---|---|---|---|---|")
    for (base in DEPOSIT_CANDIDATES) {
        val tuning = DepositTuning(basePriced = base, refillPercent = DepositBalance.REFILL_PERCENT_PER_DAY)
        val out = depositRun(
            tuning,
            days = 3,
            checkInHours = FREQUENT_CHECK_IN_HOURS,
            standingAtHour = 48,
            hullsFirst = true,
        )
        val row = out.standing ?: continue
        val mark = if (tuning.isShipped) " **" else ""
        println(
            "| ${base.grouped()}$mark | ${base / 1_450}× | ${row.hulls} | ${row.reachable} | " +
                "**${row.worthIt}** | ${row.hullsFed} | ${row.pricedStanding.grouped()} | " +
                "${share(out.days.sumOf { it.clamped }.toLong(), out.days.sumOf { it.dispatches }.toLong())} |",
        )
    }
    println()
    println("`hulls fed` is the standing stock divided by one hull's lift, summed — so it is a *stock*")
    println("against a demand of `hulls` × six check-ins a day, and it keeps moving after `worth it`")
    println("has saturated at reach. `clamped` here is only these three days and reads near zero for")
    println("any deep cap because the fleet is still small; **the counterweight to read is the")
    println("fortnight's `clamped` below**, which is what says whether the vein still binds at all.")
}

// The report §9 asks for, and the four readings that would veto the numbers. Each names the dial to
// move, because a veto that only says "wrong" sends the next round back to the same grid.
private fun printDepositReport() {
    println()
    println("## Depletion — the sweep the deposit sheet's §9 makes a merge condition")
    println()
    println("Fourteen days, four check-ins a day, one hull per world (the spread strategy §4.1 measures")
    println("as absence-neutral). `clamped` is the share of dispatches the vein stopped rather than the")
    println("fleet — the column that caught a cap of 1,000, and the one no other report in this file has.")
    println()
    println("**Caveat, and it is the same one `printFleetReport` carries: the crystal column is mostly")
    println("zero because the bot gathers crystal only while the colony is short of it, and at four")
    println("check-ins a day this colony rarely is.** A crystal reading wants a forced-currency row the")
    println("way §4's binding row did; until then, read the crystal column as absent rather than as low.")
    println()
    printStandingTable()
    println()
    println("### The fortnight, for what the cap does to income")
    println()
    println("| cap · refill | d1 metal | d7 metal | d14 metal | d14 crystal | clamped | veins held | systems |")
    println("|---|---|---|---|---|---|---|---|")
    for (base in DEPOSIT_CANDIDATES) {
        for (refill in listOf(5L, 10L)) {
            val tuning = DepositTuning(basePriced = base, refillPercent = refill)
            val out = depositRun(tuning)
            val dispatches = out.days.sumOf { it.dispatches }
            val clamped = out.days.sumOf { it.clamped }
            val shipped = if (tuning.isShipped) " **" else ""
            println(
                "| ${tuning.label}$shipped | ${out.days[0].metal.grouped()} | ${out.days[6].metal.grouped()} | " +
                    "${out.days[13].metal.grouped()} | ${out.days[13].crystal.grouped()} | " +
                    "${share(clamped.toLong(), dispatches.toLong())} | ${out.entriesHeld} | ${out.systemsSurveyed} |",
            )
        }
    }

    val shipped = DepositTuning(DepositBalance.BASE_PRICED, DepositBalance.REFILL_PERCENT_PER_DAY)
    println()
    println("### Veto 1 — does the fleet stay worth owning?")
    println()
    println("| | day 1 | day 7 | day 14 | colony/day at 14 | fleet as a share of it |")
    println("|---|---|---|---|---|---|")
    val out = depositRun(shipped)
    println(
        "| metal | ${out.days[0].metal.grouped()} | ${out.days[6].metal.grouped()} | " +
            "${out.days[13].metal.grouped()} | ${out.colonyMetalPerDay.grouped()} | " +
            "${share(out.days[13].metal, out.colonyMetalPerDay)} |",
    )
    println(
        "| crystal | ${out.days[0].crystal.grouped()} | ${out.days[6].crystal.grouped()} | " +
            "${out.days[13].crystal.grouped()} | ${out.colonyCrystalPerDay.grouped()} | " +
            "${share(out.days[13].crystal, out.colonyCrystalPerDay)} |",
    )
    println()
    println("The sheet's bar is ~25% of colony income. Below it the fleet stops being worth owning, the")
    println("hull curve bounds nothing because nobody buys a second hull, and the dial is the cap.")

    println()
    println("### The crystal column, forced — because the adaptive bot almost never asks for it")
    println()
    println("| cap · refill | d1 crystal | d7 crystal | d14 crystal | clamped | colony crystal/day |")
    println("|---|---|---|---|---|---|")
    for (base in DEPOSIT_CANDIDATES) {
        val tuning = DepositTuning(basePriced = base, refillPercent = 5)
        val row = depositRun(tuning, forceGathering = ResourceKind.CRYSTAL)
        val dispatches = row.days.sumOf { it.dispatches }
        val mark = if (tuning.isShipped) " **" else ""
        println(
            "| ${tuning.label}$mark | ${row.days[0].crystal.grouped()} | ${row.days[6].crystal.grouped()} | " +
                "${row.days[13].crystal.grouped()} | " +
                "${share(row.days.sumOf { it.clamped }.toLong(), dispatches.toLong())} | " +
                "${row.colonyCrystalPerDay.grouped()} |",
        )
    }
    println()
    println("Crystal is the game's standing scarcity and its deposits are **half the size** of a metal")
    println("one, because the cap is stated in the priced basket. That halving is the sharpest thing in")
    println("the deposit sheet nobody asked for, and this is the table that would show it hurting.")
    println()
    println("### Veto 3 — is the absent player taxed?")
    println()
    println("| cadence | metal a day at 14 | crystal a day at 14 | clamped | worlds reached |")
    println("|---|---|---|---|---|")
    for ((label, hours) in listOf("every 6h" to listOf(0, 6, 12, 18), "twice a day" to listOf(0, 12), "once a day" to listOf(0))) {
        val row = depositRun(shipped, checkInHours = hours)
        val dispatches = row.days.sumOf { it.dispatches }
        println(
            "| $label | ${row.days[13].metal.grouped()} | ${row.days[13].crystal.grouped()} | " +
                "${share(row.days.sumOf { it.clamped }.toLong(), dispatches.toLong())} | " +
                "${row.targetsReached} |",
        )
    }
    println()
    println("**The first run of this table found the mirror of the failure it was built to catch.** The")
    println("sheet's §9 asks whether the *absent* player is taxed. They are not — they are paid roughly")
    println("fifty times over, and the `worlds reached` column says why: the window rung decides how far")
    println("a run can go, reach decides how many veins you can spread across, and a player confined to")
    println("the 3h rung is confined to their own doorstep. Six worlds, stripped, living on 5% a day.")
    println()
    println("So depletion makes the long window strictly better, where the fleet sheet measured every")
    println("cadence inside 4% of each other. That is a design call rather than a constant: the answer")
    println("if it is unwanted is to make the frontier reachable at a shorter window — which is what")
    println("the drive technology was always for — and not to move the cap or the refill.")

    println()
    println("### The trap — the same player concentrating instead of spreading")
    println()
    println("| strategy | metal a day at 14 | clamped |")
    println("|---|---|---|")
    for ((label, spread) in listOf("one hull per world" to true, "the whole fleet on one world" to false)) {
        val row = depositRun(shipped, spread = spread)
        val dispatches = row.days.sumOf { it.dispatches }
        println(
            "| $label | ${row.days[13].metal.grouped()} | " +
                "${share(row.days.sumOf { it.clamped }.toLong(), dispatches.toLong())} |",
        )
    }
    println()
    println("The gap between these two rows is what the dispatch sheet's clamp copy has to close. It is")
    println("a skill the game did not previously contain, and an undiscovered one is a trap.")
}
