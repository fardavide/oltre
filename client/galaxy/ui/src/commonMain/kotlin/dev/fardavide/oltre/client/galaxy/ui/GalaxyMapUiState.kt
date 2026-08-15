package dev.fardavide.oltre.client.galaxy.ui

import dev.fardavide.oltre.core.RegionTemperament
import dev.fardavide.oltre.core.StarClass

// **The galaxy as a folded ribbon.** Claude Design, 2026-08-15 — *"Looks near, is near"*.
//
// The galaxy is one-dimensional: a system is an index in 1…250 and every travel cost in the game is
// a function of the difference of two indices. So the drawing has to agree with the metric rather
// than the metric with the drawing (Davide's call, the same day), and the whole design is one move —
// **fold the line into ten bands and let each band be a region.** Path order stays index order, so a
// distance on the drawing is a distance in the game, and a region falls out for free because a
// region *is* a contiguous run of twenty-five indices.
//
// The second dimension is bought for exactly one thing, and it is the thing 0.11.0 measured and
// could not afford: **ten region names, all legible at once.** A region is 33dp of the old strip and
// its name is 68–100dp of type at the 9.5sp floor, so ten of them overlapped two and a half times
// over. Ten bands of 337dp each have room for the longest of them with 237dp to spare.
//
// Nothing here is stored and nothing is cached. A glyph carries class, region and position, which is
// 55 µs for all 250 systems — measured, not guessed. It deliberately does **not** carry a world
// count: that is the 777 µs figure, and a fourth channel on a 3dp dot besides. The caption carries
// it, for one system, on demand.
data class GalaxyMapUiState(
    // Ten, always, region 1 first. The coordinate space is fixed, so this is a fold rather than a
    // list that grows.
    val bands: List<MapBandUiState>,
    // Two hundred and fifty, ascending. Every one of them is drawn on every frame — see the note
    // above about what that costs.
    val stars: List<MapStarUiState>,
    // Where a probe's flight crosses each whole hour, as hairlines on the map. Between none and
    // eight of them: from a home near an edge, most of the marks fall off the map, **and that is the
    // reading** — home at 165 has three to its left and one to its right, so you live near one end
    // and the map says so without a word.
    val hours: List<MapHourUiState>,
    // The only systems that carry a name. You cannot print 250 — the drawing could, at 144 µs, and
    // the screen cannot — so **a pin is what makes a name appear**, which is the whole of search on
    // a map. Home and the selection are named for the same reason and by the same mechanism.
    val names: List<MapNameUiState>,
    // Drawn at a fifth of the size, with no labels, no hour marks and no spikes: the universe view's
    // four discs are this same drawing rather than a decoration of it, so a Blaze region really is
    // where the small picture puts it.
    val mini: Boolean,
)

// One region, one band, and the design's whole identity argument: the name and the texture arrive
// together, ten times, on one screen. Nobody has to be told what a Deep is.
data class MapBandUiState(
    val region: Int,
    val name: String,
    // The tinted field behind the band — the region as weather rather than as a boundary. The hue
    // follows the temperament the way the world portrait's ramp does.
    val temperament: RegionTemperament,
    // The band the selection is standing in. One band is lit at a time and never none: the map opens
    // with home selected and a tap can only move the selection, never clear it.
    val lit: Boolean,
)

// A star, and everything the charted tier is allowed to say about one. **It may say how bright a
// star is, which region it is in and whether you have been there. It may never say what is orbiting
// it** — the portrait is the survey's reward and that is load-bearing.
data class MapStarUiState(
    val system: Int,
    // Size and luminance both, which is what keeps knowledge and astronomy on separate channels: a
    // surveyed dim star and an unsurveyed bright one are never the same mark.
    val starClass: StarClass,
    // Thousandths of the band pitch, generated in `core` from the seed. Capped at half a pitch, so
    // it can never reorder two stars — the band reads as sky, and the drawing still cannot lie about
    // which of two systems comes first.
    val driftPermille: Int,
    // Thousandths of the class radius, so two standards are siblings rather than clones. Never
    // enough to promote a star into the next class.
    val sizePermille: Int,
    // A third of the brights lean the crystal hue in the halo only — variety inside a class, mixed
    // from a resource hue the way the portrait ramp is, and never a status colour.
    val coolHalo: Boolean,
    // What you know about it, as rings outside the disc. A set rather than a precedence, because
    // they are genuinely independent: your own star can be selected, and a system can be surveyed
    // with a probe on its way back to it.
    val marks: Set<MapStarMark>,
)

