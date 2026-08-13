package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.FleetRun
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GalaxyState
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.worldAt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// Two of these come from the real generator and one does not, and the split is deliberate.
//
// The research module freezes its fixtures by hand so that a baseline moves only when the *screen*
// moves. That reasoning does not carry here: the galaxy's constants are themselves pinned, value by
// value, by `GalaxyBalanceTest` and `GalaxyDistributionTest`, so a change that moves these numbers
// is a design decision that *should* redraw the images. Hand-writing them bought nothing and cost
// something real — the first version of this file drifted from the mapper's own formatting within
// an hour, and the screenshots quietly rendered numbers the app would never produce.
//
// `everyVerdictUiState` stays hand-written because it has to: under *this* seed the home system is
// Home plus one Barren plus five Blocked, so Settleable, Occupied and a relay have no example to
// draw from here. **There is no such thing as the shipped seed** — the shell mints one per colony
// from the instant it was founded, so every player's galaxy is different, and a suite that only
// covered what one seed happens to generate would be covering an accident. The mapper's own Barren and
// Settleable branches are exercised against real generated worlds in `GalaxyUiStateTest`, which is
// where that belongs; this fixture exists so the *screen* has all seven states in one frame.

private val galaxy: GalaxyState = GalaxyState.initial(GalaxySeed(20_260_807))

// A colony that has researched nothing, which is what the three generated frames below describe:
// the mapper reads the empire's adaptation levels now, so it needs the whole state rather than the
// map half of it.
private val state: GameState = GameState.initial(galaxy.seed)

// Frozen, because the footer runs a countdown now and a baseline recorded against a wall clock
// would differ from itself every second. Epoch in UTC, so a landing time on a frame is arithmetic
// rather than a fact about the machine that recorded it.
internal val FIXTURE_NOW: Instant = Instant.fromEpochMilliseconds(0)

// The home system exactly as generated. At 0.5.1 that became seven worlds rather than four, and
// one of them is `Barren` rather than `Blocked` — genesis now starts a colony in a system that has
// somewhere to go, so the opening screen is a shopping list from the first frame instead of a wall.
// The five that are still blocked all out-yield the world the player is standing on, which is the
// pillar landing: the good ground is behind the technology nobody has bought yet.
internal val homeSystemUiState: GalaxyUiState = state.toGalaxyUiState(
    at = SystemSelection(galaxy = galaxy.home.galaxy, system = galaxy.home.system),
    now = FIXTURE_NOW,
    timeZone = TimeZone.UTC,
)

// The neighbour, and every system but one at ship time.
internal val unsurveyedSystemUiState: GalaxyUiState = state.toGalaxyUiState(
    at = SystemSelection(galaxy = galaxy.home.galaxy, system = galaxy.home.system - 1),
    now = FIXTURE_NOW,
    timeZone = TimeZone.UTC,
)

// The first system of the first galaxy: the one place a centred lens has to slide rather than
// centre, and the one origin whose ruler is one-sided.
internal val edgeOfTheGalaxyUiState: GalaxyUiState = state.toGalaxyUiState(
    at = SystemSelection(galaxy = 1, system = 1),
    now = FIXTURE_NOW,
    timeZone = TimeZone.UTC,
)

// The real mapping of the coordinate the hand-written frame below claims to be at, used for the two
// halves of that frame which are functions of *place* rather than of verdict.
private val elsewhereUiState: GalaxyUiState = state.toGalaxyUiState(
    at = SystemSelection(galaxy = 2, system = 118),
    now = FIXTURE_NOW,
    timeZone = TimeZone.UTC,
)

