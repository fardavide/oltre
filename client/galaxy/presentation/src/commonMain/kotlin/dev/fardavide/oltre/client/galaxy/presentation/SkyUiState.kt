package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.design.component.RefusalUiState
import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.design.format.milli
import dev.fardavide.oltre.client.design.format.perMillion
import dev.fardavide.oltre.client.design.format.signed
import dev.fardavide.oltre.client.design.format.toChipLabel
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.dispatch.presentation.DispatchProbeOffer
import dev.fardavide.oltre.client.dispatch.presentation.DispatchSelection
import dev.fardavide.oltre.client.dispatch.presentation.toDispatchUiState
import dev.fardavide.oltre.client.galaxy.ui.MapCaptionTrailingUiState
import dev.fardavide.oltre.client.galaxy.ui.MapCaptionUiState
import dev.fardavide.oltre.client.galaxy.ui.SkyBodyUiState
import dev.fardavide.oltre.client.galaxy.ui.SkyDepth
import dev.fardavide.oltre.client.galaxy.ui.SkyFlightUiState
import dev.fardavide.oltre.client.galaxy.ui.SkyGalaxyUiState
import dev.fardavide.oltre.client.galaxy.ui.SkyHourUiState
import dev.fardavide.oltre.client.galaxy.ui.SkyRegionUiState
import dev.fardavide.oltre.client.galaxy.ui.SkySelection
import dev.fardavide.oltre.client.galaxy.ui.SkyStarInk
import dev.fardavide.oltre.client.galaxy.ui.SkyStarUiState
import dev.fardavide.oltre.client.galaxy.ui.SkyStepUiState
import dev.fardavide.oltre.client.galaxy.ui.SkyUiState
import dev.fardavide.oltre.client.galaxy.ui.SkyWordsUiState
import dev.fardavide.oltre.client.galaxy.ui.region
import dev.fardavide.oltre.client.galaxy.ui.system
import dev.fardavide.oltre.client.galaxy.ui.world
import dev.fardavide.oltre.client.net.domain.HeldActions
import dev.fardavide.oltre.client.world.ui.WorldPortraitUiState
import dev.fardavide.oltre.core.FleetBalance
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GalaxyState
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.HostilityAxis
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.SurveyBalance
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.ToleranceFailure
import dev.fardavide.oltre.core.World
import dev.fardavide.oltre.core.WorldTraits
import dev.fardavide.oltre.core.WorldVerdict
import dev.fardavide.oltre.core.epithetFor
import dev.fardavide.oltre.core.layoutAt
import dev.fardavide.oltre.core.regionNameAt
import dev.fardavide.oltre.core.starClassAt
import dev.fardavide.oltre.core.systemNameAt
import dev.fardavide.oltre.core.systemsOf
import dev.fardavide.oltre.core.verdictFor
import dev.fardavide.oltre.core.worldAt
import dev.fardavide.oltre.core.worldNameAt
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// **Everything the Galaxy tab decides, for one sky.** The nine galaxies with every star of every
// one, the worlds beside the stars a probe has been to, and the four lines of text that change
// with where the eye is — the address across the top, the count under it, and the caption's name,
// price and verb at the foot. The types it produces live in `:client:galaxy:ui`, which knows
// nothing about a seed or a `GameState`; this is where a slot becomes a verdict and a pinch's
// depth becomes a sentence.
//
// **Regenerated whole on every change of selection or depth, and none of it stored.** Star class,
// position and region for 2,250 stars is the free tier — a few hundred microseconds — and the
// expensive generator, `worldAt`, runs only for the stars a probe has landed on and for the one
// star the caption is about. That is the same budget the fold map kept, spread over nine galaxies.
//
// **What the fog withholds it withholds here**, once, at the top: a name, a class, a world count
// and a drawn body are charted-tier facts, and the star that is grain gets none of them however
// hard the canvas is zoomed. A world is never drawn under an unsurveyed star; a socket — a body
// with no face — is drawn under the *selected* charted star only, which is the tap saying what a
// probe would buy.
internal fun GameState.toSkyUiState(
    selection: SkySelection,
    depth: SkyDepth,
    now: Instant,
    timeZone: TimeZone,
    dispatch: DispatchSelection? = null,
    held: HeldActions = HeldActions.NONE,
    // What the last tap on a verb produced, or null. A fact about a tap rather than about the sky.
    refusal: RefusalUiState? = null,
): SkyUiState = SkyUiState(
    home = galaxy.home,
    galaxies = (1..GalaxyBalance.GALAXIES).map { toSkyGalaxyUiState(index = it, selection = selection, now = now) },
    steps = stepsFor(selection),
    caption = captionFor(selection = selection, depth = depth, now = now, timeZone = timeZone),
    count = countFor(selection = selection, depth = depth),
    words = SkyWordsUiState(
        metal = Strings.resourceName(ResourceKind.METAL),
        crystal = Strings.resourceName(ResourceKind.CRYSTAL),
        alone = Strings.unsurveyedWord(),
    ),
    dispatch = dispatch?.let { selected ->
        toDispatchUiState(
            selection = selected,
            // Hoisted rather than restated: the caption already decides whether a probe can be
            // sent, and a second copy of that decision inside the sheet is a second place for the
            // two to disagree about one flight. The sheet's own module cannot price a survey and
            // must not learn to — see `DispatchProbeOffer`.
            probe = probeOfferFor(SystemAddress.of(selected.at)),
            now = now,
            held = held,
            refusal = refusal,
        )
    },
)

