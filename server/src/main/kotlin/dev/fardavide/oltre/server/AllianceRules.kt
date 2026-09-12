package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.AllianceRole
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

internal object AllianceRules {

    private val INACTIVITY = 30.days

    fun canRemove(caller: AllianceRole, target: AllianceRole): Boolean =
        caller == AllianceRole.ADMIN && target == AllianceRole.MEMBER

    fun isActive(lastSyncedAt: Instant, now: Instant): Boolean = lastSyncedAt >= now - INACTIVITY
}
