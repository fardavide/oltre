package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.AllianceRole
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

internal object AllianceRules {

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
