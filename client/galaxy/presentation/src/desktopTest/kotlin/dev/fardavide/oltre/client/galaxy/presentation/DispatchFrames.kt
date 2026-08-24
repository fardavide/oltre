package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.dispatch.presentation.DispatchSelection
import dev.fardavide.oltre.client.galaxy.ui.GalaxyUiState
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.StartRunResult
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.WorldVerdict
import dev.fardavide.oltre.core.relayAt
import dev.fardavide.oltre.core.startRun
import dev.fardavide.oltre.core.verdictFor
import dev.fardavide.oltre.core.worldAt
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.hours

// The dispatch sheet in each of the states it can be raised in — **derived from a real `GameState`
// through the real mapper**, like every other frame since 0.11. See `GalaxyFrames` and the three
// thousand lines of stated fixtures it replaced.

// A world in the home system a run may actually be sent to. Read off the seed rather than written
// down: a run's legality is `startRun`'s rule and not this file's guess — home is refused and so is
// a world somebody holds, so it is whichever world is neither.
internal val RUNNABLE: GalaxyCoordinate = frameState.let { state ->
    val home = state.galaxy.home
    (1..GalaxyBalance.SLOTS_PER_SYSTEM)
        .map { slot -> GalaxyCoordinate(galaxy = home.galaxy, system = home.system, slot = slot) }
        .first { at ->
            val world = worldAt(state.galaxy.seed, at)
            world != null && verdictFor(world, state).let { it !is WorldVerdict.Home && it !is WorldVerdict.Occupied }
        }
}

// An unsurveyed world, where the sheet's whole job is to refuse and offer a probe instead.
// **It cannot be in the home system**: genesis surveys that whole system, so every world in it is
// known from the first launch — which is the fact the ledger's genesis frame is built on.
private val unsurveyedSystem: SystemSelection = frameState.neighbourSelection()

private val UNSURVEYED: GalaxyCoordinate = frameState.let { state ->
    (1..GalaxyBalance.SLOTS_PER_SYSTEM)
        .map { slot ->
            GalaxyCoordinate(
                galaxy = unsurveyedSystem.galaxy,
                system = unsurveyedSystem.system,
                slot = slot,
            )
        }
        .first { at -> worldAt(state.galaxy.seed, at) != null && at !in state.galaxy.surveyed }
}

private fun sheet(
    state: GameState = frameState,
    target: GalaxyCoordinate = RUNNABLE,
    selection: SystemSelection = state.homeSelection(),
    gathering: ResourceKind? = null,
    ships: Int? = null,
    window: kotlin.time.Duration? = null,
): GalaxyUiState = frame(
    state = state,
    view = GalaxyView.SYSTEM,
    at = selection,
    dispatch = DispatchSelection(at = target, gathering = gathering, ships = ships, window = window),
)

internal val dispatchOfferUiState: GalaxyUiState = sheet()

// The same sheet with the bell lit, which is the one visual state the offer has that the frame above
// does not photograph. It is a *whole colony* difference rather than a world one — `announceFlights`
// is the standing position of the control — so it is set on the state rather than passed to `sheet`.
internal val dispatchAnnouncedUiState: GalaxyUiState = sheet(state = frameState.copy(announceFlights = true))

// **The same sheet with no bell at all**, which is what a colony opened after 0.18 actually sees: it
// asks about alerts by kind, so a run is announced by *Fleet returns* and this control has nothing
// left to decide. It is a whole-colony difference like the frame above it, and it is the state that
// ships — the two frames above describe a save carried forward from 0.17.
internal val dispatchByCategoryUiState: GalaxyUiState = sheet(state = byCategoryGameState)

internal val dispatchUnsurveyedUiState: GalaxyUiState =
    sheet(target = UNSURVEYED, selection = unsurveyedSystem)

