package dev.fardavide.oltre.client

// A mid-game colony's stocks. The scaffold's tests are about the frame rather than the numbers in
// it, so they all share one rail instead of each inventing its own.
//
// Unthrottled, deliberately: these are the frame's tests, and a power shortage is a state of the
// rail rather than of the scaffold around it. What the amber marks look like is asserted where it
// belongs, by the `resource_rail_throttled` baseline.
internal val testResourceRailUiState = ResourceRailUiState(
    metal = "15,534",
    metalRatePerHour = "+950/h",
    crystal = "6,286",
    crystalRatePerHour = "+304/h",
    deuterium = "732",
    deuteriumRatePerHour = "+72/h",
    throttled = false,
)