// ── The drawing ─────────────────────────────────────────────────────────────────────────────

private fun GameState.toSkyGalaxyUiState(index: Int, selection: SkySelection, now: Instant): SkyGalaxyUiState {
    val home = index == galaxy.home.galaxy
    val charted = galaxy.chartedCountIn(index)
    return SkyGalaxyUiState(
        galaxy = index,
        label = Strings.galaxyNamed(index),
        // **A galaxy says how much of itself you have charted, not how much you have surveyed** —
        // the honest line under a drawing that is mostly grain, and the one fact that separates the
        // nine besides their fare.
        known = when (charted) {
            0 -> Strings.unchartedWord()
            else -> Strings.chartedOfSystems(
                charted = Strings.plainNumber(charted),
                systems = Strings.plainNumber(GalaxyBalance.SYSTEMS_PER_GALAXY),
            )
        },
        charted = charted > 0,
        stars = starsOf(index = index, selection = selection, now = now),
        regions = regionsOf(index),
        // Hour rings and fleet paths are measured from home, and home is in one galaxy.
        hours = if (home) hourMarksAtHome() else emptyList(),
        flights = if (home) flightsFromHome() else emptyList(),
    )
}

// **The overlays come off the save, not off the generator**, which is what keeps the whole draw
// inside the free tier. "Have I been here" is a membership test against `galaxy.surveyed` — a set of
// world coordinates the save already holds — rather than `hasSurveyed`, which walks fifteen slots per
// system and is *vacuously true* for a system with no worlds in it, so a sky built on it would ring
// hundreds of empty systems nobody has ever sent a probe to.
private fun GameState.starsOf(index: Int, selection: SkySelection, now: Instant): List<SkyStarUiState> {
    val seed = galaxy.seed
    val known = galaxy.surveyed.filter { it.galaxy == index }.mapTo(mutableSetOf()) { it.system }
    val inFlight = surveys.filter { it.target.galaxy == index }.mapTo(mutableSetOf()) { it.target.system }
    val home = galaxy.home.takeIf { it.galaxy == index }?.system
    val selected = selection.system?.takeIf { it.galaxy == index }?.system
    // Hoisted out of the 250-star loop, which is the whole of this file's cost budget: one lookup
    // per galaxy rather than 250, and then one integer comparison a star.
    val span = galaxy.spanIn(index)
    return (1..GalaxyBalance.SYSTEMS_PER_GALAXY).map { system ->
        val layout = layoutAt(seed, index, system)
        val charted = span != null && system in span
        val surveyed = system in known
        val at = SystemAddress(galaxy = index, system = system)
        SkyStarUiState(
            system = system,
            driftPermille = layout.driftPermille,
            // **Position is never in the ink and class always is**, so an uncharted star costs less
            // to build rather than more: the generator is not asked what it is.
            ink = when (charted) {
                true -> SkyStarInk.Charted(
                    starClass = starClassAt(seed, index, system),
                    sizePermille = layout.sizePermille,
                )

                false -> SkyStarInk.Grain
            },
            surveyed = surveyed,
            inFlight = system in inFlight,
            // **A name is a charted fact, so the light has to reach one before the sky prints it.**
            // The surveyed stars and home are charted by construction; the only star this ever
            // silences is the *selection*, which a thumb can park anywhere including the dark —
            // and there the caption is saying `[3:240]` precisely because there is no name.
            name = when {
                charted && (surveyed || system == home || system == selected) ->
                    TextRes(systemNameAt(seed, index, system))

                else -> null
            },
            worlds = when {
                surveyed -> bodiesOf(at = at, now = now)
                charted && system == selected -> socketsOf(at)
                else -> emptyList()
            },
        )
    }
}

// The worlds about a star a probe has been to, as portraits: the traits are paid for, so the disc
// wears them, and the two deposit bars read what is left in the ground.
private fun GameState.bodiesOf(at: SystemAddress, now: Instant): List<SkyBodyUiState> =
    worldsOf(at).map { world ->
        SkyBodyUiState(
            slot = world.at.slot,
            name = TextRes(worldNameAt(galaxy.seed, world.at)),
            portrait = world.toPortrait(surveyed = world.at in galaxy.surveyed),
            home = world.at == galaxy.home,
            yours = runs.any { it.target == world.at },
            metal = galaxy.depositLeft(at = world.at, gathering = ResourceKind.METAL, now = now),
            crystal = galaxy.depositLeft(at = world.at, gathering = ResourceKind.CRYSTAL, now = now),
        )
    }