// The precedence, top to bottom, including the four states this seed's home system cannot show.
internal val everyVerdictUiState = GalaxyUiState(
    galaxies = (1..4).map { GalaxyTabUiState(label = "G$it", galaxy = it, selected = it == 2) },
    scope = "250 systems",
    coordinate = "2:118",
    detail = "BRIGHT · 6 worlds",
    compactDetail = "BRIGHT · 6",
    isHome = false,
    // Mapped for *this* coordinate rather than borrowed from another frame, and that matters even
    // though only the verdicts are under test here: the band, the footer and the astronomy line are
    // all functions of where you are standing, so a frame headed 2:118 carrying galaxy 3's ruler and
    // the flight time to somewhere else would be a baseline asserting a screen that cannot exist.
    // None of them reads a verdict, so taking them from the real mapper costs this fixture nothing.
    astronomy = elsewhereUiState.astronomy,
    reach = elsewhereUiState.reach,
    probe = elsewhereUiState.probe,
    map = bodies(
        3 to MapMark.RELAY,
        4 to MapMark.HOME,
        5 to MapMark.OCCUPIED,
        6 to MapMark.UNSURVEYED,
        8 to MapMark.BLOCKED,
        9 to MapMark.BARREN,
        11 to MapMark.SETTLEABLE,
    ),
    bands = listOf(
        OrbitBandUiState(
            band = OrbitBand.HOT,
            rows = listOf(
                WorldRowUiState(
                    coordinate = "[2:118:3]",
                    slot = 3,
                    band = OrbitBand.HOT,
                    verdict = VerdictUiState.Relay(effect = "+18% range while held"),
                    deposits = null,
                ),
            ),
        ),
        OrbitBandUiState(
            band = OrbitBand.TEMPERATE,
            rows = listOf(
                WorldRowUiState(
                    coordinate = "[2:118:4]",
                    slot = 4,
                    band = OrbitBand.TEMPERATE,
                    verdict = VerdictUiState.Home(note = homeNote()),
                    deposits = null,
                ),
                WorldRowUiState(
                    coordinate = "[2:118:5]",
                    slot = 5,
                    band = OrbitBand.TEMPERATE,
                    verdict = VerdictUiState.Occupied(note = "Held by kepler"),
                    deposits = null,
                ),
                WorldRowUiState(
                    coordinate = "[2:118:6]",
                    slot = 6,
                    band = OrbitBand.TEMPERATE,
                    verdict = VerdictUiState.Unsurveyed,
                    deposits = null,
                ),
                WorldRowUiState(
                    coordinate = "[2:118:8]",
                    slot = 8,
                    band = OrbitBand.TEMPERATE,
                    // Locked behind a ladder nobody has bought, and worked twice already — which is
                    // the 0.9 row in one card: the verdict word is back in the header, and what you
                    // can do about this world today is the pair of stocks on the line below it.
                    verdict = VerdictUiState.Blocked(
                        reading = FleetReadingUiState(
                            hazards = "ion storms · +1 danger",
                            reach = "1h 12m out and back",
                        ),
                        failures = listOf(blocked("gravity", "2.40", "1.40", "g", AdaptationTechnology.GRAVITIC, 9)),
                    ),
                    deposits = DepositReadingUiState(metal = "metal full", crystal = "crystal full"),
                ),
                WorldRowUiState(
                    coordinate = "[2:118:9]",
                    slot = 9,
                    band = OrbitBand.TEMPERATE,
                    verdict = VerdictUiState.Barren(
                        reading = FleetReadingUiState(
                            hazards = "tidally locked · +1 danger",
                            reach = "1h 14m out and back",
                        ),
                        threshold = "yield 0.81, worth it at 0.92",
                    ),
                    deposits = DepositReadingUiState(metal = "metal full", crystal = "crystal full"),
                ),
            ),
        ),
        OrbitBandUiState(
            band = OrbitBand.COLD,
            rows = listOf(
                WorldRowUiState(
                    coordinate = "[2:118:11]",
                    slot = 11,
                    band = OrbitBand.COLD,
                    verdict = VerdictUiState.Settleable(
                        note = "Yield 1.12 · metal 1.21 · crystal 0.88 · 163 fields",
                    ),
                    deposits = DepositReadingUiState(metal = "metal full", crystal = "crystal full"),
                ),
            ),
        ),
    ),
    dispatch = null,
)

// ── The six states of the card footer ────────────────────────────────────────────────────────
//
// One frame each, on the unsurveyed neighbour, because what the footer says is the only thing that
// changes between them and a baseline that also moved the world list would be asserting two things
// at once. `available` and `known` come from the real mapper — a fresh colony can afford its first
// probe and its home system was surveyed at genesis — and the other four are written out, because
// reaching them from a `GameState` means dispatching, advancing a clock and landing on a system the
// seed happens to have stocked the right way.

internal val probeUnaffordableUiState: GalaxyUiState = unsurveyedSystemUiState.copy(
    probe = ProbeActionUiState.Unaffordable(
        offer = shortOffer(),
        // The tightest reading on the screen: two durations on one row, told apart by side, by
        // colour and by the preposition.
        availableIn = "in 1h 06m",
    ),
)

internal val probeInFlightUiState: GalaxyUiState = unsurveyedSystemUiState.copy(
    probe = ProbeActionUiState.InFlight(countdown = "00:47:12", lands = "lands 12:20", progressPercent = 43),
)

// The answer about fifty-nine dispatches in sixty, and the one this frame exists to make ordinary.
internal val probeLandedUiState: GalaxyUiState = homeSystemUiState.copy(
    probe = ProbeActionUiState.Landed(
        landedAt = "Probe landed 12:20",
        summary = "5 worlds surveyed",
        find = "none settleable",
        findKind = ProbeFindKind.NONE,
    ),
)