// Every hull away, so the sheet has nothing to commit **and can say when one is back** — which is
// the whole of the refusal. An empty fleet would refuse too and have no date to give, so the state
// is built by actually sending the skiff rather than by deleting it.
//
// **Every hull *with a hold*, and the distinction arrived with the scout.** This used to send
// `state.ships` outright, which was the same thing while every hull in the pool could gather; a
// manifest carrying the fixture's scout is now refused at the door by `NotAGatheringHull`, and it is
// refused rather than stripped precisely so a fixture cannot drift into describing a fleet nobody
// chose. The sheet counts skiffs, so skiffs are what have to be away for it to have nothing left.
internal val dispatchNoShipsUiState: GalaxyUiState = frameState.let { state ->
    val away = assertIs<StartRunResult.Started>(
        startRun(
            state = state,
            target = RUNNABLE,
            gathering = ResourceKind.METAL,
            ships = Ships.of(ShipType.SKIFF, state.ships.countOf(ShipType.SKIFF)),
            window = 6.hours,
            at = FIXTURE_NOW,
        ),
    ).state
    sheet(state = away)
}

// More hulls asked for than the idle pool holds: the sheet clamps rather than refusing, because the
// number it shows is the number that would really go.
//
// **The fleet has to be bigger than one for that to be observable**, and it was not: genesis grants
// a single skiff, so `hulls = (selection.ships ?: idle).coerceIn(1, idle)` resolved 99 and null to
// the same 1 and this frame rendered the plain offer byte for byte. A screenshot recorded from it
// was identical to `galaxy_dispatch.png`, and its behaviour test would have passed against the offer
// — a fixture named for a mechanism it could not exercise.
internal val dispatchClampedUiState: GalaxyUiState =
    sheet(state = frameState.copy(ships = Ships.of(ShipType.SKIFF, 6)), ships = 99)

// A fleet big enough that what it would lift is more than the world currently holds — so the sheet
// says it is taking *the whole deposit* rather than printing a figure the ground cannot supply.
//
// **The manifest is stated rather than defaulted since 0.13.1**, and that is the frame keeping its
// subject: the sheet now opens on the fleet that empties the vein, so a blank count here would
// resolve to a manifest with nothing wasted and this would quietly become a picture of the plain
// offer — the same trap `dispatchClampedUiState` fell into at 0.9 and records below.
internal val dispatchWholeDepositUiState: GalaxyUiState =
    sheet(state = frameState.copy(ships = Ships.of(ShipType.SKIFF, 40)), ships = 40, window = 24.hours)

// The pool the suggestion is *about*: 40 idle hulls at a world a handful can empty, with the count
// left blank so the mapper fills it in. Both steppers are live here and nowhere else — the offer
// frame owns one hull, and the two clamped frames are pinned at the top of their pool — which is
// what the hold-to-repeat assertions need to be assertions about anything.
internal val dispatchSuggestedUiState: GalaxyUiState =
    sheet(state = frameState.copy(ships = Ships.of(ShipType.SKIFF, 40)), window = 24.hours)

// A world already stripped, so the offer is a wait rather than a haul.
internal val dispatchWaitingUiState: GalaxyUiState = frameState.let { state ->
    val emptied = state.galaxy.withTaken(
        target = RUNNABLE,
        gathering = ResourceKind.METAL,
        taken = state.galaxy.remaining(RUNNABLE, ResourceKind.METAL, FIXTURE_NOW),
        at = FIXTURE_NOW,
    )
    sheet(state = state.copy(galaxy = emptied), gathering = ResourceKind.METAL)
}

// Asked for more than the world can *ever* hold — the one case with no date to give, because the ask
// is bigger than the world rather than sooner than the refill. A cap is a function of the world, so
// the only way past it is a fleet larger than the planet.
internal val dispatchWaitingForeverUiState: GalaxyUiState = frameState.let { state ->
    val emptied = state.galaxy.withTaken(
        target = RUNNABLE,
        gathering = ResourceKind.METAL,
        taken = state.galaxy.remaining(RUNNABLE, ResourceKind.METAL, FIXTURE_NOW),
        at = FIXTURE_NOW,
    )
    sheet(
        state = state.copy(galaxy = emptied, ships = Ships.of(ShipType.SKIFF, 400)),
        gathering = ResourceKind.METAL,
        // Stated for `dispatchWholeDepositUiState`'s reason, and here it is the whole subject: a
        // stripped world has nothing to empty, so the suggestion on one is a single hull — which is
        // the *soonest* date there is rather than the "never" this frame exists to draw.
        ships = 400,
        window = 24.hours,
    )
}