// The worlds about the selected charted star nobody has been to: a slot and a name each, and no
// face, because a name is astronomy and a face is what the survey buys.
private fun GameState.socketsOf(at: SystemAddress): List<SkyBodyUiState> =
    worldsOf(at).map { world ->
        SkyBodyUiState(
            slot = world.at.slot,
            name = TextRes(worldNameAt(galaxy.seed, world.at)),
            portrait = WorldPortraitUiState.Unsurveyed,
            home = false,
            yours = false,
            metal = null,
            crystal = null,
        )
    }

private fun World.toPortrait(surveyed: Boolean): WorldPortraitUiState = when {
    !surveyed -> WorldPortraitUiState.Unsurveyed
    else -> WorldPortraitUiState.Surveyed(
        temperature = traits.temperature,
        gravity = traits.gravity,
        pressure = traits.pressure,
        hazards = traits.hazards,
        hasRing = hasRing,
    )
}

// What is left of a deposit as a fraction of its cap, or null where the ground never held any —
// the bar beside a world drawn large, which is a picture of `metal 174/819` rather than the words.
private fun GalaxyState.depositLeft(at: GalaxyCoordinate, gathering: ResourceKind, now: Instant): Float? {
    val cap = depositCap(at, gathering)?.takeIf { it > 0 } ?: return null
    return (remaining(at, gathering, now).toFloat() / cap.toFloat()).coerceIn(0f, 1f)
}

private fun GameState.regionsOf(index: Int): List<SkyRegionUiState> =
    (1..GalaxyBalance.REGIONS_PER_GALAXY).map { region ->
        SkyRegionUiState(
            region = region,
            name = regionName(index = index, region = region),
            lit = regionLit(index = index, region = region),
        )
    }

// **A region's name is what the light buys at region scale**, and it arrives the first time the
// light touches any star in the band — about nine times in a galaxy's life, which is the one
// discrete event in an otherwise continuous reveal. Until then the band is its index range.
private fun GameState.regionName(index: Int, region: Int): TextRes {
    val systems = systemsOf(region)
    return when (regionLit(index = index, region = region)) {
        true -> TextRes(regionNameAt(galaxy.seed, index, region))
        false -> Strings.systemRange(systems.first, systems.last)
    }
}

private fun GameState.regionLit(index: Int, region: Int): Boolean {
    val span = galaxy.spanIn(index) ?: return false
    val systems = systemsOf(region)
    return span.lo <= systems.last && span.hi >= systems.first
}

// **The probe's clock, laid about home.** `SurveyBalance` is thirty minutes plus one a system, so
// on an index-monotone drawing every whole hour is a ring — which is what retires the reach strip
// rather than reproducing it.
//
// Davide's call, 2026-08-15: the probe's and not the run's. Two rulers over one drawing is not
// survivable, and a probe is the only thing the sky can aim.
private fun GameState.hourMarksAtHome(): List<SkyHourUiState> {
    val origin = galaxy.home.system
    val base = SurveyBalance.duration(from = SystemAddress.of(galaxy.home), to = SystemAddress.of(galaxy.home))
        .inWholeMinutes
    return (1..MARKED_HOURS).flatMap { hour ->
        val away = (hour * MINUTES_PER_HOUR - base).toInt()
        if (away < 0) return@flatMap emptyList()
        listOf(origin - away, origin + away)
            .distinct()
            .filter { it in 1..GalaxyBalance.SYSTEMS_PER_GALAXY }
            .map { system -> SkyHourUiState(system = system, label = Strings.durationHours(hour.toLong())) }
    }
}

// Every fleet out of home and still in its galaxy: a probe's flight and a run's, drawn the same
// way because from this height they are the same fact.
private fun GameState.flightsFromHome(): List<SkyFlightUiState> {
    val home = galaxy.home
    return (surveys.map { it.target } + runs.map { SystemAddress.of(it.target) })
        .filter { it.galaxy == home.galaxy && it.system != home.system }
        .distinct()
        .map { SkyFlightUiState(from = home.system, to = it.system) }
}

// ── The address ─────────────────────────────────────────────────────────────────────────────

// One step per depth the selection reaches, universe first. A region in the dark is its range and
// a star in the dark is its address — the same trade the caption makes, because the bar and the
// caption are two readings of one selection.
private fun GameState.stepsFor(selection: SkySelection): List<SkyStepUiState> = buildList {
    add(SkyStepUiState(depth = SkyDepth.UNIVERSE, label = Strings.universeWord()))
    add(SkyStepUiState(depth = SkyDepth.GALAXY, label = Strings.galaxyStep(selection.galaxy)))
    selection.region?.let { region ->
        add(SkyStepUiState(depth = SkyDepth.REGION, label = regionName(index = selection.galaxy, region = region)))
    }
    selection.system?.let { at ->
        add(SkyStepUiState(depth = SkyDepth.SYSTEM, label = systemName(at)))
    }
    selection.world?.let { at ->
        add(SkyStepUiState(depth = SkyDepth.WORLD, label = TextRes(worldNameAt(galaxy.seed, at))))
    }
}