// Green once, and only on the count. The state the whole verb exists for.
internal val probeSettleableUiState: GalaxyUiState = homeSystemUiState.copy(
    probe = ProbeActionUiState.Landed(
        landedAt = "Probe landed 09:04",
        summary = "3 worlds surveyed",
        find = "1 settleable",
        findKind = ProbeFindKind.SETTLEABLE,
    ),
)

// The middle tier: neither green nor red, because it is worth reading and not worth acting on.
internal val probeNearMissUiState: GalaxyUiState = homeSystemUiState.copy(
    probe = ProbeActionUiState.Landed(
        landedAt = "Probe landed 21:47",
        summary = "4 worlds surveyed",
        find = "1 blocked at one axis",
        findKind = ProbeFindKind.NEAR_MISS,
    ),
)

// One system in 390: a star with nothing round it, and a card that refuses the sale in the words of
// the thing above it. The world list under it is empty too, which is the frame's other half — and
// since the orbit view draws one ellipse per body, an empty system is now literally an empty sky.
internal val probeNothingToSurveyUiState: GalaxyUiState = unsurveyedSystemUiState.copy(
    probe = ProbeActionUiState.NothingToSurvey(note = "15 empty slots · nothing to survey"),
    map = SystemMapUiState(bodies = emptyList(), trajectory = null),
    bands = emptyList(),
)

// The neighbour's real offer with the chip reddened — 150 metal and the flight the app really
// computes, and only the affordability flipped. Taken from the mapper rather than typed out, so the
// one thing this fixture asserts is the one thing it changes.
private fun shortOffer(): ProbeOfferUiState {
    val offer = when (val probe = unsurveyedSystemUiState.probe) {
        is ProbeActionUiState.Dispatch -> probe.offer
        else -> error("the neighbour of a fresh colony's home must be a system it can send a probe to")
    }
    return offer.copy(cost = offer.cost.copy(short = true))
}

// Borrowed from the real home world rather than retyped, so the one hand-written frame still shows
// the same axis line the app produces — non-breaking spaces included.
//
// It is borrowed rather than typed for a reason 0.5.1 proved: that release moved where genesis
// starts a colony and left this frame quoting the fields and yield of a world it no longer drew its
// axes from. A hand-written half beside a derived half is a frame that can disagree with itself,
// which is the exact failure the header warns about.
private fun homeNote(): String {
    val home = homeSystemUiState.bands.flatMap { it.rows }.first { it.verdict is VerdictUiState.Home }
    return (home.verdict as VerdictUiState.Home).note
}

// The unit is joined to its value by U+00A0 in the mapper, and a fixture that used an ordinary
// space would render a line that wraps differently from the real one.
private fun blocked(
    axis: String,
    reading: String,
    band: String,
    unit: String,
    technology: AdaptationTechnology,
    level: Int,
) =
    BlockedAxisUiState(
        axis = axis,
        reading = reading,
        tolerated = band + ' ' + unit,
        technology = technology,
        label = "${technology.name.lowercase().replaceFirstChar { it.uppercase() }} $level",
    )

// The geometry is the mapper's — a slot's orbit and its phase are functions of the slot and of
// nothing else — so the fixture states which slots hold what and derives the rest exactly as the
// app does. Hand-written coordinates here would let a baseline assert a picture the app cannot draw.
private fun bodies(vararg occupied: Pair<Int, MapMark>): SystemMapUiState {
    val sorted = occupied.sortedBy { it.first }
    return SystemMapUiState(
        bodies = sorted.mapIndexed { index, (slot, mark) ->
            MapBodyUiState(
                slot = slot,
                mark = mark,
                orbit = index.toFloat() / (sorted.size - 1).toFloat(),
            )
        },
        trajectory = null,
    )
}

// The home system with a probe out, which is the one frame that draws a trajectory: an arc leaving
// the colony for the edge of the map, fading along its own length toward where it is going, with
// the target and the time left at the faint end. Every probe in this game is launched from home, so
// this is the only place the arc can be — see `SystemMapUiState.trajectory`.
internal val homeWithProbeOutUiState: GalaxyUiState = homeSystemUiState.copy(
    map = homeSystemUiState.map.copy(trajectory = MapTrajectoryUiState(label = "[3:152] · 4h 12m")),
)

