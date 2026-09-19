package dev.fardavide.oltre.client.save.data

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

// **The store over a real file, which nothing drove until now.** `PreferencesStoreTest` covers the
// same store against `FakeSaveFile` and is a unit test by the taxonomy's own rule — a fake is not a
// boundary — and `FileSaveFileIntegrationTest` covers the file with no store above it. Between the
// two sat the thing the app actually does on every launch: encode a record, put it through a real
// filesystem, and read it back.
//
// **What the fake cannot test, and it is the whole reason this file exists.** A fake hands back the
// string it was handed, so a round trip through it proves the codec agrees with itself. It cannot
// show that the bytes survive a write, a rename and a read on a real disk, and it cannot be handed a
// file written by *another build* — which is exactly the case `PreferencesStore.Record` carries a
// default per field for, and the one whose failure costs a player the landing they chose.
class PreferencesStoreIntegrationTest {

    @Test
    fun `a machine that has never run the game remembers nothing`() = runTest {
        // given
        val store = PreferencesStore(FileSaveFile(File(temporaryDirectory(), "preferences.json")))

        // when / then
        assertEquals(Preferences.NONE, store.load())
    }

    @Test
    fun `every field survives a real write and a real read`() = runTest {
        // given
        val store = PreferencesStore(FileSaveFile(File(temporaryDirectory(), "preferences.json")))
        val remembered = Preferences(
            galaxyLanding = "SYSTEM",
            lastSeenVersion = "0.28.1",
            provider = "APPLE",
            lastReachedAt = "1789000000000",
        )

        // when
        store.save(remembered)

        // then
        assertEquals(remembered, store.load())
    }

    // **The forward-compatibility promise, tested against a file rather than against a fake.** A
    // build that knew fewer preferences wrote fewer keys, and `Record`'s per-field defaults are what
    // stop that file failing to parse and taking the fields it *did* carry down with it. Written here
    // as literal JSON on disk, because that is the only form the claim is actually about.
    @Test
    fun `a file from a build that knew fewer preferences keeps the fields it had`() = runTest {
        // given — what 0.18 wrote, before three of the four fields existed
        val directory = temporaryDirectory()
        File(directory, "preferences.json").writeText("""{"galaxyLanding":"SYSTEM"}""")
        val store = PreferencesStore(FileSaveFile(File(directory, "preferences.json")))

        // when
        val loaded = store.load()

        // then — the landing survives the upgrade, which is what the defaults are for
        assertEquals(Preferences.NONE.copy(galaxyLanding = "SYSTEM"), loaded)
    }

    // The other half of the same tolerance: a file from a build that knew *more*. It is what a
    // downgrade leaves on disk, and the unknown key has to be ignored rather than fatal.
    @Test
    fun `a file from a newer build reads rather than resetting the lot`() = runTest {
        // given
        val directory = temporaryDirectory()
        File(directory, "preferences.json")
            .writeText("""{"galaxyLanding":"SYSTEM","somethingThisBuildHasNeverHeardOf":true}""")
        val store = PreferencesStore(FileSaveFile(File(directory, "preferences.json")))

        // when / then
        assertEquals(Preferences.NONE.copy(galaxyLanding = "SYSTEM"), store.load())
    }

    // **A preference that cannot be read costs a first tap, so it answers `NONE` rather than
    // raising** — the store's own argument, and the file is where it is worth proving: half a write
    // that a killed process left behind is a real thing to find on a real disk.
    @Test
    fun `a file that is not JSON reads as nothing remembered`() = runTest {
        // given
        val directory = temporaryDirectory()
        File(directory, "preferences.json").writeText("""{"galaxyLanding":""")
        val store = PreferencesStore(FileSaveFile(File(directory, "preferences.json")))

        // when / then
        assertEquals(Preferences.NONE, store.load())
    }

    @Test
    fun `a second save replaces the first on disk`() = runTest {
        // given
        val store = PreferencesStore(FileSaveFile(File(temporaryDirectory(), "preferences.json")))
        store.save(Preferences.NONE.copy(galaxyLanding = "SYSTEM"))

        // when
        store.save(Preferences.NONE.copy(galaxyLanding = "GALAXY"))

        // then
        assertEquals(Preferences.NONE.copy(galaxyLanding = "GALAXY"), store.load())
    }

    private fun temporaryDirectory(): File = createTempDirectory("oltre-preferences").toFile().also { it.deleteOnExit() }
}
