package dev.fardavide.oltre.client.galaxy.ui

import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.dispatch.ui.DispatchUiState
import dev.fardavide.oltre.client.world.ui.WorldPortraitUiState
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.StarClass

// **One sky.** Everything the Galaxy tab draws, at every depth, is in here once — the nine galaxies
// with their 250 stars each, the worlds beside the stars a probe has been to, the fleet paths and
// the hour rings of the home galaxy — and the canvas decides what to *show* from the zoom alone.
// There is no per-depth model because there is no per-depth screen: the universe, a galaxy, a
// region, a system and a world are five zooms of the one picture, and the step bar and caption are
// the only parts of it that change with the selection.
//
// Presentation decides every word and every flag; the canvas decides only where the pixels go.
data class SkyUiState(
    val home: GalaxyCoordinate,
    // Nine, in galaxy order; `galaxies[g - 1].galaxy == g`.
    val galaxies: List<SkyGalaxyUiState>,
    // The address, universe first: `universe / galaxy 3 / region 5 / system 118 / world 3`,
    // truncated at the depth the selection reaches.
    val steps: List<SkyStepUiState>,
    val caption: MapCaptionUiState,
    // The line under the bar: what is charted, what is surveyed, what is yours, at this depth.
    val count: TextRes,
    // The three words the canvas itself sets, in the orbit view: the two deposit labels beside a
    // world drawn large, and the note under a star no probe has been to.
    val words: SkyWordsUiState,
    val dispatch: DispatchUiState?,
)

data class SkyWordsUiState(val metal: TextRes, val crystal: TextRes, val alone: TextRes)

data class SkyGalaxyUiState(
    val galaxy: Int,
    val label: TextRes,
    // The word under the label at the universe depth: *uncharted*, or how much of it is known.
    val known: TextRes,
    val charted: Boolean,
    // 250, in system order.
    val stars: List<SkyStarUiState>,
    // Ten, in region order.
    val regions: List<SkyRegionUiState>,
    // Hour rings and fleet paths exist only about home, so every other galaxy carries none.
    val hours: List<SkyHourUiState>,
    val flights: List<SkyFlightUiState>,
)

data class SkyStarUiState(
    val system: Int,
    val driftPermille: Int,
    val ink: SkyStarInk,
    val surveyed: Boolean,
    val inFlight: Boolean,
    // Charted and worth a word: the surveyed stars and home.
    val name: TextRes?,
    // What sits about the star. A surveyed star carries its worlds as portraits; the selected
    // charted star that no probe has been to carries sockets; every other star carries nothing —
    // **a world is never drawn under an unsurveyed star.**
    val worlds: List<SkyBodyUiState>,
)

sealed interface SkyStarInk {
    data object Grain : SkyStarInk
    data class Charted(val starClass: StarClass, val sizePermille: Int) : SkyStarInk
}

data class SkyRegionUiState(
    val region: Int,
    // The region's name once anything in it is charted, its system range until then.
    val name: TextRes,
    val lit: Boolean,
)

// A ring about home at the system a probe reaches in a whole hour.
data class SkyHourUiState(val system: Int, val label: TextRes)

// A fleet out: home to its target, along the path.
data class SkyFlightUiState(val from: Int, val to: Int)

data class SkyBodyUiState(
    val slot: Int,
    val name: TextRes,
    val portrait: WorldPortraitUiState,
    val home: Boolean,
    // A fleet of yours is at it or bound for it.
    val yours: Boolean,
    // What is left of the deposit as a fraction of the cap, or null where nothing was ever there.
    val metal: Float?,
    val crystal: Float?,
)

data class SkyStepUiState(val depth: SkyDepth, val label: TextRes)

// The one control: the caption under the sky, naming what is selected, pricing the way to it, and
// carrying the one verb it affords.
data class MapCaptionUiState(
    val system: TextRes,
    val coordinate: TextRes,
    val meta: TextRes,
    val compactMeta: TextRes,
    // A second line where there is more to say — the deposits of a world, the note on home.
    val detail: TextRes?,
    // The pill on the right; null when the selection affords no verb beyond the row's own dive.
    val trailing: MapCaptionTrailingUiState?,
    val own: Boolean,
)

sealed interface MapCaptionTrailingUiState {
    val label: TextRes

    // Send a probe.
    data class Dispatch(override val label: TextRes) : MapCaptionTrailingUiState

    // Send a fleet: opens the dispatch sheet on the world.
    data class Run(override val label: TextRes) : MapCaptionTrailingUiState

    // Dive one depth in.
    data class Open(override val label: TextRes) : MapCaptionTrailingUiState
}
