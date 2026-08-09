package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.design.format.milli
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.client.design.format.perMillion
import dev.fardavide.oltre.client.design.format.signed
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Hazard
import dev.fardavide.oltre.core.HostilityAxis
import dev.fardavide.oltre.core.StarClass
import dev.fardavide.oltre.core.ToleranceFailure
import dev.fardavide.oltre.core.World
import dev.fardavide.oltre.core.WorldTraits
import dev.fardavide.oltre.core.WorldVerdict
import dev.fardavide.oltre.core.relayAt
import dev.fardavide.oltre.core.starClassAt
import dev.fardavide.oltre.core.verdictFor
import dev.fardavide.oltre.core.worldAt
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// Which system is on screen. The galaxy and the system, never the slot: the page *is* a system, and
// the slot is what the fifteen rows below the map are for.
data class SystemSelection(val galaxy: Int, val system: Int)

// The three temperature bands the fifteen orbits fall into. Position is a trait — slot 1 is the
// hottest orbit and slot 15 the coldest — so the band is the one thing the charted tier can say
// about a world it knows nothing else about, and it is what lets a player learn where the deuterium
// is by looking rather than by being told.
enum class OrbitBand(val label: String, val slots: IntRange) {
    HOT(label = "Hot", slots = 1..3),
    TEMPERATE(label = "Temperate", slots = 4..10),
    COLD(label = "Cold", slots = 11..15),
    ;

    val heading: String get() = "$label · slots ${slots.first}–${slots.last}"

    companion object {
        fun of(slot: Int): OrbitBand = entries.first { slot in it.slots }
    }
}

data class GalaxyUiState(
    // Four, always — the coordinate space is fixed, so this is a segmented control rather than a
    // list that grows.
    val galaxies: List<GalaxyTabUiState>,
    val scope: String,
    val coordinate: String,
    val detail: String,
    // A 320dp Slide Over pane drops the trailing noun, exactly as the Research effect line does.
    // Abbreviation is a width decision rather than a change of voice: what goes is a noun, never a
    // number or a name — and it is authored rather than left to an ellipsis, because "4 WO…" is the
    // layout admitting defeat where "DIM · 4" is the screen still saying something true.
    val compactDetail: String,
    val isHome: Boolean,
    // What replaced the ±1 stepper. `atFirstSystem` / `atLastSystem` went with it: a lens slides
    // rather than clipping, so there is no edge to disable a control at any more.
    val reach: ReachBandUiState,
    val map: SystemMapUiState,
    // Inside the map card rather than beside it — a probe targets a star, and the map is the only
    // star-scoped object on the screen.
    val probe: ProbeActionUiState,
    val bands: List<OrbitBandUiState>,
)

data class GalaxyTabUiState(val label: String, val galaxy: Int, val selected: Boolean)

// What the map draws, which is deliberately more than the list holds: every slot, including the
// eleven empty ones. That is the whole of what the map has that the list cannot — the shape of a
// system, and where its gaps fall.
data class SystemMapUiState(val slots: List<MapSlotUiState>)

data class MapSlotUiState(val slot: Int, val mark: MapMark)

// What a dot on the map means. `EMPTY` is a tick rather than a dot, and it is most of them.
enum class MapMark { EMPTY, HOME, OCCUPIED, UNSURVEYED, BLOCKED, BARREN, SETTLEABLE, RELAY }

data class OrbitBandUiState(val band: OrbitBand, val rows: List<WorldRowUiState>)

data class WorldRowUiState(
    val coordinate: String,
    val slot: Int,
    val band: OrbitBand,
    val verdict: VerdictUiState,
)

// One row, six verdicts and a relay that is not one. Each carries exactly what its verdict earns
// and nothing more, which is what keeps `Unsurveyed` the shortest card in the app — the normal case
// on a screen where 4,746 of 4,750 worlds are unsurveyed.
sealed interface VerdictUiState {

    // The reference row. It shows its three axes and its yield because every other yield on the
    // screen is read against it, and the player should meet it on the first launch.
    data class Home(val axes: String, val detail: String) : VerdictUiState

    data class Occupied(val holder: String) : VerdictUiState

    data object Unsurveyed : VerdictUiState

