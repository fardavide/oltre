package dev.fardavide.oltre.protocol

import dev.fardavide.oltre.core.Experience
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExperienceReadingTest {

    @Test
    fun `known zero experience remains distinct from an unknown reading`() {
        val reading = Protocol.json.decodeFromString(ExperienceReading.serializer(), """{"type":"Known","earned":0}""")

        assertEquals(ExperienceReading.Known(Experience.NONE), reading)
    }

    @Test
    fun `an experience reading cannot be null`() {
        assertFailsWith<SerializationException> {
            Protocol.json.decodeFromString(ExperienceReading.serializer(), "null")
        }
    }

    @Test
    fun `an experience reading cannot be raw points`() {
        assertFailsWith<SerializationException> {
            Protocol.json.decodeFromString(ExperienceReading.serializer(), "340")
        }
    }

    @Test
    fun `an experience reading with an unknown discriminator is refused`() {
        assertFailsWith<SerializationException> {
            Protocol.json.decodeFromString(ExperienceReading.serializer(), """{"type":"Estimated","earned":340}""")
        }
    }

    @Test
    fun `known earned points retain the nonnegative experience guard`() {
        assertFailsWith<IllegalArgumentException> {
            Protocol.json.decodeFromString(ExperienceReading.serializer(), """{"type":"Known","earned":-1}""")
        }
    }
}
