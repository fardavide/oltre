package dev.fardavide.oltre.client

// A mid-game colony's stocks. The scaffold's tests are about the frame rather than the numbers in
// it, so they all share one rail instead of each inventing its own.
internal val testResourceRailUiState = ResourceRailUiState(
    metal = "15,534",
    metalRatePerHour = "+950/h",
    crystal = "6,286",
    crystalRatePerHour = "+304/h",
    deuterium = "732",
    deuteriumRatePerHour = "+72/h",
    // The scaffold's tests are about the frame, and a colony with power to spare is the state
    // that says nothing about power — which is what keeps these baselines about the frame.
    throttled = false,
)
