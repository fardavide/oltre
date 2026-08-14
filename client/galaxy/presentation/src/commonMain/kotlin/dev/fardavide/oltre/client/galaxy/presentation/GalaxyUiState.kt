package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.design.format.milli
import dev.fardavide.oltre.client.design.format.toChipLabel
import dev.fardavide.oltre.client.galaxy.ui.BlockedAxisUiState
import dev.fardavide.oltre.client.galaxy.ui.DepositReadingUiState
import dev.fardavide.oltre.client.galaxy.ui.FleetReadingUiState
import dev.fardavide.oltre.client.galaxy.ui.GalaxyTabUiState
import dev.fardavide.oltre.client.galaxy.ui.GalaxyUiState
import dev.fardavide.oltre.client.galaxy.ui.MapBodyUiState
import dev.fardavide.oltre.client.galaxy.ui.MapMark
import dev.fardavide.oltre.client.galaxy.ui.MapTrajectoryUiState
import dev.fardavide.oltre.client.galaxy.ui.OrbitBand
import dev.fardavide.oltre.client.galaxy.ui.OrbitBandUiState
import dev.fardavide.oltre.client.galaxy.ui.SystemMapUiState
import dev.fardavide.oltre.client.galaxy.ui.VerdictUiState
import dev.fardavide.oltre.client.galaxy.ui.WorldRowUiState
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.client.design.format.perMillion
import dev.fardavide.oltre.client.design.format.signed
import dev.fardavide.oltre.core.FleetBalance
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxyState
import dev.fardavide.oltre.core.ResourceKind
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
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// **Everything the Galaxy tab decides.** The types it produces live in `:client:galaxy:ui`, which
// knows nothing about a seed or a `GameState` — this is where a slot becomes a verdict, a world
// becomes a sentence and a system becomes a page.


// Which system is on screen. The galaxy and the system, never the slot: the page *is* a system, and
// the slot is what the fifteen rows below the map are for.
data class SystemSelection(val galaxy: Int, val system: Int)