// **The address is the name, because it is the only one there is.** A system's name is generated
// and generated is not free any more.
private fun GameState.systemName(at: SystemAddress): TextRes = when (galaxy.hasCharted(at)) {
    true -> TextRes(systemNameAt(galaxy.seed, at.galaxy, at.system))
    false -> Strings.systemAddress(galaxy = at.galaxy, system = at.system)
}

// ── The count ───────────────────────────────────────────────────────────────────────────────

// The line under the bar: what there is at this depth and how much of it is yours. **It follows
// the depth the pinch has reached rather than the selection alone**, so zooming out over a world
// you have selected reads the galaxy's count and not the world's verdict.
private fun GameState.countFor(selection: SkySelection, depth: SkyDepth): TextRes {
    val system = selection.system
    val world = selection.world
    return when {
        depth == SkyDepth.UNIVERSE || selection is SkySelection.Galaxy -> Strings.clauses(
            listOf(Strings.galaxiesCount(GalaxyBalance.GALAXIES), Strings.yoursIsGalaxy(galaxy.home.galaxy)),
        )

        depth == SkyDepth.GALAXY || depth == SkyDepth.REGION || system == null -> galaxyCount(selection.galaxy)
        depth == SkyDepth.SYSTEM || world == null -> systemCount(system)
        else -> worldCount(world)
    }
}

// **"61 of 250 charted" is fog's whole readout**, and it replaces the bare length rather than
// sitting beside it. It is deliberately not a second progression gauge — the strip above counts
// what you *are*, this counts what you have looked at. Fleets out is absent rather than zero.
private fun GameState.galaxyCount(index: Int): TextRes {
    val out = surveys.count { it.target.galaxy == index } + runs.count { it.target.galaxy == index }
    return Strings.clauses(
        listOfNotNull(
            Strings.chartedOfSystems(
                charted = Strings.plainNumber(galaxy.chartedCountIn(index)),
                systems = Strings.plainNumber(GalaxyBalance.SYSTEMS_PER_GALAXY),
            ),
            Strings.surveyedCount(surveyedSystemsIn(index, 1..GalaxyBalance.SYSTEMS_PER_GALAXY)),
            Strings.fleetsOut(out).takeIf { out > 0 },
        ),
    )
}

// The system's name, what it holds and what it costs to reach — or, in the dark, its address and
// what a probe there would buy. The world count is a charted fact and stays behind the light.
private fun GameState.systemCount(at: SystemAddress): TextRes = when (galaxy.hasCharted(at)) {
    false -> Strings.clauses(
        listOf(
            Strings.systemAddress(galaxy = at.galaxy, system = at.system),
            Strings.unchartedWord(),
            Strings.chartsSystems(galaxy.wouldChart(at)),
        ),
    )

    true -> Strings.clauses(
        listOf(
            TextRes(systemNameAt(galaxy.seed, at.galaxy, at.system)),
            Strings.worldCount(worldsIn(galaxy.seed, at)),
            if (at == SystemAddress.of(galaxy.home)) Strings.yourOwnSystem() else Strings.reachSingle(tripTo(at).toChipLabel()),
        ),
    )
}

// The verdict, which is the world's one sentence: what it is, why, and how dangerous. `Blocked`
// says which axes and by how much rather than an epithet, because the requirement *is* the reason.
private fun GameState.worldCount(at: GalaxyCoordinate): TextRes {
    val world = worldAt(galaxy.seed, at) ?: return systemCount(SystemAddress.of(at))
    val traits = world.traits
    val epithet = Strings.worldEpithet(epithetFor(traits))
    val hazards = Strings.hazards(traits.hazards.size)
    return when (val verdict = verdictFor(world, this)) {
        WorldVerdict.Home -> Strings.clauses(listOf(Strings.verdictWordHome(), epithet, hazards))
        is WorldVerdict.Occupied -> Strings.clauses(listOf(Strings.verdictWordOccupied(), epithet))
        WorldVerdict.Unsurveyed -> Strings.clauses(
            listOf(
                Strings.unsurveyedWord(),
                Strings.slotWord(at.slot),
                Strings.probeFlight(probeTo(SystemAddress.of(at)).toChipLabel()),
            ),
        )

        is WorldVerdict.Blocked -> Strings.clauses(
            listOf(Strings.verdictWordBlocked()) + verdict.failures.map { it.clause() } + hazards,
        )

        // Barren fails no band at all — it fails the *bar* — so its line is the yield against the
        // threshold. Naming the threshold is what makes a run of Barren answers read as calibration
        // rather than as bad luck, and Barren is designed to be a common answer.
        WorldVerdict.Barren -> Strings.clauses(
            listOf(
                Strings.verdictWordBarren(),
                Strings.noteBarren(yield = traits.yieldLabel(), threshold = worthItThreshold()),
                hazards,
            ),
        )

        is WorldVerdict.Settleable -> Strings.clauses(listOf(Strings.verdictWordSettleable(), epithet, hazards))
    }
}

