package dev.fardavide.oltre.client.save.data

import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GameSave
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.PlaceholderBalance
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.startUpgrade
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class GameStoreTest {

    @Test
    fun `a player who has never saved has no colony to load`() = runTest {
        // given
        val store = GameStore(FakeSaveFile())

        // when / then
        assertNull(store.load())
    }

    @Test
    fun `a saved colony loads back identical`() = runTest {
        // given
        val file = FakeSaveFile()
        val store = GameStore(file)
        val snapshot = GameSnapshot(lastUpdatedAt = EPOCH, state = midBuildState())

        // when
        store.save(snapshot)

        // then
        assertEquals(snapshot, store.load())
    }

    @Test
    fun `saving again replaces the previous colony`() = runTest {
        // given
        val file = FakeSaveFile()
        val store = GameStore(file)
        val later = GameSnapshot(lastUpdatedAt = EPOCH + 3.hours, state = GameState.initial())

        // when
        store.save(GameSnapshot(lastUpdatedAt = EPOCH, state = GameState.initial()))
        store.save(later)

        // then
        assertEquals(later, store.load())
        assertEquals(2, file.writeCount)
    }

    @Test
    fun `a corrupt save reads as no colony rather than crashing the app`() = runTest {
        // given
        val store = GameStore(FakeSaveFile("{ this is not a colony"))

        // when / then
        assertNull(store.load())
    }

    @Test
    fun `a save written by a newer build reads as no colony`() = runTest {
        // given
        val fromTheFuture = GameSave.encode(
            GameSnapshot(
                schemaVersion = GameSave.SCHEMA_VERSION + 1,
                lastUpdatedAt = EPOCH,
                state = GameState.initial(),
            ),
        )

        // when / then
        assertNull(GameStore(FakeSaveFile(fromTheFuture)).load())
    }

    @Test
    fun `a colony from a retired schema reads as no colony so the app starts fresh`() = runTest {
        // given a 0.0.7 save, from before the rebalance that retired schema 1
        val beforeTheRebalance = """{"schemaVersion":1,"lastUpdatedAt":"1970-01-01T00:00:00Z","state":{""" +
            """"resources":{"metalFine":0,"crystalFine":0,"deuteriumFine":0},""" +
            """"buildings":{"metalMine":1,"crystalMine":1,"deuteriumSynthesizer":1,""" +
            """"solarPlant":1,"roboticsFactory":0,"naniteFactory":0},""" +
            """"buildQueue":null,"returningFleet":null,"eventLog":[]}}"""

        // when / then
        assertNull(GameStore(FakeSaveFile(beforeTheRebalance)).load())
    }

    @Test
    fun `an empty save file reads as no colony`() = runTest {
        assertNull(GameStore(FakeSaveFile("")).load())
    }

    private fun midBuildState(): GameState {
        val cost = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(2))
        val funded = GameState.initial().copy(
            resources = Resources.of(metal = cost.metal, crystal = cost.crystal),
        )
        return assertIs<StartUpgradeResult.Started>(
            startUpgrade(funded, BuildingType.METAL_MINE, at = EPOCH),
        ).state
    }

    private companion object {
        val EPOCH = Instant.fromEpochMilliseconds(0)
    }
}
