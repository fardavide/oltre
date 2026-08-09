package dev.fardavide.oltre.client.debug.domain

import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.startUpgrade
import kotlin.test.assertIs
import kotlin.time.Instant

// `GameState.initial` requires a galaxy seed and no test here cares which map it gets. The fifth
// copy of this one-liner across the client modules — `status.md` has it filed under the duplication
// `:core-testing` exists to remove, and removing it is a build-layout change rather than this one.
internal val TEST_GALAXY_SEED: GalaxySeed = GalaxySeed(20_260_807)

internal fun freshColony(): GameState = GameState.initial(TEST_GALAXY_SEED)

internal val EPOCH: Instant = Instant.fromEpochSeconds(0)

// A colony with something in flight, which is the only interesting input to `skipAhead`. The
// starting stock affords a first metal mine, so this is the shortest way to a state with a
// completion instant in it; `assertIs` is here so a balance change that made it unaffordable fails
// this helper loudly rather than silently turning every test below into the empty case.
internal fun buildingColony(at: Instant = EPOCH): GameState =
    assertIs<StartUpgradeResult.Started>(
        startUpgrade(freshColony(), BuildingType.METAL_MINE, at = at),
    ).state