// ── The caption ─────────────────────────────────────────────────────────────────────────────

// The one control: names what is selected, prices the way to it, and carries the one verb it
// affords. **Every selection affords at most one**, and which is the depth's call as much as the
// selection's — a world opens from afar and runs from close up, because a run is a decision made
// in front of the world.
private fun GameState.captionFor(
    selection: SkySelection,
    depth: SkyDepth,
    now: Instant,
    timeZone: TimeZone,
): MapCaptionUiState = when (selection) {
    is SkySelection.Galaxy -> galaxyCaption(selection.galaxy)
    is SkySelection.Region -> regionCaption(index = selection.galaxy, region = selection.region, now = now)
    is SkySelection.System -> systemCaption(at = selection.at, depth = depth, now = now)
    is SkySelection.World -> worldCaption(at = selection.at, depth = depth, now = now, timeZone = timeZone)
}

// A galaxy is a summary and a fare. **A probe is aimed at a star and a galaxy is not one**, so an
// uncharted galaxy quotes the cheapest probe into it and offers nothing but the dive.
private fun GameState.galaxyCaption(index: Int): MapCaptionUiState {
    val home = index == galaxy.home.galaxy
    val charted = galaxy.chartedCountIn(index)
    val meta = when (charted) {
        0 -> Strings.clauses(listOf(Strings.unchartedWord(), Strings.probeFlight(probeHopTo(index).toChipLabel())))
        else -> Strings.clauses(
            listOf(
                Strings.chartedOfSystems(
                    charted = Strings.plainNumber(charted),
                    systems = Strings.plainNumber(GalaxyBalance.SYSTEMS_PER_GALAXY),
                ),
                Strings.surveyedCount(surveyedSystemsIn(index, 1..GalaxyBalance.SYSTEMS_PER_GALAXY)),
            ),
        )
    }
    return MapCaptionUiState(
        system = Strings.galaxyNamed(index),
        // Units rather than systems, because a hop is a flight cost and not a count — see
        // `distanceFromHome`.
        coordinate = when (home) {
            true -> Strings.yoursWord()
            false -> Strings.unitsOut(
                SurveyBalance.distanceUnits(
                    from = SystemAddress.of(galaxy.home),
                    to = SystemAddress(galaxy = index, system = galaxy.home.system),
                ).toLong().groupedByThousands(),
            )
        },
        meta = meta,
        compactMeta = meta,
        detail = null,
        trailing = MapCaptionTrailingUiState.Open(Strings.openWord()),
        own = home,
    )
}

// A region is twenty-five stars and what the probes have found among them. Lit, it counts the
// surveys and the veins; dark, it prices the flight to its far edge, which is the longest probe
// the region can ask for.
private fun GameState.regionCaption(index: Int, region: Int, now: Instant): MapCaptionUiState {
    val systems = systemsOf(region)
    val home = galaxy.home
    val own = index == home.galaxy && home.system in systems
    val systemsCount = Strings.systemsCount(Strings.plainNumber(systems.count()))
    val surveyed = Strings.surveyedCount(surveyedSystemsIn(index, systems))
    val veins = veinsIn(index, systems, now)
    val edge = if (abs(systems.first - home.system) > abs(systems.last - home.system)) systems.first else systems.last
    val toItsEdge = Strings.toItsEdge(probeTo(SystemAddress(galaxy = index, system = edge)).toChipLabel())
    val lit = regionLit(index = index, region = region)
    return MapCaptionUiState(
        system = regionName(index = index, region = region),
        coordinate = Strings.clauses(listOf(Strings.galaxyStep(index), Strings.systemRange(systems.first, systems.last))),
        meta = when (lit) {
            true -> Strings.clauses(
                listOfNotNull(
                    systemsCount,
                    surveyed,
                    Strings.veinsCount(veins).takeIf { veins > 0 },
                    if (own) Strings.yoursWord() else toItsEdge,
                ),
            )

            false -> Strings.clauses(listOf(systemsCount, Strings.unchartedWord(), toItsEdge))
        },
        compactMeta = when (lit) {
            true -> Strings.clauses(listOf(systemsCount, surveyed))
            false -> Strings.clauses(listOf(systemsCount, Strings.unchartedWord()))
        },
        detail = null,
        trailing = MapCaptionTrailingUiState.Open(Strings.openWord()),
        own = own,
    )
}

