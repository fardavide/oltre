package dev.fardavide.oltre.client.save.data

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

// Integration, not unit: this is the one place the save format meets a real filesystem —
// temporary directories, partial writes, a file that is not there yet. `GameStoreTest` covers
// the same store against `FakeSaveFile` and stays a unit test.
class FileSaveFileIntegrationTest {

    @Test
    fun `a file that was never written reads as nothing saved`() = runTest {
        // given
        val file = FileSaveFile(File(temporaryDirectory(), "colony.json"))

        // when / then
        assertNull(file.read())
    }

    @Test
    fun `text written reads back unchanged`() = runTest {
        // given
        val file = FileSaveFile(File(temporaryDirectory(), "colony.json"))

        // when
        file.write("""{"schemaVersion":1}""")

        // then
        assertEquals("""{"schemaVersion":1}""", file.read())
    }

    @Test
    fun `writing creates the directory the save lives in`() = runTest {
        // given — first launch on a machine that has never run the game
        val directory = File(temporaryDirectory(), "Oltre")
        val file = FileSaveFile(File(directory, "colony.json"))

        // when
        file.write("colony")

        // then
        assertEquals("colony", file.read())
    }

    @Test
    fun `writing leaves no temporary file behind`() = runTest {
        // given
        val directory = temporaryDirectory()
        val file = FileSaveFile(File(directory, "colony.json"))

        // when
        file.write("colony")

        // then
        assertFalse(File(directory, "colony.json.tmp").exists())
    }

    @Test
    fun `a second write replaces the first`() = runTest {
        // given
        val file = FileSaveFile(File(temporaryDirectory(), "colony.json"))

        // when
        file.write("first")
        file.write("second")

        // then
        assertEquals("second", file.read())
    }

    @Test
    fun `clearing removes the colony from disk`() = runTest {
        // given
        val directory = temporaryDirectory()
        val file = FileSaveFile(File(directory, "colony.json"))
        file.write("colony")

        // when
        file.clear()

        // then
        assertNull(file.read())
        assertFalse(File(directory, "colony.json").exists())
    }

    @Test
    fun `clearing a file that is not there is not an error`() = runTest {
        // given
        val file = FileSaveFile(File(temporaryDirectory(), "colony.json"))

        // when
        file.clear()

        // then
        assertNull(file.read())
    }

    @Test
    fun `clearing sweeps up a temporary file a killed process left behind`() = runTest {
        // given — what a write interrupted between `writeText` and `move` leaves on disk
        val directory = temporaryDirectory()
        val file = FileSaveFile(File(directory, "colony.json"))
        file.write("colony")
        File(directory, "colony.json.tmp").writeText("half a colony")

        // when
        file.clear()

        // then — a reset that left it would hand the next write a stale file to overwrite
        assertFalse(File(directory, "colony.json.tmp").exists())
        assertNull(file.read())
    }

    @Test
    fun `a colony written after a clear is readable`() = runTest {
        // given
        val file = FileSaveFile(File(temporaryDirectory(), "colony.json"))
        file.write("first")

        // when
        file.clear()
        file.write("second")

        // then — clearing must not leave the directory in a state a write cannot recover from
        assertEquals("second", file.read())
    }

    private fun temporaryDirectory(): File = createTempDirectory("oltre-save").toFile().also { it.deleteOnExit() }
}
