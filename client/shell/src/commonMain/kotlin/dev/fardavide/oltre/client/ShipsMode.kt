package dev.fardavide.oltre.client

// Which half of the merged Ships destination is showing. `ShipsScreen` holds one of these; nothing
// below the shell needs to know the destination was ever two tabs.
internal enum class ShipsMode {
    SHIPYARD,
    FLEETS,
}