// ── The dispatch sheet ───────────────────────────────────────────────────────────────────────
//
// Derived rather than written out, for the reason the three system frames above are: the figure the
// sheet states is `FleetBalance.cargo` to the unit, and a hand-typed one would drift from the
// arithmetic the run is actually dispatched with — which is the one number the player is shown
// *before* they commit, and therefore the one that must not be a fixture's opinion.

// The nearest world in the home system a run may actually be sent to. Read off the fixture rather
// than written down, because hardcoding a slot here would be asserting the seed — and a run's
// legality is `startRun`'s rule, not this file's guess: home is refused, so the slot is whichever
// one holds a world that is neither home nor held.
internal val RUNNABLE_SLOT: Int = homeSystemUiState.bands
    .flatMap { it.rows }
    .first { it.verdict is VerdictUiState.Blocked || it.verdict is VerdictUiState.Barren }
    .slot

// The colony the three generated frames describe, for the tests that drive the stateful screen
// rather than a mapped frame: which world has its sheet up is `GalaxyScreen`'s own state, so a tap
// that raises one is only a tap that raises one from there.
internal val testGameState: GameState = state

// The world a settler cannot have and a fleet can — which is the whole mechanic in one frame.
// Hostility gates settling, never gathering, so the commonest verdict on the map is also a perfectly
// ordinary target.
internal val dispatchOfferUiState: GalaxyUiState = state.openWorld(RUNNABLE_SLOT)

// 98% of the map. A hold cannot be priced from a world nobody has looked at, so the sheet refuses
// and offers the flight that would fix it — the one refusal in the app that hands back a verb.
internal val dispatchUnsurveyedUiState: GalaxyUiState = state.toGalaxyUiState(
    at = SystemSelection(galaxy = galaxy.home.galaxy, system = galaxy.home.system - 1),
    now = FIXTURE_NOW,
    timeZone = TimeZone.UTC,
    dispatch = DispatchSelection(
        slot = unsurveyedSystemUiState.bands.flatMap { it.rows }.first().slot,
        gathering = null,
        ships = null,
        window = null,
    ),
)

// Every hull away. The pool is the *idle* count, so this is genesis with its one granted skiff
// already out — the state a player reaches on their first check-in and cannot reach twice.
internal val dispatchNoShipsUiState: GalaxyUiState = state
    .copy(
        ships = Ships.NONE,
        runs = listOf(
            FleetRun(
                target = GalaxyCoordinate(
                    galaxy = galaxy.home.galaxy,
                    system = galaxy.home.system,
                    slot = RUNNABLE_SLOT,
                ),
                ships = Ships.of(ShipType.SKIFF, 1),
                gathering = ResourceKind.METAL,
                // The worked example's own figure, so the inbound line and the offer agree: 198 at
                // round 21's `EXTRACTION_PER_HOUR` of 60, where it read 66 at 20.
                cargo = Resources.of(metal = 198),
                dispatchedAt = FIXTURE_NOW,
                returnsAt = FIXTURE_NOW + 3.hours,
            ),
        ),
    )
    .openWorld(RUNNABLE_SLOT)

// **The clamped offer, which is where this mechanic actually lives.** A fleet big enough to outlift
// the world it is standing on: the figure is the vein rather than the fleet, the slot beside it says
// so in one token, and the clause under the stepper names the hulls that would come home empty.
internal val dispatchClampedUiState: GalaxyUiState = state
    .copy(ships = Ships.of(ShipType.SKIFF, 8))
    .openWorld(RUNNABLE_SLOT, gathering = ResourceKind.METAL, ships = 8, window = 24.hours)

// The dry world, and the rarer of the two states by construction: a vein puts a whole unit back
// every twenty minutes, so exact zero survives about a third of an hour. The sheet keeps its chips,
// its stepper and its ladder, because the wait is a function of the ask and shrinking the ask is the
// only remedy there is.
internal val dispatchWaitingUiState: GalaxyUiState = state
    .copy(ships = Ships.of(ShipType.SKIFF, 4))
    .let { colony ->
        val target = GalaxyCoordinate(
            galaxy = colony.galaxy.home.galaxy,
            system = colony.galaxy.home.system,
            slot = RUNNABLE_SLOT,
        )
        val cap = colony.galaxy.depositCap(target, ResourceKind.METAL) ?: 0
        colony.copy(galaxy = colony.galaxy.withTaken(target, ResourceKind.METAL, cap, at = FIXTURE_NOW))
    }
    .openWorld(RUNNABLE_SLOT, gathering = ResourceKind.METAL, ships = 4, window = 6.hours)

