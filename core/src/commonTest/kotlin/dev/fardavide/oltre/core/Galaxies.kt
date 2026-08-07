package dev.fardavide.oltre.core

// `GameState.initial` requires a galaxy seed, deliberately: a default is how every player quietly
// ends up in the same galaxy, and core cannot mint one for itself. Almost no test cares which map
// it gets, though, so this gives the tests their no-argument call back — as an *extension on the
// companion*, which means it exists on the test classpath only and production still has to say
// which seed it means. The member wins wherever an argument is passed, so nothing here shadows it.
internal val TEST_GALAXY_SEED: GalaxySeed = GalaxySeed(20_260_807)

internal fun GameState.Companion.initial(): GameState = GameState.initial(TEST_GALAXY_SEED)

// A second seed, for the tests that have to show two seeds disagree.
internal val OTHER_GALAXY_SEED: GalaxySeed = GalaxySeed(-4_815_162_342)

// The first coordinate of the test galaxy that actually holds a world, so a test that needs *a*
// world does not have to hunt for one. Scans in coordinate order, which keeps it stable.
internal fun firstWorld(seed: GalaxySeed): World {
    for (galaxy in 1..GalaxyBalance.GALAXIES) {
        for (system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
            for (slot in 1..GalaxyBalance.SLOTS_PER_SYSTEM) {
                worldAt(seed, GalaxyCoordinate(galaxy, system, slot))?.let { return it }
            }
        }
    }
    error("seed $seed generated no worlds at all")
}
