package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.design.format.milli
import dev.fardavide.oltre.client.design.format.toChipLabel
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
    // "195 units out · danger 1 from here · 58m out and back". **Stated once, under the system
    // header, because it is astronomy** — free, known from the first launch, and identical for all
    // fifteen slots of a system. Claude Design's call, 2026-08-10, and the argument is epistemic
    // rather than visual: a *row* printing a danger total could not say which half it came from, and
    // on an unsurveyed world — 98% of the map — it would be claiming knowledge nobody has paid for.
    // So the band lives here, the hazards live on the rows carrying their own arithmetic, and only
    // the dispatch sheet, where the number is actually spent, states the sum.
    //
    // It also hands the unsurveyed row its first honest fleet fact for free.
    val astronomy: String,
    // What replaced the ±1 stepper. `atFirstSystem` / `atLastSystem` went with it: a lens slides
    // rather than clipping, so there is no edge to disable a control at any more.
    val reach: ReachBandUiState,
    val map: SystemMapUiState,
    // Inside the map card rather than beside it — a probe targets a star, and the map is the only
    // star-scoped object on the screen.
    val probe: ProbeActionUiState,
    val bands: List<OrbitBandUiState>,
    // Null until a world row is tapped. The sheet is the feature's own navigation exactly as `at` is
    // — nothing outside this module has an opinion about which world is open — so it arrives here
    // rather than being a second screen the shell would have to know about.
    val dispatch: DispatchUiState?,
)

data class GalaxyTabUiState(val label: String, val galaxy: Int, val selected: Boolean)

// What the map draws: the system as orbits seen at a shallow angle, one ellipse per occupied slot.
//
// **This is the Sky pass's one real subtraction, and it is worth naming.** Until 0.3.0 the map was
// fifteen ticks on a horizontal line, and its whole argument was that it showed the *empty* slots —
// the shape of a system, and where its gaps fall — which the list underneath could never do. The
// orbit view cannot say that: eleven empty ellipses at 11dp apart read as noise rather than as
// absence. Davide took the trade knowingly (2026-08-10) for the picture the design is built around.
// What survives is the coordinate: an orbit's width is a function of its slot, so slot 13 is still
// visibly further out than slot 4, and the number under each body still names it.
//
// What went with the ticks is the Hot / Temperate / Cold strip. The bands are still the world list's
// section headings, so the vocabulary is not lost — but the map no longer teaches the axis by
// showing it, and that is the second half of the same trade.
data class SystemMapUiState(
    val bodies: List<MapBodyUiState>,
    // Only on the home system's map, and only while a probe is out: a flight leaves from where it
    // was launched, and every probe in this game is launched from home. Null everywhere else.
    val trajectory: MapTrajectoryUiState?,
)

data class MapBodyUiState(
    val slot: Int,
    val mark: MapMark,
    // Where this body's orbit sits between the innermost the map draws and the outermost: 0 for the
    // first body in the system, 1 for the last, evenly spaced in between. A fraction rather than a
    // length, because how wide the widest orbit is drawn is the Canvas's business.
    //
    // **Spaced by rank rather than by slot, and that is a real choice with a real cost.** Linear in
    // the slot was tried first and it is what the coordinate would deserve: slot 13 twice as far out
    // as slot 7, to scale. It does not survive contact with a real system. Fifteen slots across the
    // frame puts neighbours 11dp apart, and once every body sits on the same phase — which is what
    // stops them colliding with each other — an 11dp step is narrower than the number printed under
    // it, so a system holding worlds in slots 7 and 8 drew two overlapping discs under two
    // overlapping labels. Rank spacing gives every system the whole frame however many bodies it
    // holds, which is also what the design reference does: four worlds, four evenly spread orbits.
    //
    // What is kept: the order. Bodies are sorted by slot, so further out is still colder, and the
    // number under each one still says exactly which slot it is. What is lost is the *scale* — you
    // can no longer read the gap between two worlds off the picture, only their sequence.
    val orbit: Float,
)

// "[2:118] · 4h 12m" — where the probe is going and how long it has left, printed at the faint end
// of the arc that carries it.
data class MapTrajectoryUiState(val label: String)

// What a dot on the map means. `EMPTY` is a tick rather than a dot, and it is most of them.
enum class MapMark { EMPTY, HOME, OCCUPIED, UNSURVEYED, BLOCKED, BARREN, SETTLEABLE, RELAY }

data class OrbitBandUiState(val band: OrbitBand, val rows: List<WorldRowUiState>)

data class WorldRowUiState(
    val coordinate: String,
    val slot: Int,
    val band: OrbitBand,
    val verdict: VerdictUiState,
    // **Present exactly where a run is legal**, which is Claude Design's rule and not a coincidence:
    // absent on `Unsurveyed`, because a hold cannot be priced from a world nobody has looked at, and
    // absent on `Home` and `Occupied`, because a run there is refused outright. On the row rather
    // than inside a verdict so that `Settleable` needs no special case — one row shape, six verdicts.
    val deposits: DepositReadingUiState?,
)