// **Stars are probe targets; worlds are run targets.** A star nobody has been to offers the probe,
// a star a probe has landed on offers the dive, and the system view — where the worlds are drawn
// and each carries its own verb — offers nothing, because the caption's tap would open what is
// already open.
//
// **The third tier's caption asks the generator nothing** — not the name, not the class, not the
// world count — which is not an optimisation but the tier itself. What it says instead is the two
// facts that still make a choice: where it is, and what a probe there would buy.
private fun GameState.systemCaption(at: SystemAddress, depth: SkyDepth, now: Instant): MapCaptionUiState {
    val flight = surveys.firstOrNull { it.target == at }
    val probe = Strings.probeFlight(probeTo(at).toChipLabel())
    val landsIn = flight?.let {
        Strings.probeLandsIn((it.completesAt - now).coerceAtLeast(Duration.ZERO).toChipLabel())
    }
    if (!galaxy.hasCharted(at)) {
        val meta = Strings.clauses(listOf(Strings.unchartedWord(), Strings.chartsSystems(galaxy.wouldChart(at))))
        return MapCaptionUiState(
            system = Strings.systemAddress(galaxy = at.galaxy, system = at.system),
            coordinate = distanceFromHome(at),
            meta = meta,
            compactMeta = meta,
            // The same fallback a charted star takes when the stores or the yard are short: the
            // caption has room to say what a trip costs or to offer it, never room to say why not.
            detail = when {
                flight != null -> landsIn
                !canSendAProbe() -> probe
                else -> null
            },
            trailing = if (flight == null && canSendAProbe()) MapCaptionTrailingUiState.Dispatch(probe) else null,
            own = false,
        )
    }
    val worlds = worldsIn(galaxy.seed, at)
    val surveyed = galaxy.surveyed.any { SystemAddress.of(it) == at }
    val own = at == SystemAddress.of(galaxy.home)
    val starClass = Strings.starClassName(starClassAt(galaxy.seed, at.galaxy, at.system))
    val worldCount = Strings.worldCount(worlds)
    val trip = Strings.reachSingle(tripTo(at).toChipLabel())
    // **The hull as well as the money**: a probe flies a `SCOUT`, so a caption that read the stores
    // alone would offer a verb `startSurvey` refuses. A system with nothing in it is the one case
    // where neither verb applies: a probe sent there would come back with the same answer.
    val offers = flight == null && worlds > 0 && canSendAProbe()
    return MapCaptionUiState(
        system = TextRes(systemNameAt(galaxy.seed, at.galaxy, at.system)),
        coordinate = Strings.systemAddress(galaxy = at.galaxy, system = at.system),
        meta = when {
            !surveyed -> Strings.clauses(listOf(starClass, worldCount, trip))
            own -> Strings.clauses(listOf(Strings.yourOwnSystem(), Strings.veinsCount(veinsIn(at.galaxy, at.system..at.system, now))))
            else -> Strings.clauses(listOf(starClass, worldCount, Strings.veinsCount(veinsIn(at.galaxy, at.system..at.system, now)), trip))
        },
        // The trailing noun goes and the number stays, which is the abbreviation rule every
        // compact line on this tab follows.
        compactMeta = when {
            own -> Strings.yourOwnSystem()
            else -> Strings.clauses(listOf(starClass, worldCount))
        },
        detail = when {
            surveyed -> null
            flight != null -> landsIn
            worlds > 0 && !canSendAProbe() -> probe
            else -> null
        },
        trailing = when {
            !surveyed -> if (offers) MapCaptionTrailingUiState.Dispatch(probe) else null
            depth == SkyDepth.SYSTEM -> null
            else -> MapCaptionTrailingUiState.Open(Strings.openWord())
        },
        own = own,
    )
}

