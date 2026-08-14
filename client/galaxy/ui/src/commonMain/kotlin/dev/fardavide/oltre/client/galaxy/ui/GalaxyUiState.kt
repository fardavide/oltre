package dev.fardavide.oltre.client.galaxy.ui

import dev.fardavide.oltre.core.AdaptationTechnology

// **What the Galaxy tab draws, and nothing about how it is derived.** The four mappers that
// produce these — the page, the ruler, the probe footer and the dispatch sheet — are
// `:client:galaxy:presentation`, which depends on this module rather than the other way round.

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