// Every overlay the map has, and there are no more. Each is its own ring at its own radius, so two
// facts about one star stack instead of one hiding the other.
enum class MapStarMark {
    SURVEYED,
    IN_FLIGHT,
    HOME,
    SELECTED,
}

// "1h" against the system where a probe's flight first costs that hour. The probe's clock and not
// the run's — Davide's call, 2026-08-15: two rulers over one drawing is not survivable, and a probe
// is aimed at a star, which is the only thing the map can aim.
data class MapHourUiState(val system: Int, val label: String)

data class MapNameUiState(val system: Int, val name: String, val tone: MapNameTone)

enum class MapNameTone { HOME, SELECTED, PINNED }

// **The map's one readout and the map's one control.** Everything a system knows before a survey
// fits on two lines — name, address, class, region, world count — and the trailing element is the
// only place the map is allowed to act.
//
// It is never empty. The map opens with home selected and a tap can only move the selection, so
// there is no "nothing selected" state to design, no placeholder copy, and no dead bar at the foot
// of the screen. It also means the first thing the tab ever shows a new player is their own star,
// named, with its own clock on it.
data class MapCaptionUiState(
    val system: String,
    val coordinate: String,
    val meta: String,
    // **A premise of the design's that did not survive real text metrics.** The frame set the class,
    // the region and the world count against a trailing clock on one line and showed all of it; at
    // the real advance of JetBrains Mono the left column is about 220dp at 393dp and the string is
    // 214, so it ellipsized in the middle of the word `worlds`. What drops at 320dp is the region
    // name — a trailing noun would be the house rule, but the region is the one fact here the map
    // has already stated, in the lit band label directly above the caption.
    val compactMeta: String,
    val trailing: MapCaptionTrailingUiState,
    // The galaxy map's caption is the selection, so it takes the accent edge; the universe view's is
    // a summary of a disc, so it takes the plain card. Accent means "go tap this" and the selection
    // is what the caption acts on, which is what keeps that true here.
    val own: Boolean,
)

sealed interface MapCaptionTrailingUiState {

    // A probe, aimed at the star the caption names. **Stars are probe targets; worlds are run
    // targets** — one rule, and it falls straight out of the knowledge tiers: a run is aimed at a
    // world, and worlds are what a survey pays for, so the map cannot aim one and must not pretend
    // to.
    //
    // It dispatches rather than opening a sheet, because there is no probe sheet in the game and
    // never has been: the system page's footer is a one-tap verb and this is the same verb with the
    // same price on it. Claude Design wrote "opening the dispatch sheet already pointed"; the sheet
    // it means does not exist.
    data class Dispatch(val label: String) : MapCaptionTrailingUiState

    // The clock, in plain text, when there is nothing to aim: the run's round trip on a system you
    // already know, the probe's remaining flight on one you are already looking at.
    data class Note(val label: String) : MapCaptionTrailingUiState

    data object None : MapCaptionTrailingUiState
}

// **The universe is one gesture up, not a screen.** Four galaxies swap into the map's own frame from
// the header chip; nothing pushes and there is nothing to come back from.
//
// What four discs can mean today is honestly one thing — **what it costs to get there** — and that
// one thing is real: the four are not equidistant. Empires later tint a disc and add a holdings
// count to the line that reads "0 surveyed" now. Neither needs a new surface.
data class UniverseUiState(val discs: List<UniverseDiscUiState>)

data class UniverseDiscUiState(
    val galaxy: Int,
    val label: String,
    // The same ten bands at a fifth of the size, drawn from that galaxy's own temperament
    // permutation. Real, not decorative, and it costs one more sweep of the 55 µs.
    val map: GalaxyMapUiState,
    val known: String,
    // "run 9h 20m" — a round trip, because a hop is a commitment rather than a journey. Null on the
    // galaxy you live in, which says "home" instead.
    val cost: String?,
    val home: Boolean,
    val selected: Boolean,
)

// The head above the map and the universe. Two rows because it does two things: say which of the
// tab's two lists you are looking at, and say which galaxy.
//
// **What is not here is the search field and the filter chips** that `LedgerHeadUiState` carries.
// Both stay on the worlds list, where the question they answer — *which of the places I already
// know* — is a list question. Neither can be asked of 250 stars.
data class GalaxyHeadUiState(
    val mode: LedgerMode,
    val scale: GalaxyScale,
    // "G3", or "4 galaxies" when the four discs are up. The only way up and the only way back.
    val chip: String,
    val count: String,
)

enum class GalaxyScale { GALAXY, UNIVERSE }
