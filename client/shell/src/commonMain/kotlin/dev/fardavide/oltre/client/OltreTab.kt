package dev.fardavide.oltre.client

// The five destinations the mockup's bottom bar carries. Navigation is the shell's, not a
// feature's: the shell is the only module that may see every feature, so a tab set naming all of
// them can live nowhere else.
//
// **The `pendingWork` column is gone, and its absence is the release.** Every tab carried a
// nullable string saying what would be there one day, and `UnbuiltTabScreen` drew it — deliberately
// a real screen rather than a blank one, because a player who taps a tab has to learn it exists and
// is not built yet. Shipyard and Fleets were the last two to hold one, and with 0.8.0 the column
// would be five nulls: a field that can only ever say "no" is a field, not a table.
//
// If a sixth destination ever arrives ahead of its screen, the honest empty state comes back with it
// — and it should come back as that tab's own, in that tab's own module, rather than as a column
// here. What made the old one shell-shaped was that two tabs shared it.
enum class OltreTab(val label: String) {
    COLONY(label = "Colony"),
    RESEARCH(label = "Research"),
    SHIPYARD(label = "Shipyard"),
    GALAXY(label = "Galaxy"),
    FLEETS(label = "Fleets"),
}