// `startRun`'s rule, restated once as a question about the row: which verdicts is a run legal at.
// **Not the same set as `isRunnable`** — that one governs whether the card opens a sheet, and it
// includes `Unsurveyed`, where the sheet's whole job is to refuse and offer a probe instead. A
// deposit reading on an unsurveyed world would be the row claiming knowledge nobody paid for.
private fun VerdictUiState.pricesAHold(): Boolean = when (this) {
    is VerdictUiState.Blocked,
    is VerdictUiState.Barren,
    is VerdictUiState.Settleable,
    -> true
    VerdictUiState.Unsurveyed,
    is VerdictUiState.Home,
    is VerdictUiState.Occupied,
    is VerdictUiState.Relay,
    -> false
}

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
internal fun GameState.toGalaxyUiState(
    at: SystemSelection,
    now: Instant,
    timeZone: TimeZone,
    // Which world the player has raised the dispatch sheet on, and what they have chosen inside it.
    // Null is the honest default — a screen with no sheet up — and it is what every render before
    // the first tap passes, which is why it defaults rather than being threaded through the twenty
    // existing callers.
    dispatch: DispatchSelection? = null,
): GalaxyUiState {
    val seed = galaxy.seed
    val starClass = starClassAt(seed, at.galaxy, at.system)
    val relay = relayAt(seed, at.galaxy, at.system)
    val worlds = (1..GalaxyBalance.SLOTS_PER_SYSTEM).associateWith { slot ->
        worldAt(seed, GalaxyCoordinate(galaxy = at.galaxy, system = at.system, slot = slot))
    }

    val rows = worlds.mapNotNull { (slot, world) ->
        val coordinate = GalaxyCoordinate(galaxy = at.galaxy, system = at.system, slot = slot)
        when {
            world != null -> {
                val verdict = verdictFor(world, this).toUiState(world = world, from = galaxy.home)
                WorldRowUiState(
                    coordinate = coordinate.label(),
                    slot = slot,
                    band = OrbitBand.of(slot),
                    verdict = verdict,
                    deposits = if (verdict.pricesAHold()) toDepositReading(coordinate, now) else null,
                )
            }
            coordinate == relay -> WorldRowUiState(
                coordinate = coordinate.label(),
                slot = slot,
                band = OrbitBand.of(slot),
                verdict = VerdictUiState.Relay(effect = RELAY_EFFECT),
                deposits = null,
            )
            else -> null
        }
    }

    // Hoisted out of the constructor because the dispatch sheet's unsurveyed refusal quotes it: the
    // card's footer already decides whether a probe can be sent — in flight, unaffordable, landed —
    // and a second copy of that decision inside the sheet is a second place for the two to disagree
    // about one flight.
    val probe = toProbeActionUiState(
        at = at,
        // The worlds this system actually holds, passed rather than regenerated: the mapper has just
        // paid for all fifteen slots, and the footer's "nothing to survey" branch turns on exactly
        // the same set.
        worlds = worlds.values.filterNotNull(),
        now = now,
        timeZone = timeZone,
    )
    return GalaxyUiState(
        galaxies = (1..GalaxyBalance.GALAXIES).map { index ->
            GalaxyTabUiState(label = "G$index", galaxy = index, selected = index == at.galaxy)
        },
        scope = "${GalaxyBalance.SYSTEMS_PER_GALAXY} systems",
        coordinate = "${at.galaxy}:${at.system}",
        detail = detailFor(starClass, worlds.count { it.value != null }, compact = false),
        compactDetail = detailFor(starClass, worlds.count { it.value != null }, compact = true),
        isHome = at.galaxy == galaxy.home.galaxy && at.system == galaxy.home.system,
        astronomy = astronomyFor(at = at, worlds = worlds.values.filterNotNull()),
        reach = toReachBandUiState(at = at),
        map = SystemMapUiState(
            // Only the slots that hold something. `rows` is already exactly that set — a world or
            // the system's relay — so the map and the list below it can never disagree about what
            // is there.
            bodies = rows.sortedBy { it.slot }.let { sorted ->
                sorted.mapIndexed { index, row ->
                    MapBodyUiState(
                        slot = row.slot,
                        mark = markFor(row),
                        orbit = orbitOf(index, of = sorted.size),
                    )
                }
            },
            trajectory = if (at.galaxy == galaxy.home.galaxy && at.system == galaxy.home.system) {
                // The one landing soonest rather than whichever the list happens to hold first:
                // nothing caps simultaneous probes, and the arc can only carry one of them, so it
                // carries the one whose countdown the player is actually waiting on.
                surveys.minByOrNull { it.completesAt }?.let { job ->
                    MapTrajectoryUiState(
                        label = "[${job.target.galaxy}:${job.target.system}]" +
                            " · ${(job.completesAt - now).coerceAtLeast(Duration.ZERO).toChipLabel()}",
                    )
                }
            } else {
                null
            },
        ),
        probe = probe,
        bands = OrbitBand.entries
            .map { band -> OrbitBandUiState(band = band, rows = rows.filter { it.band == band }) }
            .filter { it.rows.isNotEmpty() },
        dispatch = dispatch?.let { toDispatchUiState(at = at, selection = it, probe = probe, now = now) },
    )
}