// **The ask no waiting ever covers**, which is the finding the waiting state was built around: the
// vein and the rate carry one multiplier, so a full fleet's lift is about the size of a vein and
// eight hulls at a day want several times what any world of this size holds. The sheet says so
// rather than naming a date, and the remedy is the stepper above it.
internal val dispatchWaitingForeverUiState: GalaxyUiState = state
    .copy(ships = Ships.of(ShipType.SKIFF, 8))
    .let { colony ->
        val target = GalaxyCoordinate(
            galaxy = colony.galaxy.home.galaxy,
            system = colony.galaxy.home.system,
            slot = RUNNABLE_SLOT,
        )
        val cap = colony.galaxy.depositCap(target, ResourceKind.METAL) ?: 0
        colony.copy(galaxy = colony.galaxy.withTaken(target, ResourceKind.METAL, cap, at = FIXTURE_NOW))
    }
    .openWorld(RUNNABLE_SLOT, gathering = ResourceKind.METAL, ships = 8, window = 24.hours)

// A world worked down but not out, which is the reading the row exists for: a fraction rather than a
// word, on the one card where a player is choosing between two currencies.
internal val dispatchWorkedUiState: GalaxyUiState = state
    .let { colony ->
        val target = GalaxyCoordinate(
            galaxy = colony.galaxy.home.galaxy,
            system = colony.galaxy.home.system,
            slot = RUNNABLE_SLOT,
        )
        val cap = colony.galaxy.depositCap(target, ResourceKind.METAL) ?: 0
        colony.copy(galaxy = colony.galaxy.withTaken(target, ResourceKind.METAL, cap * 3 / 4, at = FIXTURE_NOW))
    }
    .openWorld(RUNNABLE_SLOT, gathering = ResourceKind.METAL)

// The frontier, where the ladder narrows rather than greying out: the next galaxy is 9h 20m each
// way, so the four short rungs are simply not on the sheet. That narrowing is what teaches distance
// before any copy does — see `FleetBalance.windowsFor`.
//
// **It has to be surveyed, and the first version of this fixture was not.** An unsurveyed world
// refuses before it ever prices a window, so the sheet showed the probe refusal and the test that
// asserts the short rungs are absent passed against a sheet that had no rungs at all — a fixture
// that made its own assertion vacuous. Marked surveyed here rather than reached by dispatching a
// probe and advancing nine hours, which is a different feature's journey.
private val farSystem: SystemAddress =
    SystemAddress(galaxy = galaxy.home.galaxy % GalaxyBalance.GALAXIES + 1, system = 1)

private val farSlot: Int = (1..GalaxyBalance.SLOTS_PER_SYSTEM).first { slot ->
    worldAt(galaxy.seed, GalaxyCoordinate(galaxy = farSystem.galaxy, system = farSystem.system, slot = slot)) != null
}

internal val dispatchFarUiState: GalaxyUiState = state
    .copy(
        galaxy = galaxy.copy(
            surveyed = galaxy.surveyed + (1..GalaxyBalance.SLOTS_PER_SYSTEM)
                .map { GalaxyCoordinate(galaxy = farSystem.galaxy, system = farSystem.system, slot = it) }
                .filter { worldAt(galaxy.seed, it) != null },
        ),
    )
    .toGalaxyUiState(
        at = SystemSelection(galaxy = farSystem.galaxy, system = farSystem.system),
        now = FIXTURE_NOW,
        timeZone = TimeZone.UTC,
        dispatch = DispatchSelection(slot = farSlot, gathering = null, ships = null, window = null),
    )

private fun GameState.openWorld(
    slot: Int,
    gathering: ResourceKind? = null,
    ships: Int? = null,
    window: Duration? = null,
): GalaxyUiState = toGalaxyUiState(
    at = SystemSelection(galaxy = galaxy.home.galaxy, system = galaxy.home.system),
    now = FIXTURE_NOW,
    timeZone = TimeZone.UTC,
    dispatch = DispatchSelection(slot = slot, gathering = gathering, ships = ships, window = window),
)

// A system holding nine bodies, which the seed can produce and the four-body home cannot show: past
// this density the orbits are closer together than a slot number is wide, so the numbers interleave
// on two rows instead of overprinting each other. See `SystemMap`.
internal val crowdedSystemUiState: GalaxyUiState = unsurveyedSystemUiState.copy(
    map = bodies(
        1 to MapMark.UNSURVEYED,
        2 to MapMark.UNSURVEYED,
        4 to MapMark.BLOCKED,
        5 to MapMark.UNSURVEYED,
        7 to MapMark.HOME,
        9 to MapMark.BARREN,
        11 to MapMark.SETTLEABLE,
        13 to MapMark.RELAY,
        15 to MapMark.UNSURVEYED,
    ),
)
