package dev.fardavide.oltre.server

import com.ibm.icu.lang.UCharacter
import dev.fardavide.oltre.protocol.AllianceName

import dev.fardavide.oltre.protocol.AllianceRole
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

internal object AllianceRules {

    fun normalise(name: AllianceName): CanonicalAllianceName = CanonicalAllianceName(
        UCharacter.foldCase(name.value.trim().replace(Regex("(?U)\\s+"), " "), true),
    )

    const val SEAT_CAP = 20

    private val INACTIVITY = 30.days

    fun canRemove(caller: AllianceRole, target: AllianceRole): Boolean = when (caller) {
        AllianceRole.FOUNDER -> when (target) {
            AllianceRole.FOUNDER -> false
            AllianceRole.ADMIN, AllianceRole.MEMBER -> true
        }
        AllianceRole.ADMIN -> when (target) {
            AllianceRole.FOUNDER, AllianceRole.ADMIN -> false
            AllianceRole.MEMBER -> true
        }
        AllianceRole.MEMBER -> false
    }

    fun isActive(lastSyncedAt: Instant, now: Instant): Boolean = lastSyncedAt >= now - INACTIVITY
}

@JvmInline
internal value class CanonicalAllianceName(val value: String)