// A world part-worked, so the fraction is the reading rather than a word at either end.
internal val dispatchWorkedUiState: GalaxyUiState = frameState.let { state ->
    val half = state.galaxy.remaining(RUNNABLE, ResourceKind.METAL, FIXTURE_NOW) / 2
    sheet(
        state = state.copy(
            galaxy = state.galaxy.withTaken(
                target = RUNNABLE,
                gathering = ResourceKind.METAL,
                taken = half,
                at = FIXTURE_NOW,
            ),
        ),
        gathering = ResourceKind.METAL,
    )
}

// Another galaxy: the ladder narrows to the rungs that still leave time on station, which is how
// distance teaches itself without a word of copy.
internal val dispatchFarUiState: GalaxyUiState = frameState.let { state ->
    val far = SystemSelection(
        galaxy = if (state.galaxy.home.galaxy == GalaxyBalance.GALAXIES) 1 else state.galaxy.home.galaxy + 1,
        system = state.galaxy.home.system,
    )
    val target = (1..GalaxyBalance.SLOTS_PER_SYSTEM)
        .map { slot -> GalaxyCoordinate(far.galaxy, far.system, slot) }
        .first { worldAt(state.galaxy.seed, it) != null }
    // **Charted as well as surveyed, because a survey implies a landing.** Writing one without the
    // other builds a state the game cannot reach — and since 0.19 the page under the sheet would say
    // `UNCHARTED` while the sheet on top of it named the world and printed its richness.
    val surveyed = state.galaxy.copy(
        surveyed = state.galaxy.surveyed +
            (1..GalaxyBalance.SLOTS_PER_SYSTEM)
                .map { GalaxyCoordinate(far.galaxy, far.system, it) }
                .filter { worldAt(state.galaxy.seed, it) != null },
    ).withCharted(SystemAddress(galaxy = far.galaxy, system = far.system))
    sheet(
        state = state.copy(galaxy = surveyed, ships = Ships.of(ShipType.SKIFF, 4)),
        target = target,
        selection = far,
    )
}

// The system view of the home system, for the tests that need a page without a sheet on it.
internal val homeSystemUiState: GalaxyUiState = frame(view = GalaxyView.SYSTEM)

// A system carrying a relay, found rather than invented: one system in forty has one, and the row it
// draws is the only one on the screen that is not a card and not tappable.
private val relaySystem: SystemSelection = frameState.let { state ->
    val home = state.galaxy.home
    val system = (1..GalaxyBalance.SYSTEMS_PER_GALAXY)
        .first { relayAt(state.galaxy.seed, home.galaxy, it) != null }
    SystemSelection(galaxy = home.galaxy, system = system)
}

internal val relayCoordinate: GalaxyCoordinate = checkNotNull(
    relayAt(frameState.galaxy.seed, relaySystem.galaxy, relaySystem.system),
)

// **Charted, because a relay is a charted fact.** The first relay in the home galaxy is at system
// 16, which a genesis colony's hour of grace does not reach — and since 0.19 an orbit page the light
// has not reached draws no bodies at all, relay included. So the frame states the landing that makes
// the point of interest visible rather than asserting one the game would not draw.
internal val relaySystemUiState: GalaxyUiState = frame(
    state = frameState.copy(
        galaxy = frameState.galaxy.withCharted(
            SystemAddress(galaxy = relaySystem.galaxy, system = relaySystem.system),
        ),
    ),
    view = GalaxyView.SYSTEM,
    at = relaySystem,
)


// ── *Twice the Flight*: the two-hull picker, in the three states Design drew ─────────────────
//
// **The pool is one hauler and two idle skiffs throughout**, which is Design's own fixture — and it
// is the smallest fleet in which the control exists at all: a second hull *type* is what creates a
// berth, a clock and a cell, and one of each is what a control needs to be a control.
//
// **Held at Propulsion 1, which is the speed the design was drawn against.** It computed its figures
// from `10 + u/10`, the curve 0.14 shipped, and 0.15 halved the base — so drive 1 is what makes
// these frames the frames Design published rather than the same shape with every number doubled. It
// is also an ordinary colony on day 21, which is the premise its own provenance note states.
internal val TWO_HULL_STATE: GameState = frameState.copy(
    ships = Ships(mapOf(ShipType.HAULER to 1, ShipType.SKIFF to 2)),
    research = frameState.research.withLevel(Technology.PROPULSION, TechLevel(1)),
)

