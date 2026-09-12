package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.Experience
import dev.fardavide.oltre.protocol.AllianceMember
import dev.fardavide.oltre.protocol.AllianceMemberId
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.ExperienceReading
import dev.fardavide.oltre.protocol.PlayerProfile
import dev.fardavide.oltre.protocol.JoinRequest
import kotlin.time.Instant

internal fun memberFrom(
    id: AllianceMemberId,
    profile: PlayerProfile,
    role: AllianceRole,
    points: Long?,
    lastSyncedAt: Instant,
): AllianceMember = AllianceMember(
    id = id,
    profile = profile,
    role = role,
    experience = points?.let { ExperienceReading.Known(Experience(it)) } ?: ExperienceReading.Unknown,
    lastSyncedAt = lastSyncedAt,
)

internal fun rosterFor(
    role: AllianceRole,
    members: List<AllianceMember>,
    pending: List<JoinRequest>,
): RosterRead.Present = RosterRead.Present(
    members = members,
    pending = when (role) {
        AllianceRole.FOUNDER,
        AllianceRole.ADMIN,
        -> pending
        AllianceRole.MEMBER -> null
    },
)
