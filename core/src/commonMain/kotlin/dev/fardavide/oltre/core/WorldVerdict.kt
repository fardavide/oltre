package dev.fardavide.oltre.core

// How a world reads. `core` computes one verdict; the screen renders a sentence. Showing the three
// raw values as bars with no verdict was rejected — it makes the player do arithmetic the game can
// do, and 5–10 minute sessions cannot afford it — and so was a single 0–100 score, which hides
// *which* axis blocks, the only actionable part.

// How far the empire's tolerance has been stretched on each axis. Not serialised, because it is a
// *view* of something that is: since 0.0.17 the three ladders are real technologies and their
// levels live in `Research`, so this is what `Research.adaptationLevels()` hands back and never a
// number of its own. It stayed a separate type rather than becoming three fields on `Tolerance`
// because `levelThatTolerates` has to answer for a level the empire does not hold yet.
//
// `NONE` is therefore no longer where every empire permanently sits — it is genesis, and the thing
// the player spends the adaptation branch to leave.
data class AdaptationLevels(
    val thermal: Int,
    val gravitic: Int,
    val atmospheric: Int,
) {
    init {
        require(thermal >= 0) { "thermal adaptation must be non-negative, was $thermal" }
        require(gravitic >= 0) { "gravitic adaptation must be non-negative, was $gravitic" }
        require(atmospheric >= 0) { "atmospheric adaptation must be non-negative, was $atmospheric" }
    }

    fun levelOf(technology: AdaptationTechnology): Int = when (technology) {
        AdaptationTechnology.THERMAL -> thermal
        AdaptationTechnology.GRAVITIC -> gravitic
        AdaptationTechnology.ATMOSPHERIC -> atmospheric
    }

    companion object {
        val NONE: AdaptationLevels = AdaptationLevels(thermal = 0, gravitic = 0, atmospheric = 0)
    }
}

// Inclusive at both ends, in the axis's own unit — °C, milli-g, milli-atm.
data class ToleranceBand(val min: Int, val max: Int) {
    init {
        require(min <= max) { "a tolerance band cannot be inverted, was $min to $max" }
    }

    operator fun contains(value: Int): Boolean = value in min..max
}

data class Tolerance(
    val temperature: ToleranceBand,
    val gravity: ToleranceBand,
    val pressure: ToleranceBand,
) {
    fun bandOf(axis: HostilityAxis): ToleranceBand = when (axis) {
        HostilityAxis.TEMPERATURE -> temperature
        HostilityAxis.GRAVITY -> gravity
        HostilityAxis.PRESSURE -> pressure
    }
}

fun WorldTraits.axisValue(axis: HostilityAxis): Int = when (axis) {
    HostilityAxis.TEMPERATURE -> temperature.celsius
    HostilityAxis.GRAVITY -> gravity.milliG
    HostilityAxis.PRESSURE -> pressure.milliAtm
}

// One axis a world fails on, carrying everything the sentence needs: which axis, what the world is,
// the bound it missed, and the adaptation level that would land it. `Blocked` naming its own remedy
// is the design's load-bearing detail — "gravity 2.4 g, you tolerate 1.45 g; Gravitic Adaptation 3
// would land it" is what turns the galaxy screen into a reason to research, which is the only thing
// connecting two tabs that otherwise never speak.
data class ToleranceFailure(
    val axis: HostilityAxis,
    val worldValue: Int,
    val toleratedBound: Int,
    val closedAtLevel: Int,
)

// Sealed, in the sheet's precedence — `Home` before `Occupied` before `Unsurveyed` before the three
// surveyed answers. At ship time almost everything reads `Unsurveyed`, because surveying is a fleet
// action and fleets arrive with slice #7; that is the honest state rather than a placeholder.
sealed interface WorldVerdict {

    data object Home : WorldVerdict

    data class Occupied(val holder: EmpireId) : WorldVerdict

    data object Unsurveyed : WorldVerdict

    // Never empty, and ordered by `HostilityAxis` so the sentence reads the same way twice.
    data class Blocked(val failures: List<ToleranceFailure>) : WorldVerdict {
        init {
            require(failures.isNotEmpty()) { "a blocked world must fail at least one axis" }
        }
    }

    data object Barren : WorldVerdict

    data class Settleable(val score: YieldScore) : WorldVerdict
}

// What a caller holding the whole state should use, and the only one that can be wrong in an
// interesting way: pass `AdaptationLevels.NONE` by hand and every world stays as blocked as it was
// at genesis however deep the empire has climbed. The three-argument form below stays public
// because `levelThatTolerates` and the sim both have to ask about levels nobody holds.
fun verdictFor(world: World, state: GameState): WorldVerdict =
    verdictFor(world, state.galaxy, state.research.adaptationLevels())

// The one entry point the screen needs. Pure: the galaxy state says what the player has changed,
// the world says what the seed generated, and nothing here reads a clock.
fun verdictFor(world: World, galaxy: GalaxyState, adaptation: AdaptationLevels): WorldVerdict {
    if (world.at == galaxy.home) return WorldVerdict.Home
    galaxy.holderOf(world.at)?.let { holder -> return WorldVerdict.Occupied(holder) }
    if (world.at !in galaxy.surveyed) return WorldVerdict.Unsurveyed

    val tolerance = GalaxyBalance.tolerance(adaptation)
    val failures = HostilityAxis.entries.mapNotNull { axis -> world.traits.failureOn(axis, tolerance, adaptation) }
    if (failures.isNotEmpty()) return WorldVerdict.Blocked(failures)

    val score = GalaxyBalance.yieldScore(world.traits)
    return if (score.perMillion >= GalaxyBalance.WORTH_IT_THRESHOLD.perMillion) {
        WorldVerdict.Settleable(score)
    } else {
        // The common answer, deliberately: the median world that passes every band scores below the
        // threshold, so a survey usually returns "not worth it" and is therefore a decision rather
        // than a tax.
        WorldVerdict.Barren
    }
}

private fun WorldTraits.failureOn(
    axis: HostilityAxis,
    tolerance: Tolerance,
    adaptation: AdaptationLevels,
): ToleranceFailure? {
    val value = axisValue(axis)
    val band = tolerance.bandOf(axis)
    if (value in band) return null
    return ToleranceFailure(
        axis = axis,
        worldValue = value,
        toleratedBound = if (value < band.min) band.min else band.max,
        // Counted from level 0 rather than from where the empire already stands, so the sentence
        // names the technology level a player would go and buy.
        closedAtLevel = GalaxyBalance.levelThatTolerates(axis, value)
            .coerceAtLeast(adaptation.levelOf(axis.adaptation) + 1),
    )
}