// **a · the default, at the doorstep.** Six berths is the whole idle pool, because no manifest
// empties a full vein inside 3h — so the stepper opens at the top of its range and the `+` dims.
internal val dispatchPickerUiState: GalaxyUiState = sheet(state = TWO_HULL_STATE)

// **b · after the skiff cell is tapped.** The cell said two skiffs while the stepper said six, so
// the clamp to two berths was printed before the tap — which is what makes it not a dead control.
internal val dispatchPickerSkiffsUiState: GalaxyUiState = sheet(state = TWO_HULL_STATE, ships = 2)

// **the ladder narrowed by distance, 69 systems out.** 1h is already absent there for any hull, and
// the hauler's 3h 36m round trip needs 6h — so 3h is drawn *locked*, at 42%, with `skiffs` under it.
// That is the whole of Design's second ruling: absent means never, dim means not with these hulls.
// Read off the seed rather than written down, exactly as `RUNNABLE` is: 69 systems out is Design's
// distance, but *which slot there holds a world* is the generator's business and not this file's.
private val FAR: GalaxyCoordinate = TWO_HULL_STATE.let { state ->
    val system = state.galaxy.home.system + 69
    (1..GalaxyBalance.SLOTS_PER_SYSTEM)
        .map { slot -> GalaxyCoordinate(galaxy = state.galaxy.home.galaxy, system = system, slot = slot) }
        .first { at -> worldAt(state.galaxy.seed, at) != null }
}

// Charted with it, for the reason `dispatchFarUiState` above states: a world you may send a run to
// is a world a hull has already been to, so the light reaches it by construction.
private val FAR_SURVEYED: GameState = TWO_HULL_STATE.copy(
    galaxy = TWO_HULL_STATE.galaxy
        .copy(surveyed = TWO_HULL_STATE.galaxy.surveyed + FAR)
        .withCharted(SystemAddress(galaxy = FAR.galaxy, system = FAR.system)),
)

internal val dispatchPickerNarrowedUiState: GalaxyUiState = sheet(
    state = FAR_SURVEYED,
    target = FAR,
    selection = SystemSelection(FAR.galaxy, FAR.system),
    ships = 2,
)

// **the rung just went.** The player was on 3h with skiffs and took the hauler, so the rung it
// cannot fly is gone from under their finger and the selection moved *up* to 6h — the only direction
// available, since a window too short for a flight is too short for every shorter one. One line in
// body weight says the hauler did it, and the dim 3h is the undo.
internal val dispatchPickerMovedUiState: GalaxyUiState = sheet(
    state = FAR_SURVEYED,
    target = FAR,
    selection = SystemSelection(FAR.galaxy, FAR.system),
    ships = 6,
    window = 3.hours,
)


// **c · the clamp, on a part-worked vein.** Design's third form of the note under the cells, and the
// one its own default rule points at: *"on a part-worked vein it is already live: 620 left at
// Calianova VIII defaults to the hauler alone, with the two skiffs staying home."*
//
// The vein is worked down until one hauler empties it, so the default packs the hauler and leaves
// the skiffs at home — and the note stops being a counterfactual and becomes the clamp, which wins
// over it because it is about the run being sent rather than one that is not.
internal val dispatchPickerClampedUiState: GalaxyUiState = TWO_HULL_STATE.let { state ->
    val whole = state.galaxy.remaining(RUNNABLE, ResourceKind.METAL, FIXTURE_NOW)
    val worked = state.galaxy.withTaken(
        target = RUNNABLE,
        gathering = ResourceKind.METAL,
        // All but a sliver, so a single hauler's hold is more than the ground can supply.
        taken = whole - whole / 12,
        at = FIXTURE_NOW,
    )
    sheet(state = state.copy(galaxy = worked), gathering = ResourceKind.METAL)
}