    // Never empty, and in `HostilityAxis` order rather than by the size of the gap, so the third
    // line is in the same place on every three-axis world.
    //
    // It carries a yield and a calibration line for the same reason `Barren` does, and it needs
    // them more: 98% of surveyed worlds read `Blocked`, so this is the verdict a player meets over
    // and over. Without the yield the row stated a cost and never a worth; without the count and
    // the bar, a screen of them reads as bad luck rather than as the design — which is the exact
    // job `Barren`'s threshold sentence already does.
    data class Blocked(
        val failures: List<BlockedAxisUiState>,
        val yieldLabel: String,
        val calibration: String,
        val detail: String,
    ) : VerdictUiState {
        init {
            require(failures.isNotEmpty()) { "a blocked row must name at least one axis" }
        }
    }

    // States the ratio and then the threshold, the way the power card states a ratio before its
    // consequence. Naming the threshold is what makes a run of Barren answers read as calibration
    // rather than as bad luck — and Barren is designed to be the common answer.
    data class Barren(val yieldLabel: String, val threshold: String, val detail: String) : VerdictUiState

    data class Settleable(val yieldLabel: String, val richness: String, val detail: String) : VerdictUiState

    // Not a world, and not tappable. It states its effect and stops.
    data class Relay(val effect: String) : VerdictUiState
}

// "gravity 1.78, you tolerate 1.45 g — Gravitic 3". The unit is written once, on the tolerance:
// both numbers are the same axis and therefore the same unit, and the four characters that saves
// are what keep the technology on the line at 393dp.
data class BlockedAxisUiState(
    val axis: String,
    val reading: String,
    val tolerated: String,
    // The ladder itself as well as the string it renders. The label is what the row prints; the
    // enum is what the tap target is keyed by and what a later slice would deep-link with, and
    // keeping it means the one place this row names a technology is not a bare string.
    val technology: AdaptationTechnology,
    val label: String,
)

// PLACEHOLDER copy, marked as such for the same reason the notification copy and the unbuilt tabs'
// one-liners are: what a screen says to the player is content, and content is Davide's. The relay
// states an effect no mechanic can yet confer — there is no way to hold one until multiplayer — so
// this is the line the design flagged as its fifth open call.
private const val RELAY_EFFECT = "+18% range while held"

// 0.0.16's PLACEHOLDER header line — "Adaptation research lands later. You are at level 0." — was
// deleted here rather than replaced. It existed to account for an absence, and the absence ended
// when Research started selling the three ladders; an absence that ends does not need a successor,
// and keeping the slot alive would leave the header shaped by something no longer true.
//
// The honest candidate for the slot was where the empire actually stands — "Thermal 2 · Gravitic 0
// · Atmospheric 1" — and the design rejected it for one reason worth keeping written down: a
// tolerance band means nothing except against a reading. Every place a player needs one, the
// reading is already beside it — "gravity 2.62, you tolerate 1.45 g" on the row below, and the
// current band on the left of every adaptation row on Research. A standing total in a header would
// answer a question nobody is holding at that moment, and would be the only header in Oltre
// stating empire state that is not about what is on screen. The rail already does empire state.

// Written once because `Blocked` and `Barren` both quote it, and two rows on one screen disagreeing
// about the bar would be the screen contradicting itself. It is the number `verdictFor` actually
// decides by, not a string that looks like it.
private val WORTH_IT_AT = "worth it at ${GalaxyBalance.WORTH_IT_THRESHOLD.perMillion.perMillion()}"

