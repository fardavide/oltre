package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.AllianceMember
import dev.fardavide.oltre.protocol.AllianceMemberId
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.ExperienceReading
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
}
