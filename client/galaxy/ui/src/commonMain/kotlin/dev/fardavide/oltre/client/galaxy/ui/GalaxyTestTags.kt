package dev.fardavide.oltre.client.galaxy.ui

import dev.fardavide.oltre.core.GalaxyCoordinate

// Keyed by the address rather than by a label, for the reason `ResearchTestTags` is keyed by the
// technology: renaming what a star or a world reads cannot then silently retarget an assertion.
// **Public rather than internal since the layer split**, on `ColonyTestTags`' precedent: the
// robot that reads these lives in `:client:galaxy:ui-testing` now, because two modules' tests
// drive this screen and a test source set is visible to neither.
object GalaxyTestTags {

    const val CONTENT = "galaxy-content"

    // **The one drawing.** Since One Sky there is no map and no system view and no ledger to tell
    // apart: the universe, a galaxy, a region, a system and a world are five zooms of this canvas.
    const val SKY = "galaxy-sky"

    // The bar over the sky — the address, one step per depth — and the line under it.
    const val COUNT = "galaxy-count"

    // The caption at the foot: the sky's one readout and, when it carries a verb, its one control.
    // `CAPTION_ACTION` is present exactly when the selection affords a verb beyond the row's own
    // dive — a probe to send, a fleet to run, a depth to open.
    const val CAPTION = "galaxy-caption"
    const val CAPTION_ACTION = "galaxy-caption-action"

    fun step(depth: SkyDepth): String = "galaxy-step-${depth.name.lowercase()}"

    // ── Anchors on the canvas ────────────────────────────────────────────────────────────────
    //
    // A canvas has no nodes, so the things a tap can land on are marked by sizeless anchors laid
    // over it at their screen positions — exactly the things `SkyScene.nearest` can return at the
    // depth: galaxies at the universe, regions at the galaxy, stars past it, and the worlds of the
    // selected system once the orbit view is in. A robot clicks the anchor and the canvas beneath
    // it gets the tap.
    fun galaxy(galaxy: Int): String = "galaxy-$galaxy"

    fun region(galaxy: Int, region: Int): String = "galaxy-region-$galaxy-$region"

    fun system(galaxy: Int, system: Int): String = "galaxy-system-$galaxy-$system"

    fun world(at: GalaxyCoordinate): String = "galaxy-world-${at.galaxy}-${at.system}-${at.slot}"

    // **The dispatch sheet's own tags left with the sheet** — they are `DispatchTestTags` in
    // `:client:dispatch:ui`, because the sheet is raised from Fleets as well and a handle reading
    // `galaxy-` would name the wrong screen half the time.
}