// "195 units out · danger 1 from here · 58m out and back", and on your own doorstep "Your own system
// · danger 0 from here · 20–26m out and back". Three facts, all of them free: none needs a survey,
// and all three are the same for every slot of the system — which is exactly why this is one line
// under the header rather than a column on fifteen rows.
//
// **The range is only ever your own system's**, and it is not a rounding of anything: a run's
// distance metric is world-to-world, so within one system it is the *slot* gap that varies, where a
// hop to any other system is priced identically for all fifteen. So one number everywhere else, and
// a spread at home — where the player is choosing between neighbours and the spread is the choice.
private fun GameState.astronomyFor(at: SystemSelection, worlds: List<World>): String {
    val home = galaxy.home
    // Any slot of the system will do and slot 1 is the one that always exists: the band and the unit
    // count both ignore the slot the moment the system differs, which is the whole reason this line
    // can be stated once. Asking it of a *world* would make an empty system unanswerable.
    val anywhereInIt = GalaxyCoordinate(galaxy = at.galaxy, system = at.system, slot = 1)
    val band = FleetBalance.distanceBand(from = home, to = anywhereInIt)
    val where = if (band == 0) {
        "Your own system"
    } else {
        "${FleetBalance.distanceUnits(from = home, to = anywhereInIt).toLong().groupedByThousands()} units out"
    }
    val trips = worlds.map { it.at }
        .filter { it != home }
        .map { FleetBalance.roundTrip(from = home, to = it) }
        .sorted()
    val reach = trips.reachLabel()
    // **"from here" goes when the line will not fit, and the budget is a measurement rather than a
    // taste.** Two cases overflow and both are ordinary: the home system, which states a *range* of
    // round trips rather than one, and any target in another galaxy, whose distance is four digits
    // and whose flight is hours. The home system is the screen every player opens on.
    //
    // What goes is a noun and never a figure — the rule the header and the world row already follow
    // — and it is the least load-bearing clause here, because the first clause has already said what
    // the band is measured from.
    val full = listOfNotNull(where, "danger $band from here", reach).joinToString(SEPARATOR)
    if (full.length <= ASTRONOMY_BUDGET_CHARS) return full
    return listOfNotNull(where, "danger $band", reach).joinToString(SEPARATOR)
}

// What one line of this column holds. The content column is capped at `maxContentWidth` and padded
// 16dp a side, so at a phone's 393dp it is 361dp wide; JetBrains Mono advances 0.6em, which at the
// 10.5sp this line is set in makes 57 characters exactly 359dp. That is inside 361 on paper and
// wrapped in practice, so the budget is the measured figure with the rounding taken off rather than
// the arithmetic one.
private const val ASTRONOMY_BUDGET_CHARS = 54

// Null on a system with nothing in it: there is no round trip to nowhere, and the probe footer above
// is already saying the system is empty.
private fun List<Duration>.reachLabel(): String? {
    val shortest = firstOrNull() ?: return null
    val longest = last()
    if (shortest == longest) return "${shortest.toChipLabel()} out and back"
    val from = shortest.toChipLabel()
    val to = longest.toChipLabel()
    // "20–26m" rather than "20m–26m", but only when both ends are minutes: at the hour scale the
    // label already carries an "h" and dropping the "m" off the near end would leave "1h 04–2h 12m".
    val collapsed = if ('h' in from || 'h' in to) "$from–$to" else "${from.removeSuffix("m")}–$to"
    return "$collapsed out and back"
}

// The star class sits in the header rather than on every row, because a class is a property of the
// system. It also shifts the whole system's temperature curve by ±40 °C, which is why the map's
// band strip means something different in a BRIGHT system than in a DIM one.
private fun detailFor(starClass: StarClass, worlds: Int, compact: Boolean): String {
    if (compact) return "${starClass.name} · $worlds"
    val plural = if (worlds == 1) "world" else "worlds"
    return "${starClass.name} · $worlds $plural"
}

// Evenly across the frame, whatever the system holds. A lone body sits midway rather than at
// either edge — an orbit pinned to the inner limit would say "hot" about a world that might be the
// coldest slot in the system, and the map has no second body to say it against.
private fun orbitOf(index: Int, of: Int): Float =
    if (of <= 1) 0.5f else index.toFloat() / (of - 1).toFloat()

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

