package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.CommanderName
import dev.fardavide.oltre.protocol.MarkPreset
import dev.fardavide.oltre.protocol.PlayerMark
import dev.fardavide.oltre.protocol.PlayerProfile
import dev.fardavide.oltre.protocol.Protocol
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileRowTest {

    @Test
    fun `stored chosen name and mark remain the chosen profile`() {
        val name = CommanderName("Ada di Notte")
        val chosen = PlayerMark.Preset(MarkPreset.SEXTANT)
        val profile = PlayerProfile(name, chosen)
        val mark = Protocol.json.encodeToString(PlayerMark.serializer(), chosen)

        assertEquals(profile, profileFrom(name.value, mark))
    }

    @Test
    fun `an unfilled stored profile remains unfilled`() {
        assertEquals(PlayerProfile(null, null), profileFrom(null, null))
    }

    @Test
    fun `an unreadable name preserves the readable mark`() {
        val mark = PlayerMark.Preset(MarkPreset.SEXTANT)
        val document = Protocol.json.encodeToString(PlayerMark.serializer(), mark)

        assertEquals(PlayerProfile(null, mark), profileFrom(" ", document))
    }

    @Test
    fun `an unreadable mark preserves the readable name`() {
        assertEquals(PlayerProfile(CommanderName("Ada di Notte"), null), profileFrom("Ada di Notte", "{\"type\":\"Future\"}"))
    }
}
