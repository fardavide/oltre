package dev.fardavide.oltre.client.save.data

import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxySeed
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
        val later = GameSnapshot(lastUpdatedAt = EPOCH + 3.hours, state = freshState())

        // when
        store.save(GameSnapshot(lastUpdatedAt = EPOCH, state = freshState()))
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
                state = freshState(),
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

    @Test
    fun `a cleared store has no colony to load`() = runTest {
        // The whole of what the debug menu's reset is: after this the store answers exactly what it
        // answers on a first launch, so the shell needs no second path to build a fresh colony with.
        val file = FakeSaveFile()
        val store = GameStore(file)
        store.save(GameSnapshot(lastUpdatedAt = EPOCH, state = midBuildState()))

        // when
        store.clear()

        // then
        assertNull(store.load())
        assertEquals(1, file.clearCount)
    }

    @Test
    fun `clearing a store that never held a colony is not an error`() = runTest {
        // A player can reset twice, and the second one has nothing to delete.
        val store = GameStore(FakeSaveFile())

        store.clear()
        store.clear()

        assertNull(store.load())
    }

    @Test
    fun `a colony saved after a reset is a new colony rather than the old one amended`() = runTest {
        // given
        val file = FakeSaveFile()
        val store = GameStore(file)
        store.save(GameSnapshot(lastUpdatedAt = EPOCH, state = midBuildState()))

        // when
        store.clear()
        val fresh = GameSnapshot(lastUpdatedAt = EPOCH + 3.hours, debugUsed = true, state = freshState())
        store.save(fresh)

        // then — nothing of the build survives, and the new colony carries the debug mark
        assertEquals(fresh, store.load())
    }

    // `GameState.initial` takes a galaxy seed rather than defaulting one, so production cannot found
    // every colony in the same galaxy. The store writes whatever map it is handed.
    private fun freshState(): GameState = GameState.initial(GalaxySeed(20_260_807))

    private fun midBuildState(): GameState {
        val cost = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(2))
        val funded = freshState().copy(
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
