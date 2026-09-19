package dev.fardavide.oltre.client.save.data

import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// **The colony over a real file, which is the one path every launch of the game actually takes and
// which nothing drove until now.** `GameStoreTest` covers the same store against `FakeSaveFile` —
// a fake is not a boundary, so it is a unit test — and `FileSaveFileIntegrationTest` covers the file
// with no store above it. The pairing was untested, and it is the pairing that owns a player's
// colony.
//
// **What the fake cannot show.** It hands back the string it was handed, so a round trip through it
// proves `GameSave` agrees with itself. It cannot prove the save survives a write, a rename and a
// read on a real disk, and it cannot be handed the half-written file a killed process leaves behind
// — which is the case `GameStore.load` answers `null` for, and the answer that decides whether a
// player starts a new colony or the app raises on launch.
class GameStoreIntegrationTest {

    @Test
    fun `a machine that has never run the game has no colony`() = runTest {
        // given
        val store = GameStore(FileSaveFile(File(temporaryDirectory(), "colony.json")))

        // when / then
        assertNull(store.load())
    }

    @Test
    fun `a colony survives a real write and a real read`() = runTest {
        // given
        val store = GameStore(FileSaveFile(File(temporaryDirectory(), "colony.json")))
        val snapshot = GameSnapshot(lastUpdatedAt = EPOCH + 3.hours, state = GameState.initial(SEED))

        // when
        store.save(snapshot)

        // then — every field, through the codec and the filesystem both
        assertEquals(snapshot, store.load())
    }

    @Test
    fun `a second save replaces the colony on disk`() = runTest {
        // given
        val store = GameStore(FileSaveFile(File(temporaryDirectory(), "colony.json")))
        store.save(GameSnapshot(lastUpdatedAt = EPOCH, state = GameState.initial(SEED)))
        val later = GameSnapshot(lastUpdatedAt = EPOCH + 9.hours, state = GameState.initial(SEED))

        // when
        store.save(later)

        // then
        assertEquals(later, store.load())
    }

    // **A save that cannot be read is no save at all**, which is the store's own decision and the
    // one worth proving against a disk: this is what a process killed mid-write leaves behind, and
    // the alternative to answering `null` is raising on launch over it.
    @Test
    fun `half a file on disk reads as no colony rather than raising`() = runTest {
        // given
        val directory = temporaryDirectory()
        File(directory, "colony.json").writeText("""{"schemaVersion":21,"lastUpdatedAt":""")

        // when / then
        assertNull(GameStore(FileSaveFile(File(directory, "colony.json"))).load())
    }

    // A colony from a schema this build has retired starts over, by the same door and for the same
    // reason — `DecodeResult.Obsolete` and `Failure` leave the player in one place.
    @Test
    fun `a colony this build has retired reads as no colony`() = runTest {
        // given
        val directory = temporaryDirectory()
        File(directory, "colony.json").writeText("""{"schemaVersion":1,"lastUpdatedAt":"$EPOCH"}""")

        // when / then
        assertNull(GameStore(FileSaveFile(File(directory, "colony.json"))).load())
    }

    // Reset, on a real disk: the next launch has to meet the first-launch path rather than a file
    // the store can no longer read.
    @Test
    fun `clearing leaves the machine as it was before the first colony`() = runTest {
        // given
        val directory = temporaryDirectory()
        val store = GameStore(FileSaveFile(File(directory, "colony.json")))
        store.save(GameSnapshot(lastUpdatedAt = EPOCH, state = GameState.initial(SEED)))

        // when
        store.clear()

        // then
        assertNull(store.load())
    }

    @Test
    fun `a colony saved after a reset is readable`() = runTest {
        // given
        val store = GameStore(FileSaveFile(File(temporaryDirectory(), "colony.json")))
        store.save(GameSnapshot(lastUpdatedAt = EPOCH, state = GameState.initial(SEED)))
        store.clear()
        val second = GameSnapshot(lastUpdatedAt = EPOCH + 1.hours, state = GameState.initial(SEED))

        // when
        store.save(second)

        // then — clearing must not leave a directory a save cannot recover from
        assertEquals(second, store.load())
    }

    private fun temporaryDirectory(): File = createTempDirectory("oltre-colony").toFile().also { it.deleteOnExit() }

    private companion object {

        val EPOCH = Instant.fromEpochMilliseconds(0)

        // Fixed, never `Clock.System.now()`: a seed taken from the moment the suite runs is the
        // failure mode that made behaviour branch coverage a coin flip at 0.15.
        val SEED = GalaxySeed(20_260_807)
    }
}