// The whole state rather than its `galaxy` half, and that is the fix 0.0.17 left for this slice:
// `verdictFor(world, state)` reads the empire's real adaptation levels, where the two-argument form
// took an `AdaptationLevels` that this mapper defaulted to `NONE`. With the ladders buyable, a
// default of `NONE` would leave every world exactly as blocked as it was at genesis however deep
// the player had climbed — the screen quietly refusing to show what they had bought.
// `now` and `timeZone` arrive with 0.2.0, because the footer runs a countdown and prints a landing
// clock. This was the one screen in the app that needed neither, and it stopped being so the moment
// it grew a job of its own.
internal fun GameState.toGalaxyUiState(at: SystemSelection, now: Instant, timeZone: TimeZone): GalaxyUiState {
    val seed = galaxy.seed
    val starClass = starClassAt(seed, at.galaxy, at.system)
    val relay = relayAt(seed, at.galaxy, at.system)
    val worlds = (1..GalaxyBalance.SLOTS_PER_SYSTEM).associateWith { slot ->
        worldAt(seed, GalaxyCoordinate(galaxy = at.galaxy, system = at.system, slot = slot))
    }

    val rows = worlds.mapNotNull { (slot, world) ->
        val coordinate = GalaxyCoordinate(galaxy = at.galaxy, system = at.system, slot = slot)
        when {
            world != null -> WorldRowUiState(
                coordinate = coordinate.label(),
                slot = slot,
                band = OrbitBand.of(slot),
                verdict = verdictFor(world, this).toUiState(world.traits),
            )
            coordinate == relay -> WorldRowUiState(
                coordinate = coordinate.label(),
                slot = slot,
                band = OrbitBand.of(slot),
                verdict = VerdictUiState.Relay(effect = RELAY_EFFECT),
            )
            else -> null
        }
    }

    return GalaxyUiState(
        galaxies = (1..GalaxyBalance.GALAXIES).map { index ->
            GalaxyTabUiState(label = "G$index", galaxy = index, selected = index == at.galaxy)
        },
        scope = "${GalaxyBalance.SYSTEMS_PER_GALAXY} systems",
        coordinate = "${at.galaxy}:${at.system}",
        detail = detailFor(starClass, worlds.count { it.value != null }, compact = false),
        compactDetail = detailFor(starClass, worlds.count { it.value != null }, compact = true),
        isHome = at.galaxy == galaxy.home.galaxy && at.system == galaxy.home.system,
        reach = toReachBandUiState(at = at),
        map = SystemMapUiState(
            slots = (1..GalaxyBalance.SLOTS_PER_SYSTEM).map { slot ->
                MapSlotUiState(slot = slot, mark = markFor(rows.firstOrNull { it.slot == slot }))
            },
        ),
        probe = toProbeActionUiState(
            at = at,
            // The worlds this system actually holds, passed rather than regenerated: the mapper has
            // just paid for all fifteen slots, and the footer's "nothing to survey" branch turns on
            // exactly the same set.
            worlds = worlds.values.filterNotNull(),
            now = now,
            timeZone = timeZone,
        ),
        bands = OrbitBand.entries
            .map { band -> OrbitBandUiState(band = band, rows = rows.filter { it.band == band }) }
            .filter { it.rows.isNotEmpty() },
    )
}

// The star class sits in the header rather than on every row, because a class is a property of the
// system. It also shifts the whole system's temperature curve by ±40 °C, which is why the map's
// band strip means something different in a BRIGHT system than in a DIM one.
private fun detailFor(starClass: StarClass, worlds: Int, compact: Boolean): String {
    if (compact) return "${starClass.name} · $worlds"
    val plural = if (worlds == 1) "world" else "worlds"
    return "${starClass.name} · $worlds $plural"
}

private fun markFor(row: WorldRowUiState?): MapMark = when (row?.verdict) {
    null -> MapMark.EMPTY
    is VerdictUiState.Home -> MapMark.HOME
    is VerdictUiState.Occupied -> MapMark.OCCUPIED
    VerdictUiState.Unsurveyed -> MapMark.UNSURVEYED
    is VerdictUiState.Blocked -> MapMark.BLOCKED
    is VerdictUiState.Barren -> MapMark.BARREN
    is VerdictUiState.Settleable -> MapMark.SETTLEABLE
    is VerdictUiState.Relay -> MapMark.RELAY
}