// A world's caption is its epithet and its three readings, and under them the two deposits and
// the trip — or, on your own colony, the one note that needs no price. **The run is offered at the
// world depth only**, and only where the verb would be honoured: not on home, not on a holding,
// and not on a vein that is worked out.
private fun GameState.worldCaption(
    at: GalaxyCoordinate,
    depth: SkyDepth,
    now: Instant,
    timeZone: TimeZone,
): MapCaptionUiState {
    val world = worldAt(galaxy.seed, at) ?: return systemCaption(at = SystemAddress.of(at), depth = depth, now = now)
    val system = SystemAddress.of(at)
    val name = TextRes(worldNameAt(galaxy.seed, at))
    val coordinate = at.label()
    val trip = Strings.reachSingle(tripTo(at).toChipLabel())
    if (at !in galaxy.surveyed) {
        val flight = surveys.firstOrNull { it.target == system }
        val probe = Strings.probeFlight(probeTo(system).toChipLabel())
        return MapCaptionUiState(
            system = name,
            coordinate = coordinate,
            meta = Strings.clauses(listOf(Strings.unsurveyedWord(), Strings.slotWord(at.slot), trip)),
            compactMeta = Strings.clauses(listOf(Strings.unsurveyedWord(), Strings.slotWord(at.slot))),
            detail = when {
                flight != null -> Strings.probeLandsIn((flight.completesAt - now).coerceAtLeast(Duration.ZERO).toChipLabel())
                !canSendAProbe() -> probe
                else -> null
            },
            trailing = if (flight == null && canSendAProbe()) MapCaptionTrailingUiState.Dispatch(probe) else null,
            own = false,
        )
    }
    val traits = world.traits
    val epithet = Strings.worldEpithet(epithetFor(traits))
    val temperature = Strings.temperatureReading(traits.temperature.celsius.signed())
    val own = at == galaxy.home
    val run = runs.firstOrNull { it.target == at }
    val verdict = verdictFor(world, this)
    return MapCaptionUiState(
        system = name,
        coordinate = coordinate,
        meta = Strings.clauses(
            listOf(
                epithet,
                temperature,
                Strings.gravityReading(traits.gravity.milliG.milli()),
                Strings.pressureReading(traits.pressure.milliAtm.milli()),
            ),
        ),
        compactMeta = Strings.clauses(listOf(epithet, temperature)),
        detail = when {
            own -> Strings.noteHome()
            // Your fleet is there or bound there, and the one figure that matters is when it is back.
            run != null -> run.returnsAt.toLocalDateTime(timeZone).let {
                Strings.clauses(listOf(Strings.yourRun(), Strings.homeAt(hour = it.hour, minute = it.minute)))
            }

            else -> Strings.clauses(
                listOf(
                    Strings.resourceReading(ResourceKind.METAL, galaxy.depositWord(at, ResourceKind.METAL, now)),
                    Strings.resourceReading(ResourceKind.CRYSTAL, galaxy.depositWord(at, ResourceKind.CRYSTAL, now)),
                    trip,
                ),
            )
        },
        trailing = when {
            depth != SkyDepth.WORLD -> MapCaptionTrailingUiState.Open(Strings.openWord())
            verdict.pricesAHold() && galaxy.holdsADeposit(at, now) -> MapCaptionTrailingUiState.Run(Strings.runVerb())
            else -> null
        },
        own = own,
    )
}

// **Present exactly where a run is legal**, which is not a coincidence: absent on `Unsurveyed`
// because a hold cannot be priced from a world nobody has looked at, and absent on `Home` and
// `Occupied` because a run there is refused outright.
private fun WorldVerdict.pricesAHold(): Boolean = when (this) {
    WorldVerdict.Home, is WorldVerdict.Occupied, WorldVerdict.Unsurveyed -> false
    is WorldVerdict.Blocked, WorldVerdict.Barren, is WorldVerdict.Settleable -> true
}

// `metal full`, `metal 174/819`, `metal empty` — a word at each end because neither end poses any
// arithmetic, and a fraction between because 120 of 600 and 120 of 2,400 are the same number and not
// the same target. **Never the words this design refused**: no *left*, no *deposit*, no rate of
// refill. With no noun the line asserts nothing about who took what, which is what lets `full` be
// the honest reading of the ~98% of worlds nobody has ever worked.
private fun GalaxyState.depositWord(at: GalaxyCoordinate, gathering: ResourceKind, now: Instant): TextRes {
    val cap = depositCap(at, gathering)
    val remaining = if (cap == null) 0 else remaining(at, gathering, now)
    return when {
        cap == null || remaining <= 0 -> Strings.depositEmptyWord()
        remaining >= cap -> Strings.depositFullWord()
        else -> Strings.depositFraction(remaining.groupedByThousands(), cap.groupedByThousands())
    }
}

// A vein is a surveyed world a run could still lift something from: not the colony, and not a
// deposit worked down to nothing.
private fun GameState.veinsIn(index: Int, systems: IntRange, now: Instant): Int = galaxy.surveyed.count {
    it.galaxy == index && it.system in systems && it != galaxy.home && galaxy.holdsADeposit(it, now)
}

private fun GalaxyState.holdsADeposit(at: GalaxyCoordinate, now: Instant): Boolean =
    remaining(at, ResourceKind.METAL, now) > 0 || remaining(at, ResourceKind.CRYSTAL, now) > 0

// The systems in a range a probe has landed on. Off the survey set for the reason `starsOf` reads
// it: a world you know is what a player means by having been somewhere.
private fun GameState.surveyedSystemsIn(index: Int, systems: IntRange): Int =
    galaxy.surveyed.filter { it.galaxy == index && it.system in systems }.distinctBy { it.system }.size

// **How far away a star is, and it takes two words rather than one.** Systems inside a galaxy and
// *units* across one, because they are not the same figure: `distanceUnits` prices a galaxy hop at
// 250 — a flight cost, not a count — so one word everywhere would describe a star one galaxy over
// as `571 systems out` about a galaxy that holds 250.
private fun GameState.distanceFromHome(at: SystemAddress): TextRes = when (at.galaxy) {
    galaxy.home.galaxy -> Strings.systemsOut(abs(at.system - galaxy.home.system))
    else -> Strings.unitsOut(
        SurveyBalance.distanceUnits(from = SystemAddress.of(galaxy.home), to = at).toLong().groupedByThousands(),
    )
}

private fun GameState.probeTo(at: SystemAddress): Duration =
    SurveyBalance.duration(from = SystemAddress.of(galaxy.home), to = at)