// What is still in the ground, and the only pair of numbers on a world row since 0.9.
//
// **The stocks replaced the richnesses; they do not join them.** Richness was put on this row for the
// fleet at 0.4, and the fleet no longer reads it: the cap *is* richness times the danger multiplier,
// and strip time is the same everywhere, so what a run brings home is the stock and nothing else.
// Showing both would be one fact twice on the row that can least afford it — the header's children
// are all single-line and unwrappable, and two labelled fractions overflow the slot outright.
// Richness survives on the dispatch sheet's chip, where there is room to state it.
//
// **A word at each end and a fraction between**, which is Design's second decision: roughly 98% of
// rows have never been touched, so `full` keeps an untouched galaxy reading as a shape the eye skips
// rather than thirty figures it has to compare — and it is what makes `empty` and a working fraction
// legible at a glance down fifteen rows. The fraction is the app's own `84/163 fields` idiom, so no
// new form arrives, and the denominator is the only place a cap is ever visible on the map.
data class DepositReadingUiState(val metal: String, val crystal: String)

// One row, six verdicts and a relay that is not one.
//
// **Treatment 1b, accepted 2026-08-10, and one rule generates all six: a row leads with what you can
// do about it today.** `Blocked` and `Barren` lead with *richness*, because their verdict is not an
// offer — you cannot live there and nothing about the technology you lack changes this week. What
// you *can* do is send a hold, so the two numbers that price a hold take the headline. The other
// four lead with the verdict, because for them the verdict is the offer.
//
// The blockage is not deleted. It drops to the line below and becomes the opening clause of a
// sentence — "Blocked · gravity 1.79, you tolerate 1.40 g" — keeping its axis, its band, its accent
// technology and its tap into Research. What it stops being is a *badge*, so that a row never
// carries two label-shaped states at once. The diagnosis is the part worth keeping: the contradiction
// was never BLOCKED-against-a-fleet-offer, it was two claims set as verdicts in the same weight in
// the same slot.
//
// **What this subtracted, stated plainly because it was content that shipped:** `Blocked` lost its
// yield and its "Fails 2 of 3 bands, worth it at 0.92" calibration line, and `Home` lost its yield
// and its hazards. Both are Design's call and both are Davide's to overrule — the argument is that a
// settler's yield is not what you can act on today, and the row now has a fleet reading in that slot
// that you can.
sealed interface VerdictUiState {

    // The reference row, and the one card in the app that is lit rather than plain. Its three axes
    // are what every blocked row's "you tolerate" is measured against.
    data class Home(val note: String) : VerdictUiState

    data class Occupied(val note: String) : VerdictUiState

    // 98% of the map, and it says nothing about hazards **because it knows nothing about them** — a
    // row that leaked a trait would have performed the survey the player has not paid for. The
    // astronomy line above the list already gave it the half that is free.
    data object Unsurveyed : VerdictUiState

    // Never empty, and in `HostilityAxis` order rather than by the size of the gap, so the third
    // line is in the same place on every three-axis world.
    data class Blocked(
        val reading: FleetReadingUiState,
        val failures: List<BlockedAxisUiState>,
    ) : VerdictUiState {
        init {
            require(failures.isNotEmpty()) { "a blocked row must name at least one axis" }
        }
    }

    // The same shape as `Blocked` with one clause instead of a list: Barren fails no band at all, it
    // fails the *bar*, so its one line is the yield against the threshold. Naming the threshold is
    // what makes a run of Barren answers read as calibration rather than as bad luck — and Barren is
    // designed to be a common answer.
    data class Barren(val reading: FleetReadingUiState, val threshold: String) : VerdictUiState

    // The rarest verdict in the game and the only one that is still an offer to a settler, so it
    // keeps its badge and leads with the verdict like the other three.
    data class Settleable(val note: String) : VerdictUiState

    // Not a world, and not tappable. It states its effect and stops. No holding mechanic exists
    // until multiplayer, and a relay has no hold for a fleet to fill either.
    data class Relay(val effect: String) : VerdictUiState
}

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

// The hazards that will be taken out of a hold and how long the trip is. **It lost the richness pair
// at 0.9** — see `DepositReadingUiState`, which took the job of saying what a world is worth to a
// fleet and says it in the units a run is actually clamped by.
//
// **The hazards carry their own arithmetic and never a total** — "seismic instability · +1 danger",
// never "danger 2". The other half of that sum is the distance band, which is astronomy and is
// stated once under the system header; only the dispatch sheet, where the number is spent, adds them
// up. See `GalaxyUiState.astronomy`.
data class FleetReadingUiState(
    val hazards: String,
    val reach: String,
)

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