private fun WorldVerdict.toUiState(world: World, from: GalaxyCoordinate): VerdictUiState {
    val traits = world.traits
    return when (this) {
        WorldVerdict.Home -> VerdictUiState.Home(
            note = listOf(
                "${traits.temperature.celsius.signed()}$NBSP°C",
                "${traits.gravity.milliG.milli()}${NBSP}g",
                "${traits.pressure.milliAtm.milli()}${NBSP}atm",
                traits.fieldsLabel(),
            ).joinToString(SEPARATOR),
        )
        is WorldVerdict.Occupied -> VerdictUiState.Occupied(note = "Held by ${holder.value}")
        WorldVerdict.Unsurveyed -> VerdictUiState.Unsurveyed
        is WorldVerdict.Blocked -> VerdictUiState.Blocked(
            reading = world.toFleetReading(from = from),
            failures = failures.map { it.toUiState() },
        )
        WorldVerdict.Barren -> VerdictUiState.Barren(
            reading = world.toFleetReading(from = from),
            threshold = "yield ${traits.yieldLabel()}, $WORTH_IT_AT",
        )
        is WorldVerdict.Settleable -> VerdictUiState.Settleable(
            note = listOf(
                "Yield ${traits.yieldLabel()}",
                "metal ${traits.metalRichness.perMillion.perMillion()}",
                "crystal ${traits.crystalRichness.perMillion.perMillion()}",
                traits.fieldsLabel(),
            ).joinToString(SEPARATOR),
        )
    }
}

// The hazards a hold will pay for and the round trip. Read from `FleetBalance` rather than restated,
// so a row and the sheet it raises cannot disagree about how far away a world is.
private fun World.toFleetReading(from: GalaxyCoordinate): FleetReadingUiState = FleetReadingUiState(
    hazards = traits.fleetHazardLabel(),
    reach = "${FleetBalance.roundTrip(from = from, to = at).toChipLabel()} out and back",
)

// `metal full`, `metal 174/819`, `metal empty` — a word at each end because neither end poses any
// arithmetic, and a fraction between because 120 of 600 and 120 of 2,400 are the same number and not
// the same target.
//
// **Never the words this design refused**: no *left*, no *deposit*, no rate of refill. With no noun
// the row asserts nothing about who took what, which is what lets `full` be the honest reading of the
// ~98% of worlds nobody has ever worked.
private fun GameState.toDepositReading(at: GalaxyCoordinate, now: Instant): DepositReadingUiState =
    DepositReadingUiState(
        metal = "metal ${galaxy.stockLabel(at, ResourceKind.METAL, now)}",
        crystal = "crystal ${galaxy.stockLabel(at, ResourceKind.CRYSTAL, now)}",
    )

private fun GalaxyState.stockLabel(at: GalaxyCoordinate, gathering: ResourceKind, now: Instant): String {
    val cap = depositCap(at, gathering) ?: return "empty"
    val remaining = remaining(at, gathering, now)
    return when {
        remaining >= cap -> "full"
        remaining <= 0 -> "empty"
        else -> "${remaining.groupedByThousands()}/${cap.groupedByThousands()}"
    }
}

// "seismic instability · +1 danger", and "no hazards" when there are none — which is a fact worth
// printing rather than an absence worth hiding, because a clean world is the one you want to find.
//
// **It states its own contribution and never the total.** The other half is the distance band, which
// is astronomy and belongs to the system rather than to a world; a row printing `danger 2` could not
// say which half it came from. The comma between two hazards and the interpunct before the
// arithmetic is what keeps those two readable as different kinds of thing on one line.
private fun WorldTraits.fleetHazardLabel(): String {
    if (hazards.isEmpty()) return "no hazards"
    val named = hazards.sortedBy { it.ordinal }.joinToString(", ") { it.label() }
    return "$named$SEPARATOR+${hazards.size} danger"
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

// Internal since the dispatch sheet: the sheet heads itself with the coordinate the row it was
// raised from prints, and two copies of this would be two ways of writing one address.
internal fun GalaxyCoordinate.label(): String = "[$galaxy:$system:$slot]"

// Internal because three files in this module now join a list of facts with it — the row, the sheet
// and the astronomy line — and one screen writing "·" three different ways is the screen reading as
// three screens.
internal const val SEPARATOR = " · "

// Between a value and its unit, so a line that has to wrap never leaves "atm" alone on one.
private const val NBSP = ' '
