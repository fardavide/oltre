package dev.fardavide.oltre.client

// The five destinations the mockup's bottom bar carries. Navigation is the shell's, not a
// feature's: the shell is the only module that may see every feature, so a tab set naming all of
// them can live nowhere else.
//
// `pendingWork` is null once a tab has a real screen behind it — today the Colony, Research and
// Galaxy do, and this table is the honest record of that. The strings are **placeholder copy**:
// what a screen says to the player is content, and content is Davide's. They say the one true thing
// an unbuilt tab can, which is what will be there.
enum class OltreTab(val label: String, val pendingWork: String?) {
    COLONY(label = "Colony", pendingWork = null),
    RESEARCH(label = "Research", pendingWork = null),
    SHIPYARD(label = "Shipyard", pendingWork = "Ship construction lands here."),
    GALAXY(label = "Galaxy", pendingWork = null),
    FLEETS(label = "Fleets", pendingWork = "Fleets in flight land here."),
}