private fun GameState.probeHopTo(index: Int): Duration =
    probeTo(SystemAddress(galaxy = index, system = galaxy.home.system))

// Any slot of the system will do and slot 1 is the one that always exists: a hop to any other
// system is priced identically for all fifteen.
private fun GameState.tripTo(at: SystemAddress): Duration =
    tripTo(GalaxyCoordinate(galaxy = at.galaxy, system = at.system, slot = 1))

private fun GameState.tripTo(at: GalaxyCoordinate): Duration =
    FleetBalance.roundTrip(from = galaxy.home, to = at, research = research, ships = FleetBalance.FASTEST_HULL)

// Both halves of what a probe costs — the metal and the hull — asked as one question, because the
// caption has one answer either way. `startSurvey` checks the hull *before* the metal, and this does
// not have to agree about the order: it only has to agree about whether the verb would be refused.
private fun GameState.canSendAProbe(): Boolean =
    ships.covers(SurveyBalance.SHIPS) && resources.covers(SurveyBalance.cost())

// ── The probe, for the dispatch sheet ───────────────────────────────────────────────────────

// **The verb the sheet's refusal carries, present exactly where `startSurvey` would accept it.**
// Null for the same three reasons the verb refuses — a probe already bound there, a system whose
// worlds are all known, a colony short of the hull or the metal — and null is the sheet showing a
// refusal with no verb under it. The system page's six-state footer that once said *why* is gone
// with One Sky; the caption prices the flight before the tap and that is the whole of the telling.
//
// **The second clause obeys the third tier the way the verb does**: `hasSurveyed` is vacuously true
// of a system with no worlds, so on its own it would refuse the one flight that finds out a star is
// empty. Guarded by the light, an uncharted worldless star is offered and a charted one is not.
internal fun GameState.probeOfferFor(at: SystemAddress): DispatchProbeOffer? = when {
    surveys.any { it.target == at } -> null
    galaxy.hasCharted(at) && galaxy.hasSurveyed(at) -> null
    !canSendAProbe() -> null
    else -> DispatchProbeOffer(
        label = Strings.dispatchProbe(),
        cost = SurveyBalance.cost().metal.groupedByThousands(),
        flight = Strings.probeFlightLabel(probeTo(at).toChipLabel()),
    )
}

// ── The generator, asked once per system ────────────────────────────────────────────────────

internal fun GameState.worldsOf(at: SystemAddress): List<World> =
    (1..GalaxyBalance.SLOTS_PER_SYSTEM).mapNotNull { slot ->
        worldAt(galaxy.seed, GalaxyCoordinate(galaxy = at.galaxy, system = at.system, slot = slot))
    }

// The fifteen-slot scan, and the standing decision it carries: `core`'s own `occupiedWorldsIn` is
// internal to it, and duplicating the scan here is cheaper than widening that surface for a count
// the screen wants and the model does not.
internal fun worldsIn(seed: GalaxySeed, system: SystemAddress): Int =
    (1..GalaxyBalance.SLOTS_PER_SYSTEM).count { slot ->
        worldAt(seed, GalaxyCoordinate(galaxy = system.galaxy, system = system.system, slot = slot)) != null
    }

// Internal since the dispatch sheet: the sheet heads itself with the coordinate the caption prints,
// and two copies of this would be two ways of writing one address.
internal fun GalaxyCoordinate.label(): TextRes = Strings.coordinate(galaxy, system, slot)

// ── The verdict's arithmetic ────────────────────────────────────────────────────────────────

// "temperature +212, you tolerate +80 °C": the unit is written once, on the tolerance, because both
// figures are the same axis and so the same unit.
private fun ToleranceFailure.clause(): TextRes = Strings.blockedAxisLine(
    axis = Strings.axisName(axis),
    reading = axis.reading(worldValue),
    tolerated = axis.tolerated(toleratedBound),
)

private fun HostilityAxis.reading(value: Int): TextRes = when (this) {
    HostilityAxis.TEMPERATURE -> value.signed()
    HostilityAxis.GRAVITY, HostilityAxis.PRESSURE -> value.milli()
}

private fun HostilityAxis.tolerated(value: Int): TextRes = when (this) {
    HostilityAxis.TEMPERATURE -> Strings.temperatureReading(value.signed())
    HostilityAxis.GRAVITY -> Strings.gravityReading(value.milli())
    HostilityAxis.PRESSURE -> Strings.pressureReading(value.milli())
}

private fun WorldTraits.yieldLabel(): TextRes = GalaxyBalance.yieldScore(this).perMillion.perMillion()

private fun worthItThreshold(): TextRes = GalaxyBalance.WORTH_IT_THRESHOLD.perMillion.perMillion()

// The design drew four rings and at home four is what fits: the fourth is 210 systems out, which is
// off the edge from any home the generator places.
private const val MARKED_HOURS: Int = 4
private const val MINUTES_PER_HOUR: Int = 60
