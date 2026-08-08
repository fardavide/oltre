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

// The case the merged stock-and-rate line has to survive. Once the rate sits beside the stock
// rather than under it, the pair is what has to fit the cell — and at 320dp, a third of that is
// not enough for six figures plus a rate. These are the stocks the design measured the overflow
// with, so they are the ones the assertion uses.
internal val sixFigureResourceRailUiState = ResourceRailUiState(
    metal = "147,169",
    metalRatePerHour = "+12,400/h",
    crystal = "89,412",
    crystalRatePerHour = "+6,180/h",
    deuterium = "112,006",
    deuteriumRatePerHour = "+900/h",
    throttled = false,
)
