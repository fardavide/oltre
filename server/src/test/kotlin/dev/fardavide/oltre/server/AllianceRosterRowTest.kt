package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.Experience
import dev.fardavide.oltre.protocol.AllianceMember
import dev.fardavide.oltre.protocol.AllianceMemberId
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.CommanderName
import dev.fardavide.oltre.protocol.ExperienceReading
import dev.fardavide.oltre.protocol.MarkPreset
import dev.fardavide.oltre.protocol.PlayerMark
import dev.fardavide.oltre.protocol.PlayerProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class AllianceRosterRowTest {

    @Test
    fun `a row with missing experience preserves unknown and the member details`() {
        val id = AllianceMemberId("surrogate-member-17")
        val profile = PlayerProfile(name = null, mark = null)
        val lastSyncedAt = Instant.parse("2026-09-05T09:17:23.123456789Z")

        val member = memberFrom(
            id = id,
            profile = profile,
            role = AllianceRole.ADMIN,
            points = null,
            lastSyncedAt = lastSyncedAt,
        )

        assertEquals(
            AllianceMember(
                id = id,
                profile = profile,
                role = AllianceRole.ADMIN,
                experience = ExperienceReading.Unknown,
                lastSyncedAt = lastSyncedAt,
            ),
            member,
        )
    }

    @Test
    fun `a row with stored zero experience preserves a known reading and the chosen member details`() {
        val id = AllianceMemberId("surrogate-member-23")
        val profile = PlayerProfile(
            name = CommanderName("Ada di Notte"),
            mark = PlayerMark.Preset(MarkPreset.SEXTANT),
        )
        val lastSyncedAt = Instant.parse("2026-09-05T11:17:23.123456789Z")

        val member = memberFrom(
            id = id,
            profile = profile,
            role = AllianceRole.FOUNDER,
            points = 0L,
            lastSyncedAt = lastSyncedAt,
        )

        assertEquals(
            AllianceMember(
                id = id,
                profile = profile,
                role = AllianceRole.FOUNDER,
                experience = ExperienceReading.Known(Experience.NONE),
                lastSyncedAt = lastSyncedAt,
            ),
            member,
        )
    }
}
