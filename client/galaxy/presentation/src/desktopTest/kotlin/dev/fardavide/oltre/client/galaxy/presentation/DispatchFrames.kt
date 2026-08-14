package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.galaxy.ui.GalaxyUiState
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.StartRunResult
import dev.fardavide.oltre.core.startRun
import dev.fardavide.oltre.core.relayAt
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.WorldVerdict
import dev.fardavide.oltre.core.verdictFor
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.hours
import dev.fardavide.oltre.core.worldAt

// The dispatch sheet in each of the states it can be raised in — **derived from a real `GameState`
// through the real mapper**, like every other frame since 0.11. See `GalaxyFrames` and the three
// thousand lines of stated fixtures it replaced.

// A world in the home system a run may actually be sent to. Read off the seed rather than written
// down: a run's legality is `startRun`'s rule and not this file's guess — home is refused and so is
// a world somebody holds, so the slot is whichever one is neither.
internal val RUNNABLE_SLOT: Int = frameState.let { state ->
    val home = state.galaxy.home
    (1..GalaxyBalance.SLOTS_PER_SYSTEM).first { slot ->
        val at = GalaxyCoordinate(galaxy = home.galaxy, system = home.system, slot = slot)
        val world = worldAt(state.galaxy.seed, at)
        world != null && verdictFor(world, state).let { it !is WorldVerdict.Home && it !is WorldVerdict.Occupied }
    }
}

// An unsurveyed world, where the sheet's whole job is to refuse and offer a probe instead.
// **It cannot be in the home system**: genesis surveys that whole system, so every world in it is
// known from the first launch — which is the fact the ledger's genesis frame is built on.
private val unsurveyedSystem: SystemSelection = frameState.neighbourSelection()

private val UNSURVEYED_SLOT: Int = frameState.let { state ->
    (1..GalaxyBalance.SLOTS_PER_SYSTEM).first { slot ->
        val at = GalaxyCoordinate(
            galaxy = unsurveyedSystem.galaxy,
            system = unsurveyedSystem.system,
            slot = slot,
        )
        worldAt(state.galaxy.seed, at) != null && at !in state.galaxy.surveyed
    }
}

private fun sheet(
    state: GameState = frameState,
    slot: Int = RUNNABLE_SLOT,
    selection: SystemSelection = state.homeSelection(),
    gathering: ResourceKind? = null,
    ships: Int? = null,
    window: kotlin.time.Duration? = null,
): GalaxyUiState = frame(
    state = state,
    view = GalaxyView.SYSTEM,
    at = selection,
    dispatch = DispatchSelection(slot = slot, gathering = gathering, ships = ships, window = window),
)

internal val dispatchOfferUiState: GalaxyUiState = sheet()

internal val dispatchUnsurveyedUiState: GalaxyUiState =
    sheet(slot = UNSURVEYED_SLOT, selection = unsurveyedSystem)

// Every hull away, so the sheet has nothing to commit **and can say when one is back** — which is
// the whole of the refusal. An empty fleet would refuse too and have no date to give, so the state
// is built by actually sending the skiff rather than by deleting it.
internal val dispatchNoShipsUiState: GalaxyUiState = frameState.let { state ->
    val target = GalaxyCoordinate(
        galaxy = state.galaxy.home.galaxy,
        system = state.galaxy.home.system,
        slot = RUNNABLE_SLOT,
    )
    val away = assertIs<StartRunResult.Started>(
        startRun(
            state = state,
            target = target,
            gathering = ResourceKind.METAL,
            ships = state.ships,
            window = 6.hours,
            at = FIXTURE_NOW,
        ),
    ).state
    sheet(state = away)
}

// More hulls asked for than the idle pool holds: the sheet clamps rather than refusing, because the
// number it shows is the number that would really go.
internal val dispatchClampedUiState: GalaxyUiState = sheet(ships = 99)

// A fleet big enough that what it would lift is more than the world currently holds — so the sheet
// says it is taking *the whole deposit* rather than printing a figure the ground cannot supply.
internal val dispatchWholeDepositUiState: GalaxyUiState =
    sheet(state = frameState.copy(ships = Ships.of(ShipType.SKIFF, 40)), window = 24.hours)

// A world already stripped, so the offer is a wait rather than a haul.
internal val dispatchWaitingUiState: GalaxyUiState = frameState.let { state ->
    val target = GalaxyCoordinate(
        galaxy = state.galaxy.home.galaxy,
        system = state.galaxy.home.system,
        slot = RUNNABLE_SLOT,
    )
    val emptied = state.galaxy.withTaken(
        target = target,
        gathering = ResourceKind.METAL,
        taken = state.galaxy.remaining(target, ResourceKind.METAL, FIXTURE_NOW),
        at = FIXTURE_NOW,
    )
    sheet(state = state.copy(galaxy = emptied), gathering = ResourceKind.METAL)
}

// Asked for more than the world can *ever* hold — the one case with no date to give, because the ask
// is bigger than the world rather than sooner than the refill. A cap is a function of the world, so
// the only way past it is a fleet larger than the planet.
internal val dispatchWaitingForeverUiState: GalaxyUiState = frameState.let { state ->
    val target = GalaxyCoordinate(
        galaxy = state.galaxy.home.galaxy,
        system = state.galaxy.home.system,
        slot = RUNNABLE_SLOT,
    )
    val emptied = state.galaxy.withTaken(
        target = target,
        gathering = ResourceKind.METAL,
        taken = state.galaxy.remaining(target, ResourceKind.METAL, FIXTURE_NOW),
        at = FIXTURE_NOW,
    )
    sheet(
        state = state.copy(galaxy = emptied, ships = Ships.of(ShipType.SKIFF, 400)),
        gathering = ResourceKind.METAL,
        window = 24.hours,
    )
}

// A world part-worked, so the fraction is the reading rather than a word at either end.
internal val dispatchWorkedUiState: GalaxyUiState = frameState.let { state ->
    val target = GalaxyCoordinate(
        galaxy = state.galaxy.home.galaxy,
        system = state.galaxy.home.system,
        slot = RUNNABLE_SLOT,
    )
    val half = state.galaxy.remaining(target, ResourceKind.METAL, FIXTURE_NOW) / 2
    sheet(
        state = state.copy(
            galaxy = state.galaxy.withTaken(
                target = target,
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
    val slot = (1..GalaxyBalance.SLOTS_PER_SYSTEM).first { slot ->
        worldAt(state.galaxy.seed, GalaxyCoordinate(far.galaxy, far.system, slot)) != null
    }
    val surveyed = state.galaxy.copy(
        surveyed = state.galaxy.surveyed +
            (1..GalaxyBalance.SLOTS_PER_SYSTEM)
                .map { GalaxyCoordinate(far.galaxy, far.system, it) }
                .filter { worldAt(state.galaxy.seed, it) != null },
    )
    sheet(
        state = state.copy(galaxy = surveyed, ships = Ships.of(ShipType.SKIFF, 4)),
        slot = slot,
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

internal val relaySlot: Int = checkNotNull(
    relayAt(frameState.galaxy.seed, relaySystem.galaxy, relaySystem.system),
).slot

internal val relaySystemUiState: GalaxyUiState = frame(view = GalaxyView.SYSTEM, at = relaySystem)
