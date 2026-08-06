package dev.fardavide.oltre.client.save.data

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class FileSaveFileTest {

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

    private fun temporaryDirectory(): File = createTempDirectory("oltre-save").toFile().also { it.deleteOnExit() }
}