private fun WorldVerdict.toUiState(traits: WorldTraits): VerdictUiState = when (this) {
    WorldVerdict.Home -> VerdictUiState.Home(
        axes = listOf(
            "${traits.temperature.celsius.signed()}$NBSP°C",
            "${traits.gravity.milliG.milli()}${NBSP}g",
            "${traits.pressure.milliAtm.milli()}${NBSP}atm",
        ).joinToString(SEPARATOR),
        detail = listOfNotNull(traits.fieldsLabel(), "yield ${traits.yieldLabel()}", traits.hazardLabel("no hazards"))
            .joinToString(SEPARATOR),
    )
    is WorldVerdict.Occupied -> VerdictUiState.Occupied(holder = "Held by ${holder.value}")
    WorldVerdict.Unsurveyed -> VerdictUiState.Unsurveyed
    is WorldVerdict.Blocked -> VerdictUiState.Blocked(
        failures = failures.map { it.toUiState() },
        yieldLabel = "yield ${traits.yieldLabel()}",
        // The count comes from the row rather than from a table, and the bar is the one `Barren`
        // quotes — a blocked world reading its yield against the same 0.92 is what turns the row
        // into the shopping list the sheet asked for: the world is worth taking, the band is not
        // wide enough yet, and the line above says what widens it.
        calibration = "Fails ${failures.size} of ${HostilityAxis.entries.size} bands, $WORTH_IT_AT",
        detail = listOfNotNull(traits.fieldsLabel(), traits.hazardLabel(null)).joinToString(SEPARATOR),
    )
    WorldVerdict.Barren -> VerdictUiState.Barren(
        yieldLabel = "yield ${traits.yieldLabel()}",
        threshold = "Passes every band, $WORTH_IT_AT",
        detail = listOfNotNull(traits.fieldsLabel(), traits.hazardLabel(null)).joinToString(SEPARATOR),
    )
    is WorldVerdict.Settleable -> VerdictUiState.Settleable(
        yieldLabel = "yield ${traits.yieldLabel()}",
        richness = listOf(
            "metal ${traits.metalRichness.perMillion.perMillion()}",
            "crystal ${traits.crystalRichness.perMillion.perMillion()}",
            "deut ${traits.deuteriumRichness.perMillion.perMillion()}",
        ).joinToString(SEPARATOR),
        detail = listOfNotNull(traits.fieldsLabel(), traits.hazardLabel(null)).joinToString(SEPARATOR),
    )
}

private fun ToleranceFailure.toUiState(): BlockedAxisUiState = BlockedAxisUiState(
    axis = axis.name.lowercase(),
    reading = axis.reading(worldValue),
    tolerated = axis.tolerated(toleratedBound),
    technology = axis.adaptation,
    // "Gravitic 9", not "Gravitic Adaptation 9". All three technologies here end in the same word,
    // so it carries nothing and costs eleven characters the row does not have. Research spells it
    // out; this row has no room, and the object is the same either way.
    label = "${axis.adaptation.name.lowercase().replaceFirstChar { it.uppercase() }} $closedAtLevel",
)

private fun HostilityAxis.reading(value: Int): String = when (this) {
    HostilityAxis.TEMPERATURE -> value.signed()
    HostilityAxis.GRAVITY, HostilityAxis.PRESSURE -> value.milli()
}

// **The space before each unit below is U+00A0, not U+0020** — invisible in a diff, so it is said
// here. The blocked line is the longest on the screen and it does wrap at 393dp on a three-axis
// world; the design expects that at 320dp and tolerates it here. What it must not do is break
// between a number and its unit, which leaves "atm" alone on a line and reads as a defect rather
// than as a wrap.
private fun HostilityAxis.tolerated(value: Int): String = when (this) {
    HostilityAxis.TEMPERATURE -> "${value.signed()} °C"
    HostilityAxis.GRAVITY -> "${value.milli()} g"
    HostilityAxis.PRESSURE -> "${value.milli()} atm"
}

private fun WorldTraits.fieldsLabel(): String = "$fields fields"

private fun WorldTraits.yieldLabel(): String = GalaxyBalance.yieldScore(this).perMillion.perMillion()

// Sentence case on the last line, because a hazard is memorable in words and is not the verdict.
private fun WorldTraits.hazardLabel(ifNone: String?): String? = when {
    hazards.isEmpty() -> ifNone
    else -> hazards.sortedBy { it.ordinal }.joinToString(SEPARATOR) { it.label() }
}

private fun Hazard.label(): String = name.lowercase().replace('_', ' ')

private fun GalaxyCoordinate.label(): String = "[$galaxy:$system:$slot]"

private const val SEPARATOR = " · "

// Between a value and its unit, so a line that has to wrap never leaves "atm" alone on one.
private const val NBSP = ' '
